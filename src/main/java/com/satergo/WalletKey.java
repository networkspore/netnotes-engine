package com.satergo;


import io.netnotes.engine.Stages;
import io.netnotes.engine.apps.ergoWallet.ErgoWallets;

import com.satergo.ergo.ErgoInterface;
import com.satergo.extra.AESEncryption;

import javafx.scene.image.Image;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import org.ergoplatform.ErgoAddressEncoder;
import org.ergoplatform.P2PKAddress;
import org.ergoplatform.appkit.*;
import org.ergoplatform.sdk.SecretString;
import org.ergoplatform.sdk.wallet.secrets.DerivationPath;
import org.ergoplatform.sdk.wallet.secrets.ExtendedPublicKey;
import org.ergoplatform.sdk.wallet.secrets.ExtendedSecretKey;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 *
 * ORIGINAL MESSAGE: This API is open for extending by third-parties, but
 * consult the wallet-format.md and apply for an ID first.
 *
 * NETNOTES EDIT: wallet-format.md is not included in this distribution, go to
 * the original satergo github for details.
 */
public abstract class WalletKey {


    private static final HashMap<Integer, Type<?>> types = new HashMap<>();

    static {
        try {
            Class.forName(Type.class.getName());
        } catch (ClassNotFoundException ignored) {
        }
    }

    public static WalletKey deserialize(byte[] encrypted, ByteBuffer decrypted) {
        Type<?> type = types.get(decrypted.getShort() & 0xFFFF);
        if (type == null) {
            throw new IllegalArgumentException("Unknown wallet type with ID " + decrypted.getShort(0));
        }
        WalletKey key = type.construct();
        key.encrypted = encrypted;
        key.initCaches(decrypted);
        return key;
    }

    public static class Type<T extends WalletKey> {

        public static final Type<Local> LOCAL = registerType("LOCAL", 0, Local::new);
//		public static final Type<ViewOnly> VIEW_ONLY = registerType("VIEW_ONLY", 1, ViewOnly::new);
//		public static final Type<Ledger> LEDGER = registerType("LEDGER", 10, Ledger::new);

        private final String name;
        private final Supplier<T> constructor;

        private Type(String name, Supplier<T> constructor) {
            if (!name.toUpperCase(Locale.ROOT).equals(name)) {
                throw new IllegalArgumentException("Name must be all uppercase.");
            }
            this.name = name;
            this.constructor = Objects.requireNonNull(constructor, "constructor");
        }

        public String name() {
            return name;
        }

        public T construct() {
            return constructor.get();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Type<?> t && name.equals(t.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static <T extends WalletKey> Type<T> registerType(String name, int id, Supplier<T> constructor) {
        Type<T> type = new Type<>(name, constructor);
        if (types.containsKey(id)) {
            throw new IllegalArgumentException("Type ID " + id + " is already used by " + types.get(id));
        }
        if (types.values().stream().map(Type::name).anyMatch(n -> n.equals(name))) {
            throw new IllegalArgumentException("Type name " + name + " is already used");
        }
        types.put(id, type);
        return type;
    }

    private byte[] encrypted;
    protected final Type<?> type;

    private WalletKey(Type<?> type) {
        this.type = type;
    }

    protected void initEncryptedData(byte[] encryptedData) {
        if (this.encrypted != null) {
            throw new IllegalStateException();
        }
        this.encrypted = encryptedData;
    }

    /**
     * @implNote The data begins at index 4. Index 0-4 is the wallet type ID
     * stored as int. It must only be used for preparing things like caches (for
     * example, extended public key). The ByteBuffer is already positioned at
     * the data beginning.
     */
    public void initCaches(ByteBuffer data) {
    }


    public abstract SignedTransaction signWithPassword(SecretString password, BlockchainContext ctx, UnsignedTransaction unsignedTx, Collection<Integer> addressIndexes) throws Exception;

    public abstract Address derivePublicAddress(NetworkType networkType, int index) throws Exception;

    public abstract void viewWalletMnemonic(SecretString pass) throws Exception;

    public abstract WalletKey changedPassword(SecretString currentPassword, SecretString newPassword) throws Exception;

    public abstract void getMnemonic(ExecutorService execService, EventHandler<WorkerStateEvent> onMnemonic, EventHandler<WorkerStateEvent> onFailed);

    public byte[] copyIv() {
        return Arrays.copyOf(encrypted, 12);
    }

    public byte[] encrypted() {
        return encrypted;
    }

    /**
     * The key is encrypted and embedded into the wallet file The default
     * behavior of this class is: - keep extended public key in memory for
     * address derivations (as such, never throws Exception for
     * derivePublicAddress) - ask user for password when needed - keep the
     * secret key cached in memory for 1 minute since last use
     */
    public static class Local extends WalletKey {

        private static final int ID = 0;

        /**
         * Non-standard (legacy incorrect address derivation implementation in
         * ergo-wallet)
         */
        private boolean nonstandard;

        private ExtendedPublicKey parentExtPubKey;

        private Local() {
            super(Type.LOCAL);
        }

        private static boolean checkFormat(Mnemonic mnemonic) {
            return String.join(" ", mnemonic.getPhrase().toStringUnsecure().split(" ")).equals(mnemonic.getPhrase().toStringUnsecure());
        }

        @Override
        public void initCaches(ByteBuffer data) {
            nonstandard = data.get() == 1;
            Mnemonic mnemonic = readMnemonic(data);
            checkFormat(mnemonic);
            initParentExtPubKey(mnemonic);
        }

        private void initParentExtPubKey(Mnemonic mnemonic) {
            ExtendedSecretKey rootSecret = ExtendedSecretKey.deriveMasterKey(mnemonic.toSeed(), nonstandard);
            parentExtPubKey = ((ExtendedSecretKey) rootSecret.derive(DerivationPath.fromEncoded("m/44'/429'/0'/0").get())).publicKey();
        }

        public static Local create(boolean nonstandard, Mnemonic mnemonic, SecretString password) throws Exception {

            checkFormat(mnemonic);
            Local key = new Local();
            key.nonstandard = nonstandard;
            byte[] iv = AESEncryption.generateNonce12();
            // StandardCharsets.UTF_8.encode(CharBuffer.wrap(mnemonic.getPhrase().getData())) is not used because
            // for some reason it adds multiple null characters at the end
            byte[] mnPhraseBytes = mnemonic.getPhrase().toStringUnsecure().getBytes(StandardCharsets.UTF_8);
            byte[] mnPasswordBytes = mnemonic.getPassword().toStringUnsecure().getBytes(StandardCharsets.UTF_8);
            ByteBuffer buffer = ByteBuffer.allocate(2 + 1 + 2 + mnPhraseBytes.length + 2 + mnPasswordBytes.length)
                    .putShort((short) ID)
                    .put((byte) (nonstandard ? 1 : 0))
                    .putShort((short) mnPhraseBytes.length)
                    .put(mnPhraseBytes)
                    .putShort((short) mnPasswordBytes.length)
                    .put(mnPasswordBytes);
            key.initEncryptedData(AESEncryption.encryptData(iv, AESEncryption.generateSecretKey(password.getData(), iv), buffer.array()));
            key.initParentExtPubKey(mnemonic);
            return key;
           
        }

        private Mnemonic getMnemonic(SecretString password) throws Exception {
         
            SecretKey secretKey = AESEncryption.generateSecretKey(password.getData(), copyIv());
            byte[] decrypted = AESEncryption.decryptData(secretKey, ByteBuffer.wrap(encrypted()));
            ByteBuffer buffer = ByteBuffer.wrap(decrypted).position(3);
            return readMnemonic(buffer);
           
        }


        @Override
        public void viewWalletMnemonic(SecretString pass) throws Exception {

                    Mnemonic mnemonic = getMnemonic(pass);
                    Stages.showMagnifyingStage("Mnemonic Recovery - Wallet" , mnemonic.getPhrase().toStringUnsecure());

           
        }

        @Override
        public void getMnemonic(ExecutorService execService, EventHandler<WorkerStateEvent> onMnemonic, EventHandler<WorkerStateEvent> onFailed) {
            Stages.enterPassword(
                "Ergo - Wallet password",
                new Image(ErgoWallets.getAppIconString()),
                new Image (ErgoWallets.ICON), 
                "Wallet password", 
                execService, onPass->{
                Object obj = onPass.getSource().getValue();
                if(obj != null && obj instanceof SecretString){
                    
                    Task<Object> task = new Task<Object>() {

                        public Object call() throws Exception {

                            return  getMnemonic((SecretString) obj);
                        }
                    };

                    task.setOnFailed(onFailed);

                    task.setOnSucceeded(onMnemonic);

                    execService.submit(task);
                }

                });
        }

        private static Mnemonic readMnemonic(ByteBuffer data) {
            byte[] mnPhraseBytes = new byte[data.getShort()];
            data.get(mnPhraseBytes);
            byte[] mnPasswordBytes = new byte[data.getShort()];
            data.get(mnPasswordBytes);
            char[] mnPhraseChars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(mnPhraseBytes)).array();
            char[] mnPasswordChars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(mnPasswordBytes)).array();
            return Mnemonic.create(mnPhraseChars, mnPasswordChars);
        }

 
        @Override
        public SignedTransaction signWithPassword(SecretString password, BlockchainContext ctx, UnsignedTransaction unsignedTx, Collection<Integer> addressIndexes) throws Exception {
            return ErgoInterface.newWithMnemonicProver(ctx, nonstandard, getMnemonic(password), addressIndexes).sign(unsignedTx);
        }

        @Override
        public Address derivePublicAddress(NetworkType networkType, int index) {
            return new Address(P2PKAddress.apply(parentExtPubKey.child(index).key(), new ErgoAddressEncoder(networkType.networkPrefix)));
        }

        @Override
        public WalletKey changedPassword(SecretString currentPassword, SecretString newPassword) throws Exception {
            return create(nonstandard, getMnemonic(currentPassword), newPassword);
        }

  
    }
}
