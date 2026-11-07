package com.yangyang2857rgb.onestar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

/**
 * 拾光 v1.1 主界面：纯 Java 原生，单 View 全屏自绘。
 * 横屏只由界面上的原生「横屏」按钮切换，不随重力感应自动旋转。
 */
public class MainActivity extends Activity implements StarFieldView.Host,
        android.hardware.SensorEventListener {

    private StarFieldView view;
    private boolean backAnimating;

    // v3.0 摇一摇：加速度计无需任何权限
    private android.hardware.SensorManager sensorMgr;
    private android.hardware.Sensor accel;
    private long lastShakeAt;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setBackgroundDrawable(null);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        applyImmersive();

        view = new StarFieldView(this);
        setContentView(view);
        installInsetListener();
        installBackCallback();
        requestAdaptiveFrameRate();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        try {
            sensorMgr = (android.hardware.SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorMgr != null) accel = sensorMgr.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersive();
        StarWidget.refresh(this);
        if (sensorMgr != null && accel != null) {
            try {
                sensorMgr.registerListener(this, accel,
                        android.hardware.SensorManager.SENSOR_DELAY_GAME);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorMgr != null) {
            try {
                sensorMgr.unregisterListener(this);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onSensorChanged(android.hardware.SensorEvent e) {
        if (e == null || e.values == null || e.values.length < 3) return;
        float x = e.values[0], y = e.values[1], z = e.values[2];
        float mag = (float) Math.sqrt(x * x + y * y + z * z);
        float g = android.hardware.SensorManager.GRAVITY_EARTH;
        if (Math.abs(mag - g) < 3.1f) return;
        long now = System.nanoTime() / 1000000L;
        if (now - lastShakeAt < 1300) return;
        lastShakeAt = now;
        if (view != null) view.shakeTravel();
    }

    @Override
    public void onAccuracyChanged(android.hardware.Sensor s, int acc) {
    }

    /**
     * v5.0 边到边：把真实的 WindowInsets 安全区交给自绘层，
     * 刘海、挖孔、手势条都不会再压住按钮和面板。
     */
    private void installInsetListener() {
        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int l = 0, t = 0, r = 0, b = 0;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets i = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    l = i.left;
                    t = i.top;
                    r = i.right;
                    b = i.bottom;
                } else {
                    l = insets.getSystemWindowInsetLeft();
                    t = insets.getSystemWindowInsetTop();
                    r = insets.getSystemWindowInsetRight();
                    b = insets.getSystemWindowInsetBottom();
                }
                view.setSafeInsets(l, t, r, b);
                return insets;
            }
        });
        view.requestApplyInsets();
    }

    /**
     * v5.0 内存公平：Android 16/17 对后台内存更严格，收到系统信号就主动让位，
     * 而不是等着被整进程终止。
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (view == null) return;
        if (level >= TRIM_MEMORY_COMPLETE) {
            view.onTrim(2);
        } else if (level >= TRIM_MEMORY_RUNNING_MODERATE) {
            view.onTrim(1);
        }
    }

    /** v5.0 预测式返回（Android 13+）：先收面板，再退应用，返回手势有跟随动画。 */
    /**
     * v6.2 预测式返回（真·跟手版）。
     *
     * 系统的手势语义：从屏幕左/右边缘往内滑 = 返回，页面跟着手指走；
     * 滑到一半反方向推回边缘、再松手 = 取消，页面弹回原样。
     * 所以只注册 OnBackInvokedCallback（松手才跳）是不够的，
     * Android 14+ 必须用 OnBackAnimationCallback 接 onBackStarted /
     * onBackProgressed / onBackCancelled 三段回调，才能做到「手指到哪，页面退到哪」。
     * 注意：这三个回调跑在独立的 Handler 线程，必须 post 回主线程改 View。
     */
    private void installBackCallback() {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        new android.window.OnBackAnimationCallback() {
                            @Override
                            public void onBackStarted(android.window.BackEvent e) {
                                final float p = e == null ? 0f : e.getProgress();
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        backAnimating = true;
                                        applyBackProgress(p);
                                    }
                                });
                            }

                            @Override
                            public void onBackProgressed(android.window.BackEvent e) {
                                final float p = e == null ? 0f : e.getProgress();
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        applyBackProgress(p);
                                    }
                                });
                            }

                            @Override
                            public void onBackCancelled() {
                                // 手指推回边缘后松手：原路弹回去，什么也不关
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        animateBackToRest();
                                    }
                                });
                            }

                            @Override
                            public void onBackInvoked() {
                                runOnUiThread(new Runnable() {
                                    public void run() {
                                        // v6.4：提交时顺着方向继续滑出，不再先弹回再消失
                                        if (view != null) view.animateBackOut();
                                        onPredictiveBack();
                                    }
                                });
                            }
                        });
            } else {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        new android.window.OnBackInvokedCallback() {
                            @Override
                            public void onBackInvoked() {
                                onPredictiveBack();
                            }
                        });
            }
        } catch (Throwable ignored) {
        }
    }

    /** 跟手位移：进度 0→1，面板本身右移让位；星空保持不动。 */
    private void applyBackProgress(float p) {
        if (view == null) return;
        // v6.3：预测式返回只服务「星图面板」与「系统面板」本身。
        // 面板没开时不做任何位移，否则星空会像整页要被退掉。
        float k = Math.max(0f, Math.min(1f, p));
        if (!view.hasPanelOpen()) k = 0f;
        view.setBackProgress(k);
    }

    /** 松手（取消或提交）后 180ms 弹回原样。 */
    private void animateBackToRest() {
        if (view == null) return;
        backAnimating = false;
        view.animateBackRest();
    }

    private void onPredictiveBack() {
        if (view != null && view.closeCalendar()) return;
        if (view != null && view.closePanels()) return;
        if (isLandscapeNow()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        // 老系统（< 33）走这里；新系统由 OnBackInvokedCallback 接管
        if (view != null && view.closeCalendar()) return;
        if (view != null && view.closePanels()) return;
        if (isLandscapeNow()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            return;
        }
        super.onBackPressed();
    }

    /** v5.0 请求式帧率（Android 15+ 才有）：把自适应刷新率交给系统调度。 */
    private void requestAdaptiveFrameRate() {
        if (Build.VERSION.SDK_INT < 35) return;
        try {
            int compat;
            try {
                compat = android.view.Surface.class
                        .getField("FRAME_RATE_COMPATIBILITY_DEFAULT").getInt(null);
            } catch (Throwable t) {
                compat = 0;
            }
            java.lang.reflect.Method m = Window.class.getMethod("setFrameRate", float.class, int.class);
            m.invoke(getWindow(), 120f, Integer.valueOf(compat));
        } catch (Throwable ignored) {
        }
    }

    private void applyImmersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) applyImmersive();
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        applyImmersive();
        if (view != null) view.onOrientationChanged();
    }

    @Override
    public boolean isLandscapeNow() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    public void toggleOrientation() {
        if (isLandscapeNow()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
    }


    /** 长按详情里的「写一句」：原生输入框，深色，一句话，可清空。 */
    @Override
    public void editNote(final String date, String oldNote) {
        final float d = getResources().getDisplayMetrics().density;
        final EditText et = new EditText(this);
        et.setText(oldNote == null ? "" : oldNote);
        et.setSelection(et.getText().length());
        et.setHint("那天想留下的一句话");
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setTextSize(15);
        et.setTextColor(0xFFEEF3FF);
        et.setHintTextColor(0x668A97BD);
        et.setBackgroundColor(Color.TRANSPARENT);
        int pad = (int) (16 * d);
        et.setPadding(pad, pad, pad, pad);

        String title;
        try {
            int mo = Integer.parseInt(date.substring(5, 7));
            int da = Integer.parseInt(date.substring(8, 10));
            title = mo + "月" + da + "日 · 一句话";
        } catch (Exception e) {
            title = "一句话";
        }

        AlertDialog dlg = new AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("留下", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        if (view != null) view.setNote(date, et.getText().toString());
                    }
                })
                .setNegativeButton("算了", null)
                .create();
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface di) {
                applyImmersive();
            }
        });
        dlg.show();

        Window w = dlg.getWindow();
        if (w != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF0D1220);
            bg.setCornerRadius(18 * d);
            w.setBackgroundDrawable(bg);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    /** v4.0 给某一天的星星起名，上限 8 个字。 */
    @Override
    public void editName(final String date, String oldName) {
        final float d = getResources().getDisplayMetrics().density;
        final EditText et = new EditText(this);
        et.setText(oldName == null ? "" : oldName);
        et.setSelection(et.getText().length());
        et.setHint("给这颗星取个名字");
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(StarName.MAX)});
        et.setTextSize(15);
        et.setTextColor(0xFFFFF0D0);
        et.setHintTextColor(0x668A97BD);
        et.setBackgroundColor(Color.TRANSPARENT);
        int pad = (int) (16 * d);
        et.setPadding(pad, pad, pad, pad);

        String title;
        try {
            int mo = Integer.parseInt(date.substring(5, 7));
            int da = Integer.parseInt(date.substring(8, 10));
            title = mo + "月" + da + "日 · 星名";
        } catch (Exception e) {
            title = "星名";
        }

        AlertDialog dlg = new AlertDialog.Builder(this,
                android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("就叫它", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface di, int w) {
                        if (view != null) view.setStarName(date, et.getText().toString());
                    }
                })
                .setNegativeButton("算了", null)
                .create();
        dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
            public void onDismiss(DialogInterface di) {
                applyImmersive();
            }
        });
        dlg.show();

        Window w = dlg.getWindow();
        if (w != null) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF0D1220);
            bg.setCornerRadius(18 * d);
            w.setBackgroundDrawable(bg);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    /** v4.0 把年度星图存成一张长图。 */
    @Override
    public void savePoster() {
        Toast.makeText(this, "正在把夜空装进相册…", Toast.LENGTH_SHORT).show();
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final String path = PosterRenderer.export(app);
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this,
                                path == null ? "这里还没有星星，先点亮一颗吧"
                                        : "已存到相册：" + path,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "poster-export").start();
    }

    /** v4.0 数据变化后同步桌面小组件。 */
    @Override
    public void onDataChanged() {
        StarWidget.refresh(this);
    }

    /**
     * v6.3 补记：改用自绘星历。
     * v6.1 的原生 DatePickerDialog 与整体夜空视觉割裂、也无法显示「哪些天已经有星」，
     * 这里只负责打开，日期范围与已点亮的标记都由 StarFieldView 内部处理。
     */
    @Override
    public void pickBackfillDate() {
        if (view != null) view.openCalendar();
    }

    /** v6.1 补记 / 换色 / 抹掉后的轻提示。 */
    @Override
    public void toast(String msg) {
        if (msg == null || msg.length() == 0) return;
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

}
