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
    private SCNetClientCallback mCallback;

//    private long mLastPing;

    ServerSession(String ip, int port, int socketTimeoutMilis, SCNetClientCallback callback) {
        mIp = ip;
        mPort = port;
        mSocketTimeoutMilis = socketTimeoutMilis;

        mCallback = callback;
    }

    boolean request(MessageLite message) {
        boolean result = true;

        try {
            mRequestSender.request(message);
        }
        catch (NullPointerException e) {
            result = false;
        }

        return result;
    }

    private Stream connect() {
        Stream stream = null;
        try {
            mCallback.onAttaching();

            stream = new Stream(mIp, mPort, mSocketTimeoutMilis);

        } catch (Exception e) {
            mCallback.onAttachFailed();
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
            mResponseReceiver = new ResponseReceiver(mCallback);

            mRequestSender.start(stream, mResponseReceiver);
            mResponseReceiver.start(stream, mRequestSender); // passing client for cross-interrupt when server finished.

            try {
                mResponseReceiver.join();
                mRequestSender.interrupt();
            }  catch (InterruptedException ignored) {
                try {
                    mRequestSender.join(200);
                    if(mRequestSender.isAlive()) {
                        mRequestSender.interrupt();
                        super.interrupt();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    super.interrupt();
                }
            }

            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        mCallback.onDetach();
    }


    @Override
    public void interrupt() {
        super.interrupt();
    }
}

