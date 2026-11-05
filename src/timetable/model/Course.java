package timetable.model;

import java.util.Objects;

/**
 * 课程实体。
 *
 * <p>课程是不可变对象：一行 CSV 数据对应一个 {@code Course} 实例，加载后不再修改，
 * 所有查询都基于同一份快照，避免并发遍历时出现状态漂移。</p>
 *
 * <p>核心字段含义：</p>
 * <ul>
 *   <li>{@code startPeriod / endPeriod}：连续占用的节次区间，单节课时两者相等；</li>
 *   <li>{@code startWeek / endWeek}：生效的教学周区间，用于支持「半学期课程」，
 *       例如第 1-8 周与第 9-16 周共用同一时段的课程不算冲突。</li>
 * </ul>
 */
public final class Course implements Comparable<Course> {

    private final String id;
    private final String name;
    private final String teacher;
    private final String room;
    private final Weekday day;
    private final int startPeriod;
    private final int endPeriod;
    private final int startWeek;
    private final int endWeek;
    private final String type;

    public Course(String id, String name, String teacher, String room, Weekday day,
                  int startPeriod, int endPeriod, int startWeek, int endWeek, String type) {
        this.id = requireText(id, "课程编号");
        this.name = requireText(name, "课程名称");
        this.teacher = requireText(teacher, "任课教师");
        this.room = requireText(room, "上课教室");
        this.day = Objects.requireNonNull(day, "星期不能为空");
        this.type = (type == null || type.trim().isEmpty()) ? "未分类" : type.trim();

        if (startPeriod < 1 || endPeriod < startPeriod) {
            throw new IllegalArgumentException("节次区间非法：" + startPeriod + "-" + endPeriod);
        }
        if (startWeek < 1 || endWeek < startWeek) {
            throw new IllegalArgumentException("周次区间非法：" + startWeek + "-" + endWeek);
        }
        this.startPeriod = startPeriod;
        this.endPeriod = endPeriod;
        this.startWeek = startWeek;
        this.endWeek = endWeek;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTeacher() {
        return teacher;
    }

    public String getRoom() {
        return room;
    }

    public Weekday getDay() {
        return day;
    }

    public int getStartPeriod() {
        return startPeriod;
    }

    public int getEndPeriod() {
        return endPeriod;
    }

    public int getStartWeek() {
        return startWeek;
    }

    public int getEndWeek() {
        return endWeek;
    }

    public String getType() {
        return type;
    }

    /** 是否为全学期课程（第 1 周一直到第 16 周）。 */
    public boolean isFullTerm() {
        return startWeek == 1 && endWeek == 16;
    }

    /** 该课程是否在指定教学周生效。 */
    public boolean activeIn(int week) {
        return week >= startWeek && week <= endWeek;
    }

    /** 该课程是否覆盖指定节次。 */
    public boolean covers(int period) {
        return period >= startPeriod && period <= endPeriod;
    }

    /** 两门课程在星期与节次上是否存在重叠（不判断周次）。 */
    public boolean overlapsPeriodWith(Course other) {
        if (other == null || day != other.day) {
            return false;
        }
        return startPeriod <= other.endPeriod && other.startPeriod <= endPeriod;
    }

    /** 连续占用的节数。 */
    public int periodSpan() {
        return endPeriod - startPeriod + 1;
    }

    /** 节次的中文描述，例如「第3-4节」。 */
    public String periodLabel() {
        return startPeriod == endPeriod
                ? "第" + startPeriod + "节"
                : "第" + startPeriod + "-" + endPeriod + "节";
    }

    /** 周次的中文描述，例如「第1-8周」。 */
    public String weekLabel() {
        return "第" + startWeek + "-" + endWeek + "周";
    }

    /** 判断关键字是否命中课程名、教师或教室（忽略大小写）。 */
    public boolean matches(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return true;
        }
        String key = keyword.trim().toLowerCase();
        return name.toLowerCase().contains(key)
                || teacher.toLowerCase().contains(key)
                || room.toLowerCase().contains(key)
                || id.toLowerCase().contains(key);
    }

    @Override
    public int compareTo(Course other) {
        int byDay = Integer.compare(day.ordinal(), other.day.ordinal());
        if (byDay != 0) {
            return byDay;
        }
        int byPeriod = Integer.compare(startPeriod, other.startPeriod);
        if (byPeriod != 0) {
            return byPeriod;
        }
        int byWeek = Integer.compare(startWeek, other.startWeek);
        if (byWeek != 0) {
            return byWeek;
        }
        return name.compareTo(other.name);
    }

    @Override
    public String toString() {
        return day.label() + " " + periodLabel() + " " + name + "(" + teacher + "/" + room + ") " + weekLabel();
    }
}
