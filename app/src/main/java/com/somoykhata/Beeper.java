package com.somoykhata;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * বিপ বাজায়। শব্দ যায় ALARM স্ট্রিমে, তাই ফোন সাইলেন্ট বা ভাইব্রেট মোডে থাকলেও বাজে।
 * (অ্যালার্ম ভলিউম একদম শূন্য থাকলে অ্যাপ নিজে ভলিউম বাড়িয়ে নেয়, বিপ শেষে আগের মানে ফিরিয়ে দেয়।)
 */
final class Beeper {
    private Beeper() {}

    private static final int SR = 44100;
    private static final int FREQ = 1000;
    private static final int TONE_MS = 280;
    private static final int GAP_MS = 180;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void play(Context context, int beeps) {
        final Context ctx = context.getApplicationContext();
        final short[] pcm = build(beeps);
        final long ms = pcm.length * 1000L / SR;

        final PowerManager.WakeLock wl;
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock tmp = null;
        try {
            tmp = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "somoykhata:beep");
            tmp.acquire(ms + 4000);
        } catch (Exception ignored) {
        }
        wl = tmp;

        final AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);

        int prev = -1;
        try {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            int cur = am.getStreamVolume(AudioManager.STREAM_ALARM);
            int pct = Prefs.get(ctx).getInt("vol_min", 70);
            int want = Math.max(pct > 0 ? 1 : 0, Math.round(max * pct / 100f));
            if (cur < want) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, want, 0);
                prev = cur;
            }
        } catch (Exception ignored) {
        }
        final int restore = prev;

        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        final AudioFocusRequest focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .build();
        try {
            am.requestAudioFocus(focus);
        } catch (Exception ignored) {
        }

        AudioTrack track = null;
        try {
            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();
            track = new AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build();
            track.write(pcm, 0, pcm.length);
            track.play();
        } catch (Exception e) {
            if (track != null) {
                try {
                    track.release();
                } catch (Exception ignored) {
                }
                track = null;
            }
        }
        final AudioTrack playing = track;

        vibrate(ctx, beeps);

        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    if (playing != null) {
                        playing.release();
                    }
                } catch (Exception ignored) {
                }
                try {
                    am.abandonAudioFocusRequest(focus);
                } catch (Exception ignored) {
                }
                try {
                    if (restore >= 0) {
                        am.setStreamVolume(AudioManager.STREAM_ALARM, restore, 0);
                    }
                } catch (Exception ignored) {
                }
                try {
                    if (wl != null && wl.isHeld()) {
                        wl.release();
                    }
                } catch (Exception ignored) {
                }
            }
        }, ms + 400);
    }

    private static short[] build(int beeps) {
        int tone = SR * TONE_MS / 1000;
        int gap = SR * GAP_MS / 1000;
        int tail = SR * 100 / 1000;
        int fade = SR * 12 / 1000;
        int total = beeps * tone + (beeps - 1) * gap + tail;
        short[] out = new short[total];
        int pos = 0;
        for (int b = 0; b < beeps; b++) {
            for (int i = 0; i < tone; i++) {
                double env = 1.0;
                if (i < fade) {
                    env = i / (double) fade;
                } else if (i > tone - fade) {
                    env = (tone - i) / (double) fade;
                }
                double s = Math.sin(2 * Math.PI * FREQ * i / (double) SR) * env * 0.9;
                out[pos + i] = (short) (s * Short.MAX_VALUE);
            }
            pos += tone;
            if (b < beeps - 1) {
                pos += gap;
            }
        }
        return out;
    }

    private static void vibrate(Context ctx, int beeps) {
        try {
            if (!Prefs.get(ctx).getBoolean("vibrate", false)) {
                return;
            }
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) {
                return;
            }
            long[] t = new long[beeps * 2];
            for (int i = 0; i < beeps; i++) {
                t[2 * i] = (i == 0) ? 0 : GAP_MS;
                t[2 * i + 1] = TONE_MS;
            }
            v.vibrate(VibrationEffect.createWaveform(t, -1));
        } catch (Exception ignored) {
        }
    }
}
