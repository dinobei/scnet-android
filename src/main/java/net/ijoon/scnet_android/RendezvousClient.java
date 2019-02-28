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
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

public class RendezvousClient extends Thread {

    private DatagramChannel datagramChannel;
    private String rendezvousServerIP;
    private int rendezvousServerPort;

    public RendezvousClientCallback rendezvousClientCallback;

    private ByteBuffer byteBuffer = ByteBuffer.allocate(65535);

    private SparseArray<RendezvousSession> rendezvousSessionArray = new SparseArray<>();
    private HashMap<String, KCPPeer> kcpPeerMap = new HashMap<>();

    private KCPPeer serverKCPPeer;

    private Thread registerThread;
    private Thread rawRecvThread;
    private Thread recvThread;
    private boolean isRunning;

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

    @Override
    public synchronized void start() {
        try {
            datagramChannel = DatagramChannel.open();
            datagramChannel.socket().bind(new InetSocketAddress(0));
        } catch (IOException e) {
            throw new RuntimeException("Can't create datagram channel");
        }

        serverKCPPeer = new KCPPeer(datagramChannel, new InetSocketAddress(rendezvousServerIP, rendezvousServerPort));
        if(serverKCPPeer.getKey() == null) {
            throw new RuntimeException("getKey() return null");
        }

        kcpPeerMap.put(serverKCPPeer.getKey(), serverKCPPeer);

        // register thread
        registerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String localAddress = getLocalIpAddress(TYPE_INET4ADDRESS);
                int localPort = datagramChannel.socket().getLocalPort();

                while(!isInterrupted()) {
                    try {
                        send(serverKCPPeer, 0, RendezvousPacketType.REGISTRATION_RENDEZVOUS_CLIENT_REQUEST, localAddress + " " + localPort);
                        Thread.sleep(30*1000);

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
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


                        String key = KCPPeer.getKey(remoteAddress);
                        KCPPeer kcpPeer;
                        if(kcpPeerMap.containsKey(key)) {
                            kcpPeer = kcpPeerMap.get(key);
                        }
                        else {
                            kcpPeer = new KCPPeer(datagramChannel, remoteAddress);
                            kcpPeerMap.put(key, kcpPeer);
                        }

                        kcpPeer.lock.lock();
                        kcpPeer.kcp.Input(buffer);
                        kcpPeer.lock.unlock();
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
                byte[] buffer = new byte[100000];
                while(!isInterrupted()) {
                    HashMap<String, KCPPeer> copiedMap = new HashMap<>(kcpPeerMap);
                    for(KCPPeer kcpPeer: copiedMap.values()) {
                        kcpPeer.lock.lock();
                        int size = kcpPeer.kcp.Recv(buffer);
                        if(size > 0) {
                            // Callback
                            try {
                                callback(kcpPeer, buffer, size);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        long current = new Date().getTime();
                        if(kcpPeer.next < current) {
                            kcpPeer.kcp.Update(current);
                            kcpPeer.next = kcpPeer.kcp.Check(current);
                        }

                        kcpPeer.lock.unlock();
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        break;
                    }
                }

            }
        });
        recvThread.start();

        isRunning = true;
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

    private void send(KCPPeer kcpPeer, int connectionID, RendezvousPacketType packetType, String data) throws IOException {
        send(kcpPeer, connectionID, packetType, data, false);
    }

    private void send(KCPPeer kcpPeer, int connectionID, RendezvousPacketType packetType, String data, boolean isRelay) throws IOException {
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
            coded_output.writeInt32NoTag(packetType.toInt()); // packet type
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

    private void send(KCPPeer kcpPeer, int connectionID, MessageLite message) throws IOException {
        send(kcpPeer, connectionID, message, false);
    }

    private void send(KCPPeer kcpPeer, int connectionID, MessageLite message, boolean isRelay) throws IOException {
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

    private void callback(KCPPeer kcpPeer, byte[] buffer, int size) throws IOException {
        if(!MessageHeader.isValidMagicPacket(buffer)) {
            throw new IOException("Invalid magic packet");
        }

        CodedInputStream coded_input = CodedInputStream.newInstance(buffer);

        MessageHeader messageHeader = new MessageHeader();
        coded_input.readRawBytes(MessageHeader.MAGIC_PACKET.length);
        messageHeader.mDataSize = coded_input.readRawVarint32();
        messageHeader.mPacketType = coded_input.readRawVarint32();
        messageHeader.mMessageType = coded_input.readRawVarint32();
        messageHeader.mCryptType = coded_input.readRawVarint32();
        messageHeader.mConnectionID = coded_input.readRawVarint32();

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
                break;
            }
            case CONNECTION_FAILED:
            {
                Log.d("scnet_android", "received CONNECTION_FAILED");
                if(rendezvousClientCallback != null) {
                    rendezvousClientCallback.onConnectFailed(messageHeader.mConnectionID);
                }
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

                String targetKcpPeerKey = KCPPeer.getKey(bodyArray[0], bodyArray[1]);
                KCPPeer targetKCPPeer;
                if(kcpPeerMap.containsKey(targetKcpPeerKey)) {
                    targetKCPPeer = kcpPeerMap.get(targetKcpPeerKey);
                }
                else {
                    targetKCPPeer = new KCPPeer(datagramChannel, new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1])));
                    kcpPeerMap.put(targetKcpPeerKey, targetKCPPeer);
                }
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

                send(kcpPeer, RendezvousPacketType.DIRECT_CONNECTION_RESPONSE.toInt(), null);
                break;
            }
            case DIRECT_CONNECTION_RESPONSE: // SP only
            {
                Log.d("scnet_android", "received DIRECT_CONNECTION_RESPONSE");

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();

                KCPPeer targetKCPPeer;
                String targetKcpPeerKey = kcpPeer.getKey();
                if(kcpPeerMap.containsKey(targetKcpPeerKey)) {
                    targetKCPPeer = kcpPeerMap.get(targetKcpPeerKey);
                }
                else {
                    targetKCPPeer = new KCPPeer(datagramChannel, kcpPeer.address);
                    kcpPeerMap.put(targetKcpPeerKey, targetKCPPeer);
                }

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

                KCPPeer sourceKCPPeer;
                String sourceKcpPeerKey = KCPPeer.getKey(bodyArray[0], bodyArray[1]);
                if(kcpPeerMap.containsKey(sourceKcpPeerKey)) {
                    sourceKCPPeer = kcpPeerMap.get(sourceKcpPeerKey);
                }
                else {
                    sourceKCPPeer = new KCPPeer(datagramChannel, new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1])));
                    kcpPeerMap.put(sourceKcpPeerKey, sourceKCPPeer);
                }

                send(sourceKCPPeer, messageHeader.mConnectionID, RendezvousPacketType.REVERSE_CONNECTION_REQUEST, null);

                break;
            }
            case REVERSE_CONNECTION_REQUEST: // SP only
            {
                Log.d("scnet_android", "received REVERSE_CONNECTION_REQUEST");

                send(kcpPeer, RendezvousPacketType.REVERSE_CONNECTION_RESPONSE.toInt(), null);

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

                KCPPeer publicKCPPeer;
                String publicKcpPeerKey = KCPPeer.getKey(bodyArray[0], bodyArray[1]);
                if(kcpPeerMap.containsKey(publicKcpPeerKey)) {
                    publicKCPPeer = kcpPeerMap.get(publicKcpPeerKey);
                }
                else {
                    publicKCPPeer = new KCPPeer(datagramChannel, new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1])));
                    kcpPeerMap.put(publicKcpPeerKey, publicKCPPeer);
                }
                send(publicKCPPeer, messageHeader.mConnectionID, RendezvousPacketType.UDP_HOLE_PUNCHING_REQUEST, "1");

                KCPPeer privateKCPPeer;
                String privateKcpPeerKey = KCPPeer.getKey(bodyArray[2], bodyArray[3]);
                if(kcpPeerMap.containsKey(privateKcpPeerKey)) {
                    privateKCPPeer = kcpPeerMap.get(privateKcpPeerKey);
                }
                else {
                    privateKCPPeer = new KCPPeer(datagramChannel, new InetSocketAddress(bodyArray[2], Integer.parseInt(bodyArray[3])));
                    kcpPeerMap.put(privateKcpPeerKey, privateKCPPeer);
                }
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

                boolean isPublic = Integer.parseInt(bodyArray[0]) == 1;
                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();
                Connection connection;
                if(isPublic) {
                    // public connection (common connection)
                    rendezvousSession.publicKCPPeer = kcpPeer;
                    connection = Connection.UDP_HOLE_PUNCHING;
                    Log.d("scnet_android", "udp hole punching success from " + kcpPeer.getKey());
                }
                else {
                    // private connection (equal net, or hole punching)
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

                KCPPeer relayKCPPeer;
                String relayKcpPeerKey = KCPPeer.getKey(bodyArray[0], bodyArray[1]);
                if(kcpPeerMap.containsKey(relayKcpPeerKey)) {
                    relayKCPPeer = kcpPeerMap.get(relayKcpPeerKey);
                }
                else {
                    relayKCPPeer = new KCPPeer(datagramChannel, new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1])));
                    kcpPeerMap.put(relayKcpPeerKey, relayKCPPeer);
                }

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

                KCPPeer relayKCPPeer;
                String relayKcpPeerKey = KCPPeer.getKey(bodyArray[0], bodyArray[1]);
                if(kcpPeerMap.containsKey(relayKcpPeerKey)) {
                    relayKCPPeer = kcpPeerMap.get(relayKcpPeerKey);
                }
                else {
                    relayKCPPeer = new KCPPeer(datagramChannel, new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1])));
                    kcpPeerMap.put(relayKcpPeerKey, relayKCPPeer);
                }

                rendezvousSession.relayKCPPeer = relayKCPPeer;
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

    public boolean send(int connectionID, RendezvousPacketType packetType, String data) throws IOException {

        RendezvousSession rendezvousSession = rendezvousSessionArray.get(connectionID);
        if(rendezvousSession == null) {
            return false;
        }

        KCPPeer kcpPeer = rendezvousSession.privateKCPPeer != null ? rendezvousSession.privateKCPPeer : (rendezvousSession.publicKCPPeer != null ? rendezvousSession.publicKCPPeer : rendezvousSession.relayKCPPeer);
        if(kcpPeer == null) {
            return false;
        }

        if(rendezvousSession.privateKCPPeer == null && rendezvousSession.publicKCPPeer == null) {
            send(kcpPeer, connectionID, packetType, data, true);
        }
        else {
            send(kcpPeer, connectionID, packetType, data);
        }
        return true;
    }

    public boolean send(int connectionID, MessageLite message) throws IOException {
        RendezvousSession rendezvousSession = rendezvousSessionArray.get(connectionID);
        if(rendezvousSession == null) {
            return false;
        }

        KCPPeer kcpPeer = rendezvousSession.privateKCPPeer != null ? rendezvousSession.privateKCPPeer : (rendezvousSession.publicKCPPeer != null ? rendezvousSession.publicKCPPeer : rendezvousSession.relayKCPPeer);
        if(kcpPeer == null) {
            return false;
        }

        if(rendezvousSession.privateKCPPeer == null && rendezvousSession.publicKCPPeer == null) {
            send(kcpPeer, connectionID, message, true);
        }
        else {
            send(kcpPeer, connectionID, message);
        }
        return true;
    }

    RendezvousSession getRendezvousSessionSafety(MessageHeader messageHeader) {
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
