package net.ijoon.scnet_android;

import android.app.Activity;
import android.os.Bundle;

public class SCNetActivity extends Activity implements SCNetClientCallback, RendezvousClientCallback {
    GlobalApplication mGlobalApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGlobalApplication = (GlobalApplication)getApplicationContext();
        mGlobalApplication.mCallback = this;
        mGlobalApplication.mRendezvousClientCallback = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGlobalApplication.mCallback = this;
        mGlobalApplication.mRendezvousClientCallback = this;
    }

    
    @Override
    public void onAttaching() {
    }

    @Override
    public void onAttached() {
    }

    @Override
    public void onAttachFailed() {
    }

    @Override
    public void onDetach() {
    }

    @Override
    public void onTimedOut() {

    }

    @Override
    public void onResponse(MessageHeader messageHeader, byte[] bodyBuf) {
    }

    @Override
    public void onConnectedToRendezvousServer(String myPublicIP, String myPublicPort) {
    }

    @Override
    public void onConnecting(int connectionID) {
    }

    @Override
    public void onConnected(RendezvousSession rendezvousSession, Connection connection) {
    }

    @Override
    public void onConnectionIDCreated(RendezvousSession rendezvousSession, String ip, int port) {
    }

    @Override
    public void onTargetInvalid(String ip, int port) {
    }

    @Override
    public void onConnectFailed(int connectionID) {
    }

    @Override
    public void onConnectionUpdate(RendezvousSession rendezvousSession, Connection connection) {
    }

    @Override
    public void onConnectionRemoved(RendezvousSession rendezvousSession, Connection connection) {
    }

    @Override
    public void onDisconnected(int connectionID) {
    }

    @Override
    public void onMessage(MessageHeader messageHeader, byte[] buffer) {
    }
}