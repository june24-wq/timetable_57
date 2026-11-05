package timetable.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import timetable.data.CourseRepository;
import timetable.model.Course;
import timetable.model.Periods;
import timetable.model.Weekday;

/**
 * 课表查询服务。
 *
 * <p>所有命令行功能最终都落到这里的若干查询方法上，UI 层只负责把结果排版成表格。
 * 这样拆分的好处是：查询逻辑可以脱离控制台单独测试，也方便未来替换成 Web 接口。</p>
 */
public final class TimetableService {

    private final CourseRepository repository;

    public TimetableService(CourseRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("课程仓库不能为空");
        }
        this.repository = repository;
    }

    public CourseRepository getRepository() {
        return repository;
    }

    /** 全部课程（按星期、节次排序）。 */
    public List<Course> all() {
        List<Course> list = new ArrayList<>(repository.getAll());
        Collections.sort(list);
        return list;
    }

    /** 某一教学周内的全部课程。 */
    public List<Course> week(int week) {
        List<Course> list = new ArrayList<>();
        for (Course course : repository.getAll()) {
            if (course.activeIn(week)) {
                list.add(course);
            }
        }
        Collections.sort(list);
        return list;
    }

    /** 某一教学周内某一天的课程。 */
    public List<Course> day(Weekday day, int week) {
        List<Course> list = new ArrayList<>();
        for (Course course : week(week)) {
            if (course.getDay() == day) {
                list.add(course);
            }
        }
        return list;
    }

    /** 按课程名 / 教师 / 教室 / 编号模糊搜索某一教学周的课程。 */
    public List<Course> search(String keyword, int week) {
        List<Course> list = new ArrayList<>();
        for (Course course : week(week)) {
            if (course.matches(keyword)) {
                list.add(course);
            }
        }
        return list;
    }

    /** 指定星期、指定节次、指定周次正在上课的课程。 */
    public List<Course> activeAt(Weekday day, int period, int week) {
        List<Course> list = new ArrayList<>();
        for (Course course : week(week)) {
            if (course.getDay() == day && course.covers(period)) {
                list.add(course);
            }
        }
        return list;
    }

    /** 指定时段的具体课程，没有则返回 {@code null}。 */
    public Course courseAt(Weekday day, int period, int week) {
        List<Course> list = activeAt(day, period, week);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 指定教学周内被排到的最大节次（至少返回 8，保证课表有完整的上下半天视图）。 */
    public int maxPeriod(int week) {
        int max = 8;
        for (Course course : week(week)) {
            max = Math.max(max, course.getEndPeriod());
        }
        return Math.min(max, Periods.LAST);
    }

    /** 指定时段的空闲教室（数据中出现过的教室全集减去被占用的教室）。 */
    public List<String> freeRooms(Weekday day, int period, int week) {
        Set<String> busy = new LinkedHashSet<>();
        for (Course course : activeAt(day, period, week)) {
            busy.add(course.getRoom());
        }
        List<String> free = new ArrayList<>();
        for (String room : repository.getRooms()) {
            if (!busy.contains(room)) {
                free.add(room);
            }
        }
        return free;
    }

    /** 指定时段被占用的教室。 */
    public List<Course> busyRooms(Weekday day, int period, int week) {
        return activeAt(day, period, week);
    }

    /**
     * 某一天的节次占用情况。
     *
     * @return 长度为 {@link Periods#LAST}+1 的布尔数组，下标即节次，下标 0 不使用
     */
    public boolean[] occupiedPeriods(Weekday day, int week) {
        boolean[] occupied = new boolean[Periods.LAST + 1];
        for (Course course : day(day, week)) {
            for (int p = course.getStartPeriod(); p <= course.getEndPeriod(); p++) {
                if (Periods.isValid(p)) {
                    occupied[p] = true;
                }
            }
        }
        return occupied;
    }

    /** 某一天完全没有课（即可以自由安排）的节次列表。 */
    public List<Integer> idlePeriods(Weekday day, int week) {
        boolean[] occupied = occupiedPeriods(day, week);
        List<Integer> idle = new ArrayList<>();
        for (int p = 1; p <= Periods.LAST; p++) {
            if (!occupied[p]) {
                idle.add(p);
            }
        }
        return idle;
    }

    /** 某一天有课的最大节次，用于渲染当日视图。 */
    public int maxPeriodOfDay(Weekday day, int week) {
        int max = 4;
        for (Course course : day(day, week)) {
            max = Math.max(max, course.getEndPeriod());
        }
        return Math.min(max, Periods.LAST);
    }

    /**
     * 检测指定教学周的排课冲突。
     *
     * <p>判定流程：先按周次过滤出真正生效的课程，再两两比较同星期的节次区间是否相交；
     * 相交即记为时间冲突，若两门课还共用同一教室，则额外记一条教室冲突。
     * 因为先做了周次过滤，第 1-8 周与第 9-16 周共用时段的两门课不会被误报。</p>
     */
    public List<Conflict> conflicts(int week) {
        List<Course> active = week(week);
        List<Conflict> result = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                Course left = active.get(i);
                Course right = active.get(j);
                if (!left.overlapsPeriodWith(right)) {
                    continue;
                }
                result.add(new Conflict(Conflict.Kind.TIME, left, right));
                if (left.getRoom().equals(right.getRoom())) {
                    result.add(new Conflict(Conflict.Kind.ROOM, left, right));
                }
            }
        }
        return result;
    }

    /** 数据中出现过的全部教室。 */
    public Set<String> rooms() {
        return repository.getRooms();
    }
}
