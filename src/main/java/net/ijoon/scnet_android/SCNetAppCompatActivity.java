package net.ijoon.scnet_android;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class SCNetAppCompatActivity extends AppCompatActivity implements SCNetClientCallback {
    GlobalApplication mGlobalApplication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGlobalApplication = (GlobalApplication)getApplicationContext();
        mGlobalApplication.mCallback = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGlobalApplication.mCallback = this;
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
    public void onResponse(int packetType, int cryptType, byte[] bodyBuf) {
    }
}
