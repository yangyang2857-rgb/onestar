package com.yangyang2857rgb.onestar;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.List;
import java.util.Map;

/**
 * 拾光 v4.0 本地存储：日期 + 当天心情色 + 一句话备注 + 星名。
 * 存储格式："2026-11-04:1:今天%3A风很轻:小满,2026-11-03:0"
 * 备注与星名都经过百分号转义（% : , 换行），因此段内不含分隔符。
 * 兼容 v1.1 纯日期、v2.0 的 "date:mood"、v2.1 的 "date:mood:note" 三种旧格式。
 */
final class DaysStore {

    private static final String PREF_V2 = "onestar.v2";
    private static final String KEY_ENTRIES = "entries";

    private static final String PREF_V1 = "onestar.v1";
    private static final String KEY_DAYS = "days";

    /** 一句话备注上限（按字符数，不含空白）。 */
    static final int NOTE_MAX = NoteText.MAX;

    private DaysStore() {
    }

    static void load(Context c, List<String> outDays, Map<String, Integer> outMood,
                     Map<String, String> outNote, Map<String, String> outName) {
        load(c, outDays, outMood, outNote, outName, null);
    }

    /**
     * v6.2：第 5 段 "1" 表示这一天是事后补记的（年度长图上会加虚线圈）。
     * 旧数据没有这一段，读出来就是正常点亮，天然兼容。
     */
    static void load(Context c, List<String> outDays, Map<String, Integer> outMood,
                     Map<String, String> outNote, Map<String, String> outName,
                     java.util.Set<String> outBackfill) {
        outDays.clear();
        outMood.clear();
        if (outNote != null) outNote.clear();
        if (outName != null) outName.clear();
        if (outBackfill != null) outBackfill.clear();

        SharedPreferences sp = c.getSharedPreferences(PREF_V2, Context.MODE_PRIVATE);
        String raw = sp.getString(KEY_ENTRIES, "");

        if (raw == null || raw.trim().length() == 0) {
            migrateV1(c, outDays, outMood);
            if (!outDays.isEmpty()) save(c, outDays, outMood, outNote, outName, outBackfill);
            return;
        }

        DaysCodec.parse(raw, outDays, outMood, outNote, outName, outBackfill);
    }

    private static void migrateV1(Context c, List<String> outDays, Map<String, Integer> outMood) {
        SharedPreferences sp1 = c.getSharedPreferences(PREF_V1, Context.MODE_PRIVATE);
        String raw = sp1.getString(KEY_DAYS, "");
        if (raw == null || raw.length() == 0) return;
        for (String s : raw.split(",")) {
            s = s.trim();
            if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) continue;
            if (outDays.contains(s)) continue;
            outDays.add(s);
            outMood.put(s, 0);
        }
    }

    static void save(Context c, List<String> days, Map<String, Integer> mood,
                     Map<String, String> note, Map<String, String> name) {
        save(c, days, mood, note, name, null);
    }

    static void save(Context c, List<String> days, Map<String, Integer> mood,
                     Map<String, String> note, Map<String, String> name,
                     java.util.Set<String> backfill) {
        String out = DaysCodec.encode(days, mood, note, name, backfill);
        c.getSharedPreferences(PREF_V2, Context.MODE_PRIVATE)
                .edit().putString(KEY_ENTRIES, out).apply();
    }

    /** 原始存储串，给桌面小组件读取用（不解析结构）。 */
    static String raw(Context c) {
        String raw = c.getSharedPreferences(PREF_V2, Context.MODE_PRIVATE)
                .getString(KEY_ENTRIES, "");
        return raw == null ? "" : raw;
    }

    /** 是否已经存在任意一天的数据（小组件据此决定显示文案）。 */
    static boolean isEmpty(Context c) {
        return raw(c).trim().length() == 0;
    }

    static String cleanNote(String raw) {
        return NoteText.clean(raw);
    }

    static String encodeNote(String note) {
        return NoteText.encode(note);
    }

    static String decodeNote(String s) {
        return NoteText.decode(s);
    }
}
