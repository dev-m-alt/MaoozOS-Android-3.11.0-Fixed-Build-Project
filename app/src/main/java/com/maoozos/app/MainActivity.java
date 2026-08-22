package com.maoozos.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.graphics.Color;
import android.view.View;
import android.view.Window;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends android.app.Activity {
    private static final int REQ_NOTIFICATIONS = 7001;
    private static final int FILE_CHOOSER = 7002;
    private static final int SAVE_BACKUP = 7003;
    private String pendingBackupJson;
    private String pendingBackupMime = "application/json";
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationHelper.ensureChannel(this);
        configureWindow();
        buildWebView();
        if (savedInstanceState == null) webView.loadUrl("file:///android_asset/index.html");
        else webView.restoreState(savedInstanceState);
    }


    private void configureWindow() {
        Window w = getWindow();
        w.setStatusBarColor(Color.rgb(7, 17, 31));
        w.setNavigationBarColor(Color.rgb(7, 17, 31));
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(0);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(11,18,32));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setTextZoom(100);
        settings.setDefaultFontSize(16);
        settings.setDefaultFixedFontSize(13);
        settings.setOffscreenPreRaster(false);
        settings.setSupportMultipleWindows(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme()))) {
                    openExternal(u);
                    return true;
                }
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    Uri u = Uri.parse(url);
                    if ("http".equalsIgnoreCase(u.getScheme()) || "https".equalsIgnoreCase(u.getScheme())) {
                        openExternal(u);
                        return true;
                    }
                } catch (Exception ignored) {}
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    Intent i = params.createIntent();
                    startActivityForResult(i, FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "No file picker is available.", Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

        webView.addJavascriptInterface(new AndroidBridge(this), "MaoozAndroid");
    }

    private void openExternal(Uri uri) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No browser is available for this link.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            webView.evaluateJavascript("(function(){const d=document.getElementById('drawer');const m=document.getElementById('modalRoot');const t=document.getElementById('tourBackdrop');if(d&&d.classList.contains('open')){closeDrawer();return 'drawer';}if(m&&m.classList.contains('open')){closeModal();return 'modal';}if(t&&t.classList.contains('open')){closeTour();return 'tour';}return '';})()", value -> {
                String handled = value == null ? "" : value.replace("\"", "").trim();
                if ("drawer".equals(handled) || "modal".equals(handled) || "tour".equals(handled)) return;
                if (webView != null && webView.canGoBack()) webView.goBack();
                else MainActivity.super.onBackPressed();
            });
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.postDelayed(() -> webView.evaluateJavascript(
                    "(function(){ if(typeof syncNativeReminders==='function'){ try{syncNativeReminders();}catch(e){} } if(typeof refreshNotificationState==='function'){ try{refreshNotificationState();}catch(e){} } })()", null), 250);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                NotificationHelper.ensureChannel(this);
                Toast.makeText(this, "MaoozOS notifications enabled.", Toast.LENGTH_SHORT).show();
                if (webView != null) webView.postDelayed(() -> webView.evaluateJavascript(
                        "(function(){if(typeof syncNativeReminders==='function'){try{syncNativeReminders();}catch(e){}}})()", null), 150);
            } else {
                Toast.makeText(this, "Notifications are blocked. Enable them in Android settings to receive class alerts.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER && fileCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    results = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(results);
            fileCallback = null;
            return;
        }
        if (requestCode == SAVE_BACKUP) {
            try {
                if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingBackupJson != null) {
                    Uri uri = data.getData();
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new IllegalStateException("The selected destination could not be opened.");
                        out.write(pendingBackupJson.getBytes(StandardCharsets.UTF_8));
                        out.flush();
                    }
                    Toast.makeText(this, "MaoozOS backup exported successfully.", Toast.LENGTH_LONG).show();
                } else if (resultCode == RESULT_CANCELED) {
                    Toast.makeText(this, "Backup export cancelled. Your data was not changed.", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Backup could not be saved: " + (e.getMessage() == null ? "Unknown error." : e.getMessage()), Toast.LENGTH_LONG).show();
            } finally {
                pendingBackupJson = null;
            }
        }
    }

    public class AndroidBridge {
        private final Context context;
        AndroidBridge(Context context) { this.context = context; }

        @JavascriptInterface public boolean isAndroid() { return true; }

        @JavascriptInterface public void saveBackupFile(String json, String suggestedName) {
            runOnUiThread(() -> {
                try {
                    if (json == null || json.trim().isEmpty()) throw new IllegalArgumentException("Backup data is empty.");
                    // Validate that we received a real JSON object before opening the save dialog.
                    JSONObject check = new JSONObject(json);
                    if (!"MaoozOS".equals(check.optString("app"))) throw new IllegalArgumentException("This is not a MaoozOS backup.");
                    pendingBackupJson = json;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("application/json");
                    i.putExtra(Intent.EXTRA_TITLE, (suggestedName == null || suggestedName.trim().isEmpty()) ? "MaoozOS-full-backup.json" : suggestedName);
                    startActivityForResult(i, SAVE_BACKUP);
                } catch (Exception e) {
                    Toast.makeText(context, "Backup export could not start: " + (e.getMessage() == null ? "Unknown error." : e.getMessage()), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void requestNotifications() {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
                        return;
                    }
                    NotificationHelper.ensureChannel(context);
                    Toast.makeText(context, "MaoozOS Android notifications are enabled.", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(context, "Notifications could not be enabled. In-app alerts are still available.", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void postNotification(String title, String message) {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Notification permission is not enabled. In-app alert shown instead.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    NotificationHelper.ensureChannel(context);
                    Intent open = new Intent(context, MainActivity.class);
                    open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    int notificationId = (int)(System.currentTimeMillis() & 0x7fffffff);
                    PendingIntentHolder.send(context, title == null ? "MaoozOS" : title, message == null ? "MaoozOS reminder" : message, open, notificationId);
                } catch (Exception e) {
                    Toast.makeText(context, "Native notification failed. In-app alert remains available.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface public void openNotificationSettings() {
            runOnUiThread(() -> {
                Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            });
        }

        @JavascriptInterface public boolean canScheduleExactAlarms() {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return alarm != null && alarm.canScheduleExactAlarms();
        }

        @JavascriptInterface public void openExactAlarmSettings() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    try {
                        Intent i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(context, "Android could not open Alarms & reminders settings. Open them from Settings → Apps → MaoozOS → Alarms & reminders.", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(context, "Exact-alarm access is not required on this Android version.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface public void testNotification() {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Notifications are not enabled. Tap Enable first.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    NotificationHelper.ensureChannel(context);
                    Intent open = new Intent(context, MainActivity.class);
                    PendingIntentHolder.send(context, "MaoozOS test notification", "Native Android notifications are working.", open);
                } catch (Exception e) {
                    Toast.makeText(context, "Test notification could not be shown: " + (e.getMessage() == null ? "Check Android notification settings." : e.getMessage()), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void scheduleTestReminder(long delayMs) {
            try {
                long safeDelay = Math.max(5_000L, Math.min(delayMs, 24L * 60L * 60L * 1000L));
                long fireAt = System.currentTimeMillis() + safeDelay;
                ReminderReceiver.schedule(context, "__test__", fireAt, "MaoozOS test reminder", "This notification was scheduled by MaoozOS.", 0L, 0L, false, null, null, 0L, 0L);
            } catch (Exception e) {
                Toast.makeText(context, "Test reminder could not be scheduled: " + (e.getMessage() == null ? "Unknown error." : e.getMessage()), Toast.LENGTH_LONG).show();
            }
        }

        @JavascriptInterface public boolean syncReminders(String json) {
            try {
                JSONArray jobs = new JSONArray(json == null ? "[]" : json);
                android.content.SharedPreferences prefs = getSharedPreferences("maoozos_native_reminders", MODE_PRIVATE);
                java.util.Map<String, ?> all = prefs.getAll();
                java.util.HashMap<String, JSONObject> desired = new java.util.HashMap<>();
                long now = System.currentTimeMillis();
                for (int i = 0; i < jobs.length(); i++) {
                    JSONObject o = jobs.getJSONObject(i);
                    String id = o.optString("id", "").trim();
                    if (id.isEmpty()) continue;
                    long classAt = o.optLong("classAt", 0L);
                    long leadMinutes = Math.max(0L, o.optLong("leadMinutes", 0L));
                    long fireAt = o.optLong("fireAt", 0L);
                    if (classAt > 0L) fireAt = classAt - (leadMinutes * 60_000L);
                    if (fireAt > now) {
                        o.put("fireAt", fireAt);
                        o.put("classAt", classAt);
                        o.put("leadMinutes", leadMinutes);
                        desired.put(id, o);
                    }
                }
                // Remove alarms that no longer belong to the current timetable without
                // cancelling alarms whose scheduled fire time has not changed.
                for (String key : all.keySet()) {
                    if (!key.startsWith("record_")) continue;
                    String id = key.substring("record_".length());
                    Object raw = all.get(key);
                    long oldFire = -1L;
                    if (raw instanceof String) {
                        try { oldFire = new JSONObject((String) raw).optLong("fireAt", -1L); } catch (Exception ignored) {}
                    }
                    JSONObject wanted = desired.get(id);
                    if (wanted == null || wanted.optLong("fireAt", -1L) != oldFire) {
                        ReminderReceiver.cancel(context, id);
                    }
                }
                // Schedule only new/changed jobs. Unchanged alarms stay intact, avoiding
                // a race where a reminder can be cancelled moments before it fires.
                for (JSONObject o : desired.values()) {
                    String id = o.getString("id");
                    String raw = prefs.getString("record_" + id, null);
                    long oldFire = -1L;
                    if (raw != null) { try { oldFire = new JSONObject((String) raw).optLong("fireAt", -1L); } catch (Exception ignored) {} }
                    long fireAt = o.getLong("fireAt");
                    if (oldFire == fireAt) continue;
                    ReminderReceiver.schedule(context, id, fireAt, o.optString("title", "MaoozOS reminder"), o.optString("message", "Upcoming reminder"), o.optLong("periodMs", 0L), o.optLong("endAt", 0L), o.optBoolean("quietHours", false), o.optString("quietStart", null), o.optString("quietEnd", null), o.optLong("classAt", 0L), o.optLong("leadMinutes", 0L));
                }
                return true;
            } catch (Exception e) {
                Toast.makeText(context, "Reminder schedule could not be updated. Your timetable remains saved.", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
    }

    static final class PendingIntentHolder {
        static void send(Context context, String title, String message, Intent open) {
            send(context, title, message, open, 99991);
        }

        static void send(Context context, String title, String message, Intent open, int notificationId) {
            NotificationHelper.ensureChannel(context);
            android.app.PendingIntent content = android.app.PendingIntent.getActivity(
                    context, safeNotificationCode(notificationId), open,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );
            android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(context, NotificationHelper.CHANNEL_ID)
                    : new android.app.Notification.Builder(context);
            builder.setSmallIcon(R.drawable.ic_maoozos)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(message))
                    .setContentIntent(content)
                    .setAutoCancel(true)
                    .setPriority(android.app.Notification.PRIORITY_HIGH);
            android.app.NotificationManager manager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(notificationId, builder.build());
        }

        private static int safeNotificationCode(int value) {
            return value == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(value);
        }
    }
}
