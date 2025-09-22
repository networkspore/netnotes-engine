package io.netnotes.engine.crypto;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;

import io.netnotes.engine.messaging.NoteMessaging.Headings;
import io.netnotes.engine.noteBytes.NoteBytes;
import io.netnotes.engine.noteBytes.NoteBytesObject;
import io.netnotes.engine.noteBytes.NoteBytesReadOnly;
import io.netnotes.engine.noteBytes.NoteUUID;
import io.netnotes.engine.noteBytes.collections.NoteBytesPair;

public class NodeKeyPairs{
   
    public static final NoteBytesReadOnly EXCHANGE_PRIVATE_KEY = new NoteBytesReadOnly("x25519PK");
    public static final NoteBytesReadOnly SIGNATURE_PRIVATE_KEY = new NoteBytesReadOnly("ed25519PK");
  
    private final NoteBytesReadOnly m_uuid;
    private final boolean m_isInternal;

    private final X25519PublicKeyParameters m_exchangePublicKey;
    private final X25519PrivateKeyParameters m_exchangePrivateKey;

    // For signatures  
    private final Ed25519PrivateKeyParameters m_signPrivateKey;
    private final Ed25519PublicKeyParameters m_signPublicKey;

    public NoteBytesReadOnly getUUID(){
        return m_uuid;
    }

    public boolean isInternal(){
        return m_isInternal;
    }

    public NodeKeyPairs(){
        
        Ed25519KeyPairGenerator signingGen = new Ed25519KeyPairGenerator();
        X25519KeyPairGenerator exchangeGen = new X25519KeyPairGenerator();
        
        signingGen.init(new Ed25519KeyGenerationParameters(RandomService.getSecureRandom()));
        exchangeGen.init(new X25519KeyGenerationParameters(RandomService.getSecureRandom()));
        
        AsymmetricCipherKeyPair signingPair = signingGen.generateKeyPair();
        AsymmetricCipherKeyPair exchangePair = exchangeGen.generateKeyPair();
        
        m_uuid = NoteUUID.createLocalUUID128ReadOnly();
        m_isInternal = true;
        this.m_signPrivateKey = (Ed25519PrivateKeyParameters) signingPair.getPrivate();
        this.m_signPublicKey = (Ed25519PublicKeyParameters) signingPair.getPublic();
        this.m_exchangePrivateKey = (X25519PrivateKeyParameters) exchangePair.getPrivate();
        this.m_exchangePublicKey = (X25519PublicKeyParameters) exchangePair.getPublic(); 
    }

    public NodeKeyPairs(NoteBytesObject obj){
        m_isInternal = false;
        NoteBytesPair exPrivKeyPair = obj.get(EXCHANGE_PRIVATE_KEY);
        NoteBytesPair sigPrivKeyPair = obj.get(SIGNATURE_PRIVATE_KEY);
        NoteBytesPair uuidPair = obj.get(Headings.UUID_128);

        if(exPrivKeyPair == null || sigPrivKeyPair == null || uuidPair == null){
            throw new IllegalArgumentException("Object is missing required arguments");
        }

        NoteBytes xPrivBytes = exPrivKeyPair.getValue();
        NoteBytes sPrivBytes =  sigPrivKeyPair.getValue();
        NoteBytes uuidBytes = uuidPair.getValue();

        X25519PrivateKeyParameters xPriv = new X25519PrivateKeyParameters(xPrivBytes.get());
        Ed25519PrivateKeyParameters sPriv = new Ed25519PrivateKeyParameters(sPrivBytes.get());

        m_exchangePrivateKey = xPriv;
        m_exchangePublicKey = xPriv.generatePublicKey();

        m_signPrivateKey = sPriv;
        m_signPublicKey = sPriv.generatePublicKey();

        m_uuid = new NoteBytesReadOnly(uuidBytes.get());

        uuidBytes.ruin();
        xPrivBytes.ruin();
        sPrivBytes.ruin();
        obj.ruin();
    }
    
    public X25519PublicKeyParameters getExchangePublicKey() {
        return m_exchangePublicKey;
    }

    public X25519PrivateKeyParameters getExchangePrivateKey() {
        return m_exchangePrivateKey;
    }

     public Ed25519PublicKeyParameters m_signPublicKey() {
        return m_signPublicKey;
    }

    public Ed25519PrivateKeyParameters m_signPrivateKey() {
        return m_signPrivateKey;
    }

    public NoteBytesObject getPrivateKeysObject(){
        NoteBytesReadOnly exPrivKey = new NoteBytesReadOnly(m_exchangePrivateKey.getEncoded());
        NoteBytesReadOnly sigPrivKey = new NoteBytesReadOnly(m_signPrivateKey.getEncoded());

        NoteBytesObject obj = new NoteBytesObject(new NoteBytesPair[]{
            new NoteBytesPair(Headings.UUID_128, m_uuid),
            new NoteBytesPair(EXCHANGE_PRIVATE_KEY, exPrivKey),
            new NoteBytesPair(SIGNATURE_PRIVATE_KEY, sigPrivKey)
        });

        exPrivKey.ruin();
        sigPrivKey.ruin();
        return obj;
    }

    public void ruin(){
        m_uuid.ruin();
    }

    public boolean isRuined(){
        return m_uuid.isRuined();
    }

}