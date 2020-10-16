package net.ijoon.scnet_android;

import android.util.SparseArray;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;
import com.scnet.SCNetPacket;

import java.io.IOException;

/**
 * Created by pbj on 2017. 12. 13..
 */

public class Client extends Thread {
    private final String mIp;
    private final int mPort;
    private final int mSocketTimeoutMillis;
    private final SCNetClientCallback mClientCallback;

    Stream stream;

    int dedicatedCallbackIndex = 1000;
    SparseArray<DedicatedCallback> dedicatedCallbackMap;

    public Client(String ip, int port, int socketTimeoutMillis, SCNetClientCallback callback) {
        mIp = ip;
        mPort = port;
        mSocketTimeoutMillis = socketTimeoutMillis;
        mClientCallback = callback;

        dedicatedCallbackMap = new SparseArray<>();
    }

    private Stream connect() {
        Stream stream = null;
        try {
            mClientCallback.onAttaching();
            stream = new Stream(mIp, mPort, mSocketTimeoutMillis);
        } catch (Exception e) {
            mClientCallback.onAttachFailed();
        }

        return stream;
    }

    @Override
    public void run() {
        super.run();

        while(!isInterrupted()) {

            stream = connect();
            if(stream == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }


            // Create recv thread when connected to server
            ResponseReceiver responseReceiver = new ResponseReceiver(mClientCallback);
            responseReceiver.start(stream, dedicatedCallbackMap); // passing client for cross-interrupt when server finished.

            try {
                responseReceiver.join();
            }  catch (InterruptedException ignored) {
                try {
                    responseReceiver.interrupt();

                    responseReceiver.join();

                    super.interrupt();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    super.interrupt();
                }
            }

            try {
                Thread.sleep(1000);
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }

        mClientCallback.onDetach();
    }

    public void send(SCNetPacket.Header _header, MessageLite message, DedicatedCallback callback) throws IOException {
        final int packetType = Registry.GetPacketType(message);
        SCNetPacket.Header.Builder headerBuilder = SCNetPacket.Header.newBuilder()
                .setPacketType(packetType);
        if(_header != null && _header.getReqCb() > 0) {
            headerBuilder.setResCb(_header.getReqCb());
        }
        if(callback != null) {
            headerBuilder.setReqCb(dedicatedCallbackIndex);
            this.dedicatedCallbackMap.append(dedicatedCallbackIndex++, callback);
        }
        SCNetPacket.Header header = headerBuilder.build();

        int headerSize = header.getSerializedSize();
        int packetSize = headerSize + message.getSerializedSize();
        byte []serialized = new byte[MessageHeader.MAGIC_PACKET.length + 4 + 2 + packetSize];
        CodedOutputStream coded_output = CodedOutputStream.newInstance(serialized, 0, serialized.length);

        try {
            coded_output.writeRawBytes(MessageHeader.MAGIC_PACKET);
            coded_output.writeRawByte((packetSize>>24) & 0xFF);
            coded_output.writeRawByte((packetSize>>16) & 0xFF);
            coded_output.writeRawByte((packetSize>>8) & 0xFF);
            coded_output.writeRawByte(packetSize & 0xFF);
            coded_output.writeRawByte((headerSize>>8) & 0xFF);
            coded_output.writeRawByte(headerSize & 0xFF);
            header.writeTo(coded_output);
            message.writeTo(coded_output);
        } catch (IOException e) {
            e.printStackTrace();
        }

        stream.SafeSend(serialized, coded_output.getTotalBytesWritten());
    }

    @Override
    public void interrupt() {
        super.interrupt();
    }
}
