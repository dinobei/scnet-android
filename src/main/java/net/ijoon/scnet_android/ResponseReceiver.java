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
    private boolean mIsMainServerSession;
    private int mSessionIndex;

    private SCNetClientCallback mMainServerCallback;
    private ServerManagerCallback mServerManagerCallback;

    private Stream mStream;
    private RequestSender mRequestSender;

    ResponseReceiver(@NotNull SCNetClientCallback callback) {
        mMainServerCallback = callback;

        mIsMainServerSession = true;
    }

    ResponseReceiver(int sessionIndex, @NotNull ServerManagerCallback callback) {
        mServerManagerCallback = callback;

        mIsMainServerSession = false;
        mSessionIndex = sessionIndex;
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
        if(mIsMainServerSession) mMainServerCallback.onAttached();
        else mServerManagerCallback.onAttached(mSessionIndex);

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

            if(data[0] > (byte)127) {
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
        messageHeader.mReserved = coded_input.readInt32();
        byte[] bodyBuf = stream.SafeRecv(messageHeader.mDataSize);

        if(mIsMainServerSession) mMainServerCallback.onResponse(messageHeader, bodyBuf);
        else mServerManagerCallback.onResponse(mSessionIndex, messageHeader, bodyBuf);
    }
}
