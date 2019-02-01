package net.ijoon.scnet_android;
import java.net.InetSocketAddress;

public class RendezvousSession {

    int connectionID;

    InetSocketAddress publicAddress;
    InetSocketAddress privateAddress;
    InetSocketAddress relayAddress;

    RendezvousSession(int connectionID) {
        this.connectionID = connectionID;
    }

    boolean isConnected() {
        return relayAddress != null || publicAddress != null || privateAddress != null;
    }
}
