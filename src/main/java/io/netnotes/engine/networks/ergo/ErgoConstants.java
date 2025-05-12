package io.netnotes.engine.networks.ergo;

import java.math.BigDecimal;

import org.ergoplatform.appkit.NetworkType;

import io.netnotes.engine.NamedNodeUrl;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

public class ErgoConstants {

    public static final String ERGO_NETWORK_ID = "ERGO_NETWORK";
    public static final String NODE_NETWORK = "NODE_NETWORK";
    public static final String EXPLORER_NETWORK = "EXPLORER_NETWORK";


    public static final int MIN_PORT_NUMBER = 0;
    public final static String LOCAL_NODE = "Local Node";
    public final static String REMOTE_NODE = "Remote Node";
    public final static int MAINNET_PORT = 9053;
    public final static int TESTNET_PORT = 9052;
    public final static int EXTERNAL_PORT = 9030;
    public final static long REQUIRED_SPACE = 5L * 1024L * 1024L * 1024L;
    
    public final static long MIN_NANO_ERGS = 1000000L;
    public final static BigDecimal MIN_NETWORK_FEE = ErgoCurrency.getErgsFromNanoErgs(MIN_NANO_ERGS);
    
    public final static NamedNodeUrl TESTNET_1 = new NamedNodeUrl("QX_TESTNET", "QX_TESTNET", "74.69.128.24", 9020, "", NetworkType.TESTNET);

    public final static String ERGO_NETWORK_ICON256 = "/assets/ergo-network.png";
    public final static String ERGO_NETWORK_ICON = "/assets/ergo-network-30.png";

    public final static String ERGO_NODES_ICON = "/assets/ergoNodes-30.png";
  
    public final static String ERGO_MARKETS_ICON = "/assets/bar-chart-30.png";
    public final static String ERGO_EXPLORERS_ICON = "/assets/ergo-explorer-30.png";

    public final static ExtensionFilter ERGO_JAR_EXT = new FileChooser.ExtensionFilter("Ergo Node Jar", "*.jar");
    public final static ExtensionFilter ERGO_WALLET_EXT = new FileChooser.ExtensionFilter("Ergo Wallet", "*.erg");

    public static String getClientImg(String clientType){
        return clientType != null && clientType.equals(LOCAL_NODE) ?  "🖳 " : "🖧 ";
    }

}
