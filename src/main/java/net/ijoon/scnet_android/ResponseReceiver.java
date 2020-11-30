package net.ijoon.scnet_android;

import android.util.Log;
import android.util.SparseArray;

import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.scnet.SCNetPacket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Iterator;

/**
 * Created by pbj on 2017. 12. 15..
 */

class ResponseReceiver extends Thread {
    private SCNetClientCallback mClientCallback;

    private Stream mStream;

    SparseArray<DedicatedCallback> dedicatedCallbackMap;

    ResponseReceiver(SCNetClientCallback callback) {
        mClientCallback = callback;
    }

    @Override
    public synchronized void start() {
        throw new RuntimeException("Use requestSender.start(Stream, responseSender)");
    }

    void start(Stream stream, SparseArray<DedicatedCallback> dedicatedCallbackMap) {
        this.mStream = stream;
        this.dedicatedCallbackMap = dedicatedCallbackMap;
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
                        recv();
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

    public void recv() throws IOException {
        int headLength = MessageHeader.MAGIC_PACKET.length + 4 + 2;

        byte[] headPacket = mStream.SafeRecv(headLength);
        if(!MessageHeader.isValidMagicPacket(headPacket)) {
            throw new IOException("Invalid magic packet");
        }

        int packetSize = ByteBuffer.wrap(headPacket, 2, 4).getInt();
        int headerSize = headPacket[6] << 8 | headPacket[7];

        byte[] packetBuf = mStream.SafeRecv(packetSize);


        Parser parser = SCNetPacket.Header.parser();
        SCNetPacket.Header header = (SCNetPacket.Header) parser.parseFrom(packetBuf, 0, headerSize);
        MessageLite message = Registry.GetMessage(header.getPacketType(), packetBuf, headerSize, packetSize-headerSize);
        if(header.getResCb() > 0) {
            DedicatedCallback callback = dedicatedCallbackMap.get(header.getResCb());
            if (callback != null) {
                callback.onCallback(header, message);
                return;
            }
        }

        Registry.ProtobufCallback callback = Registry.GetProtobufCallback(header.getPacketType());
        if(callback != null)  callback.onCallback(header, message);
    }
}