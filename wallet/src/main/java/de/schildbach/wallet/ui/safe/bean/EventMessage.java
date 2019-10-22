package de.schildbach.wallet.ui.safe.bean;

/**
 * 重置钱包通知
 * @author zhangmiao
 */
public class EventMessage {
    public final static int TYPE_WALLET_RESET = 1;

    public int eventType;

    public Object eventObj;

    public EventMessage(int eventType) {
        this.eventType = eventType;
    }

    public EventMessage(int eventType, Object eventObj) {
        this.eventType = eventType;
        this.eventObj = eventObj;
    }
}
