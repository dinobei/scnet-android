package net.ijoon.scnet_android;

/**
 * Created by pbj on 2017. 12. 15..
 */

public final class MessageHeader {
    public final static byte[] MAGIC_PACKET = "IJ".getBytes();

    public static boolean isValidMagicPacket(byte[] magicPacket) {
        return magicPacket[0] == MAGIC_PACKET[0] && magicPacket[1] == MAGIC_PACKET[1];
    }
}
