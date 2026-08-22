package com.maoozos.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.provider.Settings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String PREFS = "maoozos_native_reminders";
    private static final String RECORD = "record_";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationHelper.ensureChannel(context);

        String id = intent.getStringExtra("id");
        String title = intent.getStringExtra("title");
        String message = intent.getStringExtra("message");
        long periodMs = intent.getLongExtra("periodMs", 0L);
        long classAt = intent.getLongExtra("classAt", 0L);
        long leadMinutes = intent.getLongExtra("leadMinutes", 0L);
        long endAt = intent.getLongExtra("endAt", 0L);
        boolean quietHours = intent.getBooleanExtra("quietHours", false);
        String quietStart = intent.getStringExtra("quietStart");
        String quietEnd = intent.getStringExtra("quietEnd");

        if (id == null) return;

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(
                context,
                safeRequestCode(id),
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String safeTitle = title == null ? "MaoozOS reminder" : title;
        String safeMessage = message == null ? "You have an upcoming reminder." : message;
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, NotificationHelper.CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_maoozos)
                .setContentTitle(safeTitle)
                .setContentText(safeMessage)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(safeMessage))
                .setAutoCancel(true)
                .setContentIntent(content)
                .setPriority(android.app.Notification.PRIORITY_HIGH)
                .setCategory(android.app.Notification.CATEGORY_REMINDER)
                .setOnlyAlertOnce(false);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!inQuietHours(quietHours, quietStart, quietEnd)) {
            if (manager != null) manager.notify(safeRequestCode(id), builder.build());
        }

        // Schedule the next weekly occurrence using the original class time + lead time.
        if (periodMs > 0L && classAt > 0L) {
            long nextClassAt = classAt + periodMs;
            if (endAt <= 0L || nextClassAt <= endAt) {
                long nextFireAt = nextClassAt - Math.max(0L, leadMinutes) * 60_000L;
                if (nextFireAt > System.currentTimeMillis()) {
                    schedule(context, id, nextFireAt, title, message, periodMs, endAt,
                            quietHours, quietStart, quietEnd, nextClassAt, leadMinutes);
                }
            } else {
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(RECORD + id).apply();
            }
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(RECORD + id).apply();
        }
    }

    public static void schedule(Context context, String id, long fireAt, String title, String message,
                                 long periodMs, long endAt) {
        schedule(context, id, fireAt, title, message, periodMs, endAt,
                false, null, null, 0L, 0L);
    }

    public static void schedule(Context context, String id, long fireAt, String title, String message,
                                 long periodMs, long endAt, boolean quietHours,
                                 String quietStart, String quietEnd) {
        schedule(context, id, fireAt, title, message, periodMs, endAt,
                quietHours, quietStart, quietEnd, 0L, 0L);
    }

    public static void schedule(Context context, String id, long fireAt, String title, String message,
                                 long periodMs, long endAt, boolean quietHours,
                                 String quietStart, String quietEnd, long classAt, long leadMinutes) {
        // Native layer is authoritative: when classAt/leadMinutes are supplied,
        // calculate the reminder time here so WebView timing cannot accidentally override it.
        if (classAt > 0L) {
            fireAt = classAt - (Math.max(0L, leadMinutes) * 60_000L);
        }
        if (fireAt <= System.currentTimeMillis()) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("id", id);
        intent.putExtra("title", title);
        intent.putExtra("message", message);
        intent.putExtra("periodMs", periodMs);
        intent.putExtra("classAt", classAt);
        intent.putExtra("leadMinutes", leadMinutes);
        intent.putExtra("endAt", endAt);
        intent.putExtra("quietHours", quietHours);
        intent.putExtra("quietStart", quietStart);
        intent.putExtra("quietEnd", quietEnd);

        int requestCode = safeRequestCode(id);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Exact timing is user-configurable. Keep the reminder scheduled even when
                // the special access is not granted; the UI can surface that access is needed.
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending);
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, fireAt, pending);
        }

        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("fireAt", fireAt);
            obj.put("classAt", classAt);
            obj.put("leadMinutes", leadMinutes);
            obj.put("title", title);
            obj.put("message", message);
            obj.put("periodMs", periodMs);
            obj.put("endAt", endAt);
            obj.put("quietHours", quietHours);
            obj.put("quietStart", quietStart);
            obj.put("quietEnd", quietEnd);
        } catch (Exception ignored) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(RECORD + id, obj.toString()).apply();
    }

    private static int safeRequestCode(String id) {
        if (id == null) return 1;
        int h = id.hashCode();
        return h == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(h);
    }

    private static boolean inQuietHours(boolean enabled, String start, String end) {
        if (!enabled || start == null || end == null || start.length() != 5 || end.length() != 5) return false;
        try {
            java.util.Calendar c = java.util.Calendar.getInstance();
            int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
            int s = Integer.parseInt(start.substring(0, 2)) * 60 + Integer.parseInt(start.substring(3));
            int e = Integer.parseInt(end.substring(0, 2)) * 60 + Integer.parseInt(end.substring(3));
            return s == e ? false : (s < e ? now >= s && now < e : now >= s || now < e);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void cancel(Context context, String id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                safeRequestCode(id),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm != null) alarm.cancel(pending);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(RECORD + id).apply();
    }
}
