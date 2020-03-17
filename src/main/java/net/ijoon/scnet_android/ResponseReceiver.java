package net.ijoon.scnet_android;
import android.util.Log;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.MessageLite;

import net.ijoon.scnet_android.MessageHeader.MessageType;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;

/**
 * Created by pbj on 2017. 12. 15..
 */

class ResponseReceiver extends Thread {
    private SCNetClientCallback mClientCallback;

    private Stream mStream;
    private RequestSender mRequestSender;

    ResponseReceiver(SCNetClientCallback callback) {
        mClientCallback = callback;
    }

    @Override
    public synchronized void start() {
        throw new RuntimeException("Use requestSender.start(Stream, responseSender)");
    }

    void start(Stream stream, RequestSender requestSender) {
        mStream = stream;
        mRequestSender = requestSender;
        super.start();
    }

    @Override
    public void run() {
        Log.d("scnet_android", "ResponseReceiver Thread Start");
        mClientCallback.onAttached();

        while(!isInterrupted())
        {
            try {

                Iterator<SelectionKey> iter = mStream.select(5 * 1000);

                if(!iter.hasNext()) {
                    mClientCallback.onTimedOut();
                }

                while(iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (key.isValid() && key.isReadable()) {
                        recvResponse(mStream);
                    }
                }
            }
            catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        Log.d("scnet_android", "Finished ResponseReceiver Thread");
    }

    private void recvResponse(Stream stream) throws IOException {
        byte[] magicPacket = stream.SafeRecv(MessageHeader.MAGIC_PACKET.length);
        if(!MessageHeader.isValidMagicPacket(magicPacket)) {
            throw new IOException("Invalid magic packet");
        }

        byte[] headerBuf = new byte[MessageHeader.MAX_PACKET_HEADER_SIZE];
        int idx = 0;
        int receivedHeaderComponent = 0;

        while(true) {
            byte[] data = stream.SafeRecv(1);

            headerBuf[idx++] = data[0];

            if((data[0] & 0xFF) > (byte)127) {
                continue;
            }

            if(++receivedHeaderComponent == MessageHeader.HEADER_ELEMENTS) {
                break;
            }
        }

        CodedInputStream coded_input = CodedInputStream.newInstance(headerBuf);

        MessageHeader messageHeader = new MessageHeader();
        messageHeader.mDataSize = coded_input.readInt32();
        messageHeader.mPacketType = coded_input.readInt32();
        messageHeader.mMessageType = coded_input.readInt32();
        messageHeader.mCryptType = coded_input.readInt32();
        messageHeader.mConnectionID = coded_input.readInt32();

        byte[] bodyBuf = stream.SafeRecv(messageHeader.mDataSize);
        MessageType messageType = MessageType.fromInt(messageHeader.mMessageType);
        switch (messageType) {
            case PROTOBUF:
            {
                MessageLite message = Registry.GetMessage(messageHeader.mPacketType, bodyBuf);
                Registry.ProtobufCallback callback = Registry.GetProtobufCallback(messageHeader.mPacketType);
                if(callback != null)  callback.onCallback(message);
            }
                break;
            case RAWBYTE:
            {
                Registry.RawbyteCallback callback = Registry.GetRawbyteCallback(messageHeader.mPacketType);
                if(callback != null) callback.onCallback(messageHeader.mPacketType, bodyBuf, messageHeader.mDataSize);
            }
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + messageHeader.mMessageType);
        }
    }
}
