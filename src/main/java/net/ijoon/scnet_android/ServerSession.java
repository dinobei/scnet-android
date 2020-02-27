package net.ijoon.scnet_android;
import com.google.protobuf.MessageLite;
import java.io.IOException;

/**
 * Created by pbj on 2017. 12. 13..
 */

public class ServerSession extends Thread {
    private boolean mIsMainServerSession;
    private int mSessionIndex;

    private String mIp;
    private int mPort;
    private int mSocketTimeoutMilis;

    private RequestSender mRequestSender;
    private ResponseReceiver mResponseReceiver;
    private SCNetClientCallback mMainServerCallback;
    private ServerManagerCallback mServerManagerCallback;

//    private long mLastPing;

    public ServerSession(String ip, int port, int socketTimeoutMilis, SCNetClientCallback callback) {
        mIp = ip;
        mPort = port;
        mSocketTimeoutMilis = socketTimeoutMilis;

        mIsMainServerSession = true;
        mMainServerCallback = callback;
    }

    ServerSession(int sessionIndex, String ip, int port, int socketTimeoutMilis, ServerManagerCallback callback) {
        mIp = ip;
        mPort = port;
        mSocketTimeoutMilis = socketTimeoutMilis;

        mIsMainServerSession = false;
        mServerManagerCallback = callback;
        mSessionIndex = sessionIndex;
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

    private Stream connect() {
        Stream stream = null;
        try {
            if(mIsMainServerSession) mMainServerCallback.onAttaching();
            else mServerManagerCallback.onAttaching(mSessionIndex);

            stream = new Stream(mIp, mPort, mSocketTimeoutMilis);

        } catch (Exception e) {
            if(mIsMainServerSession) mMainServerCallback.onAttachFailed();
            else mServerManagerCallback.onAttachFailed(mSessionIndex);
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

            if(mIsMainServerSession) {
                mResponseReceiver = new ResponseReceiver(mMainServerCallback);
                mResponseReceiver.start(stream, mRequestSender); // passing client for cross-interrupt when server finished.
            }
            else {
                mResponseReceiver = new ResponseReceiver(mSessionIndex, mServerManagerCallback);
                mResponseReceiver.start(stream, mRequestSender); // passing client for cross-interrupt when server finished.
            }

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
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if(mIsMainServerSession) mMainServerCallback.onDetach();
        else mServerManagerCallback.onDetach(mSessionIndex);
    }


    @Override
    public void interrupt() {
        super.interrupt();
    }
}

