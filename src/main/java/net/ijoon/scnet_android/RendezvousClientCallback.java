package net.ijoon.scnet_android;

public interface RendezvousClientCallback {
    void onConnectedToRendezvousServer(String myPublicIP, String myPublicPort);

    void onConnecting(int connectionID);
    void onConnected(RendezvousSession rendezvousSession, Connection connection);
    void onConnectFailed(int connectionID);
    void onConnectionUpdate(RendezvousSession rendezvousSession, Connection connection);
    void onConnectionRemoved(RendezvousSession rendezvousSession, Connection connection);

    void onMessage(MessageHeader messageHeader, byte[] buffer);
}
