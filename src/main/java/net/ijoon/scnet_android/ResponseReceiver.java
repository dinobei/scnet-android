package net.ijoon.scnet_android;
import android.util.Log;
import com.google.protobuf.CodedInputStream;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.Iterator;

/**
 * Created by pbj on 2017. 12. 15..
 */

class ResponseReceiver extends Thread {
    private ClientManagerCallback mCallback;

    private Stream mStream;
    private RequestSender mRequestSender;

    ResponseReceiver(@NotNull ClientManagerCallback callback) {
        mCallback = callback;
    }

    @Override
    public synchronized void start() {
        throw new RuntimeException("Use requestSender.start(Stream, responseSender)");
    }

    void start(@NotNull Stream stream, RequestSender requestSender) {
        mStream = stream;
        mRequestSender = requestSender;
        super.start();
    }

    @Override
    public void run() {
        Log.d("scnet-android", "ResponseReceiver Thread Start");
        mCallback.onAttached();

        while(!isInterrupted())
        {
            try {

                Iterator<SelectionKey> iter = mStream.select(10 * 1000);
                while(iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();
                    if (key.isReadable()) {
                        recvResponse(mStream);
                    }
                }
            }
            catch (IOException e) {
                e.printStackTrace();
                break;
            }
        }

        Log.d("scnet-android", "Finished ResponseReceiver Thread");
    }

    private void recvResponse(Stream stream) throws IOException {
        byte[] headerBuf = new byte[MessageHeader.MAX_PACKET_HEADER_SIZE];
        int idx = 0;
        int receivedHeaderComponent = 0;

        while(true) {
            byte[] data = stream.SafeRecv(1);

            headerBuf[idx++] = data[0];

            if(data[0] > (byte)127) {
                continue;
            }

            if(++receivedHeaderComponent == MessageHeader.HEADER_ELEMENTS) {
                break;
            }
        }

        CodedInputStream coded_input = CodedInputStream.newInstance(headerBuf);

        int dataSize = coded_input.readInt32();
        int packetType = coded_input.readInt32();
        int cryptType = coded_input.readInt32();
        byte[] bodyBuf = stream.SafeRecv(dataSize);

        mCallback.onResponse(packetType, cryptType, bodyBuf);
    }
}
