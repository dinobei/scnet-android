package net.ijoon.scnet_android;

import android.app.Application;

/**
 * Created by pbj on 2017. 12. 15..
 */

public class GlobalApplication extends Application implements SCNetClientCallback, RendezvousClientCallback {
    SCNetClientCallback mCallback;
    RendezvousClientCallback mRendezvousClientCallback;


    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onAttaching() {
        if(mCallback != null) mCallback.onAttaching();
    }

    @Override
    public void onAttached() {
        if(mCallback != null) mCallback.onAttached();
    }

    @Override
    public void onAttachFailed() {
        if(mCallback != null) mCallback.onAttachFailed();
    }

    @Override
    public void onDetach() {
        if(mCallback != null) mCallback.onDetach();
    }

    @Override
    public void onResponse(MessageHeader messageHeader, byte[] bodyBuf) {
        if(mCallback != null) mCallback.onResponse(messageHeader, bodyBuf);
    }

    @Override
    public void onConnectedToRendezvousServer(String myPublicIP, String myPublicPort) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onConnectedToRendezvousServer(myPublicIP, myPublicPort);
    }

    @Override
    public void onConnecting(int connectionID) {

        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onConnecting(connectionID);
    }

    @Override
    public void onConnected(RendezvousSession rendezvousSession, Connection connection) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onConnected(rendezvousSession, connection);
    }

    @Override
    public void onConnectionIDCreated(RendezvousSession rendezvousSession, String ip, int port) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onConnectionIDCreated(rendezvousSession, ip, port);
    }

    @Override
    public void onTargetInvalid(String ip, int port) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onTargetInvalid(ip, port);
    }

    @Override
    public void onConnectFailed(int connectionID) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onConnectFailed(connectionID);
    }

    @Override
    public void onConnectionUpdate(RendezvousSession rendezvousSession, Connection connection) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onConnectionUpdate(rendezvousSession, connection);
    }

    @Override
    public void onConnectionRemoved(RendezvousSession rendezvousSession, Connection connection) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onConnectionRemoved(rendezvousSession, connection);
    }

    @Override
    public void onDisconnected(int connectionID) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onDisconnected(connectionID);
    }

    @Override
    public void onMessage(MessageHeader messageHeader, byte[] buffer) {
        if(mRendezvousClientCallback != null)
            mRendezvousClientCallback.onMessage(messageHeader, buffer);
    }
}
