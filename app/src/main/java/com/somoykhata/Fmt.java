package com.somoykhata;

import java.util.Calendar;

/** বাংলা সংখ্যা, সময় ও বারের নাম। */
final class Fmt {
    private Fmt() {}

    static final String[] DAYS = {"রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"};
    static final String[] SHORT = {"রবি", "সোম", "মঙ্গল", "বুধ", "বৃহ", "শুক্র", "শনি"};

    static String bn(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') {
                b.append((char) ('০' + (ch - '0')));
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    static String bn(long n) {
        return bn(String.valueOf(n));
    }

    /** মধ্যরাত থেকে মিনিট -> "সকাল ৯:০০" */
    static String hm(int minutes) {
        int h24 = (minutes / 60) % 24;
        int m = minutes % 60;
        String period;
        if (h24 < 4) {
            period = "রাত";
        } else if (h24 < 6) {
            period = "ভোর";
        } else if (h24 < 12) {
            period = "সকাল";
        } else if (h24 < 16) {
            period = "দুপুর";
        } else if (h24 < 18) {
            period = "বিকাল";
        } else if (h24 < 20) {
            period = "সন্ধ্যা";
        } else {
            period = "রাত";
        }
        int h12 = h24 % 12;
        if (h12 == 0) {
            h12 = 12;
        }
        String mm = m < 10 ? "0" + m : String.valueOf(m);
        return period + " " + bn(h12) + ":" + bn(mm);
    }

    static String clock(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return hm(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE));
    }

    static String dur(long ms) {
        long min = Math.max(0, (ms + 59999) / 60000);
        long h = min / 60;
        long m = min % 60;
        if (h > 0 && m > 0) {
            return bn(h) + " ঘণ্টা " + bn(m) + " মিনিট";
        }
        if (h > 0) {
            return bn(h) + " ঘণ্টা";
        }
        return bn(m) + " মিনিট";
    }
}
