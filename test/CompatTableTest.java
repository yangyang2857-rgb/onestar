import com.yangyang2857rgb.onestar.CompatTable;

/** v5.0 纯逻辑单测：不依赖 Android，javac + java 直接跑。 */
public class CompatTableTest {
    static int pass = 0;
    static int fail = 0;

    static void eq(String name, Object got, Object want) {
        if (String.valueOf(got).equals(String.valueOf(want))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + name + " got=" + got + " want=" + want);
        }
    }

    public static void main(String[] a) {
        eq("major21", CompatTable.major(21), 5);
        eq("major23", CompatTable.major(23), 6);
        eq("major26", CompatTable.major(26), 8);
        eq("major29", CompatTable.major(29), 10);
        eq("major31", CompatTable.major(31), 12);
        eq("major33", CompatTable.major(33), 13);
        eq("major34", CompatTable.major(34), 14);
        eq("major35", CompatTable.major(35), 15);
        eq("major36", CompatTable.major(36), 16);
        eq("major37", CompatTable.major(37), 17);
        eq("major40", CompatTable.major(40), 17);
        eq("major1", CompatTable.major(1), 5);

        eq("label37", CompatTable.versionLabel(37), "Android 17 · API 37");
        eq("label36", CompatTable.versionLabel(36), "Android 16 · API 36");

        eq("page4k", CompatTable.pageLabel(4096), "4 KB");
        eq("page16k", CompatTable.pageLabel(16384), "16 KB");
        eq("page64k", CompatTable.pageLabel(65536), "64 KB");
        eq("pageZero", CompatTable.pageLabel(0), "? KB");
        eq("is16kYes", CompatTable.is16k(16384), true);
        eq("is16kNo", CompatTable.is16k(4096), false);
        eq("note16k", CompatTable.pageNote(16384, true), "16 KB 页 · 页表更省");
        eq("note4k-native", CompatTable.pageNote(4096, true), "4 KB 页 · 原生库已 16 KB 对齐");
        eq("note4k-pure", CompatTable.pageNote(4096, false), "4 KB 页 · 无原生库 · 天然兼容");

        eq("badge0", CompatTable.memBadge(0), "标准档");
        eq("badge1", CompatTable.memBadge(1), "已让位");
        eq("badge2", CompatTable.memBadge(2), "深度让位");

        eq("memLow", CompatTable.memNote(true, 0), "低内存机 · 已自动降载");
        eq("memTrim", CompatTable.memNote(false, 1), "系统回收 · 已让位");
        eq("memStd", CompatTable.memNote(false, 0), "标准档 · 可主动让位");

        eq("tlLen", CompatTable.timelineSdk().length, CompatTable.timelineName().length);
        eq("tlIdx21", CompatTable.timelineIndex(21), 0);
        eq("tlIdx33", CompatTable.timelineIndex(33), 5);
        eq("tlIdx37", CompatTable.timelineIndex(37), 8);
        eq("tlIdx34", CompatTable.timelineIndex(34), 5);
        eq("tlIdx99", CompatTable.timelineIndex(99), 8);

        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }
}
