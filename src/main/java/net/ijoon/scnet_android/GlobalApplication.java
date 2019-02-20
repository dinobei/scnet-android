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

public class GlobalApplication extends Application implements SCNetClientCallback {
    ServerSession mServerSession;
    SCNetClientCallback mCallback;

    MyActivityLifecycleCallbacks mMyActivityLifecycleCallbacks;

    String mServIP = null;
    int mServPort = 0;
    int mSocketTimeoutMillis = 1000;

    boolean useAutoServerSession;

    public void setUseAutoServerSession(boolean useAutoServerSession) {
        this.useAutoServerSession = useAutoServerSession;
    }

    public void setMainServerInfo(String ip, int port, int socketTimeoutMillis)
    {
        this.mServIP = ip;
        this.mServPort = port;
        this.mSocketTimeoutMillis = socketTimeoutMillis;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(useAutoServerSession && mServIP == null) {
            throw new RuntimeException("Server information must be set.");
        }
        this.mMyActivityLifecycleCallbacks = new MyActivityLifecycleCallbacks();
        registerActivityLifecycleCallbacks(mMyActivityLifecycleCallbacks);
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
                if(useAutoServerSession) {
                    mServerSession = new ServerSession(mServIP, mServPort, mSocketTimeoutMillis, GlobalApplication.this);
                    mServerSession.start();
                }
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
                if(useAutoServerSession) {
                    mServerSession.interrupt();
                }
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
        if(!useAutoServerSession) {
            throw new RuntimeException("call setUseAutoServerSession(true) if you want to use this method");
        }
        mServerSession.request(message);
    }
}
