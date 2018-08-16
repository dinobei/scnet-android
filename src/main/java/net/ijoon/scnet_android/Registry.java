package net.ijoon.scnet_android;
import com.google.protobuf.MessageLite;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by pbj on 2017. 12. 14..
 */

public class Registry {
    private static Map<String, Integer> mTypeMap = new HashMap<>();


    public static void registMessage(String className, int packetTypeInt) {
        mTypeMap.put(className, packetTypeInt);
    }

    // used in sendRequest()
    static int GetPacketType(MessageLite message)
    {
        if(mTypeMap.isEmpty()) throw new RuntimeException("You must regist protobuf messages");

        if(mTypeMap.containsKey(message.getClass().getSimpleName()))
            return mTypeMap.get(message.getClass().getSimpleName());

        throw new RuntimeException("Trying to get unknown type");
    }
}
