package io.netnotes.engine.networks.ergo;

public class ErgoPlatformExplorerData extends ErgoExplorerData {
    public final static String ERGO_PLATFORM_EXPLORER = "ERGO_PLATFORM_EXPLORER";

    public ErgoPlatformExplorerData(ErgoExplorerList ergoExplorerList){
        //"Explorer"
        super(ERGO_PLATFORM_EXPLORER,"Ergo Platform", ErgoExplorers.getSmallAppIconString(), "api.ergoplatform.com","Ergo Platform","explorer.ergoplatform.com",  ergoExplorerList);
    
    }
}
