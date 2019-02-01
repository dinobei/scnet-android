package net.ijoon.scnet_android;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * Created by pbj on 2017. 12. 13..
 */

public class Stream {
    private Selector mSelector;
    private SocketChannel mSocketChannel;

    static {
        try {

            Selector.open().close();

        } catch(IOException ie) {
            ie.printStackTrace();

        }
    }
    Stream(String ip, int port, int socketTimeoutMilis) throws IOException {
        mSocketChannel = SocketChannel.open();
        mSocketChannel.socket().setSoTimeout(socketTimeoutMilis);
        mSocketChannel.configureBlocking(false);
        mSocketChannel.connect(new InetSocketAddress(ip, port));

        Selector selector = Selector.open();
        SelectionKey clientKey = mSocketChannel.register(selector, SelectionKey.OP_CONNECT);

        if(selector.select(socketTimeoutMilis) > 0) {
            if(clientKey.isConnectable()) {
                try {
                    if(!mSocketChannel.finishConnect()) {
                        throw new IOException("finishConnect() failed");
                    }
                }
                catch (IOException e) {
                    safeClose(selector);
                    throw new IOException("IOException in finishConnect()");
                }

                mSelector = Selector.open();
                mSocketChannel.register(mSelector, SelectionKey.OP_READ);
                return;
            }
        }

        safeClose(selector);
        throw new IOException("select() failed");
    }

    private void safeClose(Selector selector) {
        try {
            mSocketChannel.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

    }

    public Iterator<SelectionKey> select(int timeoutMilis) throws IOException {
        mSelector.select(timeoutMilis);

        return mSelector.selectedKeys().iterator();
    }

    public boolean isConnected() {
        if(mSocketChannel == null) return false;
        return mSocketChannel.socket().isConnected();
    }

    public void close() throws IOException {
        mSocketChannel.socket().close();
    }

    byte[] SafeRecv(int recvSize) throws IOException {
        byte[] recvBuf = new byte[recvSize];
        int partialRecvCount;
        int totalRecvCount = 0;

        while(totalRecvCount < recvSize) {
            partialRecvCount = mSocketChannel.read(ByteBuffer.wrap(recvBuf, totalRecvCount, recvSize - totalRecvCount));
            if(partialRecvCount < 0) throw new IOException("SafeRecv() Failed");
            totalRecvCount += partialRecvCount;
        }

        return recvBuf;
    }

    int SafeRecvInt() throws IOException {
        byte[] recvBuf = SafeRecv(4);
        return byteToint(recvBuf);
    }

    String SafeRecvStr(int length) throws IOException {
        byte[] recvBuf = SafeRecv(length);
        return new String(recvBuf, 0, length);
    }

    void SafeSend(byte[] sendBuf) throws IOException {
        int sendSize = sendBuf.length;
        int partialSendCount;
        int totalSendCount = 0;

        while(totalSendCount < sendSize) {
            partialSendCount = mSocketChannel.write(ByteBuffer.wrap(sendBuf, totalSendCount, sendSize - totalSendCount));
            if(partialSendCount < 0) throw new IOException("SafeSend() Failed");
            totalSendCount += partialSendCount;

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void SafeSend(byte[] sendBuf, int length) throws IOException {
        int partialSendCount;
        int totalSendCount = 0;

        while(totalSendCount < length) {
            partialSendCount = mSocketChannel.write(ByteBuffer.wrap(sendBuf, totalSendCount, length - totalSendCount));
            if(partialSendCount < 0) throw new IOException("SafeSend() Failed");
            totalSendCount += partialSendCount;

            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void SafeSend(String str) throws IOException {
        mSocketChannel.write(ByteBuffer.wrap(str.getBytes()));
    }

    void SafeSend(int n) throws IOException {
        mSocketChannel.write(ByteBuffer.wrap(intToByte(n)));
    }


    private int byteToint(byte[] arr){
        return (arr[3] & 0xff)<<24 | (arr[2] & 0xff)<<16 |
                (arr[1] & 0xff)<<8 | (arr[0] & 0xff);
    }

    private byte[] intToByte(int n){
        byte[] b = new byte[4];

        b[3] = (byte) ((n >> 24) & 0xff);
        b[2] = (byte) ((n >> 16) & 0xff);
        b[1] = (byte) ((n >> 8) & 0xff);
        b[0] = (byte) ((n) & 0xff);

        return b;
    }
}
