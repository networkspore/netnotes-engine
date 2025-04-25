package io.netnotes.engine.apps.ergoDex;

import io.netnotes.engine.networks.ergo.ErgoBox;
import io.netnotes.engine.networks.ergo.ErgoBoxAsset;

public class ErgoDexBoxInfo {
    private String m_poolId;
    private ErgoBoxAsset m_lp;
    private ErgoBoxAsset m_x;
    private ErgoBoxAsset m_y;


    public void setPoolId(String poolId) {
        this.m_poolId = poolId;
    }

    public ErgoBoxAsset getLp() {
        return m_lp;
    }

    public void setLp(ErgoBoxAsset lp) {
        this.m_lp = lp;
    }

    public ErgoBoxAsset getX() {
        return m_x;
    }

    public void setX(ErgoBoxAsset x) {
        this.m_x = x;
    }

    public ErgoBoxAsset getY() {
        return m_y;
    }

    public void setY(ErgoBoxAsset y) {
        this.m_y = y;
    }

    public long getFeeNum() {
        return m_feeNum;
    }

    public void setM_feeNum(long feeNum) {
        this.m_feeNum = feeNum;
    }

    private long m_feeNum;

    public ErgoDexBoxInfo(ErgoBox box){

    }

    public String getPoolId(){
        return m_poolId;
    }
}
