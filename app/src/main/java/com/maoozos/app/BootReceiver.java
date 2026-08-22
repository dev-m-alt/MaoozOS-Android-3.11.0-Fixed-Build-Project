package com.maoozos.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.util.Map;

public class BootReceiver extends BroadcastReceiver {
    private static final String PREFS = "maoozos_native_reminders";
    private static final String RECORD = "record_";

    @Override
    public void onReceive(Context context, Intent intent) {
        Map<String, ?> all = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (!entry.getKey().startsWith(RECORD) || !(entry.getValue() instanceof String)) continue;
            try {
                JSONObject o = new JSONObject((String) entry.getValue());
                String id = o.getString("id");
                long fireAt = o.getLong("fireAt");
                long periodMs = o.optLong("periodMs", 0L);
                long classAt = o.optLong("classAt", 0L);
                long leadMinutes = o.optLong("leadMinutes", 0L);
                long endAt = o.optLong("endAt", 0L);
                boolean quietHours = o.optBoolean("quietHours", false);
                String quietStart = o.optString("quietStart", null);
                String quietEnd = o.optString("quietEnd", null);
                long now = System.currentTimeMillis();
                if (fireAt <= now) {
                    if (periodMs > 0L && classAt > 0L) {
                        while (fireAt <= now) {
                            classAt += periodMs;
                            fireAt = classAt - Math.max(0L, leadMinutes) * 60_000L;
                            if (endAt > 0L && classAt > endAt) break;
                        }
                    } else {
                        ReminderReceiver.cancel(context, id);
                        continue;
                    }
                }
                if (endAt > 0 && (fireAt > endAt || classAt > endAt)) {
                    ReminderReceiver.cancel(context, id);
                    continue;
                }
                ReminderReceiver.schedule(context, id, fireAt, o.optString("title", "MaoozOS reminder"), o.optString("message", "Upcoming reminder"), periodMs, endAt, quietHours, quietStart, quietEnd, classAt, leadMinutes);
            } catch (Exception ignored) {}
        }
    }
}
