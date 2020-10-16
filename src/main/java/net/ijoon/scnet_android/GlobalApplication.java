package net.ijoon.scnet_android;

import android.app.Application;

/**
 * Created by pbj on 2017. 12. 15..
 */

public class GlobalApplication extends Application implements SCNetClientCallback {
    SCNetClientCallback mCallback;


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
    public void onTimedOut() {

    }
}
