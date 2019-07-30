package net.ijoon.scnet_android;

public enum RendezvousPacketType {
    NONE(0),
    REGISTRATION_RENDEZVOUS_CLIENT_REQUEST(10000), // SP private ip, SP private port
    REGISTRATION_RENDEZVOUS_CLIENT_SUCCESS, // SP public ip, SP public port
    REGISTRATION_RELAY_SERVER_REQUEST, // nullptr
    REGISTRATION_RELAY_SERVER_SUCCESS, // nullptr
    PING_REQUEST, // nullptr
    PING_RESPONSE, // nullptr

    CONNECTION_REQUEST, // TP public ip, TP public port

    CONNECTION_ID_CREATED, // TP public ip, TP public port (RanS to SP)
    CONNECTION_ID_RECEIVED, // TP public ip (SP to RanS)
    CONNECTION_TARGET_INVALID, // TP public ip, TP public port
    CONNECTION_FAILED, // nullptr

    // relay
    RELAY_SERVICE_REQUEST, // SP pubilc ip, TP pubilc ip
    RELAY_SERVICE_READY, // nullptr
    RELAY_SERVER_INFORMATION, // RelS-ip, RelS-port, 1(SP) or 0(TP)
    REGISTRATION_RELAY_PEER_REQUEST, // 1(SP) or 0(TP)
    RELAY_SESSION_CREATED, // nullptr
    RELAY_SESSION_INVALID, // nullptr

    CONNECTION_RELAY_SERVICE_SUCCESS, // RelS-ip, RelS-port
    CONNECTION_RELAY_SERVICE_FAILED, // nullptr

    // pub/pub or pri/pub
    DIRECT_CONNECTION_AVAILABLE, // TP public ip, TP public port
    DIRECT_CONNECTION_REQUEST, // nullptr
    DIRECT_CONNECTION_RESPONSE, // nullptr

    // pub/pri
    REVERSE_CONNECTION_READY, // nullptr
    REVERSE_CONNECTION, // * SP public ip, SP public port
    REVERSE_CONNECTION_REQUEST, // nullptr
    REVERSE_CONNECTION_RESPONSE, // nullptr

    // pri/pri
    UDP_HOLE_PUNCHING_AVAILABLE, // SP's public ip, public port, private ip, private port to TP (The opposite is also the case.)
    UDP_HOLE_PUNCHING_REQUEST, // isPublic (1=true, 0=false)
    UDP_HOLE_PUNCHING_RESPONSE, // isPublic (1=true, 0=false)

    RENDEZVOUS_MSG_END;

    private final int code;

    RendezvousPacketType()
    {
        this(Counter.nextCode);
    }

    RendezvousPacketType(int code)
    {
        this.code = code;
        Counter.nextCode = code + 1;
    }

    public int toInt() {
        return this.code;
    }

    public static RendezvousPacketType fromInt(int value) {
        for (RendezvousPacketType code : RendezvousPacketType.values())
            if (code.toInt() == value)
                return code;

        return NONE;
    }

    private static class Counter
    {
        private static int nextCode = 0;
    }
}
