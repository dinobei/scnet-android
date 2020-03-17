package net.ijoon.scnet_android;
import com.google.protobuf.MessageLite;
import java.io.IOException;

/**
 * Created by pbj on 2017. 12. 13..
 */

public class ServerSession extends Thread {
    private String mIp;
    private int mPort;
    private int mSocketTimeoutMilis;

    private RequestSender mRequestSender;
    private ResponseReceiver mResponseReceiver;
    private SCNetClientCallback mClientCallback;

//    private long mLastPing;

    public ServerSession(String ip, int port, int socketTimeoutMilis, SCNetClientCallback callback) {
        mIp = ip;
        mPort = port;
        mSocketTimeoutMilis = socketTimeoutMilis;

        mClientCallback = callback;
    }

    public boolean request(MessageLite message) {
        boolean result = true;

        try {
            mRequestSender.request(message);
        }
        catch (NullPointerException e) {
            result = false;
        }

        return result;
    }

    public boolean request(int packetType, byte[] message) {
        return request(packetType, message, message.length);
    }

    public boolean request(int packetType, byte[] message, int length) {
        boolean result = true;

        try {
            mRequestSender.request(packetType, message, length);
        }
        catch (NullPointerException e) {
            result = false;
        }

        return result;
    }

    private Stream connect() {
        Stream stream = null;
        try {
            mClientCallback.onAttaching();

            stream = new Stream(mIp, mPort, mSocketTimeoutMilis);

        } catch (Exception e) {
            mClientCallback.onAttachFailed();
        }

        return stream;
    }

    @Override
    public void run() {
        super.run();

        while(!isInterrupted()) {

            Stream stream = connect();
            if(stream == null) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }

            // Create send/recv thread when connected to server
            mRequestSender = new RequestSender();
            mRequestSender.start(stream, mResponseReceiver);

            mResponseReceiver = new ResponseReceiver(mClientCallback);
            mResponseReceiver.start(stream, mRequestSender); // passing client for cross-interrupt when server finished.

            try {
                mResponseReceiver.join();
                mRequestSender.interrupt();
            }  catch (InterruptedException ignored) {
                try {
                    mResponseReceiver.interrupt();
                    mRequestSender.interrupt();

                    mResponseReceiver.join();
                    mRequestSender.join();

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
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        mClientCallback.onDetach();
    }

    @Override
    public void interrupt() {
        super.interrupt();
    }
}

