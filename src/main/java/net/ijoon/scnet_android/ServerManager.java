package net.ijoon.scnet_android;
import android.util.SparseArray;
import com.google.protobuf.MessageLite;

public class ServerManager {
    private static final ServerManager mInstance = new ServerManager();
    public static ServerManager getInstance() {
        return mInstance;
    }

    private final int MAX_CONNECTION = 64;
    private SparseArray<ServerSession> mSessionList;

    private ServerManager() {
        this.mSessionList = new SparseArray<>();
    }

    public int attach(String ip, int port, int socketTimeoutMillis, ServerManagerCallback callback) throws Exception {
        for(int i = 0 ; i < MAX_CONNECTION ; i++) {
            if(mSessionList.get(i) == null) {
                ServerSession serverSession = new ServerSession(i, ip, port, socketTimeoutMillis, callback);
                serverSession.start();

                mSessionList.put(i, serverSession);
                return i;
            }
        }

        throw new Exception("Maximum server access.");
    }

    public boolean request(int sessionIndex, MessageLite message) {
        ServerSession serverSession = mSessionList.get(sessionIndex);
        if(serverSession == null) {
            return false;
        }

        serverSession.request(message);
        return true;
    }

    public boolean detach(int sessionIndex) {
        ServerSession serverSession = mSessionList.get(sessionIndex);
        if(serverSession == null) {
            return false;
        }

        serverSession.interrupt();
        mSessionList.remove(sessionIndex);
        return true;
    }

    public void detachAll() {
        for(int i = 0 ; i < mSessionList.size() ; i++) {
            int key = mSessionList.keyAt(i);
            ServerSession serverSession = mSessionList.get(key);
            serverSession.interrupt();
        }

        mSessionList.clear();
    }


}
