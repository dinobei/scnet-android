package net.ijoon.scnet_android;

public interface RendezvousClientCallback {
    void onConnecting(int connectionID);
    void onConnected(int connectionID);
    void onConnectFailed(int connectionID);

    void onMessage(MessageHeader messageHeader, byte[] buffer);
}
