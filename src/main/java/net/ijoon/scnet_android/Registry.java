package net.ijoon.scnet_android;
import android.util.SparseArray;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MessageLite;
import com.google.protobuf.Parser;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by pbj on 2017. 12. 14..
 */

public class Registry {
    public interface ProtobufCallback {
        void onCallback(MessageLite message);
    }

    public interface RawbyteCallback {
        void onCallback(int packetType, byte[] message, int length);
    }

    private static Map<String, Integer> mTypeMap = new HashMap<>();
    private static SparseArray<Parser> mParserSparseArray = new SparseArray<>();
    private static SparseArray<ProtobufCallback> mProtobufCallbackSparseArray = new SparseArray<>();
    private static SparseArray<RawbyteCallback> mRawbyteCallbackSparseArray = new SparseArray<>();

    public static void registMessage(MessageLite classInscance, int packetTypeInt, ProtobufCallback callback) {
        mTypeMap.put(classInscance.getClass().getSimpleName(), packetTypeInt);
        mParserSparseArray.put(packetTypeInt, classInscance.getParserForType());
        if(callback != null) mProtobufCallbackSparseArray.put(packetTypeInt, callback);
    }

    public static void registMessage(int packetTypeInt, RawbyteCallback callback) {
        if(callback != null) mRawbyteCallbackSparseArray.put(packetTypeInt, callback);
    }

    // used in sendRequest()
    static int GetPacketType(MessageLite message) {
        if(mTypeMap.isEmpty()) throw new RuntimeException("You must regist protobuf messages");

        if(mTypeMap.containsKey(message.getClass().getSimpleName())) {
            Integer packetTypeInteger = mTypeMap.get(message.getClass().getSimpleName());
            assert packetTypeInteger != null;
            return packetTypeInteger;
        }

        throw new RuntimeException("Trying to get unknown type");
    }

    // used in recvResponse()
    static MessageLite GetMessage(int packetTypeInt, byte[] data) {
        if(mParserSparseArray.size() == 0) throw new RuntimeException("You must regist protobuf messages");

        Parser parser = mParserSparseArray.get(packetTypeInt);
        try {
            return (MessageLite)parser.parseFrom(data);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }

        throw new RuntimeException("Trying to get unknown type");
    }

    static ProtobufCallback GetProtobufCallback(int packetTypeInt) {
        return mProtobufCallbackSparseArray.get(packetTypeInt);
    }

    static RawbyteCallback GetRawbyteCallback(int packetTypeInt) {
        return mRawbyteCallbackSparseArray.get(packetTypeInt);
    }
}
