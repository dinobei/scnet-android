package net.ijoon.scnet_android;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

public class KCPPeer {
    InetSocketAddress address;
    KCP kcp;
    long next;
    ReentrantLock lock;
    long lastPing;

    KCPPeer(final DatagramChannel datagramChannel, InetSocketAddress inetSocketAddress) {
        address = inetSocketAddress;
        next = Calendar.getInstance().getTimeInMillis();
        lock = new ReentrantLock();
        kcp = new KCP(0x11223344) {
            @Override
            protected void output(byte[] buffer, int size) {
                try {
                    datagramChannel.send(ByteBuffer.wrap(buffer, 0, size), address);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        kcp.WndSize(128, 128);
        kcp.NoDelay(1, 20, 2, 1);

        lastPing = Calendar.getInstance().getTimeInMillis();
    }

    String getKey() {
        return getKey(address);
    }

    static String getKey(InetSocketAddress address) {
        if(address!=null) {
            return address.getAddress().getHostAddress() + ":" + address.getPort();
        }

        return null;
    }

    static String getKey(String ip, String port) {
        if(ip != null && !ip.isEmpty() && port != null && !port.isEmpty()) {
            return ip + ":" + port;
        }

        return null;
    }
}
