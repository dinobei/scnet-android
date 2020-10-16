package net.ijoon.scnet_android;

import android.app.Activity;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class SCNetAppCompatActivity extends AppCompatActivity implements SCNetClientCallback {
    GlobalApplication mGlobalApplication;
    public static ArrayList<Activity> activityList = new ArrayList<Activity>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityList.add(this);
        mGlobalApplication = (GlobalApplication)getApplicationContext();
        mGlobalApplication.mCallback = this;
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
}
