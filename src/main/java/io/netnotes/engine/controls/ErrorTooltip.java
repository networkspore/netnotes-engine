package io.netnotes.engine.controls;

import io.netnotes.engine.Utils;
import javafx.animation.PauseTransition;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

public class ErrorTooltip {
    private Tooltip m_errTooltip = null;
    private Region m_errRegion = null;
    private PauseTransition m_errPt = null;

    public ErrorTooltip(Tooltip errToolTip, Region region, PauseTransition pt){
        m_errTooltip = errToolTip;
        m_errRegion = region;
        m_errPt = pt;
    }

    public Tooltip getErrTooltip(){
        return m_errTooltip;
    }

    public PauseTransition getErrPt(){
        return m_errPt;
    }

    public Region getErrToolTipRegion(){
        return m_errRegion;
    }

    public void setErrorTooltip(Tooltip errTooltip, Region tooltipAnchor, PauseTransition pt){
        m_errTooltip = errTooltip;
        m_errRegion = tooltipAnchor;
        m_errPt = pt;
    }

    public void showErrorTooltip(String msg){
        Utils.showTip(msg, m_errRegion, m_errTooltip, m_errPt);
    }
}
