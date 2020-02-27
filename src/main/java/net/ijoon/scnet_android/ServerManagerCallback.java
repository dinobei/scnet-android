package net.ijoon.scnet_android;

/**
 * Created by pbj on 2017. 12. 14..
 */

public interface ServerManagerCallback {
    void onAttaching(int sessionIndex);
    void onAttached(int sessionIndex);
    void onAttachFailed(int sessionIndex);
    void onDetach(int sessionIndex);

    void onTimedOut(int sessionIndex);

    void onResponse(int sessionIndex, MessageHeader messageHeader, byte[] bodyBuf);
}
