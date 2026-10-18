package com.eka.pulsesync.bridge;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * One UDP datagram per BLE packet, little-endian.
 *
 *   header : u16 magic 0x5350 | u8 version=1 | u8 type | u32 seq | u64 tUs (phone monotonic us at arrival)
 *   type 1 : u8 n | n x (f32 ax, ay, az [m/s^2], gx, gy, gz [rad/s])    oldest -> newest
 *   type 2 : i32 bpm | i32 steps | i32 calories | i32 vertAmp
 *
 * forward() is called on the app's BLE dispatcher thread: it only timestamps and enqueues.
 * Everything else (reflection, serialization, send) runs on one dedicated thread, so the
 * buffers/caches below need no locking.
 */
public final class SensorBridge {
    private static final String TAG = "SensorBridge";
    private static final String TARGET_IP = "192.168.1.15"; // workstation
    private static final int TARGET_PORT = 8080;

    private static final short MAGIC = 0x5350;
    private static final byte VERSION = 1, T_IMU = 1, T_SPORT = 2;
    private static final int MAX_SAMPLES = 32;

    // Bounded queue, drop-oldest: if Wi-Fi stalls we lose stale data instead of building latency.
    private static final ThreadPoolExecutor EXEC = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(64),
            new ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "pulsesync-tx");
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());

    // --- tx-thread-only state ---
    private static final ByteBuffer BUF =
            ByteBuffer.allocate(16 + 1 + 24 * MAX_SAMPLES).order(ByteOrder.LITTLE_ENDIAN);
    private static final DatagramPacket PACKET = new DatagramPacket(BUF.array(), 0);
    private static final HashMap<Class<?>, Method[]> LIST_GETTERS = new HashMap<Class<?>, Method[]>();
    private static final HashMap<Class<?>, Method[]> XYZ_GETTERS = new HashMap<Class<?>, Method[]>();
    private static final HashMap<Class<?>, Field[]> SPORT_FIELDS = new HashMap<Class<?>, Field[]>();
    private static DatagramSocket sock;
    private static int seq;

    private SensorBridge() {}

    /** Optional: call once with an app Context (e.g. Application.onCreate) to avoid Wi-Fi power-save latency spikes. */
    public static void init(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiManager.WifiLock lock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "pulsesync");
            lock.setReferenceCounted(false);
            lock.acquire();
        } catch (Throwable t) {
            Log.w(TAG, "low-latency wifi lock unavailable: " + t);
        }
    }

    public static void forward(final Object obj) {
        if (obj == null) return;
        final long tUs = System.nanoTime() / 1000L; // timestamp at arrival, before any queueing
        EXEC.execute(new Runnable() {
            @Override public void run() {
                try {
                    dispatch(obj, tUs);
                } catch (Throwable t) {
                    Log.e(TAG, "forward failed", t);
                }
            }
        });
    }

    private static void dispatch(Object obj, long tUs) throws Exception {
        String cn = obj.getClass().getName();
        if (cn.contains("WearSensorData")) sendImu(obj, tUs);
        else if (cn.contains("tyt") || cn.contains("SportData")) sendSport(obj, tUs);
    }

    private static void sendImu(Object obj, long tUs) throws Exception {
        Class<?> c = obj.getClass();
        Method[] lg = LIST_GETTERS.get(c);
        if (lg == null) {
            lg = new Method[] { c.getMethod("getAccel"), c.getMethod("getGyro") };
            LIST_GETTERS.put(c, lg);
        }
        List<?> acc = (List<?>) lg[0].invoke(obj);
        List<?> gyr = (List<?>) lg[1].invoke(obj);
        if (acc == null || gyr == null) return;

        // Forward every sample in the batch (newest MAX_SAMPLES), not just the last one.
        int n = Math.min(Math.min(acc.size(), gyr.size()), MAX_SAMPLES);
        if (n == 0) return;
        int ao = acc.size() - n, go = gyr.size() - n;

        begin(T_IMU, tUs);
        BUF.put((byte) n);
        for (int i = 0; i < n; i++) {
            putXyz(acc.get(ao + i));
            putXyz(gyr.get(go + i));
        }
        flush();
    }

    private static void putXyz(Object sample) throws Exception {
        Class<?> c = sample.getClass();
        Method[] m = XYZ_GETTERS.get(c);
        if (m == null) {
            m = new Method[] { c.getMethod("getX"), c.getMethod("getY"), c.getMethod("getZ") };
            XYZ_GETTERS.put(c, m);
        }
        for (int k = 0; k < 3; k++) BUF.putFloat(((Number) m[k].invoke(sample)).floatValue());
    }

    private static void sendSport(Object obj, long tUs) throws Exception {
        Class<?> c = obj.getClass();
        Field[] f = SPORT_FIELDS.get(c);
        if (f == null) {
            f = new Field[] { c.getField("c"), c.getField("b"), c.getField("a"), c.getField("s") }; // hr, steps, cal, amp
            SPORT_FIELDS.put(c, f);
        }
        begin(T_SPORT, tUs);
        for (int k = 0; k < 4; k++) BUF.putInt(f[k].getInt(obj));
        flush();
    }

    private static void begin(byte type, long tUs) {
        BUF.clear();
        BUF.putShort(MAGIC).put(VERSION).put(type).putInt(seq++).putLong(tUs);
    }

    private static DatagramSocket socket() throws Exception {
        if (sock == null) {
            DatagramSocket s = new DatagramSocket();
            try { s.setTrafficClass(0x10); } catch (Throwable ignored) {} // IPTOS_LOWDELAY hint
            s.connect(new InetSocketAddress(InetAddress.getByName(TARGET_IP), TARGET_PORT));
            sock = s;
        }
        return sock;
    }

    private static void flush() {
        try {
            PACKET.setData(BUF.array(), 0, BUF.position());
            socket().send(PACKET);
        } catch (Throwable t) {
            Log.w(TAG, "udp send failed: " + t);
            if (sock != null) { sock.close(); sock = null; } // recreate on next packet
        }
    }
}
