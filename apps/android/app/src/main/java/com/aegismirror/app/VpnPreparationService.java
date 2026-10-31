package com.aegismirror.app;

import android.content.Intent;
import android.net.VpnService;

/** Discoverable consent component, NOT a forwarding engine. No TUN is created. */
public final class VpnPreparationService extends VpnService {
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf(startId);
        return START_NOT_STICKY;
    }
    @Override public void onRevoke() {
        super.onRevoke();
    }
}
