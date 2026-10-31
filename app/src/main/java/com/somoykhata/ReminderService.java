package com.somoykhata;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * ব্যাকগ্রাউন্ডে চলা সার্ভিস। সেশন শুরু থেকে প্রতি ১৫ মিনিট পর পর একটি অ্যালার্ম নিয়ে জেগে ওঠে:
 * বিজোড় ধাপে (১৫, ৪৫, ৭৫ ... মিনিট) ১টি বিপ, জোড় ধাপে (৩০, ৬০, ৯০ ... মিনিট) ২টি বিপ।
 * নির্ধারিত শেষ সময়ে ৩টি বিপ দিয়ে নিজে বন্ধ হয়।
 */
public class ReminderService extends Service {
    static final String ACT_START = "com.somoykhata.START";
    static final String ACT_STOP = "com.somoykhata.STOP";
    static final String ACT_TICK = "com.somoykhata.TICK";
    static final String ACT_END = "com.somoykhata.END";

    private static final String CHANNEL = "somoy_khata_run";
    private static final int NOTIF_ID = 1;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** সেশন শুরু। endAt = 0 হলে নিজে বন্ধ না করা পর্যন্ত চলবে। */
    static void begin(Context c, long endAt, String src) {
        SharedPreferences p = Prefs.get(c);
        p.edit()
                .putBoolean("run_on", true)
                .putLong("run_t0", System.currentTimeMillis())
                .putLong("run_end", endAt)
                .putInt("run_last", 0)
                .putString("run_src", src)
                .apply();
        try {
            c.startForegroundService(new Intent(c, ReminderService.class).setAction(ACT_START));
        } catch (Exception e) {
            p.edit().putBoolean("run_on", false).apply();
        }
    }

    /** সেশন বন্ধ। */
    static void halt(Context c) {
        c.stopService(new Intent(c, ReminderService.class));
        Prefs.get(c).edit().putBoolean("run_on", false).apply();
        Scheduler.cancelRun(c);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences p = Prefs.get(this);
        String action = intent == null ? null : intent.getAction();

        if (ACT_STOP.equals(action)) {
            p.edit().putBoolean("run_on", false).apply();
            Scheduler.cancelRun(this);
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        createChannel();
        long now = System.currentTimeMillis();

        if (!p.getBoolean("run_on", false)) {
            // কোনো সেশন নেই, কিন্তু startForegroundService দিয়ে ডাকা হয়েছে, তাই নোটিফিকেশন দেখিয়ে বন্ধ করি।
            startAsForeground(buildNotification("বন্ধ হচ্ছে"));
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        long t0 = p.getLong("run_t0", now);
        long end = p.getLong("run_end", 0);

        startAsForeground(buildNotification(nextText(p, t0, end)));

        if (ACT_END.equals(action) || (end > 0 && now >= end)) {
            finishSession(p);
            return START_NOT_STICKY;
        }

        if (ACT_TICK.equals(action)) {
            int n = (int) ((now - t0 + 90_000L) / Scheduler.INTERVAL);
            int last = p.getInt("run_last", 0);
            if (n >= 1 && n > last) {
                Beeper.play(this, n % 2 == 0 ? 2 : 1);
                p.edit().putInt("run_last", n).apply();
            }
        }

        scheduleNext(p, t0, end, now);
        updateNotification(buildNotification(nextText(p, t0, end)));
        return START_STICKY;
    }

    private void scheduleNext(SharedPreferences p, long t0, long end, long now) {
        int last = p.getInt("run_last", 0);
        long nextTick = t0 + (long) (last + 1) * Scheduler.INTERVAL;
        if (nextTick < now) {
            nextTick = now + 1000;
        }
        if (end > 0 && nextTick >= end - 20_000L) {
            // শেষ সময়ের ঠিক আগে-পরে আলাদা বিপ লাগবে না, শেষের ৩ বিপই যথেষ্ট।
            Scheduler.setExact(this, end, Scheduler.endPI(this));
            ((android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE)).cancel(Scheduler.tickPI(this));
        } else {
            Scheduler.setExact(this, nextTick, Scheduler.tickPI(this));
            if (end > 0) {
                Scheduler.setExact(this, end, Scheduler.endPI(this));
            }
        }
    }

    private void finishSession(SharedPreferences p) {
        Beeper.play(this, 3);
        p.edit().putBoolean("run_on", false).apply();
        Scheduler.cancelRun(this);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopForeground(true);
                stopSelf();
            }
        }, 2500);
    }

    private String nextText(SharedPreferences p, long t0, long end) {
        int last = p.getInt("run_last", 0);
        long next = t0 + (long) (last + 1) * Scheduler.INTERVAL;
        String kind = ((last + 1) % 2 == 0) ? "২টি বিপ" : "১টি বিপ";
        if (end > 0 && next >= end - 20_000L) {
            return "শেষ হবে " + Fmt.clock(end);
        }
        String s = "পরের বিপ " + Fmt.clock(next) + " (" + kind + ")";
        if (end > 0) {
            s += " · শেষ " + Fmt.clock(end);
        }
        return s;
    }

    private void startAsForeground(Notification n) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification(Notification n) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, n);
    }

    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "সময় খাতা চালু আছে", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        int f = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), f);
        PendingIntent stop = PendingIntent.getService(this, 3,
                new Intent(this, ReminderService.class).setAction(ACT_STOP), f);
        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_notif), "বন্ধ করুন", stop).build();
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("সময় খাতা চালু আছে")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .addAction(stopAction)
                .build();
    }
}
