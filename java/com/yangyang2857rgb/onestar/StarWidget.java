package com.yangyang2857rgb.onestar;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 拾光 v4.0「桌面小组件」：把今天的夜空放到桌面上。
 * 只读 SharedPreferences 里的原始串，文案计算交给纯逻辑 WidgetState，主线程零阻塞。
 */
public class StarWidget extends AppWidgetProvider {

    private static final SimpleDateFormat ISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    static void refresh(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            if (mgr == null) return;
            ComponentName cn = new ComponentName(ctx, StarWidget.class);
            int[] ids = mgr.getAppWidgetIds(cn);
            for (int id : ids) update(ctx, mgr, id);
        } catch (Throwable ignored) {
        }
    }

    private static void update(Context ctx, AppWidgetManager mgr, int id) {
        try {
            RemoteViews rv = build(ctx);
            mgr.updateAppWidget(id, rv);
        } catch (Throwable ignored) {
        }
    }

    private static RemoteViews build(Context ctx) {
        String raw = DaysStore.raw(ctx);
        String today = ISO.format(new Date());
        int total = WidgetState.total(raw);

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.star_widget);
        rv.setTextViewText(R.id.w_hint, WidgetState.hint(raw, today));
        rv.setTextViewText(R.id.w_line1, WidgetState.line1(raw, today, total));
        rv.setTextViewText(R.id.w_line2, WidgetState.line2(raw, today, total));

        Intent it = new Intent(ctx, MainActivity.class);
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, it, flags);
        rv.setOnClickPendingIntent(R.id.w_root, pi);
        return rv;
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    @Override
    public void onEnabled(Context ctx) {
        super.onEnabled(ctx);
        refresh(ctx);
    }
}
