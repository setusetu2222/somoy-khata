package com.somoykhata;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * সব সেটিং এখানে জমা থাকে।
 *
 * dur_min      কতক্ষণ চালু থাকবে (মিনিট), 0 মানে নিজে বন্ধ না করা পর্যন্ত
 * sched_on     রুটিন চালু কি না
 * sched_days   বারের বিটমাস্ক (bit0 = রবি ... bit6 = শনি, Calendar.DAY_OF_WEEK - 1)
 * sched_start  রুটিন শুরুর সময় (মধ্যরাত থেকে মিনিট)
 * sched_end    রুটিন শেষের সময় (মধ্যরাত থেকে মিনিট)
 * vol_min      বিপের সময় অ্যালার্ম ভলিউম কমপক্ষে কত শতাংশ থাকবে
 * vibrate      বিপের সাথে ভাইব্রেশন
 * run_on, run_t0, run_end, run_last, run_src   এখনকার সেশনের অবস্থা
 */
final class Prefs {
    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences("somoy_khata", Context.MODE_PRIVATE);
    }
}
