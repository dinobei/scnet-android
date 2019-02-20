package net.ijoon.scnet_android;

public class RendezvousSession {

    int connectionID;

    KCPPeer publicKCPPeer;
    KCPPeer privateKCPPeer;
    KCPPeer relayKCPPeer;

    RendezvousSession(int connectionID) {
        this.connectionID = connectionID;
    }

    boolean isConnected() {
        return relayKCPPeer != null || publicKCPPeer != null || privateKCPPeer != null;
    }
}
