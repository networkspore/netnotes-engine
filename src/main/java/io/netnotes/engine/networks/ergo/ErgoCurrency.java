package io.netnotes.engine.networks.ergo;

import java.math.BigDecimal;

import org.ergoplatform.appkit.NetworkType;

import io.netnotes.engine.PriceAmount;
import io.netnotes.engine.PriceCurrency;

public class ErgoCurrency extends PriceCurrency {

    public final static String TOKEN_ID = "0000000000000000000000000000000000000000000000000000000000000000";
    public final static String NAME = "Ergo";
    public final static String SYMBOL = "ERG";
    public final static String IMAGE_STRING = "/assets/unitErgo.png";
    public final static int DECIMALS = 9;
    public final static String FONT_SYMBOL  = "Σ";
    public final static String TOKEN_TYPE = "LAYER_0";
    public final static String URL_STRING = "https://ergoplatform.org/";
    private NetworkType m_networkType; 

    public ErgoCurrency(NetworkType networkType) { 
        super(TOKEN_ID, NAME, SYMBOL, DECIMALS, networkType.toString(), IMAGE_STRING, TOKEN_TYPE, FONT_SYMBOL);
        setEmissionAmount(97739925000000000L);
        setDescription("Layer 0 native currency");
        setUrl(URL_STRING);
        m_networkType = networkType;
    }

    public NetworkType getErgoNetworkType(){
        return m_networkType;
    }

    public static long getNanoErgsFromErgs(BigDecimal ergs){
        return PriceAmount.calculateBigDecimalToLong(ergs, DECIMALS);
    }

    public static BigDecimal getErgsFromNanoErgs(long nanoErgs){
        return PriceAmount.calculateLongToBigDecimal(nanoErgs, DECIMALS);
    }

}
