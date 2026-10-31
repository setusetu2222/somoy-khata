package com.somoykhata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** রুটিনের শুরুর সময়ে অ্যালার্ম এখানে আসে: পরের দিনের অ্যালার্ম বসায় আর আজকের সেশন শুরু করে। */
public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.rescheduleRoutine(context);
        Scheduler.startRoutineIfDue(context);
    }
}
