package net.ijoon.scnet_android;

import android.app.Activity;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class SCNetAppCompatActivity extends AppCompatActivity implements SCNetClientCallback,RendezvousClientCallback {
    GlobalApplication mGlobalApplication;
    public static ArrayList<Activity> activityList = new ArrayList<Activity>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityList.add(this);
        mGlobalApplication = (GlobalApplication)getApplicationContext();
        mGlobalApplication.mCallback = this;
        mGlobalApplication.mRendezvousClientCallback = this;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        activityList.remove(this);
    }

    public void activityFinish(){
        for(int i = 0; i < activityList.size(); i++)
            activityList.get(i).finish();
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
