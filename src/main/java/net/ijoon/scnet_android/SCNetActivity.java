package net.ijoon.scnet_android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import java.util.Locale;

public class SCNetActivity extends Activity implements SCNetClientCallback {
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
    public void onResponse(MessageHeader messageHeader, byte[] bodyBuf) {
    }
}
