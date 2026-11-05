package timetable.model;

/**
 * 节次时间表。
 *
 * <p>学校作息固定为每天 12 节：上午 1-4 节，下午 5-8 节，晚上 9-12 节。
 * 时间表集中定义在此处，渲染课表、计算空教室、统计空闲时段都复用同一份数据，
 * 避免各处硬编码时间字符串导致不一致。</p>
 */
public final class Periods {

    /** 每天的最大节次。 */
    public static final int LAST = 12;

    private static final String[] START = {
            "08:00", "08:55", "10:00", "10:55", "14:00", "14:55",
            "16:00", "16:55", "19:00", "19:55", "20:50", "21:45"
    };

    private static final String[] END = {
            "08:45", "09:40", "10:45", "11:40", "14:45", "15:40",
            "16:45", "17:40", "19:45", "20:40", "21:35", "22:30"
    };

    private Periods() {
    }

    public static boolean isValid(int period) {
        return period >= 1 && period <= LAST;
    }

    public static String start(int period) {
        check(period);
        return START[period - 1];
    }

    public static String end(int period) {
        check(period);
        return END[period - 1];
    }

    /** 形如 {@code 08:00-08:45} 的时间区间。 */
    public static String range(int period) {
        check(period);
        return START[period - 1] + "-" + END[period - 1];
    }

    /** 节次所属时段：上午 / 下午 / 晚上。 */
    public static String partOfDay(int period) {
        check(period);
        if (period <= 4) {
            return "上午";
        }
        return period <= 8 ? "下午" : "晚上";
    }

    private static void check(int period) {
        if (!isValid(period)) {
            throw new IllegalArgumentException("节次必须在 1-" + LAST + " 之间，当前为：" + period);
        }
    }
}
