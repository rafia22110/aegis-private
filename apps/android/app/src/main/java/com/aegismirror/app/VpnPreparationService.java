package com.aegismirror.app;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.VpnService;
import android.os.Binder;
import android.os.IBinder;
import java.util.Arrays;
import java.security.SecureRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Bound, visible-UI test helper. Never establishes a VPN interface. */
public final class VpnPreparationService extends VpnService {
    public static final String LAB_ACTION = "com.aegismirror.app.UDP_LAB";
    public interface Callback { void complete(int messageResource); }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicReference<UdpForwarder> active = new AtomicReference<>();
    private Future<?> task;
    public final class LocalBinder extends Binder {
        public void probe(String host, int port, boolean allow, Callback callback) {
            submit(host, port, allow, callback);
        }
        public void cancel() { cancelProbe(); }
    }
    private final LocalBinder binder = new LocalBinder();
    @Override public IBinder onBind(Intent intent) {
        return intent != null && LAB_ACTION.equals(intent.getAction()) ? binder : super.onBind(intent);
    }
    private synchronized void submit(String host, int port, boolean allow, Callback callback) {
        if (task != null && !task.isDone()) { callback.complete(R.string.lab_busy); return; }
        task = executor.submit(() -> {
            int result = R.string.lab_failed;
            UdpForwarder worker = null;
            try {
                if (Thread.currentThread().isInterrupted()) return;
                UdpLabTarget target = new UdpLabTarget(host, port);
                ConnectivityManager manager = getSystemService(ConnectivityManager.class);
                Network network = manager == null ? null : manager.getActiveNetwork();
                NetworkCapabilities caps = network == null ? null : manager.getNetworkCapabilities(network);
                if (VpnService.prepare(this) != null) result = R.string.lab_need_consent;
                else if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                        || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) result = R.string.lab_no_network;
                else {
                    worker = new UdpForwarder(socket -> {
                        NetworkCapabilities current = manager.getNetworkCapabilities(network);
                        if (VpnService.prepare(this) != null || current == null
                                || !current.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                                || !current.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                || !protect(socket)) return false;
                        network.bindSocket(socket);
                        socket.setBroadcast(false);
                        return true;
                    }, packet -> allow && packet.destination == target.address && packet.destinationPort == target.port, 3000);
                    active.set(worker);
                    if (Thread.currentThread().isInterrupted()) return;
                    byte[] nonce = new byte[16];
                    new SecureRandom().nextBytes(nonce);
                    byte[] input = Ipv4UdpPacket.encode(0x0a000002, target.address, 40000, target.port, nonce);
                    byte[] output = worker.forward(input, input.length);
                    if (output == null) result = allow ? R.string.lab_failed : R.string.lab_denied;
                    else {
                        Ipv4UdpPacket reply = Ipv4UdpPacket.parse(output, output.length);
                        result = reply.source == target.address && reply.sourcePort == target.port
                                && reply.destination == 0x0a000002 && reply.destinationPort == 40000
                                && Arrays.equals(nonce, reply.payload()) ? R.string.lab_ok : R.string.lab_not_echo;
                    }
                }
            } catch (IllegalArgumentException error) { result = R.string.lab_invalid_target; }
            catch (Exception error) { result = R.string.lab_failed; }
            finally {
                if (worker != null) { worker.close(); active.compareAndSet(worker, null); }
            }
            if (!Thread.currentThread().isInterrupted()) callback.complete(result);
        });
    }
    private synchronized void cancelProbe() {
        if (task != null) task.cancel(true);
        UdpForwarder worker = active.getAndSet(null);
        if (worker != null) worker.close();
    }
    @Override public boolean onUnbind(Intent intent) { cancelProbe(); return super.onUnbind(intent); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf(startId);
        return START_NOT_STICKY;
    }
    @Override public void onRevoke() { cancelProbe(); super.onRevoke(); }
    @Override public void onDestroy() { cancelProbe(); executor.shutdownNow(); super.onDestroy(); }
}
