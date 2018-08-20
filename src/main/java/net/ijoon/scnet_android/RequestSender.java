package net.ijoon.scnet_android;

import android.util.Log;

import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by pbj on 2017. 12. 21..
 */

public class RequestSender extends Thread {
    final private BlockingQueue<MessageLite> mBlockingQueue = new LinkedBlockingQueue<>();
    private Stream mStream;
    private  ResponseReceiver mResponseReceiver;

    @Override
    public synchronized void start() {
        throw new RuntimeException("Use requestSender.start(Stream, responseReceiver)");
    }

    void start(@NotNull Stream stream, ResponseReceiver responseReceiver) {
        mStream = stream;
        mResponseReceiver = responseReceiver;
        super.start();
    }

    public void request(MessageLite message) {
        try {
            mBlockingQueue.put(message);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        Log.d("scnet-android", "RequestSender Thread Start");
        while(!isInterrupted()) {
            try {
                MessageLite message = mBlockingQueue.take();
                sendRequest(mStream, message);
            } catch (InterruptedException e) {
                // BlockingQueue Interrupted
                break;
            } catch (IOException e) {
                break;
            }
        }
        Log.d("scnet-android", "Finished RequestSender Thread");
        if(mResponseReceiver != null) mResponseReceiver.interrupt();
    }

    private void sendRequest(Stream stream, MessageLite message) throws IOException {
        int dataSize = message.getSerializedSize();
        byte []serialized = new byte[dataSize + MessageHeader.MAX_PACKET_HEADER_SIZE];
        CodedOutputStream coded_output = CodedOutputStream.newInstance(serialized, 0, dataSize);


        try {
            coded_output.writeRawBytes(MessageHeader.MAGIC_PACKET);
            coded_output.writeInt32NoTag(dataSize); // data size
            coded_output.writeInt32NoTag(Registry.GetPacketType(message)); // packet type
            coded_output.writeInt32NoTag(0); // message type
            coded_output.writeInt32NoTag(0); // crypt type
            coded_output.writeInt32NoTag(0); // reserved
            message.writeTo(coded_output);
        } catch (IOException e) {
            e.printStackTrace();
        }

        stream.SafeSend(serialized, coded_output.getTotalBytesWritten());
    }
}
