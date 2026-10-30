package com.kidguard.parental;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Iterator;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * KidGuard - parental control app (single file, no AndroidX, no RecyclerView, no XML layouts).
 * Flow: Welcome -> Get started (setup) -> PIN gate -> Dashboard (Home / Limits / Tracker / Settings)
 */
public class MainActivity extends Activity {

    // ===================== THEME =====================
    static final int BG = 0xFF0E1025, CARD = 0xFF1A1D3A, CARD2 = 0xFF262A55,
            ACC = 0xFF7C5CFF, ACC2 = 0xFF22D3EE, OK = 0xFF34D399, WARN = 0xFFFBBF24,
            BAD = 0xFFF87171, TXT = 0xFFF1F3FF, MUTED = 0xFF9AA0C3;
    static final int MW = ViewGroup.LayoutParams.MATCH_PARENT, WC = ViewGroup.LayoutParams.WRAP_CONTENT;

    // ===================== UI HELPERS =====================
    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable bg(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable grad(int c1, int c2, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{c1, c2});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static TextView tv(Context c, String t, float size, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static LinearLayout.LayoutParams lpw(Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = lp(c, 0, WC, l, t, r, b);
        p.weight = 1;
        return p;
    }

    static TextView pill(Context c, String text, int bgColor, int fg, View.OnClickListener l) {
        TextView b = tv(c, text, 15, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        b.setBackground(bg(bgColor, 16, c));
        b.setClickable(true);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        l.setBackground(bg(CARD, 20, c));
        return l;
    }

    static TextView section(Context c, String t) {
        TextView v = tv(c, t, 17, TXT, true);
        v.setPadding(0, dp(c, 20), 0, dp(c, 10));
        return v;
    }

    static LinearLayout bar(Context c, int color, int heightDp) {
        LinearLayout t = new LinearLayout(c);
        t.setWeightSum(1000);
        t.setBackground(bg(0x26FFFFFF, heightDp / 2f, c));
        View fill = new View(c);
        fill.setBackground(bg(color, heightDp / 2f, c));
        t.addView(fill, new LinearLayout.LayoutParams(0, MW, 0));
        return t;
    }

    static void setBar(LinearLayout t, float f) {
        f = Math.max(0f, Math.min(1f, f));
        View fill = t.getChildAt(0);
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) fill.getLayoutParams();
        p.weight = f * 1000f;
        fill.setLayoutParams(p);
    }

    static EditText pinEdit(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TXT);
        e.setTextSize(16);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        e.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        e.setBackground(bg(CARD2, 14, c));
        return e;
    }

    static TextView stat(Context c, LinearLayout row, String label) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView v = tv(c, "-", 18, Color.WHITE, true);
        l.addView(v);
        l.addView(tv(c, label, 11, 0xB3FFFFFF, false));
        row.addView(l, new LinearLayout.LayoutParams(0, WC, 1));
        return v;
    }

    // ===================== FORMAT / APP HELPERS =====================
    static String fmt(long ms) {
        long m = ms / 60000;
        if (ms > 0 && m == 0) return "<1m";
        if (m == 0) return "0m";
        long h = m / 60;
        m = m % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
    }

    static String fmtMin(int min) {
        return fmt(min * 60000L);
    }

    static String ago(long t) {
        long s = (System.currentTimeMillis() - t) / 1000;
        if (s < 60) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " d ago";
    }

    static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    static Drawable icon(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    static ImageView iconView(Context c, String pkg, int sizeDp) {
        ImageView iv = new ImageView(c);
        Drawable d = icon(c.getPackageManager(), pkg);
        if (d != null) iv.setImageDrawable(d);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("kg", Context.MODE_PRIVATE);
    }

    static String hash(String pin) {
        try {
            MessageDigest m = MessageDigest.getInstance("SHA-256");
            byte[] d = m.digest(("kg$" + pin).getBytes("UTF-8"));
            StringBuilder s = new StringBuilder();
            for (byte b : d) s.append(String.format("%02x", b));
            return s.toString();
        } catch (Exception e) {
            return pin;
        }
    }

    // ===================== PERMISSION HELPERS =====================
    @SuppressWarnings("deprecation")
    static boolean hasUsage(Context c) {
        try {
            AppOpsManager a = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
            int m = a.checkOpNoThrow("android:get_usage_stats", Process.myUid(), c.getPackageName());
            return m == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean canOverlay(Context c) {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c);
    }

    static boolean hasLoc(Context c) {
        return Build.VERSION.SDK_INT < 23
                || c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    static void startMonitor(Context c) {
        Intent i = new Intent(c, MonitorService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception e) {
            // ignore
        }
    }

    // ===================== USAGE ENGINE =====================
    static class Usage {
        Map<String, Long> map = new HashMap<String, Long>();
        String fg;
        long total;
    }

    static final Map<String, Boolean> IGN = new ConcurrentHashMap<String, Boolean>();
    static final Set<String> DIALERS = new HashSet<String>(Arrays.asList(
            "com.android.dialer", "com.google.android.dialer", "com.android.phone",
            "com.android.incallui", "com.samsung.android.dialer", "com.android.server.telecom",
            "com.android.contacts", "com.android.emergency"));

    /** True for our own app, launchers, system UI and anything without a launcher icon. */
    static boolean ignorable(Context c, String pkg) {
        if (pkg == null) return true;
        Boolean b = IGN.get(pkg);
        if (b == null) {
            b = pkg.equals(c.getPackageName()) || c.getPackageManager().getLaunchIntentForPackage(pkg) == null;
            IGN.put(pkg, b);
        }
        return b;
    }

    static void addUse(Context c, Usage u, String pkg, long ms) {
        if (ms <= 0 || ignorable(c, pkg)) return;
        Long o = u.map.get(pkg);
        u.map.put(pkg, (o == null ? 0L : o) + ms);
        u.total += ms;
    }

    @SuppressWarnings("deprecation")
    static Usage computeUsage(Context c) {
        Usage u = new Usage();
        try {
            UsageStatsManager m = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            if (m == null) return u;
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long now = System.currentTimeMillis();
            UsageEvents ev = m.queryEvents(cal.getTimeInMillis(), now);
            if (ev == null) return u;
            UsageEvents.Event e = new UsageEvents.Event();
            String cur = null;
            long curStart = 0;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int t = e.getEventType();
                String p = e.getPackageName();
                long ts = e.getTimeStamp();
                if (t == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    if (cur != null && !cur.equals(p)) addUse(c, u, cur, ts - curStart);
                    if (cur == null || !cur.equals(p)) {
                        cur = p;
                        curStart = ts;
                    }
                } else if (t == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                    if (cur != null && cur.equals(p)) {
                        addUse(c, u, cur, ts - curStart);
                        cur = null;
                    }
                } else if (t == 16) { // SCREEN_NON_INTERACTIVE (screen off)
                    if (cur != null) {
                        addUse(c, u, cur, ts - curStart);
                        cur = null;
                    }
                }
            }
            if (cur != null) {
                addUse(c, u, cur, now - curStart);
                u.fg = cur;
            }
        } catch (Exception ex) {
            // permission missing or OEM quirk
        }
        return u;
    }

    // ===================== LOCATION STORE =====================
    static class Pt {
        long t;
        double lat, lon;
        int acc;
    }

    static List<Pt> loadHist(Context c) {
        List<Pt> out = new ArrayList<Pt>();
        String s = prefs(c).getString("loc_hist", "");
        if (s.length() == 0) return out;
        for (String line : s.split("\n")) {
            String[] f = line.split("\\|");
            if (f.length < 4) continue;
            try {
                Pt p = new Pt();
                p.t = Long.parseLong(f[0]);
                p.lat = Double.parseDouble(f[1]);
                p.lon = Double.parseDouble(f[2]);
                p.acc = Integer.parseInt(f[3]);
                out.add(p);
            } catch (Exception e) {
                // skip bad line
            }
        }
        return out;
    }

    static void saveLoc(Context c, Location l) {
        SharedPreferences s = prefs(c);
        String old = s.getString("loc_hist", "");
        String[] arr = old.length() == 0 ? new String[0] : old.split("\n");
        String key = "|" + l.getLatitude() + "|" + l.getLongitude() + "|";
        int end = arr.length;
        if (end > 0 && arr[end - 1].contains(key)) end--; // same spot: refresh its time instead of duplicating
        int start = Math.max(0, end - 59);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) sb.append(arr[i]).append('\n');
        sb.append(l.getTime()).append(key).append((int) l.getAccuracy());
        s.edit().putString("loc_hist", sb.toString()).apply();
    }

    // ===================== CLOUD (free Firebase Realtime Database over plain REST) =====================
    static final String RULES = "{\n  \"rules\": {\n    \".read\": false,\n    \".write\": false,\n    \"kg\": { \"$code\": { \".read\": true, \".write\": true } }\n  }\n}";

    static boolean isParent(Context c) {
        return "parent".equals(prefs(c).getString("role", ""));
    }

    static String newCode() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    static class Cloud {
        static final ExecutorService NET = Executors.newFixedThreadPool(2);
        static volatile long serverNow = 0;
        static volatile String lastErr = "";

        static boolean ready(Context c) {
            SharedPreferences p = prefs(c);
            return p.getString("cloud_url", "").length() > 0 && p.getString("cloud_code", "").length() > 0;
        }

        static String url(Context c, String path) {
            SharedPreferences p = prefs(c);
            return p.getString("cloud_url", "") + "/kg/" + p.getString("cloud_code", "") + "/" + path + ".json";
        }

        /** Blocking HTTP call. Returns body, or null on error (see lastErr). Never call on the UI thread. */
        static String req(String method, String url, String body) {
            HttpURLConnection cn = null;
            lastErr = "";
            try {
                cn = (HttpURLConnection) new URL(url).openConnection();
                cn.setConnectTimeout(8000);
                cn.setReadTimeout(8000);
                cn.setRequestMethod(method);
                if (body != null) {
                    cn.setDoOutput(true);
                    cn.setRequestProperty("Content-Type", "application/json");
                    OutputStream os = cn.getOutputStream();
                    os.write(body.getBytes("UTF-8"));
                    os.close();
                }
                int code = cn.getResponseCode();
                long d = cn.getDate();
                if (d > 0) serverNow = d;
                if (code < 200 || code >= 300) {
                    lastErr = "HTTP " + code + (code == 401 || code == 403 ? " (check the database rules)" : "");
                    return null;
                }
                BufferedReader r = new BufferedReader(new InputStreamReader(cn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                return sb.toString();
            } catch (Exception e) {
                lastErr = String.valueOf(e.getMessage());
                return null;
            } finally {
                if (cn != null) cn.disconnect();
            }
        }

        static JSONObject getJson(Context c, String path) {
            String r = req("GET", url(c, path), null);
            if (r == null || r.equals("null") || r.length() == 0) return null;
            try {
                return new JSONObject(r);
            } catch (Exception e) {
                return null;
            }
        }

        static void putAsync(final Context c, final String path, final String json) {
            NET.execute(new Runnable() {
                public void run() {
                    req("PUT", url(c, path), json);
                }
            });
        }
    }

    /** Settings that the parent controls, as stored in the cloud. */
    static String settingsJson(Context c) {
        SharedPreferences p = prefs(c);
        try {
            JSONObject o = new JSONObject();
            o.put("daily", p.getInt("daily_total", 0));
            o.put("lock", p.getBoolean("force_lock", false));
            o.put("bonus", p.getLong("unlock_until", 0));
            o.put("pin", p.getString("pin", ""));
            JSONObject lim = new JSONObject();
            for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
                if (en.getKey().startsWith("lim_") && en.getValue() instanceof Integer && ((Integer) en.getValue()) > 0)
                    lim.put(en.getKey().substring(4).replace('.', ','), ((Integer) en.getValue()).intValue());
            }
            o.put("lim", lim);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    static void pushSettings(Context c) {
        if (!Cloud.ready(c) || prefs(c).getString("pin", "").length() == 0) return;
        Cloud.putAsync(c, "settings", settingsJson(c));
    }

    static void applySettings(Context c, JSONObject s) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        e.putInt("daily_total", s.optInt("daily", 0));
        e.putBoolean("force_lock", s.optBoolean("lock", false));
        e.putLong("unlock_until", s.optLong("bonus", 0));
        String pin = s.optString("pin", "");
        if (pin.length() > 0) e.putString("pin", pin);
        for (String k : new ArrayList<String>(p.getAll().keySet())) {
            if (k.startsWith("lim_")) e.remove(k);
        }
        JSONObject lim = s.optJSONObject("lim");
        if (lim != null) {
            Iterator<String> it = lim.keys();
            while (it.hasNext()) {
                String k = it.next();
                int v = lim.optInt(k, 0);
                if (v > 0) e.putInt("lim_" + k.replace(',', '.'), v);
            }
        }
        e.apply();
    }

    static final int[] AV = {0xFF7C5CFF, 0xFF22D3EE, 0xFF34D399, 0xFFFBBF24, 0xFFF87171, 0xFF3B82F6};

    static void styleAvatar(Context c, TextView t, String name, int sizeDp) {
        t.setText(name.length() > 0 ? name.substring(0, 1).toUpperCase() : "?");
        t.setBackground(bg(AV[Math.abs(name.hashCode() % AV.length)], sizeDp / 2f, c));
    }

    static TextView avatar(Context c, String name, int sizeDp) {
        TextView t = tv(c, "", sizeDp * 0.42f, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        styleAvatar(c, t, name, sizeDp);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return t;
    }

    static EditText textEdit(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TXT);
        e.setTextSize(14);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        e.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        e.setBackground(bg(CARD2, 14, c));
        return e;
    }

    // ===================== PIN PAD =====================
    static class PinPad extends LinearLayout {
        interface Submit {
            /** return null when accepted, otherwise an error message */
            String onSubmit(String pin);
        }

        final StringBuilder sb = new StringBuilder();
        final TextView dots, msg;
        final Submit cb;

        PinPad(Context c, int keyDp, Submit cb) {
            super(c);
            this.cb = cb;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_HORIZONTAL);
            dots = tv(c, "", 26, TXT, true);
            dots.setGravity(Gravity.CENTER);
            msg = tv(c, " ", 13, BAD, false);
            msg.setGravity(Gravity.CENTER);
            addView(dots, lp(c, MW, WC, 0, 0, 0, 4));
            addView(msg, lp(c, MW, WC, 0, 0, 0, 10));
            String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"\u232B", "0", "\u2713"}};
            for (String[] r : rows) {
                LinearLayout line = new LinearLayout(c);
                line.setGravity(Gravity.CENTER);
                for (String k : r) {
                    final String key = k;
                    boolean ok = key.equals("\u2713");
                    TextView b = tv(c, key, 22, ok ? Color.WHITE : TXT, true);
                    b.setGravity(Gravity.CENTER);
                    b.setBackground(bg(ok ? ACC : CARD2, keyDp / 2f, c));
                    b.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            press(key);
                        }
                    });
                    line.addView(b, lp(c, dp(c, keyDp), dp(c, keyDp), 7, 6, 7, 6));
                }
                addView(line);
            }
            refresh();
        }

        void press(String k) {
            msg.setText(" ");
            if (k.equals("\u232B")) {
                if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
            } else if (k.equals("\u2713")) {
                if (sb.length() < 4) {
                    msg.setText("PIN has at least 4 digits");
                    return;
                }
                String err = cb.onSubmit(sb.toString());
                sb.setLength(0);
                if (err != null) msg.setText(err);
            } else if (sb.length() < 6) {
                sb.append(k);
            }
            refresh();
        }

        void refresh() {
            StringBuilder s = new StringBuilder();
            int n = Math.max(4, sb.length());
            for (int i = 0; i < n; i++) {
                s.append(i < sb.length() ? "\u25CF" : "\u25CB");
                if (i < n - 1) s.append("  ");
            }
            dots.setText(s.toString());
        }
    }

    // ===================== TRAIL VIEW (mini map) =====================
    static class TrailView extends View {
        final List<Pt> pts;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        TrailView(Context c, List<Pt> pts) {
            super(c);
            this.pts = pts;
        }

        @Override
        protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            int pad = dp(getContext(), 28);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(getContext(), 1));
            p.setColor(0x16FFFFFF);
            for (int i = 1; i < 5; i++) {
                cv.drawLine(0, h * i / 5f, w, h * i / 5f, p);
                cv.drawLine(w * i / 5f, 0, w * i / 5f, h, p);
            }
            if (pts.isEmpty()) return;
            double minLa = 999, maxLa = -999, minLo = 999, maxLo = -999;
            for (Pt q : pts) {
                minLa = Math.min(minLa, q.lat);
                maxLa = Math.max(maxLa, q.lat);
                minLo = Math.min(minLo, q.lon);
                maxLo = Math.max(maxLo, q.lon);
            }
            double spanLa = Math.max(maxLa - minLa, 0.0006), spanLo = Math.max(maxLo - minLo, 0.0006);
            double s = Math.min((w - 2.0 * pad) / spanLo, (h - 2.0 * pad) / spanLa);
            double offX = (w - 2.0 * pad - spanLo * s) / 2, offY = (h - 2.0 * pad - spanLa * s) / 2;
            p.setColor(ACC2);
            p.setStrokeWidth(dp(getContext(), 3));
            float px = 0, py = 0;
            for (int i = 0; i < pts.size(); i++) {
                Pt q = pts.get(i);
                float x = (float) (pad + offX + (q.lon - minLo) * s);
                float y = (float) (h - pad - offY - (q.lat - minLa) * s);
                if (i > 0) cv.drawLine(px, py, x, y, p);
                px = x;
                py = y;
            }
            p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < pts.size() - 1; i++) {
                Pt q = pts.get(i);
                float x = (float) (pad + offX + (q.lon - minLo) * s);
                float y = (float) (h - pad - offY - (q.lat - minLa) * s);
                p.setColor(0xFF7C5CFF);
                cv.drawCircle(x, y, dp(getContext(), 4), p);
            }
            p.setColor(0x4034D399);
            cv.drawCircle(px, py, dp(getContext(), 14), p);
            p.setColor(OK);
            cv.drawCircle(px, py, dp(getContext(), 7), p);
        }
    }

    // ===================== ACTIVITY STATE =====================
    SharedPreferences pf;
    final Handler h = new Handler(Looper.getMainLooper());
    final ExecutorService exec = Executors.newSingleThreadExecutor();
    boolean unlocked = false;
    int screen = 0;   // 0 welcome, 1 get started, 2 pin gate, 3 dashboard
    int tab = 0;      // 0 home, 1 limits, 2 tracker, 3 settings
    FrameLayout body;
    LinearLayout navBar;
    ObjectAnimator pulse;
    long remoteTs = 0;
    String lastHist = "";
    volatile boolean remoteAppsLoaded = false;
    volatile Map<String, String> remoteApps = new HashMap<String, String>();
    final Map<String, String> remoteNames = new ConcurrentHashMap<String, String>();

    // Home dynamic views
    TextView hTotal, hSub, hLiveName, hLiveSub, hStatApps, hStatLim, hStatState, hStatus;
    LinearLayout hBar, hList;
    ImageView hLiveIcon;
    TextView hLiveAv;

    final Runnable ticker = new Runnable() {
        public void run() {
            if (screen == 3 && (tab == 0 || (tab == 2 && isParent(MainActivity.this)))) refreshHome();
            h.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pf = prefs(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (!pf.getBoolean("onboarded", false)) showWelcome(0);
        else showPin();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pf.getBoolean("onboarded", false) && !unlocked && screen != 2) showPin();
        else if (screen == 1) showStart();
        h.removeCallbacks(ticker);
        h.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(ticker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (pf.getBoolean("onboarded", false)) unlocked = false; // ask for PIN again when coming back
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPulse();
        exec.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int rc, String[] perms, int[] res) {
        if (screen == 1) showStart();
        else if (screen == 3) selectTab(tab);
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void stopPulse() {
        if (pulse != null) {
            pulse.cancel();
            pulse = null;
        }
    }

    // ===================== 1. WELCOME =====================
    void showWelcome(final int idx) {
        screen = 0;
        stopPulse();
        final Context c = this;
        final String[] ic = {"\uD83D\uDEE1\uFE0F", "\uD83D\uDCCA", "\u23F0"};
        final String[] ti = {"Welcome to KidGuard", "Live activity insights", "Healthy screen limits"};
        final String[] su = {
                "Link your phone to your child's phone and manage everything from your own phone. 100% free.",
                "See which app is open right now and how long every app has been used today.",
                "Set daily limits per app or for the whole phone. It locks automatically when time is up."};

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG, 0xFF1B1446}));
        root.setPadding(dp(c, 28), dp(c, 36), dp(c, 28), dp(c, 28));

        TextView skip = tv(c, idx < 2 ? "Skip" : " ", 15, MUTED, true);
        skip.setGravity(Gravity.END);
        skip.setPadding(0, 0, 0, dp(c, 8));
        if (idx < 2) skip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showRole();
            }
        });
        root.addView(skip, lp(c, MW, WC, 0, 0, 0, 0));
        root.addView(new View(c), new LinearLayout.LayoutParams(MW, 0, 1));

        FrameLayout circle = new FrameLayout(c);
        circle.setBackground(grad(ACC, ACC2, 90, c));
        TextView e = tv(c, ic[idx], 78, Color.WHITE, false);
        e.setGravity(Gravity.CENTER);
        circle.addView(e, new FrameLayout.LayoutParams(MW, MW));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(c, 180), dp(c, 180));
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.bottomMargin = dp(c, 36);
        root.addView(circle, cp);

        TextView t = tv(c, ti[idx], 28, TXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, lp(c, MW, WC, 0, 0, 0, 12));
        TextView s = tv(c, su[idx], 16, MUTED, false);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.15f);
        root.addView(s, lp(c, MW, WC, 8, 0, 8, 0));

        root.addView(new View(c), new LinearLayout.LayoutParams(MW, 0, 2));

        LinearLayout dots = new LinearLayout(c);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 3; i++) {
            View d = new View(c);
            d.setBackground(bg(i == idx ? ACC : CARD2, 4, c));
            dots.addView(d, lp(c, dp(c, i == idx ? 24 : 8), dp(c, 8), 4, 0, 4, 0));
        }
        root.addView(dots, lp(c, MW, WC, 0, 0, 0, 20));

        root.addView(pill(c, idx < 2 ? "Next" : "Get started", ACC, Color.WHITE, new View.OnClickListener() {
            public void onClick(View v) {
                if (idx < 2) showWelcome(idx + 1);
                else showRole();
            }
        }), lp(c, MW, WC, 0, 0, 0, 0));

        setContentView(root);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(280).start();
    }

    // ===================== 2. GET STARTED =====================
    LinearLayout stepCard(int n, String title, String desc, boolean done) {
        Context c = this;
        LinearLayout card = card(c);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = tv(c, done ? "\u2713" : String.valueOf(n), 15, done ? BG : TXT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(bg(done ? OK : CARD2, 16, c));
        row.addView(badge, new LinearLayout.LayoutParams(dp(c, 32), dp(c, 32)));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(tv(c, title, 16, TXT, true));
        t.addView(tv(c, desc, 13, MUTED, false));
        row.addView(t, lpw(c, 12, 0, 0, 0));
        card.addView(row);
        return card;
    }

    void showRole() {
        screen = 1;
        stopPulse();
        final Context c = this;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG, 0xFF1B1446}));
        root.setPadding(dp(c, 24), dp(c, 48), dp(c, 24), dp(c, 24));
        root.addView(tv(c, "Which phone is this?", 28, TXT, true));
        root.addView(tv(c, "Install KidGuard on both phones. Choose a role on each.", 14, MUTED, false), lp(c, MW, WC, 0, 6, 0, 28));
        root.addView(roleCard("\uD83D\uDC6A", "I'm the parent", "Use this phone to watch usage, set limits and lock your child's phone.", "parent"), lp(c, MW, WC, 0, 0, 0, 14));
        root.addView(roleCard("\uD83D\uDCF1", "This is my child's phone", "Tracks apps and location, enforces limits and shows the lock screen.", "child"), lp(c, MW, WC, 0, 0, 0, 0));
        setContentView(root);
    }

    View roleCard(String emoji, String title, String desc, final String role) {
        Context c = this;
        LinearLayout card = card(c);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextView e = tv(c, emoji, 28, Color.WHITE, false);
        e.setGravity(Gravity.CENTER);
        e.setBackground(grad(ACC, ACC2, 28, c));
        card.addView(e, new LinearLayout.LayoutParams(dp(c, 56), dp(c, 56)));
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(tv(c, title, 17, TXT, true));
        t.addView(tv(c, desc, 13, MUTED, false), lp(c, MW, WC, 0, 2, 0, 0));
        card.addView(t, lpw(c, 14, 0, 0, 0));
        card.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pf.edit().putString("role", role).apply();
                showStart();
            }
        });
        return card;
    }

    TextView changeRoleLink() {
        TextView t = tv(this, "Wrong device type? Change", 13, ACC2, true);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pf.edit().remove("role").apply();
                showRole();
            }
        });
        return t;
    }

    String pairKey() {
        return pf.getString("cloud_url", "") + "|" + pf.getString("cloud_code", "");
    }

    void copyText(String label, String text) {
        try {
            ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label, text));
            toast("Copied");
        } catch (Exception e) {
            toast("Could not copy");
        }
    }

    View keyBlock() {
        final Context c = this;
        final String key = pairKey();
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        TextView kv = tv(c, key, 12, ACC2, false);
        kv.setTextIsSelectable(true);
        kv.setPadding(dp(c, 12), dp(c, 12), dp(c, 12), dp(c, 12));
        kv.setBackground(bg(CARD2, 12, c));
        l.addView(kv, lp(c, MW, WC, 0, 8, 0, 0));
        LinearLayout r = new LinearLayout(c);
        r.addView(pill(c, "Copy key", CARD2, TXT, new View.OnClickListener() {
            public void onClick(View v) {
                copyText("KidGuard key", key);
            }
        }), lpw(c, 0, 0, 6, 0));
        r.addView(pill(c, "Share", ACC, Color.WHITE, new View.OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, key);
                startActivity(Intent.createChooser(i, "Send pairing key"));
            }
        }), lpw(c, 6, 0, 0, 0));
        l.addView(r, lp(c, MW, WC, 0, 10, 0, 0));
        return l;
    }

    void connectCloud(String raw) {
        String u = raw.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.startsWith("https://") || u.length() < 15) {
            toast("Paste the full database URL (starts with https://)");
            return;
        }
        SharedPreferences.Editor e = pf.edit();
        e.putString("cloud_url", u);
        if (pf.getString("cloud_code", "").length() == 0) e.putString("cloud_code", newCode());
        e.apply();
        toast("Connecting...");
        exec.execute(new Runnable() {
            public void run() {
                final String r = Cloud.req("PUT", Cloud.url(MainActivity.this, "ping"), "{\".sv\":\"timestamp\"}");
                final String err = Cloud.lastErr;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (r != null) {
                            pf.edit().putBoolean("cloud_ok", true).apply();
                            pushSettings(MainActivity.this);
                            toast("Connected");
                        } else {
                            toast("Failed: " + err);
                        }
                        if (screen == 1) showStart();
                    }
                });
            }
        });
    }

    void connectChild(String raw) {
        String[] p = raw.trim().split("\\|");
        if (p.length != 2 || !p[0].trim().startsWith("https://") || p[1].trim().length() < 10) {
            toast("That is not a valid pairing key");
            return;
        }
        String u = p[0].trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        pf.edit().putString("cloud_url", u).putString("cloud_code", p[1].trim()).apply();
        toast("Connecting...");
        exec.execute(new Runnable() {
            public void run() {
                final JSONObject se = Cloud.getJson(MainActivity.this, "settings");
                final String err = Cloud.lastErr;
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (se != null && se.optString("pin", "").length() > 0) {
                            applySettings(MainActivity.this, se);
                            pf.edit().putBoolean("cloud_ok", true).apply();
                            toast("Linked to parent");
                        } else if (err.length() > 0) {
                            toast("Could not connect: " + err);
                        } else {
                            toast("Nothing found. Finish setup on the parent's phone first.");
                        }
                        if (screen == 1) showStart();
                    }
                });
            }
        });
    }

    void showStart() {
        String role = pf.getString("role", "");
        if (role.length() == 0) {
            showRole();
            return;
        }
        screen = 1;
        stopPulse();
        if (role.equals("parent")) startParent();
        else startChild();
    }

    void startParent() {
        final Context c = this;
        final boolean pinOk = pf.getString("pin", "").length() > 0;
        final boolean cloudOk = pf.getBoolean("cloud_ok", false) && Cloud.ready(c);

        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 20), dp(c, 32), dp(c, 20), dp(c, 28));
        sv.addView(col);
        col.addView(tv(c, "Set up parent phone", 28, TXT, true));
        col.addView(tv(c, "Two quick steps, then link your child's phone.", 14, MUTED, false), lp(c, MW, WC, 0, 4, 0, 6));
        col.addView(changeRoleLink(), lp(c, MW, WC, 0, 0, 0, 16));

        // 1: PIN
        LinearLayout s1 = stepCard(1, "Create parent PIN", "Protects the dashboard and unlocks limits", pinOk);
        if (!pinOk) {
            final EditText p1 = pinEdit(c, "New PIN (4-6 digits)");
            final EditText p2 = pinEdit(c, "Repeat PIN");
            s1.addView(p1, lp(c, MW, WC, 0, 14, 0, 0));
            s1.addView(p2, lp(c, MW, WC, 0, 8, 0, 0));
            s1.addView(pill(c, "Save PIN", ACC, Color.WHITE, new View.OnClickListener() {
                public void onClick(View v) {
                    String a = p1.getText().toString(), b = p2.getText().toString();
                    if (a.length() < 4) toast("PIN needs at least 4 digits");
                    else if (!a.equals(b)) toast("PINs do not match");
                    else {
                        pf.edit().putString("pin", hash(a)).apply();
                        pushSettings(c);
                        showStart();
                    }
                }
            }), lp(c, MW, WC, 0, 12, 0, 0));
        }
        col.addView(s1, lp(c, MW, WC, 0, 0, 0, 12));

        // 2: free cloud link
        LinearLayout s2 = stepCard(2, "Free cloud link", "Connects both phones through your own free Firebase database", cloudOk);
        if (!cloudOk) {
            s2.addView(tv(c, "1. Open console.firebase.google.com and sign in with Google. The Spark plan is free and asks for no card.\n2. Add project, then Build > Realtime Database > Create database (locked mode).\n3. Open the Rules tab, paste the rules below and press Publish.\n4. Copy the database URL from the Data tab and paste it here.", 13, MUTED, false), lp(c, MW, WC, 0, 12, 0, 0));
            TextView rules = tv(c, RULES, 11, ACC2, false);
            rules.setTypeface(Typeface.MONOSPACE);
            rules.setTextIsSelectable(true);
            rules.setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 10));
            rules.setBackground(bg(CARD2, 12, c));
            s2.addView(rules, lp(c, MW, WC, 0, 10, 0, 0));
            s2.addView(pill(c, "Copy rules", CARD2, TXT, new View.OnClickListener() {
                public void onClick(View v) {
                    copyText("Firebase rules", RULES);
                }
            }), lp(c, MW, WC, 0, 8, 0, 0));
            final EditText url = textEdit(c, "https://your-project-default-rtdb.firebaseio.com");
            s2.addView(url, lp(c, MW, WC, 0, 12, 0, 0));
            s2.addView(pill(c, "Connect", ACC, Color.WHITE, new View.OnClickListener() {
                public void onClick(View v) {
                    connectCloud(url.getText().toString());
                }
            }), lp(c, MW, WC, 0, 8, 0, 0));
        } else {
            s2.addView(tv(c, "Pairing key for your child's phone:", 13, MUTED, false), lp(c, MW, WC, 0, 12, 0, 0));
            s2.addView(keyBlock());
        }
        col.addView(s2, lp(c, MW, WC, 0, 0, 0, 12));

        // 3: info
        LinearLayout s3 = stepCard(3, "Link your child's phone", "Install KidGuard there, choose \"This is my child's phone\" and paste the key", false);
        col.addView(s3, lp(c, MW, WC, 0, 0, 0, 20));

        boolean all = pinOk && cloudOk;
        col.addView(pill(c, "Open dashboard", all ? ACC : CARD2, all ? Color.WHITE : MUTED, new View.OnClickListener() {
            public void onClick(View v) {
                if (pf.getString("pin", "").length() == 0 || !pf.getBoolean("cloud_ok", false)) {
                    toast("Complete steps 1 and 2 first");
                    return;
                }
                pf.edit().putBoolean("onboarded", true).apply();
                pushSettings(c);
                unlocked = true;
                tab = 0;
                showDash();
            }
        }), lp(c, MW, WC, 0, 0, 0, 0));
        setContentView(sv);
    }

    void startChild() {
        final Context c = this;
        final boolean linkOk = pf.getBoolean("cloud_ok", false) && Cloud.ready(c) && pf.getString("pin", "").length() > 0;
        final boolean usageOk = hasUsage(c), overlayOk = canOverlay(c), locOk = hasLoc(c);

        ScrollView sv = new ScrollView(c);
        sv.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 20), dp(c, 32), dp(c, 20), dp(c, 28));
        sv.addView(col);
        col.addView(tv(c, "Set up child's phone", 28, TXT, true));
        col.addView(tv(c, "Finish these steps once. It takes about a minute.", 14, MUTED, false), lp(c, MW, WC, 0, 4, 0, 6));
        col.addView(changeRoleLink(), lp(c, MW, WC, 0, 0, 0, 16));

        LinearLayout s1 = stepCard(1, "Link to parent", "Paste the pairing key from the parent's phone", linkOk);
        if (!linkOk) {
            final EditText key = textEdit(c, "Paste pairing key");
            s1.addView(key, lp(c, MW, WC, 0, 14, 0, 0));
            s1.addView(pill(c, "Connect", ACC, Color.WHITE, new View.OnClickListener() {
                public void onClick(View v) {
                    connectChild(key.getText().toString());
                }
            }), lp(c, MW, WC, 0, 10, 0, 0));
        }
        col.addView(s1, lp(c, MW, WC, 0, 0, 0, 12));

        LinearLayout s2 = stepCard(2, "Usage access", "Lets KidGuard see which apps are used and for how long", usageOk);
        if (!usageOk) s2.addView(pill(c, "Open settings", ACC, Color.WHITE, new View.OnClickListener() {
            public void onClick(View v) {
                openUsage();
            }
        }), lp(c, MW, WC, 0, 14, 0, 0));
        col.addView(s2, lp(c, MW, WC, 0, 0, 0, 12));

        LinearLayout s3 = stepCard(3, "Display over other apps", "Needed to show the lock screen when time is up", overlayOk);
        if (!overlayOk) s3.addView(pill(c, "Open settings", ACC, Color.WHITE, new View.OnClickListener() {
            public void onClick(View v) {
                openOverlay();
            }
        }), lp(c, MW, WC, 0, 14, 0, 0));
        col.addView(s3, lp(c, MW, WC, 0, 0, 0, 12));

        LinearLayout s4 = stepCard(4, "Location & notifications", "Powers the location tracker and the protection notice", locOk);
        if (!locOk) s4.addView(pill(c, "Allow", ACC, Color.WHITE, new View.OnClickListener() {
            public void onClick(View v) {
                askLoc();
            }
        }), lp(c, MW, WC, 0, 14, 0, 0));
        col.addView(s4, lp(c, MW, WC, 0, 0, 0, 12));

        LinearLayout tip = card(c);
        tip.setBackground(bg(0xFF2A2340, 20, c));
        tip.addView(tv(c, "Xiaomi / MIUI tip", 15, WARN, true));
        tip.addView(tv(c, "Turn on Autostart, set Battery saver to \"No restrictions\" and allow \"Display pop-up windows while running in background\" so limits keep working.", 13, MUTED, false), lp(c, MW, WC, 0, 4, 0, 0));
        tip.addView(pill(c, "Open app settings", CARD2, TXT, new View.OnClickListener() {
            public void onClick(View v) {
                openAppSettings();
            }
        }), lp(c, MW, WC, 0, 12, 0, 0));
        col.addView(tip, lp(c, MW, WC, 0, 0, 0, 20));

        boolean all = linkOk && usageOk && overlayOk && locOk;
        col.addView(pill(c, "Start protecting", all ? ACC : CARD2, all ? Color.WHITE : MUTED, new View.OnClickListener() {
            public void onClick(View v) {
                if (!pf.getBoolean("cloud_ok", false) || !hasUsage(c) || !canOverlay(c) || !hasLoc(c)) {
                    toast("Complete all steps first");
                    return;
                }
                pf.edit().putBoolean("onboarded", true).apply();
                startMonitor(c);
                unlocked = true;
                tab = 0;
                showDash();
            }
        }), lp(c, MW, WC, 0, 0, 0, 0));
        setContentView(sv);
    }

    void openUsage() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception e) {
            openAppSettings();
        }
    }

    void openOverlay() {
        try {
            if (Build.VERSION.SDK_INT >= 23)
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            openAppSettings();
        }
    }

    void openAppSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            toast("Could not open settings");
        }
    }

    void askLoc() {
        if (Build.VERSION.SDK_INT < 23) return;
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION, "android.permission.POST_NOTIFICATIONS"}, 7);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        }
    }

    // ===================== 3. PIN GATE =====================
    void showPin() {
        screen = 2;
        stopPulse();
        final Context c = this;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG, 0xFF1B1446}));
        root.setPadding(dp(c, 24), dp(c, 40), dp(c, 24), dp(c, 24));
        root.addView(new View(c), new LinearLayout.LayoutParams(MW, 0, 1));

        TextView lock = tv(c, "\uD83D\uDD12", 34, Color.WHITE, false);
        lock.setGravity(Gravity.CENTER);
        lock.setBackground(grad(ACC, ACC2, 36, c));
        root.addView(lock, lp(c, dp(c, 72), dp(c, 72), 0, 0, 0, 16));
        TextView t = tv(c, "Parent access", 24, TXT, true);
        t.setGravity(Gravity.CENTER);
        root.addView(t, lp(c, MW, WC, 0, 0, 0, 4));
        TextView s = tv(c, "Enter your PIN to open the dashboard", 14, MUTED, false);
        s.setGravity(Gravity.CENTER);
        root.addView(s, lp(c, MW, WC, 0, 0, 0, 22));

        root.addView(new PinPad(c, 66, new PinPad.Submit() {
            public String onSubmit(String pin) {
                if (hash(pin).equals(pf.getString("pin", ""))) {
                    unlocked = true;
                    showDash();
                    return null;
                }
                return "Wrong PIN, try again";
            }
        }));
        root.addView(new View(c), new LinearLayout.LayoutParams(MW, 0, 1));
        setContentView(root);
    }

    // ===================== 4. DASHBOARD SHELL =====================
    void showDash() {
        screen = 3;
        final Context c = this;
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        body = new FrameLayout(c);
        root.addView(body, new LinearLayout.LayoutParams(MW, 0, 1));
        navBar = new LinearLayout(c);
        navBar.setBackgroundColor(CARD);
        navBar.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        root.addView(navBar, new LinearLayout.LayoutParams(MW, WC));
        setContentView(root);
        selectTab(tab);
    }

    void buildNav() {
        final Context c = this;
        navBar.removeAllViews();
        String[] ic = {"\uD83C\uDFE0", "\u23F3", "\uD83D\uDCCD", "\u2699\uFE0F"};
        String[] nm = {"Home", "Limits", "Tracker", "Settings"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout it = new LinearLayout(c);
            it.setOrientation(LinearLayout.VERTICAL);
            it.setGravity(Gravity.CENTER_HORIZONTAL);
            it.setPadding(0, dp(c, 8), 0, dp(c, 8));
            TextView a = tv(c, ic[i], 20, TXT, false);
            a.setGravity(Gravity.CENTER);
            a.setAlpha(i == tab ? 1f : 0.5f);
            TextView b = tv(c, nm[i], 11, i == tab ? ACC2 : MUTED, true);
            b.setGravity(Gravity.CENTER);
            it.addView(a);
            it.addView(b);
            it.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (idx == 1 && tab != 1) remoteAppsLoaded = false;
                    selectTab(idx);
                }
            });
            navBar.addView(it, new LinearLayout.LayoutParams(0, WC, 1));
        }
    }

    void selectTab(int t) {
        tab = t;
        stopPulse();
        hTotal = null;
        body.removeAllViews();
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(this, 20), dp(this, 28), dp(this, 20), dp(this, 24));
        sv.addView(col);
        body.addView(sv, new FrameLayout.LayoutParams(MW, MW));
        if (t == 0) buildHome(col);
        else if (t == 1) buildLimits(col);
        else if (t == 2) buildTracker(col);
        else buildSettings(col);
        buildNav();
    }

    // ===================== HOME TAB =====================
    void buildHome(LinearLayout col) {
        final Context c = this;
        int hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greet = hr < 12 ? "Good morning" : (hr < 18 ? "Good afternoon" : "Good evening");

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout hl = new LinearLayout(c);
        hl.setOrientation(LinearLayout.VERTICAL);
        hl.addView(tv(c, greet, 14, MUTED, false));
        hl.addView(tv(c, "Parent dashboard", 26, TXT, true));
        head.addView(hl, lpw(c, 0, 0, 0, 0));
        hStatus = tv(c, "\u25CF ...", 12, MUTED, true);
        hStatus.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        hStatus.setBackground(bg(CARD, 14, c));
        head.addView(hStatus);
        col.addView(head, lp(c, MW, WC, 0, 0, 0, 18));

        // hero card
        LinearLayout hero = new LinearLayout(c);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 20));
        hero.setBackground(grad(ACC, 0xFF3B82F6, 24, c));
        hero.addView(tv(c, "Screen time today", 13, 0xCCFFFFFF, false));
        hTotal = tv(c, "0m", 44, Color.WHITE, true);
        hero.addView(hTotal);
        hSub = tv(c, "Loading...", 13, 0xCCFFFFFF, false);
        hero.addView(hSub);
        hBar = bar(c, Color.WHITE, 8);
        hero.addView(hBar, lp(c, MW, dp(c, 8), 0, 12, 0, 18));
        LinearLayout stats = new LinearLayout(c);
        hStatApps = stat(c, stats, "Apps used");
        hStatLim = stat(c, stats, "Limits set");
        hStatState = stat(c, stats, "Status");
        hero.addView(stats);
        col.addView(hero, lp(c, MW, WC, 0, 0, 0, 14));

        // live card
        LinearLayout live = card(c);
        live.setOrientation(LinearLayout.HORIZONTAL);
        live.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout liveHold = new FrameLayout(c);
        hLiveIcon = new ImageView(c);
        hLiveAv = tv(c, "", 20, Color.WHITE, true);
        hLiveAv.setGravity(Gravity.CENTER);
        hLiveAv.setVisibility(View.GONE);
        liveHold.addView(hLiveIcon, new FrameLayout.LayoutParams(MW, MW));
        liveHold.addView(hLiveAv, new FrameLayout.LayoutParams(MW, MW));
        live.addView(liveHold, new LinearLayout.LayoutParams(dp(c, 46), dp(c, 46)));
        LinearLayout lt = new LinearLayout(c);
        lt.setOrientation(LinearLayout.VERTICAL);
        TextView liveTag = tv(c, "\u25CF LIVE NOW", 11, OK, true);
        pulse = ObjectAnimator.ofFloat(liveTag, "alpha", 1f, 0.25f);
        pulse.setDuration(900);
        pulse.setRepeatCount(ObjectAnimator.INFINITE);
        pulse.setRepeatMode(ObjectAnimator.REVERSE);
        pulse.start();
        hLiveName = tv(c, "Idle", 17, TXT, true);
        hLiveName.setSingleLine(true);
        hLiveName.setEllipsize(TextUtils.TruncateAt.END);
        hLiveSub = tv(c, "", 13, MUTED, false);
        lt.addView(liveTag);
        lt.addView(hLiveName);
        lt.addView(hLiveSub);
        live.addView(lt, lpw(c, 14, 0, 0, 0));
        col.addView(live, lp(c, MW, WC, 0, 0, 0, 14));

        // quick actions
        final boolean forced = pf.getBoolean("force_lock", false);
        LinearLayout qa = new LinearLayout(c);
        TextView b1 = pill(c, forced ? "\uD83D\uDD13 Unlock device" : "\uD83D\uDD12 Lock now", forced ? 0xFF059669 : 0xFFE5484D, Color.WHITE, new View.OnClickListener() {
            public void onClick(View v) {
                pf.edit().putBoolean("force_lock", !forced).apply();
                pushSettings(c);
                toast(forced ? "Device unlocked" : "Device locked");
                selectTab(0);
            }
        });
        TextView b2 = pill(c, "\u23F1 +15 min bonus", CARD2, TXT, new View.OnClickListener() {
            public void onClick(View v) {
                pf.edit().putLong("unlock_until", System.currentTimeMillis() + 15 * 60000L).apply();
                pushSettings(c);
                toast("Limits paused for 15 minutes");
            }
        });
        qa.addView(b1, lpw(c, 0, 0, 6, 0));
        qa.addView(b2, lpw(c, 6, 0, 0, 0));
        col.addView(qa);

        col.addView(section(c, "Most used today"));
        hList = card(c);
        col.addView(hList);
        refreshHome();
    }

    void refreshHome() {
        if (tab == 0 && hTotal == null) return;
        final boolean parent = isParent(this);
        exec.execute(new Runnable() {
            public void run() {
                final Usage u;
                if (parent) u = fetchRemote();
                else u = computeUsage(MainActivity.this);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (screen != 3) return;
                        if (tab == 0 && hTotal != null) {
                            applyHome(u);
                        } else if (tab == 2 && parent) {
                            String hh = pf.getString("loc_hist", "");
                            if (!hh.equals(lastHist)) {
                                lastHist = hh;
                                selectTab(2);
                            }
                        }
                    }
                });
            }
        });
    }

    /** Parent phone: download the child's latest status and settings (runs on a background thread). */
    Usage fetchRemote() {
        Usage u = new Usage();
        try {
            JSONObject se = Cloud.getJson(this, "settings");
            if (se != null) applySettings(this, se);
            JSONObject st = Cloud.getJson(this, "status");
            if (st == null) {
                remoteTs = 0;
                return u;
            }
            remoteTs = st.optLong("ts", 0);
            u.total = st.optLong("total", 0);
            String fg = st.optString("fg", "");
            u.fg = fg.length() == 0 ? null : fg;
            JSONObject apps = st.optJSONObject("apps");
            if (apps != null) {
                Iterator<String> it = apps.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject a = apps.optJSONObject(k);
                    if (a == null) continue;
                    String pkg = k.replace(',', '.');
                    u.map.put(pkg, a.optLong("ms", 0));
                    remoteNames.put(pkg, a.optString("n", pkg));
                }
            }
            if (u.fg != null) remoteNames.put(u.fg, st.optString("fgn", u.fg));
            String hist = st.optString("hist", "");
            if (hist.length() > 0) pf.edit().putString("loc_hist", hist).apply();
        } catch (Exception e) {
            // ignore, show what we have
        }
        return u;
    }

    String nameOf(PackageManager pm, String pkg) {
        if (isParent(this)) {
            String n = remoteNames.get(pkg);
            return n != null ? n : pkg;
        }
        return label(pm, pkg);
    }

    void applyHome(Usage u) {
        final Context c = this;
        PackageManager pm = getPackageManager();
        final boolean parent = isParent(c);
        long now = System.currentTimeMillis();

        boolean forced = pf.getBoolean("force_lock", false);
        boolean alive;
        if (parent) alive = remoteTs > 0 && Cloud.serverNow - remoteTs < 25000;
        else alive = now - pf.getLong("svc_alive", 0) < 15000;
        String st;
        int stColor = alive ? OK : BAD;
        if (parent) {
            if (alive) st = "\u25CF Child online";
            else if (remoteTs > 0) st = "\u25CF Child offline";
            else {
                st = "\u25CF Waiting for child";
                stColor = WARN;
            }
        } else {
            st = alive ? "\u25CF Protected" : "\u25CF Not running";
        }
        hStatus.setText(st);
        hStatus.setTextColor(stColor);

        hTotal.setText(fmt(u.total));
        int lim = pf.getInt("daily_total", 0);
        if (lim > 0) {
            long left = lim * 60000L - u.total;
            hSub.setText(left > 0 ? "of " + fmtMin(lim) + " limit \u00B7 " + fmt(left) + " left" : "Daily limit reached");
            setBar(hBar, u.total / (lim * 60000f));
        } else {
            hSub.setText("No daily limit \u00B7 set one in Limits");
            setBar(hBar, 0f);
        }

        int limits = 0;
        for (Map.Entry<String, ?> en : pf.getAll().entrySet()) {
            if (en.getKey().startsWith("lim_") && en.getValue() instanceof Integer && ((Integer) en.getValue()) > 0) limits++;
        }
        if (lim > 0) limits++;
        hStatApps.setText(String.valueOf(u.map.size()));
        hStatLim.setText(String.valueOf(limits));
        hStatState.setText(forced ? "Locked" : (parent ? (alive ? "Online" : "Offline") : (alive ? "Active" : "Off")));

        hLiveAv.setVisibility(View.GONE);
        hLiveIcon.setImageDrawable(null);
        boolean liveOk = u.fg != null && (parent || !ignorable(c, u.fg)) && (!parent || alive);
        if (liveOk) {
            long used = u.map.containsKey(u.fg) ? u.map.get(u.fg) : 0L;
            int al = pf.getInt("lim_" + u.fg, 0);
            String nm = nameOf(pm, u.fg);
            hLiveName.setText(nm);
            hLiveSub.setText("Used " + fmt(used) + " today" + (al > 0 ? " \u00B7 limit " + fmtMin(al) : ""));
            if (parent) {
                hLiveAv.setVisibility(View.VISIBLE);
                styleAvatar(c, hLiveAv, nm, 46);
            } else {
                hLiveIcon.setImageDrawable(icon(pm, u.fg));
            }
        } else if (parent && !alive) {
            hLiveName.setText("Offline");
            hLiveSub.setText(remoteTs > 0 ? "Last seen " + fmt(Math.max(0, Cloud.serverNow - remoteTs)) + " ago" : "Link the child's phone to start");
        } else {
            hLiveName.setText("Idle");
            hLiveSub.setText("Home screen or screen off");
        }

        // top apps list
        hList.removeAllViews();
        List<Map.Entry<String, Long>> es = new ArrayList<Map.Entry<String, Long>>(u.map.entrySet());
        Collections.sort(es, new Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return b.getValue().compareTo(a.getValue());
            }
        });
        if (es.isEmpty()) {
            String m = parent ? "No app activity reported yet today." : (hasUsage(c) ? "No app activity yet today." : "Grant Usage access in Settings to see activity.");
            TextView e = tv(c, m, 14, MUTED, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, dp(c, 12), 0, dp(c, 12));
            hList.addView(e);
            return;
        }
        long max = Math.max(1L, es.get(0).getValue());
        for (int i = 0; i < Math.min(8, es.size()); i++) {
            final String pkg = es.get(i).getKey();
            long ms = es.get(i).getValue();
            final String name = nameOf(pm, pkg);
            int al = pf.getInt("lim_" + pkg, 0);

            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(c, 8), 0, dp(c, 8));
            row.addView(parent ? avatar(c, name, 40) : iconView(c, pkg, 40));
            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = new LinearLayout(c);
            TextView nm = tv(c, name, 15, TXT, true);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(nm, lpw(c, 0, 0, 8, 0));
            top.addView(tv(c, fmt(ms) + (al > 0 ? " / " + fmtMin(al) : ""), 13, MUTED, true));
            mid.addView(top);
            int color = ACC2;
            if (al > 0 && ms >= al * 60000L) color = BAD;
            else if (al > 0 && ms >= al * 48000L) color = WARN;
            LinearLayout b = bar(c, color, 6);
            setBar(b, (float) ms / max);
            mid.addView(b, lp(c, MW, dp(c, 6), 0, 6, 0, 0));
            row.addView(mid, lpw(c, 12, 0, 0, 0));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    pickLimit(name, "lim_" + pkg, new Runnable() {
                        public void run() {
                            refreshHome();
                        }
                    });
                }
            });
            hList.addView(row);
        }
    }

    void pickLimit(String title, final String key, final Runnable after) {
        final int[] mins = {0, 15, 30, 45, 60, 90, 120, 180, 240, 300};
        String[] names = new String[mins.length];
        for (int i = 0; i < mins.length; i++) names[i] = mins[i] == 0 ? "No limit" : fmtMin(mins[i]);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title + " \u2013 daily limit")
                .setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        pf.edit().putInt(key, mins[which]).apply();
                        pushSettings(MainActivity.this);
                        toast(mins[which] == 0 ? "Limit removed" : "Limit set: " + fmtMin(mins[which]));
                        if (after != null) after.run();
                    }
                }).show();
    }

    // ===================== LIMITS TAB =====================
    static class AppItem {
        String pkg, label;
        Drawable icon;
    }

    void buildLimits(LinearLayout col) {
        final Context c = this;
        col.addView(tv(c, "Limits", 26, TXT, true));
        col.addView(tv(c, "Apps lock automatically when today's time is used up.", 14, MUTED, false), lp(c, MW, WC, 0, 2, 0, 16));

        int tot = pf.getInt("daily_total", 0);
        LinearLayout dev = new LinearLayout(c);
        dev.setGravity(Gravity.CENTER_VERTICAL);
        dev.setPadding(dp(c, 20), dp(c, 18), dp(c, 20), dp(c, 18));
        dev.setBackground(grad(ACC, 0xFF3B82F6, 22, c));
        LinearLayout dl = new LinearLayout(c);
        dl.setOrientation(LinearLayout.VERTICAL);
        dl.addView(tv(c, "Daily device limit", 13, 0xCCFFFFFF, false));
        dl.addView(tv(c, tot > 0 ? fmtMin(tot) : "Off", 30, Color.WHITE, true));
        dev.addView(dl, lpw(c, 0, 0, 0, 0));
        TextView edit = tv(c, "Edit", 13, Color.WHITE, true);
        edit.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        edit.setBackground(bg(0x33FFFFFF, 14, c));
        dev.addView(edit);
        dev.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickLimit("Device", "daily_total", new Runnable() {
                    public void run() {
                        selectTab(1);
                    }
                });
            }
        });
        col.addView(dev, lp(c, MW, WC, 0, 0, 0, 4));

        col.addView(section(c, "App limits"));
        LinearLayout list = card(c);
        List<AppItem> items = new ArrayList<AppItem>();
        if (isParent(c)) {
            if (!remoteAppsLoaded) {
                list.addView(tv(c, "Loading apps from your child's phone...", 14, MUTED, false));
                col.addView(list);
                exec.execute(new Runnable() {
                    public void run() {
                        JSONObject o = Cloud.getJson(c, "apps");
                        Map<String, String> m = new HashMap<String, String>();
                        if (o != null) {
                            Iterator<String> it = o.keys();
                            while (it.hasNext()) {
                                String k = it.next();
                                m.put(k.replace(',', '.'), o.optString(k, k));
                            }
                        }
                        remoteApps = m;
                        remoteAppsLoaded = true;
                        runOnUiThread(new Runnable() {
                            public void run() {
                                if (screen == 3 && tab == 1) selectTab(1);
                            }
                        });
                    }
                });
                return;
            }
            if (remoteApps.isEmpty()) {
                list.addView(tv(c, "No apps yet. Open KidGuard on the child's phone once while it is online.", 14, MUTED, false));
                col.addView(list);
                remoteAppsLoaded = false;
                return;
            }
            for (Map.Entry<String, String> en : remoteApps.entrySet()) {
                AppItem a = new AppItem();
                a.pkg = en.getKey();
                a.label = en.getValue();
                items.add(a);
            }
        } else {
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
            Set<String> seen = new HashSet<String>();
            for (ResolveInfo ri : ris) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || !seen.add(p)) continue;
                AppItem a = new AppItem();
                a.pkg = p;
                a.label = ri.loadLabel(pm).toString();
                a.icon = ri.loadIcon(pm);
                items.add(a);
            }
        }
        Collections.sort(items, new Comparator<AppItem>() {
            public int compare(AppItem x, AppItem y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        for (final AppItem a : items) {
            int lim = pf.getInt("lim_" + a.pkg, 0);
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(c, 8), 0, dp(c, 8));
            if (a.icon != null) {
                ImageView iv = new ImageView(c);
                iv.setImageDrawable(a.icon);
                row.addView(iv, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
            } else {
                row.addView(avatar(c, a.label, 40));
            }
            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            TextView nm = tv(c, a.label, 15, TXT, true);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            mid.addView(nm);
            mid.addView(tv(c, lim > 0 ? "Limit " + fmtMin(lim) + " per day" : "No limit", 12, lim > 0 ? ACC2 : MUTED, false));
            row.addView(mid, lpw(c, 12, 0, 8, 0));
            row.addView(tv(c, "\u203A", 22, MUTED, true));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    pickLimit(a.label, "lim_" + a.pkg, new Runnable() {
                        public void run() {
                            selectTab(1);
                        }
                    });
                }
            });
            list.addView(row);
        }
        col.addView(list);
    }

    // ===================== TRACKER TAB =====================
    void buildTracker(LinearLayout col) {
        final Context c = this;
        lastHist = pf.getString("loc_hist", "");
        col.addView(tv(c, "Location tracker", 26, TXT, true));
        col.addView(tv(c, "Position is saved every minute or 20 m while protection runs.", 14, MUTED, false), lp(c, MW, WC, 0, 2, 0, 16));

        final List<Pt> hist = loadHist(c);
        LinearLayout last = card(c);
        if (hist.isEmpty()) {
            last.addView(tv(c, "No location yet", 20, TXT, true));
            last.addView(tv(c, isParent(c) ? "Waiting for the child's phone to report its location." : (hasLoc(c) ? "Make sure GPS is on, then tap Refresh." : "Allow location in Settings first."), 13, MUTED, false), lp(c, MW, WC, 0, 4, 0, 0));
        } else {
            final Pt p = hist.get(hist.size() - 1);
            last.addView(tv(c, "\u25CF LAST KNOWN POSITION", 11, OK, true));
            last.addView(tv(c, String.format(Locale.US, "%.5f, %.5f", p.lat, p.lon), 22, TXT, true), lp(c, MW, WC, 0, 4, 0, 0));
            last.addView(tv(c, ago(p.t) + " \u00B7 accuracy \u00B1" + p.acc + " m", 13, MUTED, false), lp(c, MW, WC, 0, 2, 0, 0));
            last.addView(pill(c, "Open in Maps", ACC, Color.WHITE, new View.OnClickListener() {
                public void onClick(View v) {
                    String q = p.lat + "," + p.lon;
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:" + q + "?q=" + q + "(Child+device)")));
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + q)));
                        } catch (Exception e2) {
                            toast("No maps app found");
                        }
                    }
                }
            }), lp(c, MW, WC, 0, 14, 0, 0));
        }
        if (!isParent(c)) {
            last.addView(pill(c, "Refresh now", CARD2, TXT, new View.OnClickListener() {
                public void onClick(View v) {
                    refreshLocationNow();
                }
            }), lp(c, MW, WC, 0, 8, 0, 0));
        } else {
            last.addView(tv(c, "Updates automatically from your child's phone.", 12, MUTED, false), lp(c, MW, WC, 0, 10, 0, 0));
        }
        col.addView(last, lp(c, MW, WC, 0, 0, 0, 14));

        // trail
        int from = Math.max(0, hist.size() - 30);
        TrailView tr = new TrailView(c, new ArrayList<Pt>(hist.subList(from, hist.size())));
        tr.setBackground(bg(CARD, 20, c));
        col.addView(tr, lp(c, MW, dp(c, 200), 0, 0, 0, 0));

        col.addView(section(c, "Recent locations"));
        LinearLayout list = card(c);
        if (hist.isEmpty()) {
            list.addView(tv(c, "History will appear here.", 14, MUTED, false));
        }
        SimpleDateFormat f = new SimpleDateFormat("HH:mm \u00B7 dd MMM", Locale.getDefault());
        for (int i = hist.size() - 1; i >= Math.max(0, hist.size() - 15); i--) {
            Pt p = hist.get(i);
            LinearLayout row = new LinearLayout(c);
            row.setPadding(0, dp(c, 7), 0, dp(c, 7));
            row.addView(tv(c, f.format(new Date(p.t)), 13, MUTED, true), lpw(c, 0, 0, 0, 0));
            row.addView(tv(c, String.format(Locale.US, "%.4f, %.4f", p.lat, p.lon), 13, TXT, false));
            list.addView(row);
        }
        col.addView(list);
    }

    void refreshLocationNow() {
        if (!hasLoc(this)) {
            toast("Allow location first");
            return;
        }
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
        } catch (SecurityException e) {
            // ignore
        }
        if (best != null) {
            saveLoc(this, best);
            toast("Location updated");
        } else {
            toast("No GPS fix yet. Turn on location and try outdoors.");
        }
        selectTab(2);
    }

    // ===================== SETTINGS TAB =====================
    View permRow(String name, boolean ok, View.OnClickListener fix) {
        Context c = this;
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(c, 8), 0, dp(c, 8));
        r.addView(tv(c, name, 15, TXT, false), lpw(c, 0, 0, 0, 0));
        TextView chip = tv(c, ok ? "\u2713 Granted" : "Fix", 13, ok ? OK : Color.WHITE, true);
        chip.setPadding(dp(c, 12), dp(c, 6), dp(c, 12), dp(c, 6));
        chip.setBackground(bg(ok ? 0x2234D399 : 0xFFE5484D, 12, c));
        if (!ok) chip.setOnClickListener(fix);
        r.addView(chip);
        return r;
    }

    void buildSettings(LinearLayout col) {
        final Context c = this;
        final boolean parent = isParent(c);
        col.addView(tv(c, "Settings", 26, TXT, true), lp(c, MW, WC, 0, 0, 0, 16));

        if (parent) {
            LinearLayout pk = card(c);
            pk.addView(tv(c, "Pairing key", 16, TXT, true));
            pk.addView(tv(c, "On your child's phone choose \"This is my child's phone\" and paste this key.", 13, MUTED, false), lp(c, MW, WC, 0, 4, 0, 0));
            pk.addView(keyBlock());
            col.addView(pk, lp(c, MW, WC, 0, 0, 0, 14));
        } else {
            LinearLayout perms = card(c);
            perms.addView(tv(c, "Permissions", 16, TXT, true));
            perms.addView(permRow("Usage access", hasUsage(c), new View.OnClickListener() {
                public void onClick(View v) {
                    openUsage();
                }
            }));
            perms.addView(permRow("Display over other apps", canOverlay(c), new View.OnClickListener() {
                public void onClick(View v) {
                    openOverlay();
                }
            }));
            perms.addView(permRow("Location", hasLoc(c), new View.OnClickListener() {
                public void onClick(View v) {
                    askLoc();
                }
            }));
            col.addView(perms, lp(c, MW, WC, 0, 0, 0, 14));
        }

        if (parent) {
            col.addView(pill(c, "Change parent PIN", CARD2, TXT, new View.OnClickListener() {
                public void onClick(View v) {
                    changePin();
                }
            }), lp(c, MW, WC, 0, 0, 0, 10));
        } else {
            col.addView(pill(c, "Restart protection", CARD2, TXT, new View.OnClickListener() {
                public void onClick(View v) {
                    startMonitor(c);
                    toast("Protection restarted");
                }
            }), lp(c, MW, WC, 0, 0, 0, 10));
        }
        col.addView(pill(c, "Lock dashboard", ACC, Color.WHITE, new View.OnClickListener() {
            public void onClick(View v) {
                unlocked = false;
                showPin();
            }
        }), lp(c, MW, WC, 0, 0, 0, 10));
        col.addView(pill(c, "Reset this phone", CARD2, BAD, new View.OnClickListener() {
            public void onClick(View v) {
                new AlertDialog.Builder(c, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                        .setTitle("Reset KidGuard?")
                        .setMessage("This clears the role, PIN link and all settings stored on this phone.")
                        .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                stopService(new Intent(c, MonitorService.class));
                                pf.edit().clear().apply();
                                unlocked = false;
                                showWelcome(0);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }), lp(c, MW, WC, 0, 0, 0, 18));

        if (!parent) {
            LinearLayout tip = card(c);
            tip.setBackground(bg(0xFF2A2340, 20, c));
            tip.addView(tv(c, "Xiaomi / MIUI", 15, WARN, true));
            tip.addView(tv(c, "Enable Autostart, set Battery saver to \"No restrictions\" and allow \"Display pop-up windows while running in background\".", 13, MUTED, false), lp(c, MW, WC, 0, 4, 0, 0));
            LinearLayout tr = new LinearLayout(c);
            tr.addView(pill(c, "App settings", CARD2, TXT, new View.OnClickListener() {
                public void onClick(View v) {
                    openAppSettings();
                }
            }), lpw(c, 0, 0, 6, 0));
            tr.addView(pill(c, "Autostart", CARD2, TXT, new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        Intent i = new Intent();
                        i.setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                        startActivity(i);
                    } catch (Exception e) {
                        openAppSettings();
                    }
                }
            }), lpw(c, 6, 0, 0, 0));
            tip.addView(tr, lp(c, MW, WC, 0, 12, 0, 0));
            col.addView(tip, lp(c, MW, WC, 0, 0, 0, 16));
        }

        TextView about = tv(c, "KidGuard 2.0 \u00B7 " + (parent ? "parent phone" : "child phone"), 12, MUTED, false);
        about.setGravity(Gravity.CENTER);
        col.addView(about);
    }

    void changePin() {
        final EditText et = pinEdit(this, "New PIN (4-6 digits)");
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Change PIN")
                .setView(et)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String p = et.getText().toString();
                        if (p.length() < 4) {
                            toast("PIN needs at least 4 digits");
                        } else {
                            pf.edit().putString("pin", hash(p)).apply();
                            pushSettings(MainActivity.this);
                            toast("PIN updated");
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ===================== BACKGROUND MONITOR SERVICE =====================
    public static class MonitorService extends Service {
        final Handler h = new Handler(Looper.getMainLooper());
        SharedPreferences pf;
        WindowManager wm;
        LocationManager lm;
        LocationListener ll;
        boolean registered = false;
        long suppressUntil = 0;
        Usage lastU;
        boolean screenOn = true;
        long lastSync = 0, lastApps = 0;

        View overlay, ovIcon;
        TextView ovTitle, ovSub, ovHome, ovParent, ovCancel;
        PinPad ovPad;

        final Runnable tick = new Runnable() {
            public void run() {
                try {
                    doTick();
                } catch (Exception e) {
                    // keep going
                }
                h.postDelayed(this, 2000);
            }
        };

        @Override
        public IBinder onBind(Intent i) {
            return null;
        }

        @Override
        public int onStartCommand(Intent i, int flags, int id) {
            pf = prefs(this);
            wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            startFg();
            startLoc();
            h.removeCallbacks(tick);
            h.post(tick);
            return START_STICKY;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            h.removeCallbacks(tick);
            hide();
            if (lm != null && ll != null) {
                try {
                    lm.removeUpdates(ll);
                } catch (Exception e) {
                    // ignore
                }
            }
        }

        void startFg() {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel("kg", "Protection status", NotificationManager.IMPORTANCE_LOW);
                ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
                b = new Notification.Builder(this, "kg");
            } else {
                b = new Notification.Builder(this);
            }
            PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            Notification n = b.setContentTitle("KidGuard is active")
                    .setContentText("Monitoring screen time and location")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
            try {
                startForeground(1, n);
            } catch (Exception e) {
                // ignore
            }
        }

        void startLoc() {
            if (registered || !hasLoc(this)) return;
            lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (ll == null) {
                ll = new LocationListener() {
                    public void onLocationChanged(Location l) {
                        saveLoc(MonitorService.this, l);
                    }

                    public void onStatusChanged(String s, int st, Bundle b) {
                    }

                    public void onProviderEnabled(String s) {
                    }

                    public void onProviderDisabled(String s) {
                    }
                };
            }
            try {
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 20, ll);
                    registered = true;
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 20, ll);
                    registered = true;
                }
            } catch (SecurityException e) {
                // permission revoked
            }
        }

        void doTick() {
            long now = System.currentTimeMillis();
            pf.edit().putLong("svc_alive", now).apply();
            if (!registered) startLoc();

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            screenOn = pm == null || pm.isInteractive();
            syncCloud(now);
            if (pm != null && !pm.isInteractive()) {
                hide();
                return;
            }
            if (!canOverlay(this)) return;
            if (now < suppressUntil) {
                hide();
                return;
            }

            Usage u = computeUsage(this);
            lastU = u;
            String fg = u.fg;
            boolean forced = pf.getBoolean("force_lock", false);

            if (forced) {
                if (fg != null && (fg.equals(getPackageName()) || DIALERS.contains(fg))) hide();
                else show("Phone locked", "Your parent has locked this device.");
                return;
            }
            if (now < pf.getLong("unlock_until", 0)) {
                hide();
                return;
            }
            if (fg == null || ignorable(this, fg) || DIALERS.contains(fg)) {
                hide();
                return;
            }
            int lim = pf.getInt("lim_" + fg, 0);
            long used = u.map.containsKey(fg) ? u.map.get(fg) : 0L;
            int tot = pf.getInt("daily_total", 0);
            if (lim > 0 && used >= lim * 60000L) {
                show("Time's up for " + label(getPackageManager(), fg), "Daily limit of " + fmtMin(lim) + " reached.");
            } else if (tot > 0 && u.total >= tot * 60000L) {
                show("Screen time is over", "Daily phone limit of " + fmtMin(tot) + " reached.");
            } else {
                hide();
            }
        }

        /** Every ~5 s: upload live status to the parent and download the parent's settings. */
        void syncCloud(long now) {
            if (!Cloud.ready(this) || now - lastSync < 5000) return;
            lastSync = now;
            final Usage u = lastU;
            final boolean fgOk = screenOn;
            final boolean sendApps = now - lastApps > 600000;
            if (sendApps) lastApps = now;
            Cloud.NET.execute(new Runnable() {
                public void run() {
                    try {
                        Context c = MonitorService.this;
                        PackageManager pm = getPackageManager();
                        JSONObject st = new JSONObject();
                        st.put("ts", new JSONObject().put(".sv", "timestamp"));
                        String fg = (fgOk && u != null && u.fg != null && !ignorable(c, u.fg)) ? u.fg : "";
                        st.put("fg", fg);
                        st.put("fgn", fg.length() > 0 ? label(pm, fg) : "");
                        st.put("total", u == null ? 0L : u.total);
                        JSONObject apps = new JSONObject();
                        if (u != null) {
                            for (Map.Entry<String, Long> en : u.map.entrySet()) {
                                JSONObject a = new JSONObject();
                                a.put("n", label(pm, en.getKey()));
                                a.put("ms", en.getValue().longValue());
                                apps.put(en.getKey().replace('.', ','), a);
                            }
                        }
                        st.put("apps", apps);
                        st.put("hist", prefs(c).getString("loc_hist", ""));
                        Cloud.req("PUT", Cloud.url(c, "status"), st.toString());

                        JSONObject se = Cloud.getJson(c, "settings");
                        if (se != null) applySettings(c, se);

                        if (sendApps) {
                            JSONObject al = new JSONObject();
                            Intent main = new Intent(Intent.ACTION_MAIN);
                            main.addCategory(Intent.CATEGORY_LAUNCHER);
                            for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
                                String p = ri.activityInfo.packageName;
                                if (p.equals(getPackageName())) continue;
                                al.put(p.replace('.', ','), ri.loadLabel(pm).toString());
                            }
                            Cloud.req("PUT", Cloud.url(c, "apps"), al.toString());
                        }
                    } catch (Exception e) {
                        // network or JSON problem: try again in 5 s
                    }
                }
            });
        }

        void padMode(boolean on) {
            ovIcon.setVisibility(on ? View.GONE : View.VISIBLE);
            ovHome.setVisibility(on ? View.GONE : View.VISIBLE);
            ovParent.setVisibility(on ? View.GONE : View.VISIBLE);
            ovPad.setVisibility(on ? View.VISIBLE : View.GONE);
            ovCancel.setVisibility(on ? View.VISIBLE : View.GONE);
        }

        void show(String title, String sub) {
            if (overlay != null) {
                ovTitle.setText(title);
                ovSub.setText(sub);
                return;
            }
            Context c = this;
            LinearLayout l = new LinearLayout(c);
            l.setOrientation(LinearLayout.VERTICAL);
            l.setGravity(Gravity.CENTER);
            l.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF0E1025, 0xFF2A1458}));
            l.setPadding(dp(c, 28), dp(c, 28), dp(c, 28), dp(c, 28));
            l.setClickable(true);

            TextView ic = tv(c, "\u23F3", 64, Color.WHITE, false);
            ic.setGravity(Gravity.CENTER);
            ovIcon = ic;
            ovTitle = tv(c, title, 26, TXT, true);
            ovTitle.setGravity(Gravity.CENTER);
            ovSub = tv(c, sub, 15, MUTED, false);
            ovSub.setGravity(Gravity.CENTER);
            ovHome = pill(c, "Go to Home screen", ACC, Color.WHITE, new View.OnClickListener() {
                public void onClick(View v) {
                    suppressUntil = System.currentTimeMillis() + 2500;
                    Intent i = new Intent(Intent.ACTION_MAIN);
                    i.addCategory(Intent.CATEGORY_HOME);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(i);
                    } catch (Exception e) {
                        // ignore
                    }
                    hide();
                }
            });
            ovParent = pill(c, "\uD83D\uDD11 Parent unlock", CARD2, TXT, new View.OnClickListener() {
                public void onClick(View v) {
                    padMode(true);
                }
            });
            ovPad = new PinPad(c, 56, new PinPad.Submit() {
                public String onSubmit(String pin) {
                    if (hash(pin).equals(pf.getString("pin", ""))) {
                        long until = System.currentTimeMillis() + 15 * 60000L;
                        pf.edit().putLong("unlock_until", until).putBoolean("force_lock", false).apply();
                        Cloud.putAsync(MonitorService.this, "settings/lock", "false");
                        Cloud.putAsync(MonitorService.this, "settings/bonus", String.valueOf(until));
                        hide();
                        return null;
                    }
                    return "Wrong PIN";
                }
            });
            ovCancel = tv(c, "Cancel", 15, MUTED, true);
            ovCancel.setPadding(dp(c, 20), dp(c, 10), dp(c, 20), dp(c, 10));
            ovCancel.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    padMode(false);
                }
            });

            l.addView(ic, lp(c, MW, WC, 0, 0, 0, 12));
            l.addView(ovTitle, lp(c, MW, WC, 0, 0, 0, 8));
            l.addView(ovSub, lp(c, MW, WC, 0, 0, 0, 28));
            l.addView(ovHome, lp(c, MW, WC, 0, 0, 0, 10));
            l.addView(ovParent, lp(c, MW, WC, 0, 0, 0, 0));
            l.addView(ovPad);
            l.addView(ovCancel);
            ovPad.setVisibility(View.GONE);
            ovCancel.setVisibility(View.GONE);

            int type = Build.VERSION.SDK_INT >= 26
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(MW, MW, type,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            try {
                wm.addView(l, p);
                overlay = l;
            } catch (Exception e) {
                overlay = null;
            }
        }

        void hide() {
            if (overlay != null) {
                try {
                    wm.removeView(overlay);
                } catch (Exception e) {
                    // ignore
                }
                overlay = null;
            }
        }
    }

    // ===================== BOOT RECEIVER =====================
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            if (prefs(c).getBoolean("onboarded", false) && !isParent(c)) startMonitor(c);
        }
    }
}
