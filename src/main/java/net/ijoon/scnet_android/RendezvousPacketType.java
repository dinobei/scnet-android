package net.ijoon.scnet_android;

public enum RendezvousPacketType {
    NONE(0),
    REGISTRATION_RENDEZVOUS_CLIENT_REQUEST(10000), // SP private ip, SP private port
    REGISTRATION_RENDEZVOUS_CLIENT_SUCCESS(10001), // SP public ip, SP public port
    REGISTRATION_RELAY_SERVER_REQUEST(10002), // nullptr
    REGISTRATION_RELAY_SERVER_SUCCESS(10003), // nullptr

    CONNECTION_REQUEST(10004), // TP public ip, TP public port

    CONNECTION_FAILED(10005), // nullptr

    // relay
    RELAY_SERVICE_REQUEST(10006), // (CID) SP pubilc ip, TP pubilc ip
    RELAY_SERVICE_READY(10007), // (CID) nullptr
    RELAY_SERVER_INFORMATION(10008), // (CID) RelS-ip, RelS-port, 1(SP) or 0(TP)
    REGISTRATION_RELAY_PEER_REQUEST(10009), // (CID) 1(SP) or 0(TP)
    REGISTRATION_RELAY_PEER_SUCCESS(10010), // (CID) nullptr
    REGISTRATION_RELAY_PEER_FAILED(10011), // (CID) nullptr
    RELAY_SESSION_CREATED(10012), // (CID) nullptr
    RELAY_SESSION_CREATING_FAILED(10013), // (CID) nullptr

    CONNECTION_RELAY_SERVICE_SUCCESS(10014), // (CID) RelS-ip, RelS-port
    CONNECTION_RELAY_SERVICE_FAILED(10015), // (CID) nullptr

    // pub/pub or pri/pub
    DIRECT_CONNECTION_AVAILABLE(10016), // TP public ip, TP public port
    DIRECT_CONNECTION_REQUEST(10017), // nullptr
    DIRECT_CONNECTION_RESPONSE(10018), // nullptr

    // pub/pri
    REVERSE_CONNECTION_READY(10019), // nullptr
    REVERSE_CONNECTION(10020), // * SP public ip, SP public port
    REVERSE_CONNECTION_REQUEST(10021), // nullptr
    REVERSE_CONNECTION_RESPONSE(10022), // nullptr

    // pri/pri
    UDP_HOLE_PUNCHING_AVAILABLE(10023), // SP's public ip, public port, private ip, private port to TP (The opposite is also the case.)
    UDP_HOLE_PUNCHING_REQUEST(10024), // isPublic (1=true, 0=false)
    UDP_HOLE_PUNCHING_RESPONSE(10025), // isPublic (1=true, 0=false)

    RENDEZVOUS_MSG_END(10026);

    private final int code;

    RendezvousPacketType(int code) {
        this.code = code;
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
}
