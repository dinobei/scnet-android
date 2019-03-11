package net.ijoon.scnet_android;

import android.util.Log;
import android.util.SparseArray;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.MessageLite;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.charset.Charset;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class RendezvousClient extends Thread {

    private DatagramChannel datagramChannel;
    private String rendezvousServerIP;
    private int rendezvousServerPort;

    public RendezvousClientCallback rendezvousClientCallback;

    private ByteBuffer byteBuffer = ByteBuffer.allocate(65535);

    private SparseArray<RendezvousSession> rendezvousSessionArray = new SparseArray<>();
    private final ConcurrentHashMap<String, KCPPeer> kcpPeerMap = new ConcurrentHashMap<>();

    private KCPPeer serverKCPPeer;

    private Thread registerThread;
    private Thread rawRecvThread;
    private Thread recvThread;
    private boolean isRunning;
    private boolean isCallConnCallback;

    public final static int TYPE_INET4ADDRESS = 1;
    public final static int TYPE_INET6ADDRESS = 2;

    public static String getLocalIpAddress(int type) {
        try {
            for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = ( NetworkInterface ) en.nextElement();
                for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = ( InetAddress ) enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {
                        switch (type) {
                            case TYPE_INET6ADDRESS:
                                if (inetAddress instanceof Inet6Address) {
                                    return inetAddress.getHostAddress();
                                }
                                break;
                            case TYPE_INET4ADDRESS:
                                if (inetAddress instanceof Inet4Address) {
                                    return inetAddress.getHostAddress();
                                }
                                break;
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    public RendezvousClient(String ip, int port) {
        rendezvousServerIP = ip;
        rendezvousServerPort = port;
    }

    private KCPPeer getKcpPeer(InetSocketAddress address) {
        String key = KCPPeer.getKey(address);
        KCPPeer kcpPeer;
        if(kcpPeerMap.containsKey(key)) {
            kcpPeer = kcpPeerMap.get(key);
        }
        else {
            kcpPeer = new KCPPeer(datagramChannel, address);
            kcpPeerMap.put(key, kcpPeer);
        }
        return kcpPeer;
    }

    @Override
    public synchronized void start() {
        try {
            datagramChannel = DatagramChannel.open();
            datagramChannel.socket().bind(new InetSocketAddress(0));
        } catch (IOException e) {
            throw new RuntimeException("Can't create datagram channel");
        }

        serverKCPPeer = getKcpPeer(new InetSocketAddress(rendezvousServerIP, rendezvousServerPort));

        final long timeout = 60 * 1000;
        final long pingInterval = 30 * 1000;
        final long loopInterval = 5 * 1000;
        // register thread
        registerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String localAddress = getLocalIpAddress(TYPE_INET4ADDRESS);
                int localPort = datagramChannel.socket().getLocalPort();
                String data = localAddress + " " + localPort + " android";

                send(serverKCPPeer, 0, RendezvousPacketType.REGISTRATION_RENDEZVOUS_CLIENT_REQUEST, data);

                long lastRegistrationTime = Calendar.getInstance().getTimeInMillis();

                ArrayList<String> willDeleteKcpPeer = new ArrayList<>();
                while(!isInterrupted()) {
                    try {
                        Thread.sleep(loopInterval);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }

                    long current = Calendar.getInstance().getTimeInMillis();

                    if(lastRegistrationTime + pingInterval < current) {
                        lastRegistrationTime = current;
                        send(serverKCPPeer, 0, RendezvousPacketType.REGISTRATION_RENDEZVOUS_CLIENT_REQUEST, data);
                    }

                    willDeleteKcpPeer.clear();
                    synchronized (kcpPeerMap) {
                        for (KCPPeer kcpPeer : kcpPeerMap.values()) {
                            if (kcpPeer.lastPing + timeout < current) {
                                willDeleteKcpPeer.add(kcpPeer.getKey());
                            } else if (kcpPeer.lastPing + pingInterval < current) {
                                send(kcpPeer, 0, RendezvousPacketType.PING_REQUEST, null);
                            }
                        }

                        for(int i = 0 ; i < willDeleteKcpPeer.size() ; i++) {
                            Log.i("scnet_android", "removed kcpPeer: " + willDeleteKcpPeer.get(i));
                            kcpPeerMap.remove(willDeleteKcpPeer.get(i));
                        }
                    }

                    for(int i = 0 ; i < rendezvousSessionArray.size() ; i++) {
                        RendezvousSession rendezvousSession = rendezvousSessionArray.valueAt(i);
                        if(rendezvousSession == null) continue;
                        if(rendezvousSession.relayKCPPeer != null) {
                            KCPPeer relayKcpPeer = rendezvousSession.relayKCPPeer;
                            if(relayKcpPeer.lastPing + timeout < current) {
                                Log.i("scnet_android", "relay peer removed, " + relayKcpPeer.lastPing);
                                rendezvousSession.relayKCPPeer = null;
                                if(rendezvousClientCallback != null) {
                                    rendezvousClientCallback.onConnectionRemoved(rendezvousSession, Connection.RELAY);
                                }
                            }
                        }

                        if(rendezvousSession.publicKCPPeer != null) {
                            KCPPeer publicKcpPeer = rendezvousSession.publicKCPPeer;
                            if(publicKcpPeer.lastPing + timeout < current) {
                                Log.i("scnet_android", "public peer removed, " + publicKcpPeer.lastPing);
                                rendezvousSession.publicKCPPeer = null;
                                if(rendezvousClientCallback != null) {
                                    rendezvousClientCallback.onConnectionRemoved(rendezvousSession, Connection.PUBLIC);
                                }
                            }
                        }

                        if(rendezvousSession.privateKCPPeer != null) {
                            KCPPeer privateKcpPeer = rendezvousSession.privateKCPPeer;
                            if(privateKcpPeer.lastPing + timeout < current) {
                                Log.i("scnet_android", "private peer removed, " + privateKcpPeer.lastPing);
                                rendezvousSession.privateKCPPeer = null;
                                if(rendezvousClientCallback != null) {
                                    rendezvousClientCallback.onConnectionRemoved(rendezvousSession, Connection.PRIVATE);
                                }
                            }
                        }

                        if(!rendezvousSession.isConnected()) {
                            int connectionID = rendezvousSession.connectionID;
                            rendezvousSessionArray.delete(connectionID);
                            if(rendezvousClientCallback != null) {
                                rendezvousClientCallback.onDisconnected(connectionID);
                            }

                            Log.i("scnet_android", "Disconnected, connectionID=" + connectionID);
                        }
                    }

                }

            }
        });
        registerThread.start();

        // Raw recv thread
        rawRecvThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(!isInterrupted()) {
                    try {
                        byteBuffer.clear();
                        InetSocketAddress remoteAddress = (InetSocketAddress) datagramChannel.receive(byteBuffer);
                        byteBuffer.flip();

                        byte[] buffer = byteBuffer.array();

                        synchronized (kcpPeerMap) {
                            KCPPeer kcpPeer = getKcpPeer(remoteAddress);
                            kcpPeer.lock.lock();
                            kcpPeer.kcp.Input(buffer);
                            kcpPeer.lock.unlock();
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }

            }
        });
        rawRecvThread.start();

        // recv thread
        recvThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[MessageHeader.MAX_RECV_SIZE];
                while(!isInterrupted()) {
                    long current = Calendar.getInstance().getTimeInMillis();
                    long minInterval = 10;
                    synchronized (kcpPeerMap) {
                        for(KCPPeer kcpPeer: kcpPeerMap.values()) {
                            kcpPeer.lock.lock();

                            int size = kcpPeer.kcp.Recv(buffer);
                            if(kcpPeer.next < current) {
                                kcpPeer.kcp.Update(current);
                                kcpPeer.next = kcpPeer.kcp.Check(current);

                                if( (kcpPeer.next - current) < minInterval ) minInterval = (kcpPeer.next - current);
                            }

                            kcpPeer.lock.unlock();

                            if(size > 0) {
                                try {
                                    callback(kcpPeer, buffer, size);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                    try {
                        Thread.sleep(minInterval);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }

            }
        });
        recvThread.start();

        isRunning = true;
        isCallConnCallback = false;
    }


    public void finish() {
        registerThread.interrupt();
        rawRecvThread.interrupt();
        recvThread.interrupt();


        try {
            datagramChannel.close();

            registerThread.join();
            rawRecvThread.join();
            recvThread.join();

            kcpPeerMap.clear();
            rendezvousSessionArray.clear();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        isRunning = false;
    }

    public boolean getIsRunning() {
        return isRunning;
    }

    private void send(KCPPeer kcpPeer, int connectionID, RendezvousPacketType packetType, String data) {
        _send(kcpPeer, connectionID, packetType.toInt(), data, false);
    }

    private void sendRelay(KCPPeer kcpPeer, int connectionID, RendezvousPacketType packetType, String data)  {
        _send(kcpPeer, connectionID, packetType.toInt(), data, true);
    }

    private void _send(KCPPeer kcpPeer, int connectionID, int packetType, String data, boolean isRelay) {
        ByteBuffer byteBuffer = null;
        int dataSize = 0;
        if(data != null) {
            byteBuffer = ByteBuffer.wrap(data.getBytes());
            dataSize = byteBuffer.array().length;
        }

        byte []serialized = new byte[MessageHeader.MAGIC_PACKET.length + dataSize + MessageHeader.MAX_PACKET_HEADER_SIZE];
        CodedOutputStream coded_output = CodedOutputStream.newInstance(serialized, 0, serialized.length);

        MessageHeader.MessageType messageType = isRelay ? MessageHeader.MessageType.RAWBYTE_RELAY : MessageHeader.MessageType.RAWBYTE;
        try {
            coded_output.writeRawBytes(MessageHeader.MAGIC_PACKET);
            coded_output.writeInt32NoTag(dataSize); // data size
            coded_output.writeInt32NoTag(packetType); // packet type
            coded_output.writeInt32NoTag(messageType.toInt()); // message type
            coded_output.writeInt32NoTag(0); // crypt type
            coded_output.writeInt32NoTag(connectionID); // connectionID
            if(data != null) {
                coded_output.writeRawBytes(byteBuffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        kcpPeer.lock.lock();
        kcpPeer.kcp.Send(Arrays.copyOf(serialized, coded_output.getTotalBytesWritten()));
        kcpPeer.lock.unlock();
    }

    private void send(KCPPeer kcpPeer, int connectionID, MessageLite message) {
        _send(kcpPeer, connectionID, message, false);
    }

    private void sendRelay(KCPPeer kcpPeer, int connectionID, MessageLite message) {
        _send(kcpPeer, connectionID, message, true);
    }

    private void _send(KCPPeer kcpPeer, int connectionID, MessageLite message, boolean isRelay) {
        int dataSize = message.getSerializedSize();
        byte []serialized = new byte[MessageHeader.MAGIC_PACKET.length + dataSize + MessageHeader.MAX_PACKET_HEADER_SIZE];
        CodedOutputStream coded_output = CodedOutputStream.newInstance(serialized, 0, serialized.length);

        MessageHeader.MessageType messageType = isRelay ? MessageHeader.MessageType.PROTOBUF_RELAY : MessageHeader.MessageType.PROTOBUF;
        try {
            coded_output.writeRawBytes(MessageHeader.MAGIC_PACKET);
            coded_output.writeInt32NoTag(dataSize); // data size
            coded_output.writeInt32NoTag(Registry.GetPacketType(message)); // packet type
            coded_output.writeInt32NoTag(messageType.toInt()); // message type
            coded_output.writeInt32NoTag(0); // crypt type
            coded_output.writeInt32NoTag(connectionID); // connection id
            message.writeTo(coded_output);
        } catch (IOException e) {
            e.printStackTrace();
        }

        kcpPeer.lock.lock();
        kcpPeer.kcp.Send(Arrays.copyOf(serialized, coded_output.getTotalBytesWritten()));
        kcpPeer.lock.unlock();
    }

    private MessageHeader messageHeader = new MessageHeader();
    private void callback(KCPPeer kcpPeer, byte[] buffer, int size) throws IOException {
        if(!MessageHeader.isValidMagicPacket(buffer)) {
            throw new IOException("Invalid magic packet");
        }

        CodedInputStream coded_input = CodedInputStream.newInstance(buffer);

        coded_input.readRawBytes(MessageHeader.MAGIC_PACKET.length);
        messageHeader.mDataSize = coded_input.readRawVarint32();
        messageHeader.mPacketType = coded_input.readRawVarint32();
        messageHeader.mMessageType = coded_input.readRawVarint32();
        messageHeader.mCryptType = coded_input.readRawVarint32();
        messageHeader.mConnectionID = coded_input.readRawVarint32();

        kcpPeer.lastPing = Calendar.getInstance().getTimeInMillis();

        MessageHeader.MessageType messageType = MessageHeader.MessageType.fromInt(messageHeader.mMessageType);

        switch (messageType) {
            case PROTOBUF:
                if(rendezvousClientCallback != null) {
                    byte[] bodyByteArray = Arrays.copyOfRange(buffer, coded_input.getTotalBytesRead(), size);
                    rendezvousClientCallback.onMessage(messageHeader, bodyByteArray);
                }
                return;
            case RAWBYTE:
            {
                RendezvousPacketType rendezvousPacketType = RendezvousPacketType.fromInt(messageHeader.mPacketType);
                if(rendezvousPacketType != RendezvousPacketType.NONE) break;

                if(rendezvousClientCallback != null) {
                    byte[] bodyByteArray = Arrays.copyOfRange(buffer, coded_input.getTotalBytesRead(), size);
                    rendezvousClientCallback.onMessage(messageHeader, bodyByteArray);
                }

                return;
            }
            default:
                break;
        }

        if(messageType != MessageHeader.MessageType.RAWBYTE) {
            throw new RuntimeException("currently only support message type RAWBYTE for Rendezvous message");
        }

        RendezvousPacketType rendezvousPacketType = RendezvousPacketType.fromInt(messageHeader.mPacketType);
        String body = "";
        if(messageHeader.mDataSize != 0) {
            Charset charset = Charset.forName("UTF-8");
            ByteBuffer bodyBuffer = ByteBuffer.wrap(buffer, coded_input.getTotalBytesRead(), messageHeader.mDataSize);
            body = charset.decode(bodyBuffer).toString();
        }
        switch (rendezvousPacketType) {
            case REGISTRATION_RENDEZVOUS_CLIENT_SUCCESS: {
                Log.d("scnet_android", "received REGISTRATION_RENDEZVOUS_CLIENT_SUCCESS");

                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 2) {
                    Log.e("scnet_android", "[REGISTRATION_RENDEZVOUS_CLIENT_SUCCESS] invalid parameters");
                    break;
                }

                String myPublicIP = bodyArray[0];
                String myPublicPort = bodyArray[1];
                Log.i("scnet_android", "[REGISTRATION_RENDEZVOUS_CLIENT_SUCCESS] MyPublicAddress=" + KCPPeer.getKey(myPublicIP, myPublicPort));

                if(!isCallConnCallback) {
                    isCallConnCallback = true;
                    if(rendezvousClientCallback != null) {
                        rendezvousClientCallback.onConnectedToRendezvousServer(myPublicIP, myPublicPort);
                    }
                }
                break;
            }
            case CONNECTION_TARGET_INVALID:
            {
                Log.d("scnet_android", "received CONNECTION_TARGET_INVALID");
                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 2) {
                    Log.e("scnet_android", "[CONNECTION_TARGET_INVALID] invalid parameters");
                    break;
                }

                if(rendezvousClientCallback != null) {
                    rendezvousClientCallback.onTargetInvalid(bodyArray[0], Integer.parseInt(bodyArray[1]));
                }
                break;
            }
            case CONNECTION_ID_CREATED:
            {
                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 2) {
                    Log.e("scnet_android", "[CONNECTION_ID_CREATED] invalid parameters");
                    break;
                }

                if(rendezvousClientCallback != null) {
                    RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                    rendezvousClientCallback.onConnectionIDCreated(rendezvousSession, bodyArray[0], Integer.parseInt(bodyArray[1]));
                }

                send(kcpPeer, messageHeader.mConnectionID, RendezvousPacketType.CONNECTION_ID_RECEIVED, bodyArray[0]);
                break;
            }
            case DIRECT_CONNECTION_AVAILABLE: // SP only
            {
                Log.d("scnet_android", "received DIRECT_CONNECTION_AVAILABLE");

                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 2) {
                    Log.e("scnet_android", "[DIRECT_CONNECTION_AVAILABLE] invalid parameters");
                    break;
                }

                if(rendezvousClientCallback != null) {
                    rendezvousClientCallback.onConnecting(messageHeader.mConnectionID);
                }

                InetSocketAddress address = new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1]));
                KCPPeer targetKCPPeer = getKcpPeer(address);
                send(targetKCPPeer, messageHeader.mConnectionID, RendezvousPacketType.DIRECT_CONNECTION_REQUEST, null);
                break;
            }
            case DIRECT_CONNECTION_REQUEST: // TP only
            {
                Log.d("scnet_android", "received DIRECT_CONNECTION_REQUEST");

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();

                rendezvousSession.publicKCPPeer = kcpPeer;

                Log.d("scnet_android", "directly connected from " + kcpPeer.getKey());

                if(rendezvousClientCallback != null) {
                    if(!isConnected) {
                        rendezvousClientCallback.onConnected(rendezvousSession, Connection.DIRECT_CONNECTION);
                    }
                    else {
                        rendezvousClientCallback.onConnectionUpdate(rendezvousSession, Connection.DIRECT_CONNECTION);
                    }
                }

                send(kcpPeer, messageHeader.mConnectionID, RendezvousPacketType.DIRECT_CONNECTION_RESPONSE, null);
                break;
            }
            case DIRECT_CONNECTION_RESPONSE: // SP only
            {
                Log.d("scnet_android", "received DIRECT_CONNECTION_RESPONSE");

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();
                rendezvousSession.publicKCPPeer = kcpPeer;

                Log.d("scnet_android", "directly connected from " + kcpPeer.getKey());
                if(rendezvousClientCallback != null) {
                    if(!isConnected) {
                        rendezvousClientCallback.onConnected(rendezvousSession, Connection.DIRECT_CONNECTION);
                    }
                    else {
                        rendezvousClientCallback.onConnectionUpdate(rendezvousSession, Connection.DIRECT_CONNECTION);
                    }
                }

                break;
            }
            case REVERSE_CONNECTION_READY: // SP only
            {
                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                if(!rendezvousSession.isConnected()) {
                    if(rendezvousClientCallback != null) {
                        rendezvousClientCallback.onConnecting(messageHeader.mConnectionID);
                    }
                }
                break;
            }
            case REVERSE_CONNECTION: // TP only
            {
                Log.d("scnet_android", "received REVERSE_CONNECTION");

                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 2) {
                    Log.e("scnet_android", "[REVERSE_CONNECTION] invalid parameters");
                    break;
                }

                InetSocketAddress sourceAddress = new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1]));
                KCPPeer sourceKCPPeer = getKcpPeer(sourceAddress);
                send(sourceKCPPeer, messageHeader.mConnectionID, RendezvousPacketType.REVERSE_CONNECTION_REQUEST, null);

                break;
            }
            case REVERSE_CONNECTION_REQUEST: // SP only
            {
                Log.d("scnet_android", "received REVERSE_CONNECTION_REQUEST");

                send(kcpPeer, messageHeader.mConnectionID, RendezvousPacketType.REVERSE_CONNECTION_RESPONSE, null);

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();

                rendezvousSession.publicKCPPeer = kcpPeer;
                Log.d("scnet_android", "reversely connected from " + kcpPeer.getKey());

                if(rendezvousClientCallback != null) {
                    if(!isConnected) {
                        rendezvousClientCallback.onConnected(rendezvousSession, Connection.REVERSE_CONNECTION);
                    }
                    else {
                        rendezvousClientCallback.onConnectionUpdate(rendezvousSession, Connection.REVERSE_CONNECTION);
                    }
                }
                break;
            }
            case REVERSE_CONNECTION_RESPONSE: // TP only
            {
                Log.d("scnet_android", "received REVERSE_CONNECTION_RESPONSE");

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();
                rendezvousSession.publicKCPPeer = kcpPeer;
                Log.d("scnet_android", "reversely connected from " + kcpPeer.getKey());

                if(rendezvousClientCallback != null) {
                    if(!isConnected) {
                        rendezvousClientCallback.onConnected(rendezvousSession, Connection.REVERSE_CONNECTION);
                    }
                    else {
                        rendezvousClientCallback.onConnectionUpdate(rendezvousSession, Connection.REVERSE_CONNECTION);
                    }
                }
                break;
            }

            case UDP_HOLE_PUNCHING_AVAILABLE:
            {
                Log.d("scnet_android", "received UDP_HOLE_PUNCHING_AVAILABLE");

                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 4) {
                    Log.e("scnet_android", "[UDP_HOLE_PUNCHING_AVAILABLE] invalid parameters");
                    break;
                }

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                if(!rendezvousSession.isConnected()) {
                    if(rendezvousClientCallback != null) {
                        rendezvousClientCallback.onConnecting(messageHeader.mConnectionID);
                    }
                }

                InetSocketAddress publicAddress = new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1]));
                KCPPeer publicKCPPeer = getKcpPeer(publicAddress);
                send(publicKCPPeer, messageHeader.mConnectionID, RendezvousPacketType.UDP_HOLE_PUNCHING_REQUEST, "1");

                InetSocketAddress privateAddress = new InetSocketAddress(bodyArray[2], Integer.parseInt(bodyArray[3]));
                KCPPeer privateKCPPeer = getKcpPeer(privateAddress);
                send(privateKCPPeer, messageHeader.mConnectionID, RendezvousPacketType.UDP_HOLE_PUNCHING_REQUEST, "0");

                break;
            }
            case UDP_HOLE_PUNCHING_REQUEST:
            {
                Log.d("scnet_android", "received UDP_HOLE_PUNCHING_REQUEST");

                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 1) {
                    Log.e("scnet_android", "[UDP_HOLE_PUNCHING_REQUEST] invalid parameters");
                    break;
                }

                send(kcpPeer, messageHeader.mConnectionID, RendezvousPacketType.UDP_HOLE_PUNCHING_RESPONSE, bodyArray[0]);

                break;
            }
            case UDP_HOLE_PUNCHING_RESPONSE:
            {
                Log.d("scnet_android", "received UDP_HOLE_PUNCHING_RESPONSE");

                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 1) {
                    Log.e("scnet_android", "[UDP_HOLE_PUNCHING_RESPONSE] invalid parameters");
                    break;
                }

                Log.d("logdinobei", "Integer.parseInt(bodyArray[0]): " + bodyArray[0]);
                boolean isPublic = Integer.parseInt(bodyArray[0]) == 1;
                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();
                Connection connection;
                if(isPublic) {
                    // udp hole punching
                    rendezvousSession.publicKCPPeer = kcpPeer;
                    connection = Connection.UDP_HOLE_PUNCHING;
                    Log.d("scnet_android", "connected by udp hole punching from " + kcpPeer.getKey());
                }
                else {
                    // equal NAT
                    rendezvousSession.privateKCPPeer = kcpPeer;
                    connection = Connection.EQUAL_NAT;
                    Log.d("scnet_android", "connected under equal NAT from " + kcpPeer.getKey());
                }

                if(rendezvousClientCallback != null) {
                    if(!isConnected) {
                        rendezvousClientCallback.onConnected(rendezvousSession, connection);
                    }
                    else {
                        rendezvousClientCallback.onConnectionUpdate(rendezvousSession, connection);
                    }
                }

                break;
            }
            case RELAY_SERVER_INFORMATION:
            {
                Log.d("scnet_android", "received RELAY_SERVER_INFORMATION");

                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 3) {
                    Log.e("scnet_android", "[RELAY_SERVER_INFORMATION] invalid parameters");
                    break;
                }

                // regist connection ID
                // note: wrap below code by function
                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);

                if(rendezvousClientCallback != null) {
                    rendezvousClientCallback.onConnecting(messageHeader.mConnectionID);
                }

                Log.d("scnet_android", "relay server: " + bodyArray[0] + ", " + bodyArray[1]);

                InetSocketAddress relayAddress = new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1]));
                KCPPeer relayKCPPeer = getKcpPeer(relayAddress);
                send(relayKCPPeer, messageHeader.mConnectionID, RendezvousPacketType.REGISTRATION_RELAY_PEER_REQUEST, bodyArray[2]);
                break;
            }
            case CONNECTION_RELAY_SERVICE_SUCCESS:
            {
                Log.d("scnet_android", "received CONNECTION_RELAY_SERVICE_SUCCESS");

                String[] bodyArray = body.split(" ");
                if(bodyArray.length != 2) {
                    Log.e("scnet_android", "[CONNECTION_RELAY_SERVICE_SUCCESS] invalid parameters");
                    break;
                }

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);

                InetSocketAddress relayAddress = new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1]));
                rendezvousSession.relayKCPPeer = getKcpPeer(relayAddress);
                Log.d("scnet_android", "connected by a relay from " + KCPPeer.getKey(bodyArray[0], bodyArray[1]));
                if(rendezvousClientCallback != null) {
                    rendezvousClientCallback.onConnected(rendezvousSession, Connection.RELAY);
                }

                break;
            }
            case CONNECTION_RELAY_SERVICE_FAILED:
            {
                Log.d("scnet_android", "received CONNECTION_RELAY_SERVICE_FAILED");
                break;
            }
            case REGISTRATION_RELAY_PEER_SUCCESS:
            {
                Log.d("scnet_android", "received REGISTRATION_RELAY_PEER_SUCCESS");
                break;
            }
            case REGISTRATION_RELAY_PEER_FAILED:
            {
                Log.d("scnet_android", "received REGISTRATION_RELAY_PEER_FAILED");
                break;
            }
            case PING_REQUEST:
            {
                Log.d("scnet_android", "received PING_REQUEST from " + kcpPeer.getKey());
                send(kcpPeer, 0, RendezvousPacketType.PING_RESPONSE, null);
                break;
            }
            case PING_RESPONSE:
            {
                Log.d("scnet_android", "received PING_RESPONSE");
                break;
            }
            default:
            {
                Log.e("scnet_android", "Undefined message received, " + rendezvousPacketType);
                break;
            }


        }

    }

    public void connect(String ip, int port) throws IOException {
        String data = ip + " " + port;
        send(serverKCPPeer, 0, RendezvousPacketType.CONNECTION_REQUEST, data);
    }

    public boolean send(int packetType, String data) {
        if(RendezvousPacketType.fromInt(packetType) != RendezvousPacketType.NONE) {
            Log.d("scnet_android", "User application can't send rendezvous message.");
            return false;
        }

        KCPPeer kcpPeer = getKcpPeer(new InetSocketAddress(rendezvousServerIP, rendezvousServerPort));
        _send(kcpPeer, 0, packetType, data, false);
        return true;
    }

    public boolean send(MessageLite message) {
        KCPPeer kcpPeer = getKcpPeer(new InetSocketAddress(rendezvousServerIP, rendezvousServerPort));
        _send(kcpPeer, 0, message, false);
        return true;
    }

    public boolean send(int connectionID, RendezvousPacketType packetType, String data) {

        RendezvousSession rendezvousSession = rendezvousSessionArray.get(connectionID);
        if(rendezvousSession == null) {
            return false;
        }

        KCPPeer kcpPeer = rendezvousSession.privateKCPPeer != null ? rendezvousSession.privateKCPPeer : (rendezvousSession.publicKCPPeer != null ? rendezvousSession.publicKCPPeer : rendezvousSession.relayKCPPeer);
        if(kcpPeer == null) {
            return false;
        }

        if(rendezvousSession.privateKCPPeer == null && rendezvousSession.publicKCPPeer == null) {
            sendRelay(kcpPeer, connectionID, packetType, data);
        }
        else {
            send(kcpPeer, connectionID, packetType, data);
        }
        return true;
    }

    public boolean send(int connectionID, MessageLite message) {
        RendezvousSession rendezvousSession = rendezvousSessionArray.get(connectionID);
        if(rendezvousSession == null) {
            return false;
        }

        KCPPeer kcpPeer;
        if(rendezvousSession.privateKCPPeer != null) {
            kcpPeer = rendezvousSession.privateKCPPeer;
            _send(kcpPeer, connectionID, message, false);
        }
        else if(rendezvousSession.publicKCPPeer != null) {
            kcpPeer = rendezvousSession.publicKCPPeer;
            _send(kcpPeer, connectionID, message, false);
        }
        else if(rendezvousSession.relayKCPPeer != null) {
            kcpPeer = rendezvousSession.relayKCPPeer;
            _send(kcpPeer, connectionID, message, true);
        }
        else {
            return false;
        }

        return true;
    }

    private RendezvousSession getRendezvousSessionSafety(MessageHeader messageHeader) {
        RendezvousSession rendezvousSession = rendezvousSessionArray.get(messageHeader.mConnectionID);
        if(rendezvousSession == null) {
            rendezvousSession = new RendezvousSession(messageHeader.mConnectionID);
            if(messageHeader.mConnectionID != 0) {
                rendezvousSessionArray.append(messageHeader.mConnectionID, rendezvousSession);
            }
        }

        return rendezvousSession;
    }
}
