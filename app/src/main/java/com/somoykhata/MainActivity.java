package com.somoykhata;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;

public class MainActivity extends Activity {
    private static final int BG = 0xFFF2F5F8;
    private static final int CARD = 0xFFFFFFFF;
    private static final int INK = 0xFF121C26;
    private static final int MUTED = 0xFF5B6B7B;
    private static final int ACCENT = 0xFF2457D6;
    private static final int DANGER = 0xFFC0392B;
    private static final int LINE = 0xFFCDD5DD;

    /** শনিবার থেকে শুক্রবার পর্যন্ত Calendar.DAY_OF_WEEK মান। */
    private static final int[] DOW = {7, 1, 2, 3, 4, 5, 6};
    private static final int[] DURS = {30, 60, 120, 180, 240, 360, 480, 720, 0};
    private static final String[] DUR_LABELS = {
            "৩০ মিনিট", "১ ঘণ্টা", "২ ঘণ্টা", "৩ ঘণ্টা", "৪ ঘণ্টা",
            "৬ ঘণ্টা", "৮ ঘণ্টা", "১২ ঘণ্টা", "নিজে বন্ধ না করা পর্যন্ত"
    };

    private SharedPreferences p;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView statusTv;
    private TextView routineNextTv;
    private TextView volTv;
    private Button mainBtn;
    private Button startBtn;
    private Button endBtn;
    private final TextView[] chips = new TextView[7];

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        p = Prefs.get(this);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Scheduler.rescheduleRoutine(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(32));
        sv.addView(root);

        root.addView(text("সময় খাতা", 30, true, INK));
        root.addView(text("১৫ মিনিটে ১টি বিপ, ৩০ মিনিটে ২টি বিপ", 14, false, MUTED));

        // --- চালু / বন্ধ
        LinearLayout c1 = card(root, null);
        statusTv = text("", 16, false, INK);
        c1.addView(statusTv);
        mainBtn = button("চালু করুন", ACCENT);
        mainBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleRun();
            }
        });
        c1.addView(mainBtn, lp(14));

        // --- কতক্ষণ চালু থাকবে
        LinearLayout c2 = card(root, "কতক্ষণ চালু থাকবে");
        Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, DUR_LABELS));
        int cur = p.getInt("dur_min", 120);
        for (int i = 0; i < DURS.length; i++) {
            if (DURS[i] == cur) {
                sp.setSelection(i);
            }
        }
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                p.edit().putInt("dur_min", DURS[position]).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        c2.addView(sp, lp(6));
        c2.addView(text("“চালু করুন” চাপলে এই সময় পর্যন্ত বিপ বাজবে, শেষে ৩টি বিপ দিয়ে নিজে বন্ধ হবে।", 13, false, MUTED), lp(6));

        // --- রুটিন
        LinearLayout c3 = card(root, "রুটিন (নিজে নিজে চালু হবে)");
        Switch sw = new Switch(this);
        sw.setText("রুটিন চালু");
        sw.setTextSize(16);
        sw.setTextColor(INK);
        sw.setChecked(p.getBoolean("sched_on", false));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                p.edit().putBoolean("sched_on", on).apply();
                Scheduler.rescheduleRoutine(MainActivity.this);
                if (on) {
                    Scheduler.startRoutineIfDue(MainActivity.this);
                }
                refresh();
            }
        });
        c3.addView(sw, lp(4));

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        startBtn = outlineButton("");
        endBtn = outlineButton("");
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(true);
            }
        });
        endBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickTime(false);
            }
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.rightMargin = dp(4);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(4);
        times.addView(startBtn, half);
        times.addView(endBtn, half2);
        c3.addView(times, lp(10));

        LinearLayout days = new LinearLayout(this);
        days.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 7; i++) {
            final int dow = DOW[i];
            TextView t = text(Fmt.SHORT[dow - 1], 13, true, INK);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(10));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int mask = p.getInt("sched_days", 95);
                    mask ^= (1 << (dow - 1));
                    p.edit().putInt("sched_days", mask).apply();
                    Scheduler.rescheduleRoutine(MainActivity.this);
                    refresh();
                }
            });
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            l.leftMargin = dp(2);
            l.rightMargin = dp(2);
            days.addView(t, l);
            chips[i] = t;
        }
        c3.addView(days, lp(10));
        routineNextTv = text("", 14, false, MUTED);
        c3.addView(routineNextTv, lp(10));

        // --- বিপ সেটিং
        LinearLayout c4 = card(root, "বিপের সেটিং");
        volTv = text("", 14, false, INK);
        c4.addView(volTv, lp(2));
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(p.getInt("vol_min", 70));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                p.edit().putInt("vol_min", progress).apply();
                refresh();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        c4.addView(sb, lp(4));
        c4.addView(text("এটা অ্যালার্মের ভলিউম। ফোন সাইলেন্ট থাকলেও বিপ এই ভলিউমে বাজবে, বাজার পর আগের ভলিউম ফিরে আসবে।", 13, false, MUTED), lp(4));

        Switch vib = new Switch(this);
        vib.setText("বিপের সাথে ভাইব্রেশন");
        vib.setTextSize(16);
        vib.setTextColor(INK);
        vib.setChecked(p.getBoolean("vibrate", false));
        vib.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton b, boolean on) {
                p.edit().putBoolean("vibrate", on).apply();
            }
        });
        c4.addView(vib, lp(12));

        LinearLayout tests = new LinearLayout(this);
        tests.setOrientation(LinearLayout.HORIZONTAL);
        Button t1 = outlineButton("১ বিপ শুনুন");
        Button t2 = outlineButton("২ বিপ শুনুন");
        t1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Beeper.play(MainActivity.this, 1);
            }
        });
        t2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Beeper.play(MainActivity.this, 2);
            }
        });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        a.rightMargin = dp(4);
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        b2.leftMargin = dp(4);
        tests.addView(t1, a);
        tests.addView(t2, b2);
        c4.addView(tests, lp(12));

        // --- ব্যাটারি
        LinearLayout c5 = card(root, "ফোন যাতে অ্যাপ বন্ধ করে না দেয়");
        c5.addView(text("অনেক ফোন (Xiaomi, Oppo, Vivo, Samsung ইত্যাদি) ব্যাটারি বাঁচাতে ব্যাকগ্রাউন্ড অ্যাপ বন্ধ করে দেয়। নিচের বোতাম চেপে “অনুমতি দিন” বা “Unrestricted” বেছে নিন।", 13, false, MUTED), lp(2));
        Button bat = outlineButton("ব্যাটারি সীমা তুলে দিন");
        bat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askBatteryExemption();
            }
        });
        c5.addView(bat, lp(10));

        return sv;
    }

    // ---------------------------------------------------------------- actions

    private void toggleRun() {
        if (p.getBoolean("run_on", false)) {
            ReminderService.halt(this);
            Toast.makeText(this, "বন্ধ হয়েছে", Toast.LENGTH_SHORT).show();
        } else {
            int d = p.getInt("dur_min", 120);
            long end = d > 0 ? System.currentTimeMillis() + d * 60000L : 0;
            ReminderService.begin(this, end, "manual");
            Toast.makeText(this, "চালু হয়েছে। প্রথম বিপ ১৫ মিনিট পরে।", Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    private void pickTime(final boolean isStart) {
        String key = isStart ? "sched_start" : "sched_end";
        int cur = p.getInt(key, isStart ? 9 * 60 : 18 * 60);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(android.widget.TimePicker view, int h, int m) {
                int v = h * 60 + m;
                int s = isStart ? v : p.getInt("sched_start", 9 * 60);
                int e = isStart ? p.getInt("sched_end", 18 * 60) : v;
                if (e <= s) {
                    Toast.makeText(MainActivity.this, "শেষের সময় শুরুর সময়ের পরে হতে হবে", Toast.LENGTH_LONG).show();
                    return;
                }
                p.edit().putInt(isStart ? "sched_start" : "sched_end", v).apply();
                Scheduler.rescheduleRoutine(MainActivity.this);
                refresh();
            }
        }, cur / 60, cur % 60, false).show();
    }

    private void askBatteryExemption() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "এটা আগেই করা আছে", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- refresh

    private void refresh() {
        long now = System.currentTimeMillis();
        boolean on = p.getBoolean("run_on", false);

        mainBtn.setText(on ? "বন্ধ করুন" : "চালু করুন");
        mainBtn.setBackground(rounded(on ? DANGER : ACCENT, 0, 0));

        if (on) {
            long t0 = p.getLong("run_t0", now);
            long end = p.getLong("run_end", 0);
            int last = p.getInt("run_last", 0);
            long next = t0 + (long) (last + 1) * Scheduler.INTERVAL;
            String s = "চালু আছে";
            if (end > 0 && next >= end - 20_000L) {
                s += "\nশেষ হবে " + Fmt.clock(end) + " (বাকি " + Fmt.dur(end - now) + ")";
            } else {
                String kind = ((last + 1) % 2 == 0) ? "২টি বিপ" : "১টি বিপ";
                s += "\nপরের বিপ: " + Fmt.clock(next) + " (" + kind + ")";
                if (end > 0) {
                    s += "\nশেষ হবে " + Fmt.clock(end) + " (বাকি " + Fmt.dur(end - now) + ")";
                } else {
                    s += "\nনিজে বন্ধ না করা পর্যন্ত চলবে";
                }
            }
            statusTv.setText(s);
        } else {
            statusTv.setText("বন্ধ আছে");
        }

        startBtn.setText("শুরু: " + Fmt.hm(p.getInt("sched_start", 9 * 60)));
        endBtn.setText("শেষ: " + Fmt.hm(p.getInt("sched_end", 18 * 60)));

        int mask = p.getInt("sched_days", 95);
        for (int i = 0; i < 7; i++) {
            boolean sel = ((mask >> (DOW[i] - 1)) & 1) == 1;
            GradientDrawable g = rounded(sel ? ACCENT : CARD, sel ? 0 : 1, LINE);
            chips[i].setBackground(g);
            chips[i].setTextColor(sel ? Color.WHITE : INK);
        }

        if (p.getBoolean("sched_on", false)) {
            long nx = Scheduler.nextStart(p, now);
            if (nx > 0) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(nx);
                routineNextTv.setText("পরের স্বয়ংক্রিয় চালু: " + Fmt.DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + Fmt.clock(nx));
            } else {
                routineNextTv.setText("কোনো বার বাছাই করা নেই");
            }
        } else {
            routineNextTv.setText("রুটিন বন্ধ আছে");
        }

        volTv.setText("বিপের ভলিউম কমপক্ষে " + Fmt.bn(p.getInt("vol_min", 70)) + "%");
    }

    // ---------------------------------------------------------------- helpers

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        l.topMargin = dp(topDp);
        return l;
    }

    private GradientDrawable rounded(int fill, int strokeDp, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(fill);
        if (strokeDp > 0) {
            g.setStroke(dp(strokeDp), strokeColor);
        }
        return g;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return t;
    }

    private LinearLayout card(LinearLayout parent, String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(rounded(CARD, 1, LINE));
        if (title != null) {
            c.addView(text(title, 17, true, INK));
        }
        parent.addView(c, lp(14));
        return c;
    }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(17);
        b.setTextColor(Color.WHITE);
        b.setMinHeight(dp(52));
        b.setBackground(rounded(color, 0, 0));
        return b;
    }

    private Button outlineButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(ACCENT);
        b.setMinHeight(dp(48));
        b.setBackground(rounded(CARD, 1, ACCENT));
        return b;
    }
}
