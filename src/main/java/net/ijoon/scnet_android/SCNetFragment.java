package net.ijoon.scnet_android;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

public class SCNetFragment extends Fragment implements SCNetClientCallback {
    GlobalApplication mGlobalApplication;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGlobalApplication = (GlobalApplication)getActivity().getApplicationContext();
        mGlobalApplication.mCallback = this;
    }

    @Override
    public void onResume() {
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
    public void onTimedOut() {
    }
}