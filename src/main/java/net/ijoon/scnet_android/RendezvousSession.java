package net.ijoon.scnet_android;
import java.net.InetSocketAddress;

public class RendezvousSession {

    int connectionID;

    RendezvousClient rendezvousClient;

    InetSocketAddress publicAddress;
    InetSocketAddress privateAddress;
    InetSocketAddress relayAddress;

    RendezvousSession(RendezvousClient rendezvousClient, int connectionID) {
        this.rendezvousClient = rendezvousClient;
        this.connectionID = connectionID;
    }

    boolean isConnected() {
        return relayAddress != null || publicAddress != null || privateAddress != null;
    }
}
