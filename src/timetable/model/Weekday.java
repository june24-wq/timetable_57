package timetable.model;

/**
 * 星期枚举。
 *
 * <p>枚举顺序固定为周一至周日，直接参与课程排序，因此不允许调整声明顺序。</p>
 */
public enum Weekday {

    MONDAY("周一", "星期一"),
    TUESDAY("周二", "星期二"),
    WEDNESDAY("周三", "星期三"),
    THURSDAY("周四", "星期四"),
    FRIDAY("周五", "星期五"),
    SATURDAY("周六", "星期六"),
    SUNDAY("周日", "星期日");

    private final String label;
    private final String alias;

    Weekday(String label, String alias) {
        this.label = label;
        this.alias = alias;
    }

    public String label() {
        return label;
    }

    public String alias() {
        return alias;
    }

    /**
     * 宽松解析星期：支持「周一 / 星期一 / 周1 / 礼拜一 / 1 / MONDAY」等写法。
     *
     * @param raw 用户输入
     * @return 对应枚举
     * @throws IllegalArgumentException 无法识别时抛出，由调用方转换为友好提示
     */
    public static Weekday parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("星期不能为空，可写周一、星期一或 1");
        }
        String text = raw.trim().toUpperCase();
        for (Weekday day : values()) {
            if (text.equals(day.label) || text.equals(day.alias) || text.equals(day.name())) {
                return day;
            }
        }
        String digit = text.replace("星期", "").replace("周", "").replace("礼拜", "");
        if (digit.length() == 1 && Character.isDigit(digit.charAt(0))) {
            int index = digit.charAt(0) - '1';
            if (index >= 0 && index < values().length) {
                return values()[index];
            }
            if (digit.charAt(0) == '7') {
                return SUNDAY;
            }
        }
        throw new IllegalArgumentException("无法识别的星期：" + raw + "，可写周一至周日");
    }
}
