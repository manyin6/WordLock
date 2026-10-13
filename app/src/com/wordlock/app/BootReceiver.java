package com.wordlock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Plan.schedule(c);
        if (Prefs.locked(c)) {
            try { c.startForegroundService(new Intent(c, LockService.class)); } catch (Exception e) { }
        }
    }
}
