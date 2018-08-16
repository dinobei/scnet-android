package net.ijoon.scnet_android;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;

import com.google.protobuf.MessageLite;

import java.util.Stack;

/**
 * Created by pbj on 2017. 12. 15..
 */

public class GlobalApplication extends Application implements ClientManagerCallback {
    private ClientManager mClientManager;
    ClientManagerCallback mCallback;

    MyActivityLifecycleCallbacks mMyActivityLifecycleCallbacks;

    String mServIP = null;
    int mServPort = 0;

    public void setServerInfo(String ip, int port)
    {
        mServIP = ip;
        mServPort = port;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(mServIP == null) {
            throw new RuntimeException("Server information must be set.");
        }
        this.mMyActivityLifecycleCallbacks = new MyActivityLifecycleCallbacks();
        registerActivityLifecycleCallbacks(mMyActivityLifecycleCallbacks);
    }

    @Override
    public void onAttaching() {
        mCallback.onAttaching();
    }

    @Override
    public void onAttached() {
        mCallback.onAttached();
    }

    @Override
    public void onAttachFailed() {
        mCallback.onAttachFailed();
    }

    @Override
    public void onDetach() {
        mCallback.onDetach();
    }

    @Override
    public void onCallback(int packetType, int cryptType, byte[] bodyBuf) {
        mCallback.onCallback(packetType, cryptType, bodyBuf);
    }

    public class MyActivityLifecycleCallbacks implements ActivityLifecycleCallbacks {

        // running activity count
        private int running = 0;
        private Stack<Activity> mApplicationStack = new Stack<>();


        @Override
        public void onActivityCreated(Activity activity, Bundle bundle) {

        }

        @Override
        public void onActivityStarted(Activity activity) {
            mApplicationStack.push(activity);


            if (++running == 1) {
                // running activity is 1,
                // app must be returned from background just now (or first launch)
                mClientManager = new ClientManager(mServIP, mServPort, 1000, GlobalApplication.this);
                mClientManager.start();

            } else if (running > 1) {
                // 2 or more running activities,
                // should be foreground already.
            }
        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @RequiresApi(api = Build.VERSION_CODES.DONUT)
        @Override
        public void onActivityStopped(Activity activity) {
            mApplicationStack.pop();
            if (--running == 0) {
                // no active activity
                // app goes to background
                mClientManager.interrupt();
            }
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    }

    public void request(MessageLite message) {
        mClientManager.request(message);
    }
}
