package io.netnotes.engine.networks.ergo;

import org.ergoplatform.appkit.NetworkType;

import io.netnotes.engine.NamedNodeUrl;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class ErgoConstants {
    public static final int MIN_PORT_NUMBER = 0;
    public final static String LOCAL_NODE = "Local Node";
    public final static String LIGHT_CLIENT = "Remote Node";
    public final static int MAINNET_PORT = 9053;
    public final static int TESTNET_PORT = 9052;
    public final static int EXTERNAL_PORT = 9030;
    public final static long REQUIRED_SPACE = 5L * 1024L * 1024L * 1024L;
    public final static NamedNodeUrl TESTNET_1 = new NamedNodeUrl("QX_TESTNET", "QX_TESTNET", "74.69.128.24", 9020, "", NetworkType.TESTNET);

    public final static String ERGO_NETWORK_ICON256 = "/assets/ergo-network.png";
    public final static String ERGO_NETWORK_ICON = "/assets/ergo-network-30.png";

    public final static String ERGO_NODES_ICON = "/assets/ergoNodes-30.png";
    public final static String ERGO_WALLETS_ICON = "/assets/ergo-wallet-30.png";
    public final static String ERGO_MARKETS_ICON = "/assets/bar-chart-30.png";
    public final static String ERGO_EXPLORERS_ICON = "/assets/ergo-explorer-30.png";

    public final static ExtensionFilter ERGO_JAR_EXT = new FileChooser.ExtensionFilter("Ergo Node Jar", "*.jar");
    public final static ExtensionFilter ERGO_WALLET_EXT = new FileChooser.ExtensionFilter("Ergo Wallet", "*.erg");
}
