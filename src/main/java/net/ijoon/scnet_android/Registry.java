package net.ijoon.scnet_android;
import android.util.SparseArray;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;
import com.scnet.SCNetPacket;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by pbj on 2017. 12. 14..
 */

public class Registry {
    public interface ProtobufCallback {
        void onCallback(SCNetPacket.Header header, MessageLite message);
    }

    private static Map<String, Integer> mTypeMap = new HashMap<>();
    private static SparseArray<Parser> mParserSparseArray = new SparseArray<>();
    private static SparseArray<ProtobufCallback> mProtobufCallbackSparseArray = new SparseArray<>();

    public static void registMessage(MessageLite classInscance, int packetTypeInt, ProtobufCallback callback) {
        mTypeMap.put(classInscance.getClass().getSimpleName(), packetTypeInt);
        mParserSparseArray.put(packetTypeInt, classInscance.getParserForType());
        if(callback != null) mProtobufCallbackSparseArray.put(packetTypeInt, callback);
    }

    static int GetPacketType(MessageLite message) {
        if(mTypeMap.isEmpty()) throw new RuntimeException("You must regist protobuf messages");

        if(mTypeMap.containsKey(message.getClass().getSimpleName())) {
            Integer packetTypeInteger = mTypeMap.get(message.getClass().getSimpleName());
            assert packetTypeInteger != null;
            return packetTypeInteger;
        }

        throw new RuntimeException("Trying to get unknown type");
    }

    static MessageLite GetMessage(int packetTypeInt, byte[] data) {
        return GetMessage(packetTypeInt, data, 0, data.length);
    }

    static MessageLite GetMessage(int packetTypeInt, byte[] data, int off, int len) {
        if(mParserSparseArray.size() == 0) throw new RuntimeException("You must regist protobuf messages");

        Parser parser = mParserSparseArray.get(packetTypeInt);
        try {
            return (MessageLite)parser.parseFrom(data, off, len);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }

        throw new RuntimeException("Trying to get unknown type");
    }

    static ProtobufCallback GetProtobufCallback(int packetTypeInt) {
        return mProtobufCallbackSparseArray.get(packetTypeInt);
    }
}
