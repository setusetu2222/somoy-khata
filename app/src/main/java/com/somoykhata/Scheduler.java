package com.somoykhata;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/** অ্যালার্ম বসানো আর রুটিনের হিসাব। */
final class Scheduler {
    private Scheduler() {}

    /** বিপের ব্যবধান: ১৫ মিনিট। বিজোড় ধাপে ১টি বিপ, জোড় ধাপে (৩০, ৬০, ... মিনিটে) ২টি বিপ। */
    static final long INTERVAL = 15L * 60L * 1000L;

    private static final int FLAGS = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    static final String ACT_ROUTINE = "com.somoykhata.ROUTINE";

    private static AlarmManager alarms(Context c) {
        return (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
    }

    static PendingIntent tickPI(Context c) {
        Intent i = new Intent(c, ReminderService.class).setAction(ReminderService.ACT_TICK);
        return PendingIntent.getForegroundService(c, 1, i, FLAGS);
    }

    static PendingIntent endPI(Context c) {
        Intent i = new Intent(c, ReminderService.class).setAction(ReminderService.ACT_END);
        return PendingIntent.getForegroundService(c, 2, i, FLAGS);
    }

    private static PendingIntent routinePI(Context c) {
        Intent i = new Intent(c, ScheduleReceiver.class).setAction(ACT_ROUTINE);
        return PendingIntent.getBroadcast(c, 10, i, FLAGS);
    }

    private static PendingIntent showPI(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        return PendingIntent.getActivity(c, 11, i, FLAGS);
    }

    static void setExact(Context c, long when, PendingIntent pi) {
        AlarmManager a = alarms(c);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !a.canScheduleExactAlarms()) {
                a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            } else {
                a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
            }
        } catch (SecurityException e) {
            a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, pi);
        }
    }

    static void cancelRun(Context c) {
        alarms(c).cancel(tickPI(c));
        alarms(c).cancel(endPI(c));
    }

    private static boolean dayOn(SharedPreferences p, Calendar cal) {
        int mask = p.getInt("sched_days", 95);
        return ((mask >> (cal.get(Calendar.DAY_OF_WEEK) - 1)) & 1) == 1;
    }

    private static long atMinutes(Calendar base, int minutes) {
        Calendar x = (Calendar) base.clone();
        x.set(Calendar.HOUR_OF_DAY, minutes / 60);
        x.set(Calendar.MINUTE, minutes % 60);
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        return x.getTimeInMillis();
    }

    /** পরের স্বয়ংক্রিয় শুরুর সময়। কোনো দিন বাছাই না থাকলে 0। */
    static long nextStart(SharedPreferences p, long now) {
        int start = p.getInt("sched_start", 9 * 60);
        Calendar today = Calendar.getInstance();
        today.setTimeInMillis(now);
        for (int d = 0; d <= 7; d++) {
            Calendar day = (Calendar) today.clone();
            day.add(Calendar.DAY_OF_YEAR, d);
            if (!dayOn(p, day)) {
                continue;
            }
            long t = atMinutes(day, start);
            if (t > now) {
                return t;
            }
        }
        return 0;
    }

    /** এখন যদি আজকের রুটিন-জানালার ভেতরে থাকি, তবে তার শেষ সময়। নইলে 0। */
    static long windowEnd(SharedPreferences p, long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        if (!dayOn(p, cal)) {
            return 0;
        }
        long s = atMinutes(cal, p.getInt("sched_start", 9 * 60));
        long e = atMinutes(cal, p.getInt("sched_end", 18 * 60));
        if (now >= s && now < e) {
            return e;
        }
        return 0;
    }

    /** রুটিনের পরের শুরুর জন্য অ্যালার্ম বসায় (বা রুটিন বন্ধ থাকলে বাতিল করে)। */
    static void rescheduleRoutine(Context c) {
        SharedPreferences p = Prefs.get(c);
        AlarmManager a = alarms(c);
        PendingIntent pi = routinePI(c);
        a.cancel(pi);
        if (!p.getBoolean("sched_on", false)) {
            return;
        }
        long next = nextStart(p, System.currentTimeMillis());
        if (next > 0) {
            a.setAlarmClock(new AlarmManager.AlarmClockInfo(next, showPI(c)), pi);
        }
    }

    /** রুটিন-জানালার ভেতরে থাকলে এবং এখন চালু না থাকলে সেশন শুরু করে। */
    static void startRoutineIfDue(Context c) {
        SharedPreferences p = Prefs.get(c);
        if (!p.getBoolean("sched_on", false) || p.getBoolean("run_on", false)) {
            return;
        }
        long end = windowEnd(p, System.currentTimeMillis());
        if (end > 0) {
            ReminderService.begin(c, end, "routine");
        }
    }
}
