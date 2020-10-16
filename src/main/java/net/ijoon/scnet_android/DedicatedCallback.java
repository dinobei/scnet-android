package net.ijoon.scnet_android;

import com.google.protobuf.MessageLite;
import com.scnet.SCNetPacket;

public interface DedicatedCallback {
    void onCallback(SCNetPacket.Header header, MessageLite message);
}
