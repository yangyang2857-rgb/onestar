package com.yangyang2857rgb.onestar;

/**
 * 拾光 v6.1 星册编辑的纯逻辑（无 android 依赖，可直接 JVM 单测）。
 *
 * 负责：补记日期的合法性判断、确认文案、以及删除前的二次确认状态机。
 * 视图层只做绘制与点击分发，规则集中在这里。
 */
final class DayEdit {

    /** 补记最多回溯的年数，避免误选到 1970 之类的极端日期。 */
    static final int MAX_BACK_YEARS = 5;

    private DayEdit() {
    }

    /** 是否是形如 2026-11-04 的日期。 */
    static boolean isIso(String date) {
        if (date == null) return false;
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        int mo, da;
        try {
            mo = Integer.parseInt(date.substring(5, 7));
            da = Integer.parseInt(date.substring(8, 10));
        } catch (Exception e) {
            return false;
        }
        if (mo < 1 || mo > 12) return false;
        return da >= 1 && da <= daysInMonth(Integer.parseInt(date.substring(0, 4)), mo);
    }

    private static int daysInMonth(int year, int month) {
        switch (month) {
            case 2:
                boolean leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
                return leap ? 29 : 28;
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    /**
     * 能否补记这一天：格式合法、不晚于今天、且不超过回溯年限。
     * today 必须是同格式的日期。
     */
    static boolean canBackfill(String date, String today) {
        if (!isIso(date) || !isIso(today)) return false;
        if (date.compareTo(today) > 0) return false;
        int ty = Integer.parseInt(today.substring(0, 4));
        String earliest = String.format(java.util.Locale.US, "%04d", ty - MAX_BACK_YEARS)
                + today.substring(4);
        return date.compareTo(earliest) >= 0;
    }

    /** "2026-11-04" -> "11 月 4 日"；不合法则原样返回。 */
    static String pretty(String date) {
        if (!isIso(date)) return date == null ? "" : date;
        int mo = Integer.parseInt(date.substring(5, 7));
        int da = Integer.parseInt(date.substring(8, 10));
        return I18n.date(mo, da);
    }

    /** 补记一枚新星时的提示。 */
    static String addedText(String date) {
        return I18n.f("把 %s 补回来了", "Brought back %s", pretty(date));
    }

    /** 补记过去某天时的提示（与当天现点区分开）。 */
    static String backfillText(String date) {
        return I18n.f("补记成功 · %s，这颗星会记着它是补上的",
                "Backfilled · %s. This star will remember it was added later.", pretty(date));
    }

    /** 长按空白直接补记的引导。 */
    static String backfillHint() {
        return I18n.t("松手前选一天，就是补记", "Pick a day before letting go to backfill");
    }

    /** 补记已点亮的那天等同于换色。 */
    static String recoloredText(String date, String moodName) {
        String name = moodName == null ? "" : moodName;
        return I18n.f("%1$s 换成「%2$s」", "%1$s switched to %2$s", pretty(date), name);
    }

    /** 抹掉某天的提示。 */
    static String removedText(String date) {
        return I18n.f("抹掉了 %s 那颗星", "Erased the star on %s", pretty(date));
    }

    /** 详情卡里的删除按钮文案：第一下问、第二下抹。 */
    static String deleteLabel(boolean armed) {
        return armed
                ? I18n.t("再点一次抹掉", "Tap again to erase")
                : I18n.t("抹掉这颗", "Erase this one");
    }
}
