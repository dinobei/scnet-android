package net.ijoon.scnet_android;

public enum Connection {
    NONE(0),
    DIRECT_CONNECTION(1),
    REVERSE_CONNECTION(2),
    EQUAL_NAT(3),
    UDP_HOLE_PUNCHING(4),
    RELAY(5);

    private final int code;

    Connection(int code) {
        this.code = code;
    }

    public int toInt() {
        return this.code;
    }

    public static Connection fromInt(int value) {
        for (Connection code : Connection.values())
            if (code.toInt() == value)
                return code;

        return NONE;
    }
}
