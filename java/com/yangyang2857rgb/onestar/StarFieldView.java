package com.yangyang2857rgb.onestar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 拾光 v4.2「柔光」——纯 Java 原生星图视图，全部界面 Canvas 自绘，无 WebView。
 *
 * v4.2：柔光玻璃视觉（小米澎湃 OS4 同款）——图标玻璃砖、胶囊按钮、
 * 星图面板、详情卡统一改为柔光玻璃：竖向柔光 + 左上光斑 + 底部内阴影 +
 * 顶沿厚度高光 + 多描边外柔光；按钮增加按压回弹高光。
 *
 * v4.1：全新深空玻璃图标（自适应/圆形/全密度）；页眉暖金细线、要点提示轮播、
 * 回放收束小结、胶囊按钮内高光。
 *
 * v2.0 新灵魂：
 * 1. 点亮时选一种心情色（5 色），星不再只是白点，而是那天的颜色；
 * 2. 同月的星自动连成星座并得到算法命名（如「十一月 · 三星长夜」）；
 * 3. 星图面板：12 个月情绪色带柱 + 星语 + 主导心情；
 * 4. 星光音：AudioTrack 合成的钟磬，音高随心情变化。
 */
public class StarFieldView extends View {

    interface Host {
        void toggleOrientation();

        boolean isLandscapeNow();

        /** 打开原生输入框，编辑某一天的备注（oldNote 可为空串）。 */
        void editNote(String date, String oldNote);

        /** 打开原生输入框，给某一天起的星名（oldName 可为空串）。 */
        void editName(String date, String oldName);

        /** 导出年度星图长图，由宿主完成落盘与提示。 */
        void savePoster();

        /** 数据有变更，宿主据此刷新桌面小组件。 */
        void onDataChanged();

        /** v6.1 打开原生日期选择器，补记过去的一天。 */
        void pickBackfillDate();

        /** v6.1 轻提示（补记、换色、抹掉的反馈）。 */
        void toast(String msg);
    }

    // ---------- 配色 ----------
    private static final int C_TEXT = 0xFFEEF3FF;
    private static final int C_DIM = 0xFF8A97BD;
    private static final int C_WARM = 0xFFFFF0D0;
    private static final int C_COOL = 0xFFDFE9FF;
    private static final int C_BG = 0xFF04050D;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF tmpRect = new RectF();

    private float dp;
    private int W, H;
    private boolean land;

    private float padL, padT, padB;
    private RectF skyR = new RectF();
    private RectF btnOrient = new RectF();
    private RectF btnBook = new RectF();
    private RectF btnSys = new RectF();
    private float colW;

    // v5.0 系统适配：安全区（刘海 / 手势条）+ 内存降载档位
    private float safeL, safeT, safeR, safeB;
    private boolean sysOpen;
    private int trimLevel;
    private boolean memLow;

    // 数据
    private final List<String> days = new ArrayList<String>();
    private final Map<String, Integer> mood = new HashMap<String, Integer>();
    private final Map<String, String> notes = new HashMap<String, String>();
    /** v4.0 星名：date -> 自定义名字 */
    private final Map<String, String> names = new HashMap<String, String>();
    private String today;
    private boolean litToday;
    private int streak, spanDays, total, bestStreak;

    // 背景星
    private static final int BG_N = 190;
    private final float[] bgU = new float[BG_N];
    private final float[] bgV = new float[BG_N];
    private final float[] bgR = new float[BG_N];
    private final float[] bgP = new float[BG_N];
    private final float[] bgA = new float[BG_N];

    // 用户星
    private final List<float[]> starPts = new ArrayList<float[]>(); // x,y,r,moodIndex
    private final List<String> starDates = new ArrayList<String>();

    // 动画
    private final List<float[]> pulses = new ArrayList<float[]>();
    private final List<float[]> meteors = new ArrayList<float[]>();
    private float flyX0, flyY0, flyX1, flyY1;
    private long flyT0 = -1;
    private boolean flying;
    private int flyMood;
    private static final long FLY_MS = 1150;

    // 心情选择环
    private boolean picking;
    private float pickCx, pickCy;
    private final float[][] pickPt = new float[Constellation.MOOD_N][2];
    private long pickT0;
    private static final long PICK_TIMEOUT = 4200;
    private static final float PICK_R = 76f;

    private boolean panelOpen, panelVisibleAnim;
    private float panelT;
    private boolean mapMode = true;

    private long t0 = System.nanoTime() / 1000000L;
    private long nextMeteorAt = 5000;

    private Bitmap glowBmp;
    private final PorterDuffColorFilter[] filters = new PorterDuffColorFilter[Constellation.MOOD_N];
    private final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private float downX, downY;
    private long downTime;
    private boolean downActive, downMoved, longFired;
    private static final long LONG_MS = 380;
    private static final float LONG_SLOP = 14f;

    // 长按详情浮层
    private boolean peekOpen;
    private String peekDate;
    private float peekT;
    private final RectF peekCard = new RectF();
    private final RectF peekNoteBtn = new RectF();
    private final RectF peekNameBtn = new RectF();
    private final RectF peekDelBtn = new RectF();
    private final List<String> wrapBuf = new ArrayList<String>();

    // v6.1 星册编辑：补记目标日 / 删除二次确认
    private String pendingDate;
    private boolean peekDelArmed;

    // v6.2 补记标记：哪些天是事后补上的（年度长图上会单独标出）
    private final java.util.Set<String> backfilled = new java.util.HashSet<String>();

    // v6.2 抹掉后的一次性撤销：5 秒内可以救回来
    private String undoDate;
    private int undoMood;
    private String undoNote, undoName;
    private boolean undoWasBackfill;
    private long undoUntil;
    private float undoT;
    private final RectF undoRect = new RectF();
    private static final long UNDO_MS = 5000;
    private final Runnable undoExpire = new Runnable() {
        @Override
        public void run() {
            if (undoDate != null) invalidate();
        }
    };

    // v6.3 预测式返回：进度只推动面板本身，星空不跟着整屏后退
    private float backP;
    private android.animation.ValueAnimator backAnim;
    private final Path backClip = new Path();   // 返回跟手时面板的圆角裁剪路径
    private String verCache;   // 右下角版本号：只读一次包信息，避免每帧走 PackageManager

    // ---------- v6.3 补记选日：自绘星历（替掉古早的原生 DatePickerDialog） ----------
    private boolean calOpen;
    private float calT;
    private int calYear, calMonth;              // calMonth 取值 0-11
    private int calPressed = -1;                // -1 无 · -2 关闭 · -3 上一月 · -4 下一月 · >=0 日格
    private float calCell;
    private final RectF calCard = new RectF();
    private final RectF calPrevBtn = new RectF();
    private final RectF calNextBtn = new RectF();
    private final RectF calCloseBtn = new RectF();
    private final RectF[] calCells = new RectF[42];
    private final RectF[] calRings = new RectF[42];
    private final String[] calDates = new String[42];
    private final boolean[] calUsable = new boolean[42];
    private final boolean[] calLit = new boolean[42];
    private final boolean[] calBack = new boolean[42];
    private boolean calDirty = true;
    private int calLastW, calLastH;

    {
        for (int i = 0; i < 42; i++) {
            calCells[i] = new RectF();
            calRings[i] = new RectF();
        }
    }

    private RectF closeBtnRect = new RectF();

    // v4.0 星图面板底部：补记 / 回放 / 存图 / 收起
    private final RectF panelReplayBtn = new RectF();
    private final RectF panelSaveBtn = new RectF();
    private final RectF panelBackfillBtn = new RectF();

    // v4.0 星轨回放
    private boolean replayOn, replayDragging;
    private float replayProg;
    private long replayT0;
    private int replayIdx = -1;
    private final RectF replayBar = new RectF();

    // v3.0 摇一摇回望：一道流星飞向过去某一天
    private int travelIdx = -1;
    private long travelT0;
    private float travelSX, travelSY;
    private final Random rnd = new Random();

    // v3.0 星月琴：点击星图上的月份，按月内顺序奏乐
    private final RectF[] barRects = new RectF[12];
    private int panelYear = 2026;
    private int playMonth = -1;
    private int playStep = 0;
    private int playTotal = 0;
    private String playDate;
    private final Handler uiH = new Handler(Looper.getMainLooper());
    private final List<Runnable> playRuns = new ArrayList<Runnable>();

    public StarFieldView(Context c) {
        super(c);
        dp = getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_HARDWARE, null);
        buildGlow();
        for (int i = 0; i < Constellation.MOOD_N; i++) {
            filters[i] = new PorterDuffColorFilter(Constellation.MOOD_GLOW[i], PorterDuff.Mode.SRC_IN);
        }
        for (int i = 0; i < BG_N; i++) {
            bgU[i] = hash01("bgu" + i);
            bgV[i] = hash01("bgv" + i);
            bgR[i] = 0.35f + hash01("bgr" + i) * 0.95f;
            bgP[i] = hash01("bgp" + i) * 6.2831f;
            bgA[i] = 0.18f + hash01("bga" + i) * 0.45f;
        }
        today = iso.format(new Date());
        refreshData();
        nextMeteorAt = 6000 + (long) (hash01("m" + today) * 7000);
        // v5.0 内存公平：低内存机开屏即降到轻载档
        if (Compat.shouldTrim(getContext())) onTrim(1);
    }

    private void buildGlow() {
        int s = 128;
        glowBmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(glowBmp);
        Paint gp = new Paint(Paint.ANTI_ALIAS_FLAG);
        gp.setShader(new RadialGradient(s / 2f, s / 2f, s / 2f,
                new int[]{0xFFFFFFFF, 0x9AFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.22f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(s / 2f, s / 2f, s / 2f, gp);
    }

    // ---------- 数据 ----------
    void refreshData() {
        DaysStore.load(getContext(), days, mood, notes, names, backfilled);
        Collections.sort(days);
        today = iso.format(new Date());
        litToday = days.contains(today);
        total = days.size();
        streak = Constellation.streakOf(days, today);
        spanDays = calcSpan();
        bestStreak = calcBestStreak();
        rebuildStars();
        invalidate();
    }

    private int calcSpan() {
        if (days.isEmpty()) return 0;
        long d = (Constellation.parseDate(days.get(days.size() - 1))
                - Constellation.parseDate(days.get(0))) / 86400000L;
        return (int) d + 1;
    }

    private int calcBestStreak() {
        if (days.isEmpty()) return 0;
        int best = 1, cur = 1;
        for (int i = 1; i < days.size(); i++) {
            long diff = (Constellation.parseDate(days.get(i))
                    - Constellation.parseDate(days.get(i - 1))) / 86400000L;
            if (diff == 1) cur++;
            else cur = 1;
            if (cur > best) best = cur;
        }
        return best;
    }

    private void rebuildStars() {
        starPts.clear();
        starDates.clear();
        starDates.addAll(days);
        for (String d : starDates) {
            float u = 0.07f + 0.86f * hash01("u" + d);
            float v = 0.12f + 0.74f * hash01("v" + d);
            float r = 1.9f + hash01("r" + d) * 1.6f;
            Integer m = mood.get(d);
            starPts.add(new float[]{u, v, r, m == null ? 0 : m});
        }
    }

    static float hash01(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return ((h >>> 12) & 0xFFFFFF) / 16777215f;
    }

    // ---------- 布局 ----------
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        W = w;
        H = h;
        layoutAll();
    }

    private void layoutAll() {
        if (W == 0 || H == 0) return;
        land = W > H;
        // v5.0 边到边：把刘海与手势条安全区算进内容边距，按钮不再压住导航条
        padL = 26 * dp + safeL;
        padT = 30 * dp + safeT;
        padB = 26 * dp + safeB;

        float bw = 78 * dp, bh = 34 * dp, gap = 10 * dp;
        btnOrient.set(W - padL - bw, padT - 6 * dp, W - padL, padT - 6 * dp + bh);
        btnBook.set(btnOrient.left - gap - 60 * dp, btnOrient.top,
                btnOrient.left - gap, btnOrient.bottom);
        btnSys.set(btnBook.left - gap - 60 * dp, btnOrient.top,
                btnBook.left - gap, btnOrient.bottom);

        if (land) {
            colW = Math.max(230 * dp, W * 0.34f);
            colW = Math.min(colW, W * 0.42f);
            skyR.set(colW, 0, W, H);
        } else {
            colW = W;
            skyR.set(0, 0, W, H);
        }
    }

    void onOrientationChanged() {
        layoutAll();
        picking = false;
        invalidate();
    }

    /** v5.0：宿主把 WindowInsets 安全区传进来（边到边 / 刘海 / 手势条）。 */
    void setSafeInsets(int l, int t, int r, int b) {
        float fl = l / dp, ft = t / dp, fr = r / dp, fb = b / dp;
        if (Math.abs(fl - safeL) < 0.5f && Math.abs(ft - safeT) < 0.5f
                && Math.abs(fr - safeR) < 0.5f && Math.abs(fb - safeB) < 0.5f) return;
        safeL = fl;
        safeT = ft;
        safeR = fr;
        safeB = fb;
        layoutAll();
        invalidate();
    }

    /**
     * v5.0 内存公平：系统回收内存时主动让位。
     * level 越大让得越多：1 释放着色器与光晕位图，2 再关掉氛围流星。
     */
    void onTrim(int level) {
        trimLevel = Math.max(trimLevel, Math.min(level, 2));
        if (trimLevel >= 1) {
            glassCache.clear();
            sheenCache.clear();
        }
        if (trimLevel >= 2 && glowBmp != null && !glowBmp.isRecycled()) {
            glowBmp.recycle();
            glowBmp = null;
        }
        invalidate();
    }

    private void releaseCaches() {
        glassCache.clear();
        sheenCache.clear();
        if (glowBmp != null && !glowBmp.isRecycled()) {
            glowBmp.recycle();
            glowBmp = null;
        }
    }

    boolean isSysOpen() {
        return sysOpen;
    }

    /** v6.3：预测式返回只认「星图面板」与「系统面板」两层，其它视图不参与。 */
    boolean hasPanelOpen() {
        return sysOpen || panelOpen;
    }

    boolean closePanels() {
        boolean had = sysOpen || panelOpen;
        sysOpen = false;
        panelOpen = false;
        // v6.4：不在这里硬复位 backP，让提交返回时面板先顺着方向滑出再淡掉；
        // backP 由 onDraw 在面板彻底消失后归零（见 panelT <= 0.001f 分支）
        if (had) {
            cancelPlay();
            invalidate();
        }
        return had;
    }

    float ux(float u) {
        return skyR.left + u * skyR.width();
    }

    float vy(float v) {
        return skyR.top + v * skyR.height();
    }

    // ---------- 绘制 ----------
    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime() / 1000000L - t0;

        if (picking && now - pickT0 > PICK_TIMEOUT) picking = false;

        drawBackground(c);
        drawBgStars(c, now);
        if (mapMode) drawConstellations(c, now);
        drawUserStars(c, now);
        drawPulses(c, now);
        drawFlying(c, now);
        advanceTravel(c, now);
        drawMeteors(c, now);

        drawHud(c, now);
        drawButtons(c);
        if (picking) drawPicking(c, now);

        if (downActive && !longFired && !downMoved && !picking && !panelOpen && !sysOpen
                && System.nanoTime() / 1000000L - downTime > LONG_MS) {
            longFired = true;
            boolean hitStar = openPeekAt(downX, downY);
            if (!hitStar) {
                // v6.2 长按空白：跳过星图面板，直接进入补记
                Host dh = host();
                boolean onChrome = btnBook.contains(downX, downY)
                        || btnSys.contains(downX, downY)
                        || btnOrient.contains(downX, downY)
                        || closeBtnRect.contains(downX, downY)
                        || undoRect.contains(downX, downY);
                if (dh != null && !onChrome) {
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    post(new Runnable() {
                        @Override
                        public void run() {
                            dh.pickBackfillDate();
                        }
                    });
                }
            }
        }

        float target = (panelOpen || sysOpen) ? 1f : 0f;
        panelT += (target - panelT) * 0.28f;
        if (Math.abs(target - panelT) < 0.002f) panelT = target;
        if (picking) panelT = 0f;
        if (panelT <= 0.001f && backP > 0f) {
            backP = 0f;                       // 面板已彻底淡出：跟手进度归零，不影响下次打开
            if (backAnim != null) backAnim.cancel();
        }
        if (!picking && panelT > 0.001f) {
            // v6.3.1：对齐系统预测式返回的卡片式表现——
            // 遮罩单独变淡，面板整体「缩小 · 圆角 · 右移」，后面的星空原地不动
            float ease = 1 - (float) Math.pow(1 - panelT, 3);
            drawPanelScrim(c, ease * (1f - 0.55f * backP));
            c.save();
            float bk = backP;
            if (bk > 0.001f) {
                float sc = 1f - 0.10f * bk;                       // 最多缩到 0.90
                float dx = W * 0.22f * bk + W * 0.5f * (1f - sc);  // 右移，同时保持居中补偿
                float dy = H * 0.5f * (1f - sc);
                c.translate(dx, dy);
                c.scale(sc, sc);
                float rr = 16 * dp * bk;                          // 圆角最多 16dp
                if (rr > 0.5f) {
                    backClip.reset();
                    backClip.addRoundRect(0, 0, W, H, rr, rr, Path.Direction.CW);
                    c.clipPath(backClip);
                }
            }
            if (sysOpen) drawSystemPanel(c);
            else drawPanel(c);
            c.restore();
        }

        float peekTarget = peekOpen ? 1f : 0f;
        peekT += (peekTarget - peekT) * 0.3f;
        if (Math.abs(peekTarget - peekT) < 0.002f) peekT = peekTarget;
        if (!peekOpen && peekT <= 0.002f) peekDate = null;
        if (peekT > 0.001f) drawPeek(c);
        drawUndo(c, now);

        if (replayOn) {
            if (!replayDragging) {
                replayProg = Replay.progressOf(now - replayT0, Replay.durationOf(starPts.size()));
            }
            int idx = Replay.indexAt(replayProg, starPts.size());
            if (idx != replayIdx) {
                replayIdx = idx;
                if (idx >= 0 && idx < starDates.size() && (idx % 3 == 0 || idx == starDates.size() - 1)) {
                    Integer mv = mood.get(starDates.get(idx));
                    ChimePlayer.play(mv == null ? 0 : mv);
                }
            }
            drawReplay(c);
        }

        if (trimLevel < 2) ambientMeteor(now);
        drawCalendar(c);
        postInvalidateOnAnimation();
    }

    // ---------- v4.0 星轨回放 ----------
    private void startReplay() {
        if (starPts.size() < 2) {
            pulse();
            return;
        }
        cancelPlay();
        peekOpen = false;
        picking = false;
        panelOpen = false;
        sysOpen = false;
        replayOn = true;
        replayDragging = false;
        replayProg = 0f;
        replayIdx = -1;
        replayT0 = System.nanoTime() / 1000000L - t0;
        pulse();
        invalidate();
    }

    void stopReplay() {
        if (!replayOn) return;
        replayOn = false;
        replayDragging = false;
        replayIdx = -1;
        invalidate();
    }

    private void drawReplay(Canvas c) {
        int n = starPts.size();
        if (n == 0) return;
        p.reset();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF03040A);
        p.setAlpha(186);
        c.drawRect(0, 0, W, H, p);
        p.setAlpha(255);

        float e = Replay.ease(replayProg);
        float head = e * (n - 1);

        // 走过的轨迹
        Path pa = new Path();
        int last = (int) Math.floor(head);
        for (int i = 0; i <= last && i < n; i++) {
            float[] sp = starPts.get(i);
            float x = ux(sp[0]), y = vy(sp[1]);
            if (i == 0) pa.moveTo(x, y);
            else pa.lineTo(x, y);
        }
        p.reset();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(C_WARM);
        p.setAlpha(34);
        p.setStrokeWidth(3.4f * dp);
        c.drawPath(pa, p);
        p.setAlpha(150);
        p.setStrokeWidth(1.2f * dp);
        c.drawPath(pa, p);

        // 星：走过的亮，未走的只剩一点余光
        for (int i = 0; i < n; i++) {
            float[] sp = starPts.get(i);
            float x = ux(sp[0]), y = vy(sp[1]);
            int m = (int) sp[3];
            if (m < 0 || m >= Constellation.MOOD_N) m = 0;
            boolean done = i <= last;
            p.setStyle(Paint.Style.FILL);
            p.setColor(done ? Constellation.MOOD_COLOR[m] : 0x66C8D4F2);
            p.setAlpha(done ? 240 : 60);
            c.drawCircle(x, y, sp[2] * dp * (done ? 1f : 0.62f), p);
        }

        // 头部光点
        int hi = Math.max(0, Math.min(n - 1, last));
        float hx0 = ux(starPts.get(hi)[0]), hy0 = vy(starPts.get(hi)[1]);
        int hm = (int) starPts.get(hi)[3];
        if (hm < 0 || hm >= Constellation.MOOD_N) hm = 0;
        glow(c, hx0, hy0, 22 * dp, Constellation.MOOD_GLOW[hm], 0.7f, filters[hm]);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFDF6);
        p.setAlpha(250);
        c.drawCircle(hx0, hy0, 2.6f * dp, p);
        p.setAlpha(255);

        // 顶部标题
        textCenter(c, "星 轨 回 放", W / 2f, padT + 24 * dp, 12.5f, C_TEXT, 215, 3.0f);
        int cur = Replay.indexAt(replayProg, n);
        if (cur >= 0 && cur < starDates.size()) {
            textCenter(c, Replay.caption(starDates.get(cur)), W / 2f, padT + 52 * dp, 17, C_WARM, 235, 1.6f);
            Integer mv = mood.get(starDates.get(cur));
            int mm = mv == null ? 0 : mv;
            textCenter(c, "第 " + (cur + 1) + " 颗 · " + Constellation.MOOD_NAME[mm], W / 2f,
                    padT + 74 * dp, 10.5f, Constellation.MOOD_COLOR[mm], 200, 1.0f);
        }

        if (replayProg >= 1f) {
            textCenter(c, "共 " + n + " 颗星 · 跨越 " + spanDays + " 天", W / 2f,
                    padT + 96 * dp, 10.5f, C_DIM, 195, 1.3f);
        }

        // 底部进度条（可拖动）
        float barL = 34 * dp, barR = W - 34 * dp;
        float barY = H - padB - 58 * dp;
        replayBar.set(barL, barY - 16 * dp, barR, barY + 16 * dp);
        p.reset();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2.4f * dp);
        p.setColor(0x22FFFFFF);
        c.drawLine(barL, barY, barR, barY, p);
        float kx = barL + (barR - barL) * replayProg;
        p.setColor(C_WARM);
        p.setAlpha(215);
        c.drawLine(barL, barY, kx, barY, p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        c.drawCircle(kx, barY, 4.2f * dp, p);
        p.setColor(0xFF0A0E1B);
        p.setAlpha(230);
        c.drawCircle(kx, barY, 1.7f * dp, p);
        p.setAlpha(255);

        text(c, Replay.caption(starDates.get(0)), barL, barY + 22 * dp, 8.5f, C_DIM, 120, 0.3f, false);
        textRight(c, Replay.caption(starDates.get(starDates.size() - 1)), barR, barY + 22 * dp, 8.5f, C_DIM, 120);
        textCenter(c, replayProg >= 1f ? "走完了 · 轻触空白再看一遍" : "拖动进度 · 轻触空白退出",
                W / 2f, H - padB - 24 * dp, 9.5f, C_DIM, 110, 0.6f);
    }

    private void drawBackground(Canvas c) {
        p.reset();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(C_BG);
        c.drawRect(0, 0, W, H, p);

        p.setShader(new RadialGradient(skyR.centerX(), skyR.centerY() * 0.92f,
                Math.max(W, H) * 0.78f,
                new int[]{0xFF0B1026, 0xFF070A18, 0xFF04050D},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
    }

    private void drawBgStars(Canvas c, long now) {
        p.reset();
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < BG_N; i++) {
            float a = bgA[i] * (0.55f + 0.45f * (float) Math.sin(now * 0.00048f + bgP[i]));
            if (a < 0.02f) continue;
            float x = skyR.left + bgU[i] * skyR.width();
            float y = skyR.top + bgV[i] * skyR.height();
            p.setColor(C_COOL);
            p.setAlpha((int) (a * 255));
            c.drawCircle(x, y, bgR[i] * dp, p);
        }
        p.setAlpha(255);
    }

    /** 星座连线：按月分组，组内按日期连成一条弯曲星座，染色为该月主导心情色 */
    private void drawConstellations(Canvas c, long now) {
        if (starPts.size() < 2) return;

        // 按月分组（保持 days 已排序）
        Map<String, List<Integer>> groups = new HashMap<String, List<Integer>>();
        List<String> order = new ArrayList<String>();
        for (int i = 0; i < starDates.size(); i++) {
            String mk = Constellation.monthKey(starDates.get(i));
            List<Integer> g = groups.get(mk);
            if (g == null) {
                g = new ArrayList<Integer>();
                groups.put(mk, g);
                order.add(mk);
            }
            g.add(i);
        }

        p.reset();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);

        for (String mk : order) {
            List<Integer> g = groups.get(mk);
            if (g.size() < 2) continue;
            int dm = dominantMoodOf(g);
            int color = Constellation.MOOD_GLOW[dm];

            for (int k = 1; k < g.size(); k++) {
                float[] a = starPts.get(g.get(k - 1));
                float[] b = starPts.get(g.get(k));
                float ax = ux(a[0]), ay = vy(a[1]);
                float bx = ux(b[0]), by = vy(b[1]);
                boolean tail = (k == g.size() - 1) && mk.equals(Constellation.monthKey(today)) && litToday;
                p.setColor(tail ? C_WARM : color);
                p.setAlpha(tail ? 70 : 40);
                p.setStrokeWidth((tail ? 1.15f : 0.9f) * dp);
                float mx = (ax + bx) / 2f, my = (ay + by) / 2f;
                float nx = -(by - ay), ny = (bx - ax);
                float nl = (float) Math.sqrt(nx * nx + ny * ny);
                if (nl > 0.001f) {
                    nx = nx / nl * 14 * dp;
                    ny = ny / nl * 14 * dp;
                }
                path.reset();
                path.moveTo(ax, ay);
                path.quadTo(mx + nx, my + ny, bx, by);
                c.drawPath(path, p);
            }
        }
        p.setAlpha(255);

        // 星座名标注：仅标最近 3 个成形的月份，避免拥挤
        int shown = 0;
        for (int oi = order.size() - 1; oi >= 0 && shown < 3; oi--) {
            String mk = order.get(oi);
            List<Integer> g = groups.get(mk);
            if (g.size() < 3) continue;
            float[] f = starPts.get(g.get(0));
            float tx = ux(f[0]);
            float ty = vy(f[1]) - 9 * dp;
            String nm = Constellation.constellationName(mk, datesOf(g), mood);
            int dm = dominantMoodOf(g);
            p.reset();
            p.setTextSize(8.5f * dp);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(Constellation.MOOD_COLOR[dm]);
            float veil = Math.max(panelT, peekT);
            p.setAlpha((int) (120 * (1f - Math.min(1f, veil))));
            c.drawText(nm, tx, ty, p);
            shown++;
        }
        p.setAlpha(255);
    }

    private List<String> datesOf(List<Integer> idx) {
        List<String> out = new ArrayList<String>();
        for (Integer i : idx) out.add(starDates.get(i));
        return out;
    }

    private int dominantMoodOf(List<Integer> idx) {
        int[] cnt = new int[Constellation.MOOD_N];
        for (Integer i : idx) {
            int m = (int) starPts.get(i)[3];
            if (m >= 0 && m < Constellation.MOOD_N) cnt[m]++;
        }
        int best = 0;
        for (int i = 1; i < Constellation.MOOD_N; i++) if (cnt[i] > cnt[best]) best = i;
        return best;
    }

    private void drawUserStars(Canvas c, long now) {
        p.reset();
        for (int i = 0; i < starPts.size(); i++) {
            float[] s = starPts.get(i);
            float x = ux(s[0]), y = vy(s[1]);
            int m = (int) s[3];
            if (m < 0 || m >= Constellation.MOOD_N) m = 0;
            boolean isToday = litToday && i == starPts.size() - 1 && today.equals(starDates.get(i));
            float breathe = 0.86f + 0.14f * (float) Math.sin(now * 0.0009f + i);
            float r = s[2] * dp * (isToday ? 1.5f : 1f);

            glow(c, x, y, r * (isToday ? 17f : 10f),
                    Constellation.MOOD_GLOW[m], isToday ? 0.62f : 0.34f, filters[m]);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Constellation.MOOD_COLOR[m]);
            p.setAlpha(255);
            c.drawCircle(x, y, r * breathe, p);

            if (isToday) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.9f * dp);
                p.setColor(Constellation.MOOD_COLOR[m]);
                p.setAlpha(130);
                float l = r * 4.8f;
                c.drawLine(x - l, y, x + l, y, p);
                c.drawLine(x, y - l, x, y + l, p);
            }

            // v3.0 星月琴：正在发声的那颗星，戴一枚呼吸环
            if (playDate != null && playDate.equals(starDates.get(i))) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f * dp);
                p.setColor(C_WARM);
                p.setAlpha((int) (140 + 100 * (0.5f + 0.5f * Math.sin(now * 0.008f))));
                c.drawCircle(x, y, r * 3.6f, p);
                p.setAlpha(70);
                p.setStrokeWidth(0.7f * dp);
                c.drawCircle(x, y, r * 5.4f, p);
            }

            // v3.0 起点：第一颗星永远带一枚细环
            if (i == 0 && starPts.size() > 1) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.7f * dp);
                p.setColor(C_WARM);
                p.setAlpha(110);
                c.drawCircle(x, y, r * 2.6f, p);
            }
        }
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);
    }

    /** 复用同一支画笔，避免每帧为每颗星 new Paint（内存公平：减少抖动与 GC）。 */
    private final Paint glowPaint = new Paint();

    private void glow(Canvas c, float x, float y, float r, int color, float a, PorterDuffColorFilter f) {
        if (r <= 0) return;
        if (glowBmp == null || glowBmp.isRecycled()) {
            // 位图已被系统内存压力回收：退化成实心光点，视觉损失很小，但不再占多余内存
            p.reset();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color);
            p.setAlpha((int) (Math.min(1f, a) * 235));
            c.drawCircle(x, y, Math.min(r, 2.2f * dp), p);
            p.setAlpha(255);
            return;
        }
        glowPaint.setFilterBitmap(true);
        glowPaint.setColorFilter(f);
        glowPaint.setAlpha((int) (Math.min(1f, a) * 255));
        tmpRect.set(x - r, y - r, x + r, y + r);
        c.drawBitmap(glowBmp, null, tmpRect, glowPaint);
    }

    private void drawPulses(Canvas c, long now) {
        p.reset();
        p.setStyle(Paint.Style.STROKE);
        for (int i = pulses.size() - 1; i >= 0; i--) {
            float[] pl = pulses.get(i);
            float k = (now - pl[2]) / 1400f;
            if (k >= 1f) {
                pulses.remove(i);
                continue;
            }
            int mc = (int) pl[3];
            if (mc < 0 || mc >= Constellation.MOOD_N) mc = 0;
            float r = (8 + k * 62) * dp;
            p.setColor(Constellation.MOOD_COLOR[mc]);
            p.setAlpha((int) (255 * 0.5f * (1 - k)));
            p.setStrokeWidth((1.5f * (1 - k) + 0.4f) * dp);
            c.drawCircle(pl[0], pl[1], r, p);
        }
        p.setAlpha(255);
    }

    private void drawFlying(Canvas c, long now) {
        if (!flying) return;
        float k = Math.min(1f, (now - flyT0) / (float) FLY_MS);
        float e = 1 - (float) Math.pow(1 - k, 3);
        float x = flyX0 + (flyX1 - flyX0) * e;
        float y = flyY0 + (flyY1 - flyY0) * e - (float) Math.sin(e * Math.PI) * 46 * dp;

        int mc = flyMood;
        if (mc < 0 || mc >= Constellation.MOOD_N) mc = 0;

        p.reset();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 1; i <= 5; i++) {
            float kk = Math.max(0f, k - i * 0.035f);
            float ee = 1 - (float) Math.pow(1 - kk, 3);
            float tx = flyX0 + (flyX1 - flyX0) * ee;
            float ty = flyY0 + (flyY1 - flyY0) * ee - (float) Math.sin(ee * Math.PI) * 46 * dp;
            p.setColor(Constellation.MOOD_COLOR[mc]);
            p.setAlpha((int) (80 * (1 - i / 6f)));
            p.setStrokeWidth(2.2f * dp * (1 - i / 7f));
            c.drawLine(tx, ty, x, y, p);
        }
        p.setAlpha(255);

        glow(c, x, y, 30 * dp, Constellation.MOOD_GLOW[mc], 0.66f, filters[mc]);
        p.reset();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFDF6);
        c.drawCircle(x, y, 2.8f * dp, p);

        if (k >= 1f) {
            flying = false;
            pulses.add(new float[]{flyX1, flyY1, now, mc});
            refreshData();
            pulse();
            ChimePlayer.play(mc);
        }
    }

    private void drawMeteors(Canvas c, long now) {
        p.reset();
        for (int i = meteors.size() - 1; i >= 0; i--) {
            float[] m = meteors.get(i);
            float x = m[0], y = m[1], vx = m[2], vy = m[3];
            float life = m[4], len = m[5];
            if (life <= 0 || y > H + 60 || x < -100 || x > W + 100) {
                meteors.remove(i);
                continue;
            }
            float tx = x - vx * len, ty = y - vy * len;
            Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
            mp.setShader(new LinearGradient(tx, ty, x, y,
                    new int[]{0x00FFFFFF, 0x66DFE9FF, 0xCCFFFFFF},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            mp.setStrokeWidth(1.35f * dp);
            mp.setStrokeCap(Paint.Cap.ROUND);
            mp.setStyle(Paint.Style.STROKE);
            mp.setAlpha((int) (255 * Math.min(1f, life)));
            c.drawLine(tx, ty, x, y, mp);
            c.drawCircle(x, y, 1.6f * dp, mp);
        }
        p.setAlpha(255);
    }

    private void ambientMeteor(long now) {
        if (now < nextMeteorAt) return;
        nextMeteorAt = now + 9000 + (long) (hash01("n" + now) * 9000);
        spawnMeteor(skyR.left + skyR.width() * (0.15f + 0.7f * hash01("mx" + now)),
                skyR.top + skyR.height() * 0.12f);
    }

    private void spawnMeteor(float x, float y) {
        if (meteors.size() > 5) return;
        float r = hash01("dy" + x + y);
        meteors.add(new float[]{x, y, 1.9f * dp, (3.4f + r * 2.6f) * dp, 1f, 7.5f});
    }

    // ---------- HUD ----------
    private int todayMood() {
        Integer m = mood.get(today);
        return m == null ? 0 : m;
    }

    private void drawHud(Canvas c, long now) {
        if (!land) {
            text(c, "ONE STAR A DAY", padL, padT - 12 * dp, 9.5f, C_DIM, 150, 3.4f, false);
            text(c, "拾光", padL, padT + 14 * dp, 21, C_TEXT, 235, 1.2f, true);
            text(c, "星穹", padL + 52 * dp, padT + 14 * dp, 10, C_DIM, 120, 2.2f, false);
            hairline(c, padT + 26 * dp, 56 * dp);

            String line1, line2, hint;
            if (litToday) {
                line1 = "第 " + total + " 颗星";
                line2 = Constellation.MOOD_NAME[todayMood()] + "的一天 · " + today;
                hint = "已经亮着 " + streak + " 天 · 摇一摇回到过去";
            } else {
                line1 = "轻触夜空";
                line2 = "选一种颜色，点亮今天";
                hint = "心情会变成星的颜色";
            }
            float baseY = H - padB - 46 * dp;
            text(c, line1, padL, baseY, 17.5f, C_TEXT, 235, 0.6f, false);
            text(c, line2, padL, baseY + 21 * dp, 12f, C_DIM, 190, 0.8f, false);
            drawTip(c, padL, H - padB - 6 * dp, now, false);

        } else {
            text(c, "ONE STAR A DAY", padL, padT - 12 * dp, 9.5f, C_DIM, 150, 3.4f, false);
            text(c, "拾光", padL, padT + 16 * dp, 23, C_TEXT, 238, 1.2f, true);
            text(c, "星穹", padL + 54 * dp, padT + 16 * dp, 10, C_DIM, 120, 2.2f, false);
            hairline(c, padT + 29 * dp, 58 * dp);

            float y = padT + 72 * dp;
            p.reset();
            p.setTextSize(54 * dp);
            p.setColor(C_TEXT);
            p.setAlpha(240);
            p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            c.drawText(String.valueOf(total), padL, y, p);
            float nw = p.measureText(String.valueOf(total));
            p.setTypeface(android.graphics.Typeface.DEFAULT);
            text(c, "颗星", padL + nw + 8 * dp, y - 4 * dp, 13, C_DIM, 190, 0.8f, false);

            y += 30 * dp;
            text(c, litToday ? "今天已点亮 · " + Constellation.MOOD_NAME[todayMood()] : "今天还未点亮",
                    padL, y, 12.5f, litToday ? Constellation.MOOD_COLOR[todayMood()] : C_DIM,
                    litToday ? 235 : 170, 0.6f, false);
            y += 22 * dp;
            text(c, "连续 " + streak + " 天 · 陪伴 " + spanDays + " 天", padL, y, 11.5f, C_DIM, 150, 0.4f, false);

            drawTip(c, padL, H - padB - 12 * dp, now, false);
        }
    }

    /** v4.1：底部提示轮播，三条轻提示轮流浮现，替掉原本一行静态小字 */
    private void drawTip(Canvas c, float x, float y, long now, boolean centered) {
        String t1 = litToday ? "已经亮着 " + streak + " 天 · 摇一摇回到过去" : "心情会变成星的颜色";
        String t2 = "轻触夜空 · 点亮今天";
        String t3 = "长按星星 · 看那天、写字、起名";
        String t4 = "长按空白 · 直接补记某一天";
        String t5 = "右上角星图 · 看见整年";
        String[] tips = new String[]{t1, t2, t3, t4, t5};
        long period = 4600L;
        int idx = (int) ((now / period) % tips.length);
        float ph = (now % period) / (float) period;
        float fade = Math.min(1f, Math.min(ph / 0.12f, (1f - ph) / 0.12f));
        int alpha = (int) (115 * Math.max(0f, fade));
        if (centered) textCenter(c, tips[idx], x, y, 10.5f, C_DIM, alpha, 0.5f);
        else text(c, tips[idx], x, y, 10.5f, C_DIM, alpha, 0.5f, false);
    }

    /** v4.1：标题下方一缕极细暖金线，给页眉一点金属质感 */
    private void hairline(Canvas c, float y, float w) {
        p.reset();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        float x = padL;
        for (int i = 0; i < 10; i++) {
            p.setColor(0xFFD9B27A);
            p.setAlpha((int) (52 * (1f - i / 10f)));
            c.drawLine(x + w * i / 10f, y, x + w * (i + 1) / 10f, y, p);
        }
        p.setAlpha(255);
    }

    // ---------- v4.2 柔光玻璃（小米澎湃 OS4 同款）----------
    private final HashMap<String, Shader[]> glassCache = new HashMap<String, Shader[]>();
    private final HashMap<String, LinearGradient> sheenCache = new HashMap<String, LinearGradient>();
    private int pressedPill = 0;

    /** 顶沿柔光：多档渐变缓降，不留任何硬边（取代旧版描边白线） */
    private LinearGradient sheenShader(float top, float dist) {
        String k = ((int) top) + "x" + ((int) dist);
        LinearGradient g = sheenCache.get(k);
        if (g == null) {
            if (sheenCache.size() > 24) sheenCache.clear();
            g = new LinearGradient(0, top, 0, top + dist,
                    new int[]{0x38FFFFFF, 0x22FFFFFF, 0x10FFFFFF, 0x05FFFFFF, 0x00FFFFFF},
                    new float[]{0f, 0.18f, 0.42f, 0.7f, 1f}, Shader.TileMode.CLAMP);
            sheenCache.put(k, g);
        }
        return g;
    }

    private Shader[] glassShaders(float w, float h) {
        if (w < 2f || h < 2f) return null;
        String k = ((int) w) + "x" + ((int) h);
        Shader[] s = glassCache.get(k);
        if (s == null) {
            if (glassCache.size() > 32) glassCache.clear();
            s = new Shader[3];
            // 竖向柔光：顶部亮 → 中部通透 → 底部回光
            // 顶部档位从 0x46 降到 0x2A，并加密首段采样点，避免最上沿"起头太亮"形成亮带
            s[0] = new LinearGradient(0, 0, 0, h,
                    new int[]{0x2AFFFFFF, 0x26FFFFFF, 0x1EFFFFFF, 0x1AFFFFFF, 0x2EFFFFFF, 0x12FFFFFF},
                    new float[]{0f, 0.08f, 0.2f, 0.46f, 0.72f, 1f}, Shader.TileMode.CLAMP);
            // 左上光源柔斑（从 0x5C 降到 0x30，光源中心略移出面板上边界）
            s[1] = new RadialGradient(w * 0.26f, -h * 0.06f, Math.max(w, h) * 0.95f,
                    0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
            // 底部内阴影（玻璃厚度下的暗边）
            s[2] = new LinearGradient(0, h * 0.62f, 0, h,
                    0x00000000, 0x4C04071A, Shader.TileMode.CLAMP);
            glassCache.put(k, s);
        }
        return s;
    }

    /**
     * 柔光玻璃板：内部压暗 + 竖向柔光 + 左上光斑 + 底部内阴影 + 顶沿高光 + 外柔光边。
     * @param scrim  背景压暗强度（0~255），保证玻璃上的文字可读
     * @param accent 心情色微染（0 表示不染）
     * @param mul    整体透明度（淡入淡出用）
     */
    private void glassPlate(Canvas c, RectF r, float rad, int scrim, int accent, float mul) {
        if (mul <= 0.01f) return;
        int layer = -1;
        if (mul < 0.995f) {
            layer = c.saveLayerAlpha(r.left - 10 * dp, r.top - 10 * dp,
                    r.right + 10 * dp, r.bottom + 10 * dp, (int) (255 * mul));
        }
        float w = r.width(), h = r.height();
        if (w < 2f || h < 2f) {
            if (layer >= 0) c.restoreToCount(layer);
            return;
        }
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setMaskFilter(null);

        if (scrim > 0) {
            p.setColor(0xFF05070F);
            p.setAlpha(scrim);
            c.drawRoundRect(r, rad, rad, p);
            p.setAlpha(255);
        }

        int save = c.save();
        path.reset();
        path.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(path);
        Shader[] sh = glassShaders(w, h);
        if (sh == null) { c.restoreToCount(save); if (layer >= 0) c.restoreToCount(layer); return; }
        c.save();
        c.translate(r.left, r.top);
        p.setShader(sh[0]);
        c.drawRect(0, 0, w, h, p);
        p.setShader(sh[1]);
        c.drawRect(0, 0, w, h, p);
        if (accent != 0) {
            p.setShader(new RadialGradient(w * 0.5f, 0f, w * 0.9f,
                    (accent & 0x00FFFFFF) | 0x24000000, accent & 0x00FFFFFF,
                    Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
        }
        p.setShader(sh[2]);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        c.restore();
        c.restore();

        // 玻璃厚度：顶沿柔光 —— 用竖向渐变替掉原来的 1.5dp 描边矩形
        // （旧写法把只有 2dp 高的圆角矩形描边，上下两条描边直接叠成一根实心白条，即用户看到的白线）
        int hs = c.save();
        path.reset();
        path.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(path);
        float sheen = Math.min(h * 0.45f, 8 * dp);
        p.setStyle(Paint.Style.FILL);
        p.setShader(sheenShader(r.top, sheen));
        c.drawRect(r.left, r.top, r.right, r.top + sheen + 0.5f, p);
        p.setShader(null);
        c.restoreToCount(hs);

        // 外沿亮边 + 三层外柔光（硬件层不支持 MaskFilter，用多描边模拟）
        p.setStrokeWidth(1f);
        p.setAlpha(38);
        c.drawRoundRect(r, rad, rad, p);
        p.setStrokeWidth(2.4f * dp);
        p.setAlpha(20);
        c.drawRoundRect(r, rad, rad, p);
        p.setStrokeWidth(5f * dp);
        p.setAlpha(10);
        c.drawRoundRect(r, rad, rad, p);

        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);
        if (layer >= 0) c.restoreToCount(layer);
    }

    private void drawButtons(Canvas c) {
        drawPill(c, btnBook, "星图", 1, panelOpen);
        drawPill(c, btnSys, "系统", 3, sysOpen);
        drawPill(c, btnOrient, land ? "竖屏" : "横屏", 2, false);
    }

    private void drawPill(Canvas c, RectF r, String label, int icon, boolean active) {
        float rad = r.height() / 2f;
        // v4.2：柔光玻璃胶囊（选中态以暖金微染）
        glassPlate(c, r, rad, 46, active ? 0xFFE9BE74 : 0, 1f);

        if (pressedPill == icon) {
            p.reset();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            p.setAlpha(30);
            c.drawRoundRect(r, rad, rad, p);
            p.setAlpha(255);
        }
        p.reset();
        p.setStyle(Paint.Style.FILL);

        float iconCx = r.left + 16 * dp;
        float cy = r.centerY();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * dp);
        p.setColor(active ? 0xFFFFE7B8 : 0xCCD8E2FF);

        if (icon == 3) {
            RectF box = new RectF(iconCx - 5.5f * dp, cy - 5.5f * dp, iconCx + 5.5f * dp, cy + 5.5f * dp);
            c.drawRoundRect(box, 1.8f * dp, 1.8f * dp, p);
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(iconCx, cy, 1.5f * dp, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.9f * dp);
            p.setAlpha(190);
            for (int i = -1; i <= 1; i++) {
                float off = i * 3.2f * dp;
                c.drawLine(iconCx - 8.4f * dp, cy + off, iconCx - 5.5f * dp, cy + off, p);
                c.drawLine(iconCx + 5.5f * dp, cy + off, iconCx + 8.4f * dp, cy + off, p);
                c.drawLine(iconCx + off, cy - 8.4f * dp, iconCx + off, cy - 5.5f * dp, p);
                c.drawLine(iconCx + off, cy + 5.5f * dp, iconCx + off, cy + 8.4f * dp, p);
            }
            p.setAlpha(255);
        } else if (icon == 1) {
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(iconCx - 4 * dp, cy + 3 * dp, 1.5f * dp, p);
            c.drawCircle(iconCx + 1 * dp, cy - 4 * dp, 1.5f * dp, p);
            c.drawCircle(iconCx + 5 * dp, cy + 2.5f * dp, 1.5f * dp, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.8f * dp);
            p.setAlpha(160);
            c.drawLine(iconCx - 2.8f * dp, cy + 2 * dp, iconCx + 0.2f * dp, cy - 2.8f * dp, p);
            c.drawLine(iconCx + 2.2f * dp, cy - 3 * dp, iconCx + 4.4f * dp, cy + 1.4f * dp, p);
            p.setAlpha(255);
        } else {
            RectF box = new RectF(iconCx - 6.5f * dp, cy - 4.2f * dp, iconCx + 1.5f * dp, cy + 4.2f * dp);
            c.drawRoundRect(box, 1.6f * dp, 1.6f * dp, p);
            p.setStyle(Paint.Style.FILL);
            path.reset();
            path.moveTo(iconCx + 5.5f * dp, cy - 4.6f * dp);
            path.lineTo(iconCx + 5.5f * dp, cy - 0.2f * dp);
            path.lineTo(iconCx + 1.4f * dp, cy - 2.4f * dp);
            path.close();
            c.drawPath(path, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.9f * dp);
            c.drawArc(new RectF(iconCx - 2.5f * dp, cy - 3.4f * dp, iconCx + 4.6f * dp, cy + 3.4f * dp), -60, 120, false, p);
        }

        text(c, label, iconCx + 12 * dp, cy + 4 * dp, 11.5f, active ? C_WARM : 0xFFD8E2FF, 215, 1.2f, false);
    }

    // ---------- 心情选择环 ----------
    private void startPicking(float x, float y) {
        float r = PICK_R * dp;
        float cx = Math.max(padL + r * 0.55f, Math.min(W - padL - r * 0.55f, x));
        pickCx = cx;
        pickCy = Math.max(r + padT + 26 * dp, Math.min(H - padB - r - 30 * dp, y));
        picking = true;
        pickT0 = System.nanoTime() / 1000000L - t0;

        // 五个色点排成上半弧（-166°..-14°）
        for (int i = 0; i < Constellation.MOOD_N; i++) {
            double a = Math.toRadians(-166 + i * 38);
            pickPt[i][0] = pickCx + (float) Math.cos(a) * r;
            pickPt[i][1] = pickCy + (float) Math.sin(a) * r;
        }
        pulse();
        invalidate();
    }

    private void drawPicking(Canvas c, long now) {
        // 引导文字（v6.1：补记时换成那天的日期）
        boolean back = pendingDate != null;
        boolean exists = back && days.contains(pendingDate);
        textCenter(c, back ? DayEdit.pretty(pendingDate) : "今天是什么颜色",
                pickCx, pickCy + 6 * dp, back ? 12 : 11, C_TEXT, 190, back ? 0.8f : 1.6f);
        textCenter(c, back ? (exists ? "换成另一种颜色" : "选一颗，补上这颗星") : "选一颗，放进夜空",
                pickCx, pickCy + 24 * dp, 9.5f, C_DIM, 120, 0.8f);

        // 中心柔光
        glow(c, pickCx, pickCy, 42 * dp, C_WARM, 0.16f, filters[0]);

        for (int i = 0; i < Constellation.MOOD_N; i++) {
            float x = pickPt[i][0], y = pickPt[i][1];
            float appear = Math.min(1f, (now - pickT0 - i * 45) / 220f);
            if (appear <= 0) continue;
            float rad = 13 * dp * (0.7f + 0.3f * appear);

            glow(c, x, y, rad * 3.4f, Constellation.MOOD_GLOW[i], 0.5f * appear, filters[i]);

            p.reset();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Constellation.MOOD_COLOR[i]);
            p.setAlpha((int) (245 * appear));
            c.drawCircle(x, y, rad, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1f * dp);
            p.setColor(0x40FFFFFF);
            p.setAlpha((int) (120 * appear));
            c.drawCircle(x, y, rad + 4 * dp, p);

            // v4.2：玻璃球内高光 + 底部内阴影，贴合柔光玻璃语汇
            float hx = x - rad * 0.34f, hy = y - rad * 0.42f, hr = rad * 0.82f;
            if (hr > 0.5f) {
                Shader hi = new RadialGradient(hx, hy, hr,
                        new int[]{0x8CFFFFFF, 0x22FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
                p.setStyle(Paint.Style.FILL);
                p.setShader(hi);
                p.setAlpha((int) (255 * appear));
                c.drawCircle(hx, hy, hr, p);
                p.setShader(null);

                Shader low = new RadialGradient(x, y + rad * 0.62f, rad * 1.05f,
                        new int[]{0x00212B4C, 0x4A10203F, 0x6E0B1730},
                        new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
                p.setShader(low);
                p.setAlpha((int) (190 * appear));
                c.drawCircle(x, y + rad * 0.55f, rad * 0.9f, p);
                p.setShader(null);
                p.setAlpha(255);
            }

            textCenter(c, Constellation.MOOD_NAME[i], x, y + rad + 16 * dp, 9.5f,
                    Constellation.MOOD_COLOR[i], (int) (190 * appear), 0.6f);
        }
        p.setAlpha(255);
    }

    // ---------- 星图面板 ----------
    private void drawPanel(Canvas c) {
        float ease = 1 - (float) Math.pow(1 - panelT, 3);
        p.reset();

        boolean compact = land;
        float cardW, x0, y0 = 0, dx = 0, dy = 0;
        float rowH = compact ? 22 * dp : 31 * dp;
        float chartH = compact ? 26 * dp : 44 * dp;
        float curveH = compact ? 34 * dp : 62 * dp;
        int verseLines = compact ? 1 : 2;
        float btnH = 34 * dp;
        float divH = compact ? 16 * dp : 24 * dp;
        float headH = compact ? 38 * dp : 46 * dp;

        if (land) {
            cardW = Math.min(colW - 20 * dp, 400 * dp);
            x0 = 18 * dp;
            y0 = 18 * dp;
            dx = -(1 - ease) * (cardW + 40 * dp);
            dy = 0;
        } else {
            cardW = W - 2 * 20 * dp;
            x0 = 20 * dp;
            dx = 0;
        }
        float availH = land ? H - 36 * dp : H - padT - padB - 6 * dp;

        // 内容自然高度：顺序排布后，按钮贴底放置；屏幕太矮时先省掉星语，再压曲线
        float need;
        while (true) {
            need = headH + 10 * dp + divH + rowH * 3 + 6 * dp + chartH + 22 * dp
                    + 14 * dp + curveH + 18 * dp + verseLines * 15 * dp + 16 * dp + btnH + 22 * dp;
            if (need <= availH || (verseLines == 0 && curveH <= 34 * dp)) break;
            if (verseLines > 0) verseLines = 0;
            else curveH = Math.max(34 * dp, curveH * 0.72f);
        }
        float cardH = Math.min(need, availH);
        if (!land) y0 = H - padB - 8 * dp - cardH;

        RectF card = new RectF(x0 + dx, y0 + dy, x0 + dx + cardW, y0 + dy + cardH);

        // v4.2：星图面板 → 柔光玻璃
        glassPlate(c, card, 22 * dp, 132, 0, ease);
        p.setAlpha(255);

        float px = card.left + 22 * dp;
        float right = card.right - 22 * dp;
        float ry = card.top + headH - 10 * dp;
        text(c, "星图", px, ry, 17, C_TEXT, 235, 1.4f, true);
        textRight(c, "STAR CHART", right, ry - 2 * dp, 8.5f, C_DIM, 120);
        ry += 10 * dp;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.8f * dp);
        p.setColor(0x14FFFFFF);
        c.drawLine(px, ry, right, ry, p);
        ry += divH;

        ry = statRow(c, "已点亮", total + " 天", px, right, ry, rowH);
        ry = statRow(c, "最长连续", Math.max(streak, bestStreak) + " 天", px, right, ry, rowH);
        ry = statRow(c, "陪伴", spanDays + " 天", px, right, ry, rowH);

        // 12 个月色带柱
        ry += 6 * dp;
        int year = Constellation.busiestYear(days, Integer.parseInt(today.substring(0, 4)));
        panelYear = year;
        int[] counts = Constellation.monthlyCounts(days, year);
        int[] colors = Constellation.monthlyColors(days, mood, year);
        int maxC = 1;
        for (int v : counts) if (v > maxC) maxC = v;

        float gap = 4 * dp;
        float bw = (right - px - gap * 11) / 12f;
        float baseY = ry + chartH;

        p.reset();
        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 12; i++) {
            float bx = px + i * (bw + gap);
            RectF br = new RectF(bx, ry, bx + bw, baseY);
            barRects[i] = br;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x14FFFFFF);
            p.setAlpha(255);
            c.drawRoundRect(br, 2 * dp, 2 * dp, p);
            if (counts[i] > 0) {
                float h = Math.max(4 * dp, chartH * counts[i] / (float) maxC);
                p.setColor(colors[i]);
                p.setAlpha(215);
                c.drawRoundRect(new RectF(bx, baseY - h, bx + bw, baseY), 2 * dp, 2 * dp, p);
            }
            p.setAlpha(255);
            if (playMonth == i) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f * dp);
                p.setColor(C_WARM);
                p.setAlpha(playStep < playTotal ? 210 : 120);
                c.drawRoundRect(new RectF(bx - 1.5f * dp, ry - 1.5f * dp, bx + bw + 1.5f * dp, baseY + 1.5f * dp),
                        3 * dp, 3 * dp, p);
                p.setStyle(Paint.Style.FILL);
                p.setAlpha(255);
            }
        }
        p.setAlpha(255);
        String chartLabel = year + " 年星盘 · 轻触试听";
        if (playMonth >= 0) {
            chartLabel = "♪ " + Melody.monthName(playMonth)
                    + (playTotal > 0 ? " · " + Math.min(playStep + 1, playTotal) + "/" + playTotal : " · 空");
        }
        text(c, chartLabel, px, baseY + 13 * dp, 9, playMonth >= 0 ? C_WARM : C_DIM, playMonth >= 0 ? 200 : 130,
                0.6f, false);
        textRight(c, "最亮 " + Constellation.MOOD_NAME[dominantMoodAll()], right, baseY + 13 * dp, 9,
                Constellation.MOOD_COLOR[dominantMoodAll()], 200);

        // v4.0 心情曲线：近六周的起伏
        ry = baseY + 26 * dp;
        float[] series = MoodCurve.weekly(days, mood, today, 6);
        text(c, "心情曲线 · 近六周", px, ry, 9, C_DIM, 155, 0.6f, false);
        textRight(c, MoodCurve.caption(series), right, ry, 9, C_WARM, 195);
        ry += 9 * dp;
        drawCurveSeries(c, series, px, right, ry, curveH);
        text(c, MoodCurve.weeksAgoLabel(today, 6), px, ry + curveH + 10 * dp, 8, C_DIM, 95, 0.4f, false);
        textRight(c, "本周", right, ry + curveH + 10 * dp, 8, C_DIM, 95);
        ry += curveH + 22 * dp;

        // 星语（按宽度折行）
        String note = Constellation.verse(days, mood, total, streak, spanDays);
        int perLine = land ? 18 : 21;
        int maxLines = verseLines;
        int drawn = 0;
        for (int i = 0; i < note.length() && drawn < maxLines; i += perLine) {
            String seg = note.substring(i, Math.min(note.length(), i + perLine));
            if (drawn == maxLines - 1 && note.length() > i + perLine) seg = seg.substring(0, seg.length() - 1) + "…";
            text(c, seg, px, ry, 10.5f, C_DIM, 150, 0.3f, false);
            ry += 15 * dp;
            drawn++;
        }

        // v6.1 底部四枚按钮：补记 / 回放 / 存图 / 收起
        float bh = btnH;
        float bTop = card.bottom - bh - 20 * dp;
        float gap2 = 7 * dp;
        float bW = (right - px - gap2 * 3) / 4f;
        panelBackfillBtn.set(px, bTop, px + bW, bTop + bh);
        panelReplayBtn.set(px + bW + gap2, bTop, px + bW * 2 + gap2, bTop + bh);
        panelSaveBtn.set(px + bW * 2 + gap2 * 2, bTop, px + bW * 3 + gap2 * 2, bTop + bh);
        closeBtnRect.set(px + (bW + gap2) * 3, bTop, right, bTop + bh);
        drawGhostBtn(c, panelBackfillBtn, "补记", C_WARM);
        drawGhostBtn(c, panelReplayBtn, "回放", C_WARM);
        drawGhostBtn(c, panelSaveBtn, "存图", C_WARM);
        drawGhostBtn(c, closeBtnRect, "收起", C_WARM);
    }

    private void drawGhostBtn(Canvas c, RectF r, String label, int color) {
        p.reset();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x0EFFFFFF);
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x44FFE7BE);
        c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
        textCenter(c, label, r.centerX(), r.centerY() + 4 * dp, 11.5f, color, 220, 0.8f);
    }

    /** 六周情绪折线：值域 0..1，空缺不连线。 */
    private void drawCurveSeries(Canvas c, float[] series, float left, float right, float top, float h) {
        int n = series == null ? 0 : series.length;
        if (n == 0) return;
        float inset = 6 * dp;
        float stepX = n > 1 ? (right - left - inset * 2) / (n - 1) : 0;

        p.reset();
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.7f * dp);
        p.setColor(0x14FFFFFF);
        c.drawLine(left, top + h, right, top + h, p);
        p.setColor(0x0CFFFFFF);
        c.drawLine(left, top + h * 0.5f, right, top + h * 0.5f, p);

        Path pa = new Path();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            if (Float.isNaN(series[i])) {
                started = false;
                continue;
            }
            float x = left + inset + i * stepX;
            float y = top + h - h * clamp01(series[i]);
            if (!started) {
                pa.moveTo(x, y);
                started = true;
            } else {
                pa.lineTo(x, y);
            }
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(2.4f * dp);
        p.setColor(C_WARM);
        p.setAlpha(40);
        c.drawPath(pa, p);
        p.setStrokeWidth(1.5f * dp);
        p.setAlpha(195);
        c.drawPath(pa, p);

        p.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            if (Float.isNaN(series[i])) continue;
            float x = left + inset + i * stepX;
            float y = top + h - h * clamp01(series[i]);
            p.setColor(C_WARM);
            p.setAlpha(235);
            c.drawCircle(x, y, 2.3f * dp, p);
            p.setColor(0xFF0A0E1B);
            p.setAlpha(210);
            c.drawCircle(x, y, 0.9f * dp, p);
        }
        p.setAlpha(255);
    }

    private float clamp01(float v) {
        return v < 0 ? 0 : (v > 1f ? 1f : v);
    }

    // ---------- v5.0 系统适配面板 ----------
    private float sysRow(Canvas c, String label, String note, String value,
                         float x, float right, float y, float rowH) {
        text(c, label, x, y, 12f, C_DIM, 190, 0.8f, false);
        text(c, note, x, y + 12 * dp, 8.5f, C_DIM, 115, 0.3f, false);
        textRight(c, value, right, y + 2 * dp, 14f, C_TEXT, 232);
        return y + rowH;
    }

    private void drawPanelScrim(Canvas c, float k) {
        if (k <= 0.001f) return;
        p.reset();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF03040A);
        p.setAlpha((int) (205 * Math.min(1f, k)));
        c.drawRect(0, 0, W, H, p);
        p.setAlpha(255);
    }

    private void drawSystemPanel(Canvas c) {
        float ease = 1 - (float) Math.pow(1 - panelT, 3);
        p.reset();

        boolean compact = land;
        float headH = compact ? 38 * dp : 46 * dp;
        float bigH = compact ? 40 * dp : 52 * dp;
        float tlH = compact ? 44 * dp : 58 * dp;
        float rowH = compact ? 24 * dp : 31 * dp;
        float footH = compact ? 34 * dp : 46 * dp;

        float cardW, x0, y0 = 0, dx = 0;
        if (land) {
            cardW = Math.min(colW - 20 * dp, 400 * dp);
            x0 = 18 * dp;
            y0 = 18 * dp;
            dx = -(1 - ease) * (cardW + 40 * dp);
        } else {
            cardW = W - 40 * dp;
            x0 = 20 * dp;
        }
        float need = headH + 8 * dp + bigH + tlH + 16 * dp + rowH * 4 + footH;
        float availH = land ? H - 36 * dp : H - padT - padB - 6 * dp;
        float cardH = Math.min(need, availH);
        // 屏幕太矮时把富余高度让给页脚，保证四点事实仍然排得下
        footH = Math.max(footH, cardH - (headH + 8 * dp + bigH + tlH + 16 * dp + rowH * 4));
        if (!land) y0 = Math.max(padT - 10 * dp, (H - cardH) / 2f + 8 * dp);

        RectF card = new RectF(x0 + dx, y0, x0 + dx + cardW, y0 + cardH);
        glassPlate(c, card, 22 * dp, 132, 0, ease);
        p.setAlpha(255);

        float px = card.left + 22 * dp;
        float right = card.right - 22 * dp;
        float ry = card.top + headH - 10 * dp;
        text(c, "系统适配", px, ry, 17, C_TEXT, 235, 1.4f, true);
        textRight(c, "SYSTEM COMPAT", right, ry - 2 * dp, 8.5f, C_DIM, 120);
        ry += 10 * dp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.8f * dp);
        p.setColor(0x14FFFFFF);
        c.drawLine(px, ry, right, ry, p);

        // 大标题：当前系统版本（真机 SDK，不写死）
        float top = ry + 8 * dp;
        text(c, Compat.versionLabel(), px, top + (compact ? 17 : 20) * dp,
                compact ? 19f : 23f, C_WARM, 240, 0.5f, true);
        text(c, NativeCore.PAGE ? "零权限 · 原生库按 16 KB 对齐" : "零权限 · 无原生库 · 天然兼容",
                px, top + (compact ? 30 : 36) * dp, 9.5f, C_DIM, 140, 0.4f, false);
        // 右侧状态：内存是否已让位
        String badge = CompatTable.memBadge(trimLevel);
        textRight(c, badge, right, top + (compact ? 17 : 20) * dp, 12f,
                trimLevel > 0 ? C_WARM : C_DIM, 200);

        // 历代版本刻度：5.0 → 17
        int[] sdkNodes = {21, 23, 26, 29, 31, 33, 35, 36, 37};
        String[] nameNodes = {"5", "6", "8", "10", "12", "13", "15", "16", "17"};
        float ty = top + bigH;
        float lineY = ty + (compact ? 12 : 15) * dp;
        float step = (right - px) / (sdkNodes.length - 1);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f * dp);
        p.setColor(0x33FFFFFF);
        c.drawLine(px, lineY, right, lineY, p);
        int curSdk = Compat.SDK;
        for (int i = 0; i < sdkNodes.length; i++) {
            float nx = px + step * i;
            boolean passed = sdkNodes[i] <= curSdk;
            boolean cur = Math.abs(sdkNodes[i] - curSdk) < 1 && sdkNodes[i] <= 37;
            p.setStyle(Paint.Style.FILL);
            p.setColor(passed ? (cur ? C_WARM : 0xFFFFE7B8) : 0xFF6C7898);
            p.setAlpha(passed ? 235 : 150);
            c.drawCircle(nx, lineY, (cur ? 3.6f : 2.4f) * dp, p);
            if (cur) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1f * dp);
                p.setColor(C_WARM);
                p.setAlpha(110);
                c.drawCircle(nx, lineY, 6.6f * dp, p);
            }
            p.setAlpha(255);
            textCenter(c, nameNodes[i], nx, lineY + 13 * dp, 7.8f,
                    cur ? C_WARM : C_DIM, cur ? 245 : (passed ? 175 : 105), 0f);
        }
        // v6.3：这行必须跟着实测页大小走，16 KB 设备才谈大页收益
        String tl = CompatTable.is16k(Compat.pageSize())
                ? "16 KB 页 · 页表项约 -75% · TLB 覆盖约 x4"
                : "4 KB 页 · 原生库按 16 KB 预对齐，换机即兼容";
        text(c, tl, px, ty + (compact ? 2 : 3) * dp, 8.2f, C_DIM, 120, 0.3f, false);

        // 四项实测事实
        float rowTop = ty + tlH + 4 * dp;
        String heap = Compat.memoryClass(getContext()) + " MB";
        String pageNote = CompatTable.pageCompat(NativeCore.loadedCount() > 0);
        String memNote = CompatTable.memNote(Compat.lowRam(getContext()), trimLevel);
        ry = rowTop + 12 * dp;
        ry = sysRow(c, "页大小", pageNote, Compat.pageLabel(), px, right, ry, rowH);
        ry = sysRow(c, "内存档", memNote, heap, px, right, ry, rowH);
        ry = sysRow(c, "刷新率", "随内容自适应", Compat.refreshLabel(getContext()), px, right, ry, rowH);
        ry = sysRow(c, "边到边", "刘海 / 手势条已让行", "已适配", px, right, ry, rowH);

        // 页脚（divider 与文字都相对于四行结束位置排，不会被压线）
        float rowEnd = ry - rowH;
        float divY = rowEnd + 6 * dp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.8f * dp);
        p.setColor(0x14FFFFFF);
        c.drawLine(px, divY, right, divY, p);
        float fy = rowEnd + (compact ? 22 : 24) * dp;
        text(c, "预测式返回 · 暗色跟随 · 帧率自适", px, fy, 9.2f, C_DIM, 170, 0.3f, false);
        if (verCache == null) verCache = Compat.appVersion(getContext());
        textRight(c, "SHIGUANG v" + verCache, right, fy, 8.5f, C_DIM, 125);
        if (!compact) {
            text(c, "内存公平：系统内存紧张时先释放着色器与位图", px, fy + 13 * dp, 8.5f, C_DIM, 110, 0.2f, false);
            textRight(c, "\u00A9 2026 \u738b\u5b59\u9633 \u00b7 \u72ec\u7acb\u5f00\u53d1", right, fy + 13 * dp, 8.5f, C_DIM, 110);
        }
    }

    private int dominantMoodAll() {
        return Constellation.dominantMood(days, mood);
    }

    private float statRow(Canvas c, String label, String value, float left, float right, float y, float rowH) {
        text(c, label, left, y, 12f, C_DIM, 175, 0.8f, false);
        textRight(c, value, right, y, 15f, C_TEXT, 225);
        return y + rowH;
    }

    // ---------- 文本工具 ----------
    private void text(Canvas c, String s, float x, float y, float size, int color, int alpha,
                      float spacing, boolean bold) {
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setAlpha(alpha);
        p.setTextSize(size * dp);
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(bold ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
        if (Build.VERSION.SDK_INT >= 21) p.setLetterSpacing(spacing * 0.12f);
        c.drawText(s, x, y, p);
    }

    private void textRight(Canvas c, String s, float x, float y, float size, int color, int alpha) {
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setAlpha(alpha);
        p.setTextSize(size * dp);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(s, x, y, p);
    }

    private void textCenter(Canvas c, String s, float x, float y, float size, int color, int alpha, float spacing) {
        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setAlpha(alpha);
        p.setTextSize(size * dp);
        p.setTextAlign(Paint.Align.CENTER);
        if (Build.VERSION.SDK_INT >= 21) p.setLetterSpacing(spacing * 0.12f);
        c.drawText(s, x, y, p);
    }

    private void pulse() {
        try {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        } catch (Throwable ignored) {
        }
    }

    // ---------- 触摸 ----------
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        if (calOpen) return onCalendarTouch(e, x, y);
        if (replayOn) {
            int act = e.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                if (y > replayBar.top - 20 * dp && y < replayBar.bottom + 20 * dp) {
                    replayDragging = true;
                    replayProg = Replay.seek(x, replayBar.left, replayBar.right);
                }
            } else if (act == MotionEvent.ACTION_MOVE) {
                if (replayDragging) replayProg = Replay.seek(x, replayBar.left, replayBar.right);
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                if (replayDragging) {
                    replayDragging = false;
                } else if (act == MotionEvent.ACTION_UP) {
                    if (replayProg >= 1f) {
                        // 走完了：再走一遍
                        replayProg = 0f;
                        replayIdx = -1;
                        replayT0 = System.nanoTime() / 1000000L - t0;
                    } else {
                        stopReplay();
                    }
                }
            }
            invalidate();
            return true;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                downTime = System.nanoTime() / 1000000L;
                downActive = true;
                downMoved = false;
                longFired = false;
                pressedPill = btnBook.contains(x, y) ? 1 : (btnOrient.contains(x, y) ? 2 : 0);
                if (pressedPill != 0) invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downActive && !downMoved) {
                    float mx = x - downX, my = y - downY;
                    if (Math.sqrt(mx * mx + my * my) > LONG_SLOP * dp) downMoved = true;
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                downActive = false;
                pressedPill = 0;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                downActive = false;
                pressedPill = 0;
                invalidate();
                float dx = x - downX, dy = y - downY;
                if (!longFired && Math.sqrt(dx * dx + dy * dy) < 18 * dp &&
                        System.nanoTime() / 1000000L - downTime < 520) {
                    handleTap(x, y);
                } else if (longFired) {
                    // 长按已经打开详情，抬手不再触发其它交互
                    invalidate();
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void handleTap(float x, float y) {
        if (undoDate != null && !peekOpen && !panelOpen && !sysOpen && !picking
                && System.nanoTime() / 1000000L <= undoUntil && undoRect.contains(x, y)) {
            undoDelete();
            return;
        }
        if (peekOpen) {
            boolean editNote = peekNoteBtn.contains(x, y);
            boolean editName = peekNameBtn.contains(x, y);
            boolean del = peekDelBtn.contains(x, y);
            String d = peekDate;
            if (del) {
                // v6.1 手滑保护：第一下亮红询问，第二下才真删
                if (!peekDelArmed) {
                    peekDelArmed = true;
                    pulse();
                    invalidate();
                    return;
                }
                peekOpen = false;
                peekDelArmed = false;
                invalidate();
                if (d != null) deleteDay(d);
                return;
            }
            peekOpen = false;
            peekDelArmed = false;
            invalidate();
            if (d != null && (editNote || editName)) {
                Host h = host();
                if (h == null) return;
                if (editName) {
                    String oldName = names.get(d);
                    h.editName(d, oldName == null ? "" : oldName);
                } else {
                    String old = notes.get(d);
                    h.editNote(d, old == null ? "" : old);
                }
            }
            return;
        }
        if (sysOpen) {
            boolean onBtn = btnSys.contains(x, y);
            sysOpen = false;
            invalidate();
            if (!onBtn) return;
            pulse();
            return;
        }
        if (panelOpen) {
            if (panelBackfillBtn.contains(x, y)) {
                pulse();
                panelOpen = false;
                cancelPlay();
                invalidate();
                Host h = host();
                if (h != null) h.pickBackfillDate();
                return;
            }
            if (panelReplayBtn.contains(x, y)) {
                pulse();
                panelOpen = false;
                startReplay();
                return;
            }
            if (panelSaveBtn.contains(x, y)) {
                pulse();
                Host h = host();
                if (h != null) h.savePoster();
                return;
            }
            int bar = hitBar(x, y);
            if (bar >= 0) {
                playMonth(bar);
                return;
            }
            panelOpen = false;
            cancelPlay();
            invalidate();
            return;
        }
        if (picking) {
            int hit = hitPick(x, y);
            String target = pendingDate;
            pendingDate = null;
            if (hit >= 0) {
                picking = false;
                if (target != null) litDate(target, pickCx, pickCy, hit);
                else lightToday(pickCx, pickCy, hit);
            } else {
                picking = false;
                invalidate();
            }
            return;
        }
        if (btnOrient.contains(x, y)) {
            pulse();
            Host h = host();
            if (h != null) h.toggleOrientation();
            return;
        }
        if (btnSys.contains(x, y)) {
            pulse();
            resetBackProgress();
            sysOpen = true;
            panelOpen = false;
            cancelPlay();
            invalidate();
            return;
        }
        if (btnBook.contains(x, y)) {
            pulse();
            resetBackProgress();
            panelOpen = true;
            invalidate();
            return;
        }
        if (!litToday && !flying) {
            startPicking(x, y);
        } else {
            spawnMeteor(x, y);
            pulse();
        }
    }

    // ---------- 长按：某一天的心情与备注 ----------
    private boolean openPeekAt(float x, float y) {
        String date = hitStar(x, y);
        if (date == null) {
            if (peekOpen) {
                peekOpen = false;
                invalidate();
            }
            return false;
        }
        peekDate = date;
        peekOpen = true;
        peekDelArmed = false;
        pulse();
        invalidate();
        return true;
    }

    private String hitStar(float x, float y) {
        float best = 30 * dp;
        String hit = null;
        for (int i = 0; i < starPts.size(); i++) {
            float[] s = starPts.get(i);
            float sx = ux(s[0]), sy = vy(s[1]);
            float d = (float) Math.sqrt((sx - x) * (sx - x) + (sy - y) * (sy - y));
            if (d < best) {
                best = d;
                hit = starDates.get(i);
            }
        }
        return hit;
    }

    // ---------- v3.0 摇一摇：一道流星带你回到某一天 ----------
    void shakeTravel() {
        if (starDates.isEmpty() || starPts.isEmpty()) return;
        if (peekOpen || panelOpen || picking || travelIdx >= 0 || !panelTReady()) return;
        int n = starDates.size();
        int i = rnd.nextInt(n);
        if (n > 1 && peekDate != null && peekDate.equals(starDates.get(i))) i = (i + 1) % n;
        travelIdx = i;
        travelT0 = System.nanoTime() / 1000000L;
        boolean fromLeft = rnd.nextBoolean();
        travelSX = fromLeft ? -14 * dp : W + 14 * dp;
        travelSY = H * (0.06f + rnd.nextFloat() * 0.44f);
        cancelPlay();
        peekOpen = false;
        invalidate();
    }

    private boolean panelTReady() {
        return panelT <= 0.02f;
    }

    private void advanceTravel(Canvas c, long now) {
        if (travelIdx < 0 || travelIdx >= starPts.size()) return;
        long dt = now - travelT0;
        float pr = Math.min(1f, dt / 760f);
        float[] s = starPts.get(travelIdx);
        float tx = ux(s[0]), ty = vy(s[1]);
        float e = 1 - (float) Math.pow(1 - pr, 2.2);
        float hx = travelSX + (tx - travelSX) * e;
        float hy = travelSY + (ty - travelSY) * e - (float) Math.sin(Math.PI * e) * 46 * dp;

        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setStyle(Paint.Style.STROKE);
        for (int k = 1; k <= 9; k++) {
            float f0 = Math.max(0f, e - k * 0.032f);
            float f1 = Math.max(0f, e - (k - 1) * 0.032f);
            if (f1 <= f0) continue;
            float x0 = travelSX + (tx - travelSX) * f0;
            float y0 = travelSY + (ty - travelSY) * f0 - (float) Math.sin(Math.PI * f0) * 46 * dp;
            float x1 = travelSX + (tx - travelSX) * f1;
            float y1 = travelSY + (ty - travelSY) * f1 - (float) Math.sin(Math.PI * f1) * 46 * dp;
            tp.setColor(C_WARM);
            tp.setAlpha((int) (170 * (1 - k / 9f)));
            tp.setStrokeWidth((2.2f - k * 0.18f) * dp);
            c.drawLine(x0, y0, x1, y1, tp);
        }

        Paint hp = new Paint(Paint.ANTI_ALIAS_FLAG);
        hp.setColor(C_WARM);
        hp.setAlpha(240);
        c.drawCircle(hx, hy, 3.4f * dp, hp);
        hp.setAlpha(90);
        c.drawCircle(hx, hy, 7.5f * dp, hp);

        if (pr >= 1f) {
            String d = starDates.get(travelIdx);
            travelIdx = -1;
            peekDate = d;
            peekOpen = true;
            Integer mv = mood.get(d);
            ChimePlayer.play(mv == null ? 0 : mv);
            pulse();
            invalidate();
        }
    }

    // ---------- v3.0 星月琴：点一个月份，听这个月的星 ----------
    private int hitBar(float x, float y) {
        for (int i = 0; i < 12; i++) {
            RectF r = barRects[i];
            if (r == null) continue;
            if (x >= r.left - 3 * dp && x <= r.right + 3 * dp
                    && y >= r.top - 8 * dp && y <= r.bottom + 8 * dp) return i;
        }
        return -1;
    }

    private void playMonth(int idx) {
        cancelPlay();
        final List<Integer> picks = new ArrayList<Integer>();
        for (int i = 0; i < starDates.size(); i++) {
            String d = starDates.get(i);
            if (Melody.monthIndex(d) != idx) continue;
            if (!d.startsWith(String.valueOf(panelYear))) continue;
            picks.add(i);
        }
        playMonth = idx;
        playStep = 0;
        playTotal = picks.size();
        playDate = null;
        pulse();
        for (int k = 0; k < picks.size(); k++) {
            final int ord = k;
            final int si = picks.get(k);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    String d = starDates.get(si);
                    Integer mv = mood.get(d);
                    playStep = ord;
                    playDate = d;
                    ChimePlayer.playNote(mv == null ? 0 : mv, Melody.semitone(d), 520);
                    invalidate();
                }
            };
            playRuns.add(r);
            uiH.postDelayed(r, k * 300L);
        }
        invalidate();
    }

    private void cancelPlay() {
        for (int i = 0; i < playRuns.size(); i++) uiH.removeCallbacks(playRuns.get(i));
        playRuns.clear();
        playMonth = -1;
        playStep = 0;
        playTotal = 0;
        playDate = null;
    }

    void setNote(String date, String note) {
        if (date == null) return;
        String n = DaysStore.cleanNote(note);
        if (n.length() == 0) notes.remove(date);
        else notes.put(date, n);
        DaysStore.save(getContext(), days, mood, notes, names, backfilled);
        Host sh = host();
        if (sh != null) sh.onDataChanged();
        invalidate();
    }

    /** v4.0：给某颗星起名 / 改名 / 清空。 */
    void setStarName(String date, String name) {
        if (date == null) return;
        String nm = StarName.clean(name);
        if (nm.length() == 0) names.remove(date);
        else names.put(date, nm);
        DaysStore.save(getContext(), days, mood, notes, names, backfilled);
        Host sh = host();
        if (sh != null) sh.onDataChanged();
        invalidate();
    }

    private void drawPeek(Canvas c) {
        float a = peekT;
        p.reset();
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(C_BG);
        p.setAlpha((int) (198 * a));
        c.drawRect(0, 0, W, H, p);
        p.setAlpha(255);

        String date = peekDate == null ? today : peekDate;
        Integer mi = mood.get(date);
        int m = mi == null ? 0 : mi;
        if (m < 0 || m >= Constellation.MOOD_N) m = 0;
        int mc = Constellation.MOOD_COLOR[m];
        String note = notes.get(date);
        boolean hasNote = note != null && note.length() > 0;

        float cw = Math.min(W - 2 * padL, 336 * dp);
        float padIn = 20 * dp;
        float textW = cw - 2 * padIn;

        p.setTypeface(Typeface.DEFAULT);
        p.setLetterSpacing(0f);
        p.setTextSize(13.5f * dp);
        if (hasNote) wrapText(note, textW);
        else {
            wrapBuf.clear();
            wrapBuf.add("还没写下备注");
        }
        int lines = wrapBuf.size();

        float h = 204 * dp + lines * 21 * dp;
        float left = W / 2f - cw / 2f;
        float top = H / 2f - h / 2f;
        float maxTop = H - padB - 10 * dp - h;
        if (top > maxTop) top = maxTop;
        if (top < padT + 6 * dp) top = padT + 6 * dp;
        peekCard.set(left, top, left + cw, top + h);

        // v4.2：详情卡 → 柔光玻璃（以当日心情色微染光晕）
        glassPlate(c, peekCard, 20 * dp, 150, mc, a);
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);

        float yDate = top + 42 * dp;
        int mo = Integer.parseInt(date.substring(5, 7));
        int da = Integer.parseInt(date.substring(8, 10));
        text(c, mo + "月" + da + "日", left + padIn, yDate, 24, C_TEXT, (int) (242 * a), 0.5f, true);
        textRight(c, date.substring(0, 4), left + cw - padIn, yDate, 11, C_DIM, (int) (135 * a));
        textRight(c, weekdayOf(date), left + cw - padIn, yDate + 17 * dp, 11.5f, C_DIM, (int) (185 * a));

        float yMood = yDate + 24 * dp;
        p.setStyle(Paint.Style.FILL);
        p.setColor(mc);
        p.setAlpha((int) (245 * a));
        c.drawCircle(left + padIn + 4 * dp, yMood - 4 * dp, 4 * dp, p);
        p.setAlpha(255);
        text(c, Constellation.MOOD_NAME[m], left + padIn + 16 * dp, yMood, 13.5f, mc, (int) (238 * a), 0.6f, false);

        int idx = starDates.indexOf(date) + 1;
        p.setTextSize(13.5f * dp);
        float nameW = p.measureText(Constellation.MOOD_NAME[m]);
        if (idx > 0) {
            text(c, "第 " + idx + " 颗", left + padIn + 16 * dp + nameW + 10 * dp, yMood,
                    10.5f, C_DIM, (int) (150 * a), 0.3f, false);
        }

        // v4.0 星名：有自定义名显示自定义名，否则显示「第 N 颗 · 心情词」
        String custom = StarName.clean(names.get(date));
        boolean named = custom.length() > 0;
        String nm = named ? custom : "还没有名字";
        float yName = yMood + 23 * dp;
        p.setStyle(Paint.Style.FILL);
        p.setColor(mc);
        p.setAlpha((int) (150 * a));
        c.drawCircle(left + padIn + 3 * dp, yName - 3.5f * dp, 1.7f * dp, p);
        p.setAlpha(255);
        text(c, named ? "「" + nm + "」" : nm, left + padIn + 11 * dp, yName, 12.5f,
                named ? C_WARM : C_DIM, (int) ((named ? 228 : 130) * a), 0.3f, false);
        textRight(c, "星名", left + cw - padIn, yName, 9, C_DIM, (int) (105 * a));

        float yDiv = yName + 12 * dp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0xFFFFFFFF);
        p.setAlpha((int) (26 * a));
        c.drawLine(left + padIn, yDiv, left + cw - padIn, yDiv, p);
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL);

        float yNote = yDiv + 24 * dp;
        if (hasNote) {
            p.setColor(mc);
            p.setAlpha((int) (75 * a));
            tmpRect.set(left + padIn - 8 * dp, yNote - 13 * dp,
                    left + padIn - 6 * dp, yNote + (lines - 1) * 21 * dp + 4 * dp);
            c.drawRoundRect(tmpRect, 1 * dp, 1 * dp, p);
            p.setAlpha(255);
            for (int i = 0; i < lines; i++) {
                text(c, wrapBuf.get(i), left + padIn, yNote + i * 21 * dp,
                        13.5f, C_WARM, (int) (226 * a), 0.2f, false);
            }
        } else {
            text(c, "还没写下备注", left + padIn, yNote, 12.5f, C_DIM, (int) (120 * a), 0.2f, false);
        }

        float btnY = top + 140 * dp + lines * 21 * dp;
        // v6.1 三枚等价胶囊：起个名 / 写一句 / 抹掉这颗
        float bgap = 8 * dp;
        float bw3 = (textW - bgap * 2) / 3f;
        peekNameBtn.set(left + padIn, btnY, left + padIn + bw3, btnY + 34 * dp);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setAlpha((int) (14 * a));
        c.drawRoundRect(peekNameBtn, 17 * dp, 17 * dp, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFFFFE7BE);
        p.setAlpha((int) (60 * a));
        c.drawRoundRect(peekNameBtn, 17 * dp, 17 * dp, p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        textCenter(c, names.containsKey(date) ? "改名" : "起个名", peekNameBtn.centerX(), btnY + 22 * dp,
                11.5f, C_WARM, (int) (225 * a), 0.8f);

        peekNoteBtn.set(peekNameBtn.right + bgap, btnY,
                peekNameBtn.right + bgap + bw3, btnY + 34 * dp);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        p.setAlpha((int) (18 * a));
        c.drawRoundRect(peekNoteBtn, 17 * dp, 17 * dp, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(0xFFFFE7BE);
        p.setAlpha((int) (70 * a));
        c.drawRoundRect(peekNoteBtn, 17 * dp, 17 * dp, p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        textCenter(c, hasNote ? "改一句" : "写一句", peekNoteBtn.centerX(), btnY + 22 * dp,
                12, C_WARM, (int) (232 * a), 1.0f);

        peekDelBtn.set(peekNoteBtn.right + bgap, btnY,
                peekNoteBtn.right + bgap + bw3, btnY + 34 * dp);
        int dColor = peekDelArmed ? 0xFFFF7070 : 0xFFFF9E9E;
        p.setStyle(Paint.Style.FILL);
        p.setColor(peekDelArmed ? 0x30FF5A5A : 0x0EFFFFFF);
        p.setAlpha((int) ((peekDelArmed ? 210 : 255) * a));
        c.drawRoundRect(peekDelBtn, 17 * dp, 17 * dp, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(dColor);
        p.setAlpha((int) ((peekDelArmed ? 190 : 80) * a));
        c.drawRoundRect(peekDelBtn, 17 * dp, 17 * dp, p);
        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        textCenter(c, DayEdit.deleteLabel(peekDelArmed), peekDelBtn.centerX(), btnY + 22 * dp,
                10.5f, dColor, (int) ((peekDelArmed ? 245 : 205) * a), 0.2f);

        textCenter(c, peekDelArmed ? "再点一次，这一天就真的没了" : "轻触空白收起",
                left + cw / 2f, btnY + 52 * dp, 9.5f,
                peekDelArmed ? 0xFFFF9E9E : C_DIM,
                (int) ((peekDelArmed ? 200 : 105) * a), 0.3f);
    }

    private void wrapText(String s, float maxW) {
        wrapBuf.clear();
        StringBuilder line = new StringBuilder();
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            float cw = p.measureText(ch);
            if (w + cw > maxW && line.length() > 0) {
                wrapBuf.add(line.toString());
                line.setLength(0);
                w = 0;
            }
            line.append(ch);
            w += cw;
        }
        if (line.length() > 0) wrapBuf.add(line.toString());
        if (wrapBuf.size() > 4) {
            while (wrapBuf.size() > 4) wrapBuf.remove(wrapBuf.size() - 1);
            String last = wrapBuf.get(3);
            while (last.length() > 1 && p.measureText(last + "…") > maxW) {
                last = last.substring(0, last.length() - 1);
            }
            wrapBuf.set(3, last + "…");
        }
    }

    private String weekdayOf(String date) {
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(iso.parse(date));
            String[] w = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
            return w[cal.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return "";
        }
    }

    private int hitPick(float x, float y) {
        float best = 26 * dp;
        int hit = -1;
        for (int i = 0; i < Constellation.MOOD_N; i++) {
            float dx = x - pickPt[i][0], dy = y - pickPt[i][1];
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < best) {
                best = d;
                hit = i;
            }
        }
        return hit;
    }

    private void lightToday(float fromX, float fromY, int moodIdx) {
        litDate(iso.format(new Date()), fromX, fromY, moodIdx);
    }

    /**
     * v6.1 点亮任意一天：今天、补记的过去某天，或给已点亮的一天换颜色。
     * 记录完重算连续 / 最长连续 / 陪伴天数，再把星光从指尖送到那颗星上。
     */
    private void litDate(String date, float fromX, float fromY, int moodIdx) {
        if (!DayEdit.isIso(date)) return;
        today = iso.format(new Date());
        boolean existed = days.contains(date);
        int m = (moodIdx < 0 || moodIdx >= Constellation.MOOD_N) ? 0 : moodIdx;

        if (!existed) {
            days.add(date);
            Collections.sort(days);
        }
        mood.put(date, m);
        if (!existed && !date.equals(today)) backfilled.add(date);

        litToday = days.contains(today);
        total = days.size();
        streak = Constellation.streakOf(days, today);
        spanDays = calcSpan();
        bestStreak = calcBestStreak();

        DaysStore.save(getContext(), days, mood, notes, names, backfilled);
        Host dh = host();
        if (dh != null) {
            dh.onDataChanged();
            if (existed) {
                dh.toast(DayEdit.recoloredText(date, Constellation.MOOD_NAME[m]));
            } else if (!date.equals(today)) {
                dh.toast(DayEdit.backfillText(date));
            }
        }
        rebuildStars();

        float[] target = null;
        for (int i = 0; i < starDates.size(); i++) {
            if (starDates.get(i).equals(date)) {
                target = starPts.get(i);
                break;
            }
        }
        if (target == null) target = new float[]{0.5f, 0.5f, 2.5f, m};
        flyX0 = fromX;
        flyY0 = fromY;
        flyX1 = ux(target[0]);
        flyY1 = vy(target[1]);
        flyMood = m;
        flyT0 = System.nanoTime() / 1000000L - t0;
        flying = true;
        pulses.add(new float[]{fromX, fromY, flyT0, m});
        pulse();
        invalidate();
    }

    /** v6.1 抹掉某一天的星：连备注与星名一起清掉，编号顺延。 */
    private void deleteDay(String date) {
        if (date == null || !days.remove(date)) return;
        Integer oldMood = mood.remove(date);
        undoDate = date;
        undoMood = oldMood == null ? 0 : oldMood;
        undoNote = notes.remove(date);
        undoName = names.remove(date);
        undoWasBackfill = backfilled.remove(date);
        undoUntil = System.nanoTime() / 1000000L + UNDO_MS;
        // v6.3：低内存档不再逐帧刷新，补一次定时重绘，保证 5 秒后胶囊照样淡出
        uiH.removeCallbacks(undoExpire);
        uiH.postDelayed(undoExpire, UNDO_MS + 60L);

        today = iso.format(new Date());
        litToday = days.contains(today);
        total = days.size();
        streak = Constellation.streakOf(days, today);
        spanDays = calcSpan();
        bestStreak = calcBestStreak();

        DaysStore.save(getContext(), days, mood, notes, names, backfilled);
        Host dh = host();
        if (dh != null) {
            dh.onDataChanged();
            dh.toast(DayEdit.removedText(date));
        }
        peekDate = null;
        rebuildStars();
        pulse();
        invalidate();
    }

    // ---------- v6.3 补记选日：自绘星历 ----------

    /** 打开星历：回到当前月，并让其它浮层先收起。 */
    void openCalendar() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        calYear = c.get(java.util.Calendar.YEAR);
        calMonth = c.get(java.util.Calendar.MONTH);
        calOpen = true;
        calPressed = -1;
        calT = 0f;
        calDirty = true;
        peekOpen = false;
        peekDelArmed = false;
        panelOpen = false;
        sysOpen = false;
        picking = false;
        cancelPlay();
        // 自绘面板对读屏器不透明，进入时补一句位置说明
        announceForAccessibility("补记哪一天，只能选今天及以前五年内的日期");
        invalidate();
    }

    boolean isCalOpen() {
        return calOpen;
    }

    /** 返回手势进度：0 静止，1 面板被推到底。只影响面板，不动星空。 */
    void setBackProgress(float p) {
        float k = Math.max(0f, Math.min(1f, p));
        if (backAnim != null) {
            backAnim.cancel();
            backAnim = null;
        }
        if (k == backP) return;
        backP = k;
        invalidate();
    }

    /** 手指退回边缘或面板已收起：200ms 减速弹回原位。 */
    void animateBackRest() {
        if (backP <= 0.001f) return;
        if (backAnim != null) backAnim.cancel();
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(backP, 0f);
        va.setDuration(200L);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                Object v = a.getAnimatedValue();
                backP = v instanceof Float ? (Float) v : 0f;
                invalidate();
            }
        });
        backAnim = va;
        va.start();
    }

    /** 提交返回：面板顺着手势方向继续滑出（backP -> 1），随后由 panelT 淡出收场。 */
    void animateBackOut() {
        if (backAnim != null) backAnim.cancel();
        float from = Math.max(0.06f, backP);
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(from, 1f);
        va.setDuration(160L);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                Object v = a.getAnimatedValue();
                backP = v instanceof Float ? (Float) v : 0f;
                invalidate();
            }
        });
        backAnim = va;
        va.start();
    }

    /** 面板重新打开前清掉上一次返回残留的进度。 */
    void resetBackProgress() {
        if (backAnim != null) {
            backAnim.cancel();
            backAnim = null;
        }
        if (backP != 0f) {
            backP = 0f;
            invalidate();
        }
    }

    boolean closeCalendar() {
        if (!calOpen) return false;
        calOpen = false;
        calPressed = -1;
        invalidate();
        return true;
    }

    private void shiftMonth(int delta) {
        int y = calYear, m = calMonth + delta;
        while (m < 0) { m += 12; y--; }
        while (m > 11) { m -= 12; y++; }
        java.util.Calendar now = java.util.Calendar.getInstance();
        int ty = now.get(java.util.Calendar.YEAR);
        int tm = now.get(java.util.Calendar.MONTH);
        if (y > ty || (y == ty && m > tm)) return;  // 不翻到未来
        calYear = y;
        calMonth = m;
        calPressed = -1;
        calDirty = true;
        pulse();
        invalidate();
    }

    /** 命中判定：允许在矩形外 pad 像素内也吃下点击，小屏上更好按。 */
    private static boolean hitPad(RectF r, float x, float y, float pad) {
        return x >= r.left - pad && x <= r.right + pad
                && y >= r.top - pad && y <= r.bottom + pad;
    }

    /** 重算星历的几何与每格状态（缓存在数组里，绘制与命中都用它）。 */
    private void layoutCalendar() {
        if (W <= 0 || H <= 0) return;
        float cardW = Math.min(W - 24 * dp, 360 * dp);
        float inset = 14 * dp;
        float headH = 50 * dp;
        float monthH = 38 * dp;
        float weekH = 20 * dp;
        float footH = 54 * dp;
        // 横屏时上下内边距意义不大，直接按屏高给空间，避免卡片顶出屏幕
        float avail = (land ? H - 20 * dp : H - padT - padB - 26 * dp);
        float maxGrid = Math.max(120 * dp, avail - (headH + monthH + weekH + footH));
        float cellW = (cardW - inset * 2) / 7f;
        calCell = Math.min(cellW, Math.max(28 * dp, maxGrid / 6f));
        float gridH = calCell * 6f;
        float cardH = headH + monthH + weekH + gridH + footH;
        float x0 = (W - cardW) / 2f;
        float want = Math.max(padT + 6 * dp, (H - cardH) / 2f - 8 * dp);
        float y0 = Math.min(want, H - cardH - 10 * dp);
        if (y0 < 6 * dp) y0 = 6 * dp;
        calCard.set(x0, y0, x0 + cardW, y0 + cardH);

        float my = calCard.top + headH + monthH / 2f;
        float by = my - 17 * dp;
        calPrevBtn.set(calCard.left + inset, by, calCard.left + inset + 34 * dp, by + 34 * dp);
        calNextBtn.set(calCard.right - inset - 34 * dp, by, calCard.right - inset, by + 34 * dp);
        calCloseBtn.set(calCard.left + inset, calCard.bottom - footH + (footH - 30 * dp) / 2f,
                calCard.left + inset + 58 * dp, calCard.bottom - footH + (footH + 30 * dp) / 2f);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(calYear, calMonth, 1);
        int firstDow = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1;
        int dim = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
        String todayStr = iso.format(new Date());
        float gx = calCard.left + (cardW - 7 * cellW) / 2f;
        float gy = calCard.top + headH + monthH + weekH;
        for (int i = 0; i < 42; i++) {
            int day = i - firstDow + 1;
            float cx = gx + cellW * (i % 7) + cellW / 2f;
            float cy = gy + calCell * (i / 7) + calCell / 2f;
            calCells[i].set(cx - cellW / 2f, cy - calCell / 2f, cx + cellW / 2f, cy + calCell / 2f);
            float r = Math.min(cellW, calCell) * 0.40f;
            calRings[i].set(cx - r, cy - r, cx + r, cy + r);
            if (day < 1 || day > dim) {
                calDates[i] = null;
                calUsable[i] = false;
                calLit[i] = false;
                calBack[i] = false;
            } else {
                String ds = String.format(java.util.Locale.US, "%04d-%02d-%02d", calYear, calMonth + 1, day);
                calDates[i] = ds;
                calUsable[i] = DayEdit.canBackfill(ds, todayStr);
                calLit[i] = days.contains(ds);
                calBack[i] = backfilled.contains(ds);
            }
        }
    }

    private void drawCalendar(Canvas c) {
        float target = calOpen ? 1f : 0f;
        calT += (target - calT) * 0.26f;
        if (Math.abs(target - calT) < 0.002f) calT = target;
        if (calT <= 0.002f) return;
        float ease = 1 - (float) Math.pow(1 - calT, 3);
        if (calDirty || calLastW != W || calLastH != H) {
            layoutCalendar();
            calLastW = W;
            calLastH = H;
            calDirty = false;
        }
        if (calCard.isEmpty()) return;

        p.reset();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF03040A);
        p.setAlpha((int) (192 * ease));
        c.drawRect(0, 0, W, H, p);
        p.setAlpha(255);

        glassPlate(c, calCard, 22 * dp, 140, 0, ease);

        // 内边距必须与 layoutCalendar 保持一致，否则绘制与命中区会错位
        float inset = 14 * dp;
        int a = (int) (255 * ease);
        String todayStr = iso.format(new Date());

        // 标题 + 可选范围（规则写在标题旁，不再占用日格空间）
        text(c, "补记哪一天", calCard.left + inset + 4 * dp, calCard.top + 32 * dp,
                16.5f, C_TEXT, Math.min(240, a), 1.6f, true);
        textRight(c, "今天及以前 · 近五年", calCard.right - inset - 2 * dp, calCard.top + 24 * dp,
                8.4f, C_DIM, (int) (120 * ease));

        // 月份切换
        float my = calCard.top + 50 * dp + 19 * dp + 5 * dp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f * dp);
        p.setColor(C_WARM);
        p.setAlpha((int) (210 * ease));
        float ax = calPrevBtn.centerX(), ay = calPrevBtn.centerY();
        c.drawLine(ax + 3.4f * dp, ay - 5.2f * dp, ax - 3 * dp, ay, p);
        c.drawLine(ax - 3 * dp, ay, ax + 3.4f * dp, ay + 5.2f * dp, p);
        boolean nextOn = canShiftForward();
        p.setColor(nextOn ? C_WARM : C_DIM);
        p.setAlpha((int) ((nextOn ? 210 : 90) * ease));
        ax = calNextBtn.centerX();
        c.drawLine(ax - 3.4f * dp, ay - 5.2f * dp, ax + 3 * dp, ay, p);
        c.drawLine(ax + 3 * dp, ay, ax - 3.4f * dp, ay + 5.2f * dp, p);
        textCenter(c, calYear + " 年 " + (calMonth + 1) + " 月", calCard.centerX(), my,
                13.5f, C_TEXT, Math.min(225, a), 0.8f);

        // 星期表头
        String[] wk = {"日", "一", "二", "三", "四", "五", "六"};
        float wkY = calCells[0].top - 7 * dp;
        for (int i = 0; i < 7; i++) {
            textCenter(c, wk[i], calCells[i].centerX(), wkY, 8.5f, C_DIM, (int) (130 * ease), 0f);
        }

        // 日格
        for (int i = 0; i < 42; i++) {
            if (calDates[i] == null) continue;
            float cx = calCells[i].centerX(), cy = calCells[i].centerY();
            float r = calRings[i].width() / 2f;
            boolean isToday = calDates[i].equals(todayStr);

            if (isToday) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f * dp);
                p.setColor(C_WARM);
                p.setAlpha((int) (150 * ease));
                c.drawCircle(cx, cy, r, p);
            }
            if (calPressed == i) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                p.setAlpha((int) (30 * ease));
                c.drawCircle(cx, cy, r, p);
            }
            if (calLit[i]) {
                p.setStyle(calBack[i] ? Paint.Style.STROKE : Paint.Style.FILL);
                p.setStrokeWidth(1f * dp);
                p.setColor(C_WARM);
                p.setAlpha((int) ((calBack[i] ? 150 : 210) * ease));
                c.drawCircle(cx, cy + r + 2.6f * dp, 1.6f * dp, p);
            }
            int ta = calUsable[i] ? 238 : 84;
            textCenter(c, String.valueOf(Integer.parseInt(calDates[i].substring(8, 10))),
                    cx, cy + 4.4f * dp, 12.5f,
                    calUsable[i] ? C_TEXT : C_DIM, (int) (ta * ease), 0f);
        }

        // 底部：取消胶囊 + 图例
        float fy = calCloseBtn.centerY() + 4 * dp;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f * dp);
        p.setColor(C_WARM);
        p.setAlpha((int) ((calPressed == -2 ? 120 : 62) * ease));
        c.drawRoundRect(calCloseBtn, calCloseBtn.height() / 2f, calCloseBtn.height() / 2f, p);
        textCenter(c, "取消", calCloseBtn.centerX(), fy, 11.5f, C_WARM,
                (int) (205 * ease), 0.6f);
        textRight(c, "实心点 已点亮 · 空心点 补记", calCard.right - inset - 2 * dp, fy, 8.4f,
                C_DIM, (int) (118 * ease));
    }

    private boolean canShiftForward() {
        java.util.Calendar now = java.util.Calendar.getInstance();
        int ty = now.get(java.util.Calendar.YEAR);
        int tm = now.get(java.util.Calendar.MONTH);
        return calYear < ty || (calYear == ty && calMonth < tm);
    }

    private boolean onCalendarTouch(MotionEvent e, float x, float y) {
        int act = e.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN) {
            calPressed = -1;
            if (hitPad(calCloseBtn, x, y, 7 * dp)) {
                calPressed = -2;
            } else if (hitPad(calPrevBtn, x, y, 6 * dp)) {
                calPressed = -3;
            } else if (hitPad(calNextBtn, x, y, 6 * dp)) {
                calPressed = -4;
            } else {
                for (int i = 0; i < 42; i++) {
                    if (calUsable[i] && hitPad(calCells[i], x, y, 3 * dp)) {
                        calPressed = i;
                        break;
                    }
                }
            }
            if (calPressed == -1 && !calCard.contains(x, y)) calPressed = -2;  // 点卡片外 = 收起
            invalidate();
            return true;
        }
        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            int was = calPressed;
            calPressed = -1;
            if (act == MotionEvent.ACTION_UP) {
                if (was == -2 && (hitPad(calCloseBtn, x, y, 7 * dp) || !calCard.contains(x, y))) {
                    closeCalendar();
                    return true;
                }
                if (was == -3 && hitPad(calPrevBtn, x, y, 6 * dp)) {
                    shiftMonth(-1);
                    return true;
                }
                if (was == -4 && hitPad(calNextBtn, x, y, 6 * dp)) {
                    shiftMonth(1);
                    return true;
                }
                if (was >= 0 && calDates[was] != null && hitPad(calCells[was], x, y, 3 * dp)) {
                    String d = calDates[was];
                    // 选完立刻收日历，避免淡出遮罩压住紧随其后的选色环
                    calOpen = false;
                    calT = 0f;
                    calPressed = -1;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    beginBackfill(d);
                    return true;
                }
            }
            invalidate();
            return true;
        }
        return true;
    }

    /** v6.1 宿主选定补记日期后进入选色环。 */
    void beginBackfill(String date) {
        if (!DayEdit.canBackfill(date, iso.format(new Date()))) return;
        pendingDate = date;
        peekOpen = false;
        peekDelArmed = false;
        panelOpen = false;
        sysOpen = false;
        replayOn = false;
        cancelPlay();
        startPicking(W / 2f, H * 0.42f);
        invalidate();
    }

    /** v6.2 抹掉后的撤销条：5 秒内点一下就长回来。 */
    private void drawUndo(Canvas c, long now) {
        // v6.3 修正：undoUntil 存的是 System.nanoTime() 绝对毫秒，而传入的 now 是相对 t0 的毫秒，
        // 直接比较恒成立，胶囊 5 秒后不会消失。这里统一换成绝对时钟。
        long absNow = System.nanoTime() / 1000000L;
        boolean live = undoDate != null && absNow <= undoUntil;
        float target = live ? 1f : 0f;
        undoT += (target - undoT) * 0.24f;
        if (Math.abs(target - undoT) < 0.004f) undoT = target;
        if (!live && undoT <= 0.004f) {
            undoDate = null;
            undoRect.setEmpty();
            return;
        }
        if (undoT <= 0.004f) return;

        float w = Math.min(W - 48 * dp, 268 * dp);
        float h = 46 * dp;
        undoRect.set(W / 2f - w / 2f, H - 132 * dp - h / 2f,
                W / 2f + w / 2f, H - 132 * dp + h / 2f);

        int a = (int) (255 * undoT);
        p.reset();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xCC0A1024);
        p.setAlpha((int) (a * 0.92f));
        c.drawRoundRect(undoRect, h / 2f, h / 2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1f);
        p.setColor(0x66BFD2FF);
        p.setAlpha((int) (a * 0.55f));
        c.drawRoundRect(undoRect, h / 2f, h / 2f, p);

        // 剩下的秒数做成一条细进度
        long left = Math.max(0, undoUntil - absNow);
        float ratio = Math.max(0f, Math.min(1f, left / (float) UNDO_MS));
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFD9A0);
        p.setAlpha((int) (a * 0.75f));
        float pad = 12 * dp;
        float lineY = undoRect.bottom - 9 * dp;
        c.drawRoundRect(new RectF(undoRect.left + pad, lineY,
                undoRect.left + pad + (undoRect.width() - pad * 2) * ratio, lineY + 1.6f * dp),
                1 * dp, 1 * dp, p);

        p.setStyle(Paint.Style.FILL);
        String label = "已抹掉 " + (undoDate == null ? "" : DayEdit.pretty(undoDate));
        text(c, label, undoRect.left + 20 * dp, undoRect.centerY() + 4 * dp, 11f, 0xFFDCE6FF, (int) (a * 0.9f), 1.4f, false);

        float cw = 76 * dp;
        RectF undoBtn = new RectF(undoRect.right - cw - 8 * dp, undoRect.top + 7 * dp,
                undoRect.right - 8 * dp, undoRect.bottom - 7 * dp);
        p.setColor(0x2EFFF0D0);
        c.drawRoundRect(undoBtn, undoBtn.height() / 2f, undoBtn.height() / 2f, p);
        textCenter(c, "撤销", undoBtn.centerX(), undoBtn.centerY() + 4 * dp, 11f, C_WARM, a, 0.6f);
        p.setAlpha(255);
    }

    /** v6.2 把刚抹掉的那颗星原样补回来（备注 / 星名 / 补记标记一起还原）。 */
    private void undoDelete() {
        String date = undoDate;
        if (date == null) return;
        if (!days.contains(date)) {
            days.add(date);
            Collections.sort(days);
        }
        mood.put(date, undoMood);
        if (undoNote != null) notes.put(date, undoNote);
        if (undoName != null) names.put(date, undoName);
        if (undoWasBackfill) backfilled.add(date);
        else backfilled.remove(date);

        undoDate = null;
        undoRect.setEmpty();
        undoT = 0f;

        today = iso.format(new Date());
        litToday = days.contains(today);
        total = days.size();
        streak = Constellation.streakOf(days, today);
        spanDays = calcSpan();
        bestStreak = calcBestStreak();

        DaysStore.save(getContext(), days, mood, notes, names, backfilled);
        Host dh = host();
        if (dh != null) {
            dh.onDataChanged();
            dh.toast("已恢复 " + DayEdit.pretty(date) + " 的星");
        }
        rebuildStars();
        peekDate = null;
        peekOpen = false;
        pulse();
        invalidate();
    }

    private Host host() {
        if (getContext() instanceof Host) return (Host) getContext();
        return null;
    }

    boolean isLand() {
        return land;
    }
}
