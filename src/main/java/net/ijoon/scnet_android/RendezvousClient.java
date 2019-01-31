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
import java.util.Enumeration;

public class RendezvousClient extends Thread {

    DatagramChannel datagramChannel;
    String rendezvousServerIP;
    int rendezvousServerPort;

    public RendezvousClientCallback rendezvousClientCallback;

    ByteBuffer byteBuffer = ByteBuffer.allocate(65535);

    SparseArray<RendezvousSession> rendezvousSessionArray = new SparseArray<>();

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
        }catch (Exception e) {
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
            e.printStackTrace();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                String localAddress = getLocalIpAddress(TYPE_INET4ADDRESS);
                int localPort = datagramChannel.socket().getLocalPort();

                while(!isInterrupted()) {
                    try {
                        send(new InetSocketAddress(rendezvousServerIP, rendezvousServerPort), RendezvousPacketType.REGISTRATION_RENDEZVOUS_CLIENT_REQUEST, localAddress + " " + localPort);
                        Thread.sleep(60*1000);

                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        }).start();

        new Thread(new Runnable() {
            @Override
            public void run() {
                while(!isInterrupted()) {
                    try {
                        recv();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        }).start();

    }

    @Override
    public void run() {

    }

    public void send(InetSocketAddress remoteAddress, RendezvousPacketType packetType, String data) throws IOException {
        send(remoteAddress,0, packetType, data);
    }

    public void send(InetSocketAddress remoteAddress, int connectionID, RendezvousPacketType packetType, String data) throws IOException {
        ByteBuffer byteBuffer = null;
        int dataSize = 0;
        if(data != null) {
            byteBuffer = ByteBuffer.wrap(data.getBytes());
            dataSize = byteBuffer.array().length;

        }

        byte []serialized = new byte[MessageHeader.MAGIC_PACKET.length + dataSize + MessageHeader.MAX_PACKET_HEADER_SIZE];
        CodedOutputStream coded_output = CodedOutputStream.newInstance(serialized, 0, serialized.length);

        try {
            coded_output.writeRawBytes(MessageHeader.MAGIC_PACKET);
            coded_output.writeInt32NoTag(dataSize); // data size
            coded_output.writeInt32NoTag(packetType.toInt()); // packet type
            coded_output.writeInt32NoTag(MessageHeader.MessageType.RAWBYTE.toInt()); // message type
            coded_output.writeInt32NoTag(0); // crypt type
            coded_output.writeInt32NoTag(connectionID); // connectionID
            if(data != null) {
                coded_output.writeRawBytes(byteBuffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        datagramChannel.send(ByteBuffer.wrap(serialized, 0, coded_output.getTotalBytesWritten()), remoteAddress);
    }

    void send(InetSocketAddress remoteAddress, int connectionID, MessageLite message) throws IOException {
        int dataSize = message.getSerializedSize();
        byte []serialized = new byte[MessageHeader.MAGIC_PACKET.length + dataSize + MessageHeader.MAX_PACKET_HEADER_SIZE];
        CodedOutputStream coded_output = CodedOutputStream.newInstance(serialized, 0, serialized.length);

        try {
            coded_output.writeRawBytes(MessageHeader.MAGIC_PACKET);
            coded_output.writeInt32NoTag(dataSize); // data size
            coded_output.writeInt32NoTag(Registry.GetPacketType(message)); // packet type
            coded_output.writeInt32NoTag(MessageHeader.MessageType.PROTOBUF.toInt()); // message type
            coded_output.writeInt32NoTag(0); // crypt type
            coded_output.writeInt32NoTag(connectionID); // connection id
            message.writeTo(coded_output);
        } catch (IOException e) {
            e.printStackTrace();
        }

        datagramChannel.send(ByteBuffer.wrap(serialized, 0, coded_output.getTotalBytesWritten()), remoteAddress);
    }

    public void recv() throws IOException {
        byteBuffer.clear();
        InetSocketAddress remoteAddress = (InetSocketAddress) datagramChannel.receive(byteBuffer);
        byteBuffer.flip();

        byte[] buffer = byteBuffer.array();

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
                    byte[] bodyByteArray = Arrays.copyOfRange(buffer, coded_input.getTotalBytesRead(), byteBuffer.limit());
                    rendezvousClientCallback.onMessage(messageHeader, bodyByteArray);
                }
                return;
            case RAWBYTE:
            {
                RendezvousPacketType rendezvousPacketType = RendezvousPacketType.fromInt(messageHeader.mPacketType);
                if(rendezvousPacketType != RendezvousPacketType.NONE) break;

                if(rendezvousClientCallback != null) {
                    byte[] bodyByteArray = Arrays.copyOfRange(buffer, coded_input.getTotalBytesRead(), byteBuffer.limit());
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
                Log.i("scnet_android", "[REGISTRATION_RENDEZVOUS_CLIENT_SUCCESS] MyPublicAddress=" + myPublicIP + ":" + myPublicPort);
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

                send(new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1])), messageHeader.mConnectionID, RendezvousPacketType.DIRECT_CONNECTION_REQUEST, null);
                break;
            }
            case DIRECT_CONNECTION_REQUEST: // TP only
            {
                Log.d("scnet_android", "received DIRECT_CONNECTION_REQUEST");

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();
                rendezvousSession.publicAddress = new InetSocketAddress(remoteAddress.getHostName(), remoteAddress.getPort());
                Log.d("scnet_android", "directly connected from " + remoteAddress.getHostName() + ":" + remoteAddress.getPort());

                if(!isConnected) {
                    if(rendezvousClientCallback != null) {
                        rendezvousClientCallback.onConnected(messageHeader.mConnectionID);
                    }
                }

                send(remoteAddress, messageHeader.mConnectionID, RendezvousPacketType.DIRECT_CONNECTION_RESPONSE, null);
                break;
            }
            case DIRECT_CONNECTION_RESPONSE: // SP only
            {
                Log.d("scnet_android", "received DIRECT_CONNECTION_RESPONSE");

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();
                rendezvousSession.publicAddress = new InetSocketAddress(remoteAddress.getHostName(), remoteAddress.getPort());
                Log.d("scnet_android", "directly connected from " + remoteAddress.getHostName() + ":" + remoteAddress.getPort());
                if(!isConnected) {
                    if(rendezvousClientCallback != null) {
                        rendezvousClientCallback.onConnected(messageHeader.mConnectionID);
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
                send(sourceAddress, messageHeader.mConnectionID, RendezvousPacketType.REVERSE_CONNECTION_REQUEST, null);

                break;
            }
            case REVERSE_CONNECTION_REQUEST: // SP only
            {
                Log.d("scnet_android", "received REVERSE_CONNECTION_REQUEST");

                send(remoteAddress, messageHeader.mConnectionID, RendezvousPacketType.REVERSE_CONNECTION_RESPONSE, null);

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();
                rendezvousSession.publicAddress = new InetSocketAddress(remoteAddress.getHostName(), remoteAddress.getPort());
                Log.d("scnet_android", "reversely connected from " + remoteAddress.getHostName() + ":" + remoteAddress.getPort());

                if(!isConnected) {
                    if(rendezvousClientCallback != null) {
                        rendezvousClientCallback.onConnected(messageHeader.mConnectionID);
                    }
                }
                break;
            }
            case REVERSE_CONNECTION_RESPONSE: // TP only
            {
                Log.d("scnet_android", "received REVERSE_CONNECTION_RESPONSE");

                RendezvousSession rendezvousSession = getRendezvousSessionSafety(messageHeader);
                boolean isConnected = rendezvousSession.isConnected();
                rendezvousSession.publicAddress = new InetSocketAddress(remoteAddress.getHostName(), remoteAddress.getPort());
                Log.d("scnet_android", "reversely connected from " + remoteAddress.getHostName() + ":" + remoteAddress.getPort());

                if(!isConnected) {
                    if(rendezvousClientCallback != null) {
                        rendezvousClientCallback.onConnected(messageHeader.mConnectionID);
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

                // private, public 정보로 동시에 연결 요청 메시지를 보냄
                InetSocketAddress publicAddress = new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1]));
                send(publicAddress, messageHeader.mConnectionID, RendezvousPacketType.UDP_HOLE_PUNCHING_REQUEST, "1");

                InetSocketAddress privateAddress = new InetSocketAddress(bodyArray[2], Integer.parseInt(bodyArray[3]));
                send(privateAddress, messageHeader.mConnectionID, RendezvousPacketType.UDP_HOLE_PUNCHING_REQUEST, "0");

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

                send(remoteAddress, messageHeader.mConnectionID, RendezvousPacketType.UDP_HOLE_PUNCHING_RESPONSE, bodyArray[0]);

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
                if(isPublic) {
                    // public connection (common connection)
                    rendezvousSession.publicAddress = new InetSocketAddress(remoteAddress.getHostName(), remoteAddress.getPort());
                    Log.d("scnet_android", "udp hole punching success from " + remoteAddress.getHostName() + ":" + remoteAddress.getPort());
                }
                else {
                    // private connection (equal net, or hole punching)
                    rendezvousSession.privateAddress = new InetSocketAddress(remoteAddress.getHostName(), remoteAddress.getPort());
                    Log.d("scnet_android", "connected under equal NAT from " + remoteAddress.getHostName() + ":" + remoteAddress.getPort());
                }

                if(!isConnected) {
                    if(rendezvousClientCallback != null) {
                        rendezvousClientCallback.onConnected(messageHeader.mConnectionID);
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
                send(relayAddress, messageHeader.mConnectionID, RendezvousPacketType.REGISTRATION_RELAY_PEER_REQUEST, bodyArray[2]);
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
                rendezvousSession.relayAddress = new InetSocketAddress(bodyArray[0], Integer.parseInt(bodyArray[1]));
                Log.d("scnet_android", "connected by a relay from " + bodyArray[0] + ":" + bodyArray[1]);
                if(rendezvousClientCallback != null) {
                    rendezvousClientCallback.onConnected(messageHeader.mConnectionID);
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

    public boolean send(int connectionID, RendezvousPacketType packetType, String data) throws IOException {

        RendezvousSession rendezvousSession = rendezvousSessionArray.get(connectionID);
        if(rendezvousSession == null) {
            return false;
        }

        if(rendezvousSession.privateAddress != null) {
            send(rendezvousSession.privateAddress, connectionID, packetType, data);
            return true;
        }
        else if(rendezvousSession.publicAddress != null) {
            send(rendezvousSession.publicAddress, connectionID, packetType, data);
            return true;
        }
        else if(rendezvousSession.relayAddress != null) {
            send(rendezvousSession.relayAddress, connectionID, packetType, data);
            return true;
        }

        return false;
    }

    RendezvousSession getRendezvousSessionSafety(MessageHeader messageHeader) {
        RendezvousSession rendezvousSession = rendezvousSessionArray.get(messageHeader.mConnectionID);
        if(rendezvousSession == null) {
            rendezvousSession = new RendezvousSession(this, messageHeader.mConnectionID);
            rendezvousSessionArray.append(messageHeader.mConnectionID, rendezvousSession);
        }

        return rendezvousSession;
    }
}
