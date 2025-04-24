package io.netnotes.engine;


public interface AmountBoxInterface {
    String getTokenId();
    long getTimeStamp();
    void setTimeStamp(long timeStamp);
    void shutdown();
    PriceAmount getPriceAmount();


}
