package net.ijoon.scnet_android;

public enum RendezvousPacketType {
    NONE(0),
    REGISTRATION_RENDEZVOUS_CLIENT_REQUEST(10000), // SP private ip, SP private port
    REGISTRATION_RENDEZVOUS_CLIENT_SUCCESS(10001), // SP public ip, SP public port
    REGISTRATION_RELAY_SERVER_REQUEST(10002), // nullptr
    REGISTRATION_RELAY_SERVER_SUCCESS(10003), // nullptr
    PING_RELAY_PEER(10004), // nullptr
    PING_CONNECTED_PEER(10005), // nullptr
    PING_CONNECTED_PEER_RESPONSE(10006), // nullptr

    CONNECTION_REQUEST(10007), // TP public ip, TP public port

    CONNECTION_FAILED(10008), // nullptr

    // relay
    RELAY_SERVICE_REQUEST(10009), // SP pubilc ip, TP pubilc ip
    RELAY_SERVICE_READY(10010), // nullptr
    RELAY_SERVER_INFORMATION(10011), // RelS-ip, RelS-port, 1(SP) or 0(TP)
    REGISTRATION_RELAY_PEER_REQUEST(10012), // 1(SP) or 0(TP)
    REGISTRATION_RELAY_PEER_SUCCESS(10013), // nullptr
    REGISTRATION_RELAY_PEER_FAILED(10014), // nullptr
    RELAY_SESSION_CREATED(10015), // nullptr
    RELAY_SESSION_CREATING_FAILED(10016), // nullptr
    RELAY_SESSION_INVALID(10017), // nullptr

    CONNECTION_RELAY_SERVICE_SUCCESS(10018), // RelS-ip, RelS-port
    CONNECTION_RELAY_SERVICE_FAILED(10019), // nullptr

    // pub/pub or pri/pub
    DIRECT_CONNECTION_AVAILABLE(10020), // TP public ip, TP public port
    DIRECT_CONNECTION_REQUEST(10021), // nullptr
    DIRECT_CONNECTION_RESPONSE(10022), // nullptr

    // pub/pri
    REVERSE_CONNECTION_READY(10023), // nullptr
    REVERSE_CONNECTION(10024), // * SP public ip, SP public port
    REVERSE_CONNECTION_REQUEST(10025), // nullptr
    REVERSE_CONNECTION_RESPONSE(10026), // nullptr

    // pri/pri
    UDP_HOLE_PUNCHING_AVAILABLE(10027), // SP's public ip, public port, private ip, private port to TP (The opposite is also the case.)
    UDP_HOLE_PUNCHING_REQUEST(10028), // isPublic (1=true, 0=false)
    UDP_HOLE_PUNCHING_RESPONSE(10029), // isPublic (1=true, 0=false)

    RENDEZVOUS_MSG_END(10030);

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
