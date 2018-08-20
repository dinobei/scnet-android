package net.ijoon.scnet_android;

/**
 * Created by pbj on 2017. 12. 15..
 */

public final class MessageHeader {
    public final static byte[] MAGIC_PACKET = "IJ".getBytes();

    public MessageHeader() {}

    public MessageHeader(int dataSize, int packetType, int messageType, int cryptType, int reserved) {
        this.mDataSize = dataSize;
        this.mPacketType = packetType;
        this.mMessageType = messageType;
        this.mCryptType = cryptType;
        this.mReserved = reserved;
    }

    public static boolean isValidMagicPacket(byte[] magicPacket) {
        return magicPacket[0] == MAGIC_PACKET[0] && magicPacket[1] == MAGIC_PACKET[1];
    }

    public final static int HEADER_ELEMENTS = 5;
    public final static int MAX_PACKET_HEADER_SIZE = (7 * HEADER_ELEMENTS);

    public int mDataSize;
    public int mPacketType;
    public int mMessageType;
    public int mCryptType;
    public int mReserved;


}
