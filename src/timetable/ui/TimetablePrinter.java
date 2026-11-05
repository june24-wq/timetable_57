package timetable.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import timetable.model.Course;
import timetable.model.Periods;
import timetable.model.Weekday;
import timetable.service.Conflict;
import timetable.service.Semester;
import timetable.service.TimetableService;

/**
 * 课表渲染器。
 *
 * <p>只负责「把数据画成文本」，不做任何查询与业务判断。所有方法直接写标准输出，
 * 输出内容全部使用 Unicode 制表符（┌┬┐├┼┤└┴┘），在不支持中文的终端里也能保持表格结构。</p>
 */
public final class TimetablePrinter {

    private static final int WIDTH = 104;
    private static final int CELL_MAX_WIDTH = 16;

    private TimetablePrinter() {
    }

    /** 整周课表。 */
    public static void printWeek(TimetableService service, Semester semester, int week) {
        List<Course> courses = service.week(week);
        String title = semester.titleOf(week);
        String subtitle = "日期区间：" + semester.rangeOf(week) + "　共 " + courses.size() + " 门次课程";

        if (courses.isEmpty()) {
            banner(title, subtitle);
            System.out.println("第 " + week + " 周没有排课，可能是假期或课程尚未开始。");
            return;
        }

        boolean hasWeekend = false;
        for (Course course : courses) {
            if (course.getDay().ordinal() >= 5) {
                hasWeekend = true;
            }
        }

        List<Weekday> days = new ArrayList<>();
        for (Weekday day : Weekday.values()) {
            if (hasWeekend || day.ordinal() < 5) {
                days.add(day);
            }
        }

        int maxPeriod = service.maxPeriod(week);
        String[] headers = new String[days.size() + 1];
        headers[0] = "节次";
        for (int i = 0; i < days.size(); i++) {
            headers[i + 1] = days.get(i).label();
        }

        int[] aligns = new int[headers.length];
        int[] caps = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            aligns[i] = TextUtil.ALIGN_CENTER;
            caps[i] = i == 0 ? 4 : CELL_MAX_WIDTH;
        }

        List<String[]> rows = new ArrayList<>();
        for (int period = 1; period <= maxPeriod; period++) {
            String[] row = new String[days.size() + 1];
            row[0] = String.valueOf(period);
            for (int i = 0; i < days.size(); i++) {
                Course course = service.courseAt(days.get(i), period, week);
                row[i + 1] = course == null ? "——" : course.getName();
            }
            rows.add(row);
        }

        int[] fixed = new int[headers.length];
        fixed[0] = 6;
        int dayWidth = 10;
        for (String[] row : rows) {
            for (int i = 1; i < row.length; i++) {
                dayWidth = Math.max(dayWidth, TextUtil.width(row[i]));
            }
        }
        dayWidth = Math.min(dayWidth, CELL_MAX_WIDTH);
        for (int i = 1; i < fixed.length; i++) {
            fixed[i] = dayWidth;
        }

        String grid = TextUtil.table(headers, rows, aligns, caps, fixed);
        banner(title, subtitle, TextUtil.width(grid.substring(0, grid.indexOf('\n'))));
        System.out.println(grid);
        System.out.println();
        printPeriodLegend(maxPeriod);
        System.out.println();
        printDetailList(courses);
    }

    /** 单日课表。 */
    public static void printDay(TimetableService service, Semester semester, Weekday day, int week) {
        List<Course> courses = service.day(day, week);
        String title = semester.getClassName() + " · 第 " + week + " 周 · " + day.label()
                + "（" + semester.dateOf(day, week) + "）";
        banner(title, semester.getName() + "　当日共 " + courses.size() + " 门次课程");

        if (courses.isEmpty()) {
            System.out.println("本日没有安排课程，12 个节次全部空闲。");
            return;
        }

        int maxPeriod = service.maxPeriodOfDay(day, week);
        String[] headers = {"节次", "时间", "课程名称", "任课教师", "教室", "周次", "类型"};
        int[] aligns = {TextUtil.ALIGN_CENTER, TextUtil.ALIGN_CENTER, TextUtil.ALIGN_LEFT,
                TextUtil.ALIGN_CENTER, TextUtil.ALIGN_LEFT, TextUtil.ALIGN_CENTER, TextUtil.ALIGN_CENTER};
        int[] caps = {4, 11, 22, 10, 14, 10, 8};

        List<String[]> rows = new ArrayList<>();
        for (int period = 1; period <= maxPeriod; period++) {
            Course course = service.courseAt(day, period, week);
            if (course == null) {
                rows.add(new String[]{String.valueOf(period), Periods.range(period), "（空闲）", "——", "——", "——", "——"});
            } else {
                rows.add(new String[]{
                        String.valueOf(period),
                        Periods.range(period),
                        course.getName(),
                        course.getTeacher(),
                        course.getRoom(),
                        course.weekLabel(),
                        course.getType()
                });
            }
        }
        System.out.println(TextUtil.table(headers, rows, aligns, caps));
        System.out.println();
        List<Integer> idle = service.idlePeriods(day, week);
        System.out.println("本日空闲节次：" + ranges(idle) + "（共 " + idle.size() + " 节）");
    }

    /** 搜索结果。 */
    public static void printSearch(List<Course> hits, String keyword, Semester semester, int week) {
        banner("搜索「" + keyword + "」· 第 " + week + " 周",
                semester.getName() + "　匹配 " + hits.size() + " 门次课程（匹配范围：课程名称 / 教师 / 教室 / 编号）");
        if (hits.isEmpty()) {
            System.out.println("未找到匹配课程。可尝试更短的关键字，或换用 --data 指定其它数据文件。");
            return;
        }
        printDetailList(hits);
    }

    /** 空教室查询。 */
    public static void printFree(TimetableService service, Semester semester, Weekday day, int period, int week) {
        List<String> free = service.freeRooms(day, period, week);
        List<Course> busy = service.busyRooms(day, period, week);
        int total = service.rooms().size();

        String title = day.label() + " 第 " + period + " 节（" + Periods.range(period) + "）· 第 " + week + " 周";
        banner(title, "教室总数 " + total + " 间　已被占用 " + busy.size() + " 间　空闲 " + free.size() + " 间");

        if (!busy.isEmpty()) {
            System.out.println("【占用情况】");
            for (Course course : busy) {
                System.out.println("  · " + TextUtil.padRight(course.getRoom(), 16)
                        + course.getName() + " / " + course.getTeacher() + " / " + course.weekLabel());
            }
            System.out.println();
        }

        System.out.println("【空闲教室】");
        System.out.println(TextUtil.wrapJoin(free, "　", WIDTH - 4));
        System.out.println();
        System.out.println("提示：空闲教室按数据中出现的教室全集计算，未包含教室容量、多媒体设备等属性。");
    }

    /** 空闲时段统计。 */
    public static void printIdle(TimetableService service, Semester semester, Weekday day, int week) {
        boolean[] occupied = service.occupiedPeriods(day, week);
        int totalRooms = service.rooms().size();
        List<Integer> idle = service.idlePeriods(day, week);

        String title = semester.getClassName() + " · 第 " + week + " 周 · " + day.label()
                + "（" + semester.dateOf(day, week) + "）空闲时段";
        banner(title, "一天 12 节　有课 " + (Periods.LAST - idle.size()) + " 节　空闲 " + idle.size() + " 节");

        String[] headers = {"节次", "时间", "时段", "班级状态", "本时段可用教室数"};
        int[] aligns = {TextUtil.ALIGN_CENTER, TextUtil.ALIGN_CENTER, TextUtil.ALIGN_CENTER,
                TextUtil.ALIGN_CENTER, TextUtil.ALIGN_CENTER};

        List<String[]> rows = new ArrayList<>();
        for (int period = 1; period <= Periods.LAST; period++) {
            int freeRooms = service.freeRooms(day, period, week).size();
            rows.add(new String[]{
                    String.valueOf(period),
                    Periods.range(period),
                    Periods.partOfDay(period),
                    occupied[period] ? "有课" : "空闲",
                    freeRooms + " / " + totalRooms
            });
        }
        System.out.println(TextUtil.table(headers, rows, aligns, null));
        System.out.println();
        System.out.println("连续空闲区间：" + ranges(idle));
    }

    /** 冲突检测结果。 */
    public static void printConflicts(List<Conflict> conflicts, Semester semester, int week) {
        banner("课程冲突检测 · 第 " + week + " 周",
                semester.getName() + "　检测范围：该周全部生效课程（已按周次过滤，半学期课程不会误报）");
        if (conflicts.isEmpty()) {
            System.out.println("结果：未发现时间冲突或教室冲突，本教学周排课安排合理。");
            return;
        }
        int timeCount = 0;
        int roomCount = 0;
        for (Conflict conflict : conflicts) {
            if (conflict.getKind() == Conflict.Kind.TIME) {
                timeCount++;
            } else {
                roomCount++;
            }
            System.out.println("  " + conflict.describe());
        }
        System.out.println();
        System.out.println("汇总：时间冲突 " + timeCount + " 条，教室冲突 " + roomCount + " 条，需人工调整排课。");
    }

    /** 教室清单。 */
    public static void printRooms(Set<String> rooms) {
        banner("教室清单", "数据中共出现 " + rooms.size() + " 间教室");
        String[] headers = {"序号", "教室", "是否被使用"};
        int[] aligns = {TextUtil.ALIGN_CENTER, TextUtil.ALIGN_LEFT, TextUtil.ALIGN_CENTER};
        List<String[]> rows = new ArrayList<>();
        int index = 1;
        for (String room : rooms) {
            rows.add(new String[]{String.valueOf(index++), room, "已排课"});
        }
        System.out.println(TextUtil.table(headers, rows, aligns, null));
    }

    /** 课程明细表。 */
    private static void printDetailList(List<Course> courses) {
        System.out.println("课程明细（按星期、节次排序）：");
        String[] headers = {"序号", "课程编号", "课程名称", "任课教师", "教室", "星期", "节次", "周次", "类型"};
        int[] aligns = {TextUtil.ALIGN_CENTER, TextUtil.ALIGN_CENTER, TextUtil.ALIGN_LEFT, TextUtil.ALIGN_CENTER,
                TextUtil.ALIGN_LEFT, TextUtil.ALIGN_CENTER, TextUtil.ALIGN_CENTER, TextUtil.ALIGN_CENTER,
                TextUtil.ALIGN_CENTER};
        int[] caps = {4, 8, 24, 10, 16, 6, 10, 10, 8};
        List<String[]> rows = new ArrayList<>();
        int index = 1;
        for (Course course : courses) {
            rows.add(new String[]{
                    String.valueOf(index++),
                    course.getId(),
                    course.getName(),
                    course.getTeacher(),
                    course.getRoom(),
                    course.getDay().label(),
                    course.periodLabel(),
                    course.weekLabel(),
                    course.getType()
            });
        }
        System.out.println(TextUtil.table(headers, rows, aligns, caps));
    }

    /** 节次时间对照表，两行展示，避免课表下方信息过长。 */
    private static void printPeriodLegend(int maxPeriod) {
        List<String> morning = new ArrayList<>();
        List<String> afternoon = new ArrayList<>();
        List<String> evening = new ArrayList<>();
        for (int period = 1; period <= maxPeriod; period++) {
            String item = period + " 节 " + Periods.range(period);
            if (period <= 4) {
                morning.add(item);
            } else if (period <= 8) {
                afternoon.add(item);
            } else {
                evening.add(item);
            }
        }
        StringBuilder sb = new StringBuilder("节次时间：");
        sb.append("上午 ").append(String.join("　", morning));
        System.out.println(sb);
        System.out.println("　　　　　下午 " + String.join("　", afternoon)
                + (evening.isEmpty() ? "" : "　晚上 " + String.join("　", evening)));
    }

    /** 把节次列表压缩成「第1-2、5、9-12节」这样的区间描述。 */
    private static String ranges(List<Integer> periods) {
        if (periods == null || periods.isEmpty()) {
            return "无";
        }
        List<String> parts = new ArrayList<>();
        int start = periods.get(0);
        int previous = start;
        for (int i = 1; i < periods.size(); i++) {
            int current = periods.get(i);
            if (current == previous + 1) {
                previous = current;
                continue;
            }
            parts.add(rangeLabel(start, previous));
            start = current;
            previous = current;
        }
        parts.add(rangeLabel(start, previous));
        return String.join("、", parts);
    }

    private static String rangeLabel(int start, int end) {
        return start == end ? "第" + start + "节" : "第" + start + "-" + end + "节";
    }

    private static void banner(String title, String subtitle) {
        banner(title, subtitle, WIDTH);
    }

    /** 输出顶部横幅，宽度可指定，便于与紧随其后的表格对齐。 */
    private static void banner(String title, String subtitle, int width) {
        System.out.println(TextUtil.repeat('═', width));
        System.out.println(" " + title);
        if (subtitle != null && !subtitle.isEmpty()) {
            System.out.println(" " + subtitle);
        }
        System.out.println(TextUtil.repeat('═', width));
    }
}
