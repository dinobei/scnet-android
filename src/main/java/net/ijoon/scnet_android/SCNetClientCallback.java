package net.ijoon.scnet_android;

/**
 * Created by pbj on 2017. 12. 14..
 */

public interface SCNetClientCallback {
    void onAttaching();
    void onAttached();
    void onAttachFailed();
    void onDetach();

    void onResponse(MessageHeader messageHeader, byte[] bodyBuf);
}
