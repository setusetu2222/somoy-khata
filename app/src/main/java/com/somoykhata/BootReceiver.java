package com.somoykhata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** ফোন রিস্টার্ট বা অ্যাপ আপডেটের পর রুটিনের অ্যালার্ম আবার বসায়। */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Prefs.get(context).edit().putBoolean("run_on", false).apply();
        Scheduler.rescheduleRoutine(context);
        Scheduler.startRoutineIfDue(context);
    }
}
