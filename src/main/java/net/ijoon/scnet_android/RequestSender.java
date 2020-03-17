package net.ijoon.scnet_android;

import android.util.Log;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by pbj on 2017. 12. 21..
 */

public class RequestSender extends Thread {
    final private BlockingQueue<MessageWrapper> mBlockingQueue = new LinkedBlockingQueue<>();
    private Stream mStream;
    private  ResponseReceiver mResponseReceiver;

    @Override
    public synchronized void start() {
        throw new RuntimeException("Use requestSender.start(Stream, responseReceiver)");
    }

    void start(Stream stream, ResponseReceiver responseReceiver) {
        mStream = stream;
        mResponseReceiver = responseReceiver;
        super.start();
    }

    public void request(MessageLite message) {
        MessageWrapper messageWrapper = new MessageWrapper();
        messageWrapper.messageType = MessageHeader.MessageType.PROTOBUF;
        messageWrapper.packetType = Registry.GetPacketType(message);
        messageWrapper.objMessage = message;
        messageWrapper.length = message.getSerializedSize();

        try {
            mBlockingQueue.put(messageWrapper);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void request(int packetType, byte[] message) {
        request(packetType, message, message.length);
    }

    public void request(int packetType, byte[] message, int length) {
        MessageWrapper messageWrapper = new MessageWrapper();
        messageWrapper.messageType = MessageHeader.MessageType.RAWBYTE;
        messageWrapper.packetType = packetType;
        messageWrapper.objMessage = message;
        messageWrapper.length = length;

        try {
            mBlockingQueue.put(messageWrapper);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void request(MessageWrapper messageWrapper) {
        try {
            mBlockingQueue.put(messageWrapper);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        Log.d("scnet_android", "RequestSender Thread Start");
        while(!isInterrupted()) {
            try {
                MessageWrapper messageWrapper = mBlockingQueue.take();
                switch(messageWrapper.messageType) {
                    case PROTOBUF:
                    {
                        MessageLite message = (MessageLite)messageWrapper.objMessage;
                        sendRequest(mStream, messageWrapper.packetType, message);
                    }
                        break;
                    case RAWBYTE:
                    {
                        byte[] message = (byte[])messageWrapper.objMessage;
                        sendRequest(mStream, messageWrapper.packetType, message, messageWrapper.length);
                    }
                        break;
                    default:
                            break;
                }
            } catch (InterruptedException e) {
                // BlockingQueue Interrupted
                break;
            } catch (IOException e) {
                break;
            }
        }
        Log.d("scnet_android", "Finished RequestSender Thread");
        if(mResponseReceiver != null) mResponseReceiver.interrupt();
    }

    private void sendRequest(Stream stream, int packetType, MessageLite message) throws IOException {
        int dataSize = message.getSerializedSize();
        byte []serialized = new byte[MessageHeader.MAGIC_PACKET.length + dataSize + MessageHeader.MAX_PACKET_HEADER_SIZE];
        CodedOutputStream coded_output = CodedOutputStream.newInstance(serialized, 0, serialized.length);

        try {
            coded_output.writeRawBytes(MessageHeader.MAGIC_PACKET);
            coded_output.writeInt32NoTag(dataSize); // data size
            coded_output.writeInt32NoTag(packetType); // packet type
            coded_output.writeInt32NoTag(MessageHeader.MessageType.PROTOBUF.toInt()); // message type
            coded_output.writeInt32NoTag(0); // crypt type
            coded_output.writeInt32NoTag(0); // connection id
            message.writeTo(coded_output);
        } catch (IOException e) {
            e.printStackTrace();
        }

        stream.SafeSend(serialized, coded_output.getTotalBytesWritten());
    }

    private void sendRequest(Stream stream, int packetType, byte[] message, int length) throws IOException {
        byte []serialized = new byte[MessageHeader.MAGIC_PACKET.length + length + MessageHeader.MAX_PACKET_HEADER_SIZE];
        CodedOutputStream coded_output = CodedOutputStream.newInstance(serialized, 0, serialized.length);

        try {
            coded_output.writeRawBytes(MessageHeader.MAGIC_PACKET);
            coded_output.writeInt32NoTag(length); // data size
            coded_output.writeInt32NoTag(packetType); // packet type
            coded_output.writeInt32NoTag(MessageHeader.MessageType.RAWBYTE.toInt()); // message type
            coded_output.writeInt32NoTag(0); // crypt type
            coded_output.writeInt32NoTag(0); // connection id
            coded_output.writeRawBytes(message, 0, length);
        } catch (IOException e) {
            e.printStackTrace();
        }

        stream.SafeSend(serialized, coded_output.getTotalBytesWritten());
    }
}
