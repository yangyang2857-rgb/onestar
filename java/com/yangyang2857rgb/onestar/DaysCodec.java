package com.yangyang2857rgb.onestar;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 拾光 v6.2 纯逻辑存储编码（无 android 依赖，可直接 JVM 单测）。
 *
 * 每行格式："date:mood[:note][:name][:backfill]"
 *  - 第 3 段备注、第 4 段星名，都经过百分号转义，段内不会出现 ":" 或 ","
 *  - 第 5 段 "1" 表示这一天是事后补记的；没有这一段就是当天现点
 * 旧版本（v1.1 纯日期 / v2.0 date:mood / v2.1 date:mood:note）都能读。
 */
final class DaysCodec {

    private DaysCodec() {
    }

    static boolean isIso(String s) {
        return s != null && s.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    static String encode(List<String> days, Map<String, Integer> mood,
                         Map<String, String> note, Map<String, String> name,
                         Set<String> backfill) {
        StringBuilder sb = new StringBuilder();
        java.util.List<String> f = new java.util.ArrayList<String>();
        for (String d : days) {
            if (sb.length() > 0) sb.append(',');
            Integer m = mood == null ? null : mood.get(d);
            int mv = (m == null) ? 0 : m;
            if (mv < 0 || mv >= Constellation.MOOD_N) mv = 0;

            String n = note == null ? null : note.get(d);
            String nm = name == null ? null : name.get(d);
            boolean hasNote = n != null && n.length() > 0;
            boolean hasName = nm != null && nm.length() > 0;

            f.clear();
            f.add(d);
            f.add(String.valueOf(mv));
            f.add(hasNote ? NoteText.encode(n) : "");
            f.add(hasName ? NoteText.encode(nm) : "");
            if (backfill != null && backfill.contains(d)) f.add("1");
            // 去掉用不上的尾部空段（补记标记在位时不会动到它）
            while (f.size() > 2 && f.get(f.size() - 1).length() == 0) f.remove(f.size() - 1);
            for (int i = 0; i < f.size(); i++) {
                if (i > 0) sb.append(':');
                sb.append(f.get(i));
            }
        }
        return sb.toString();
    }

    static void parse(String raw, List<String> outDays, Map<String, Integer> outMood,
                      Map<String, String> outNote, Map<String, String> outName,
                      Set<String> outBackfill) {
        outDays.clear();
        outMood.clear();
        if (outNote != null) outNote.clear();
        if (outName != null) outName.clear();
        if (outBackfill != null) outBackfill.clear();
        if (raw == null) return;

        for (String seg : raw.split(",")) {
            seg = seg.trim();
            if (seg.length() == 0) continue;

            String[] f = seg.split(":", -1);
            String date = f[0].trim();
            if (!isIso(date)) continue;
            if (outDays.contains(date)) continue;

            int mood = 0;
            if (f.length > 1) {
                try {
                    mood = Integer.parseInt(f[1].trim());
                } catch (Exception ignored) {
                    mood = 0;
                }
            }
            if (mood < 0 || mood >= Constellation.MOOD_N) mood = 0;

            outDays.add(date);
            outMood.put(date, mood);
            if (outNote != null && f.length > 2) {
                String note = NoteText.decode(f[2]);
                if (note.length() > 0) outNote.put(date, note);
            }
            if (outName != null && f.length > 3) {
                String nm = StarName.clean(NoteText.decode(f[3]));
                if (nm.length() > 0) outName.put(date, nm);
            }
            if (outBackfill != null && f.length > 4 && "1".equals(f[4].trim())) {
                outBackfill.add(date);
            }
        }
    }
}
