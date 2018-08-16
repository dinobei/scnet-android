package net.ijoon.scnet_android;

/**
 * Created by pbj on 2017. 12. 14..
 */

public interface ClientManagerCallback {
    void onAttaching();
    void onAttached();
    void onAttachFailed();
    void onDetach();

    void onResponse(int packetType, int cryptType, byte[] bodyBuf);
}
