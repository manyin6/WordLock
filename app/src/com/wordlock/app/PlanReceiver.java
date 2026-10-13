package com.wordlock.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PlanReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (Prefs.getInt(c, "planOn", 0) == 1 && !Prefs.locked(c)) {
            try { LockService.start(c, Prefs.getInt(c, "planDur", 60)); } catch (Exception e) { }
        }
        Plan.schedule(c);
    }
}
