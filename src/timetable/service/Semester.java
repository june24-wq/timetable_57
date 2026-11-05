package timetable.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import timetable.model.Weekday;

/**
 * 学期日历。
 *
 * <p>「当前第几周」不是写死的常量，而是用「学期第一周的周一」与系统日期换算，
 * 这样程序在任何一天运行都能得到正确的教学周，也方便下一学期只改一处配置。</p>
 *
 * <p>换算规则：{@code week = floor((today - firstMonday) / 7) + 1}，
 * 使用 {@link Math#floorDiv} 保证日期早于开学时得到 0 或负数而不是错误的正数。</p>
 */
public final class Semester {

    private final String name;
    private final String className;
    private final LocalDate firstMonday;
    private final int totalWeeks;

    public Semester(String name, String className, LocalDate firstMonday, int totalWeeks) {
        if (firstMonday == null) {
            throw new IllegalArgumentException("开学日期不能为空");
        }
        if (firstMonday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("开学日期必须是周一：" + firstMonday);
        }
        if (totalWeeks < 1) {
            throw new IllegalArgumentException("教学周总数必须为正数");
        }
        this.name = name;
        this.className = className;
        this.firstMonday = firstMonday;
        this.totalWeeks = totalWeeks;
    }

    /** 本项目对应的学期配置：2026-2027 学年第一学期，2026-09-07 开学，共 16 周。 */
    public static Semester defaultSemester() {
        return new Semester("2026-2027 学年第一学期", "区块链252", LocalDate.of(2026, 9, 7), 16);
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }

    public LocalDate getFirstMonday() {
        return firstMonday;
    }

    public int getTotalWeeks() {
        return totalWeeks;
    }

    /** 计算某个日期所属的教学周；早于开学返回小于 1 的值，超出学期返回大于总周数的值。 */
    public int weekOf(LocalDate date) {
        long days = ChronoUnit.DAYS.between(firstMonday, date);
        return (int) Math.floorDiv(days, 7L) + 1;
    }

    /**
     * 当前教学周。
     *
     * <p>若系统日期不在学期内（例如假期或跨学期运行），会被收敛到 1 或总周数，
     * 并在 {@link #inTerm(LocalDate)} 中给出提示，避免抛出异常打断使用。</p>
     */
    public int currentWeek() {
        return clamp(weekOf(LocalDate.now()));
    }

    public boolean inTerm(LocalDate date) {
        int week = weekOf(date);
        return week >= 1 && week <= totalWeeks;
    }

    public int clamp(int week) {
        if (week < 1) {
            return 1;
        }
        return Math.min(week, totalWeeks);
    }

    /** 指定教学周、指定星期的具体日期。 */
    public LocalDate dateOf(Weekday day, int week) {
        return firstMonday.plusWeeks(clamp(week) - 1L).plusDays(day.ordinal());
    }

    /** 指定教学周的周一。 */
    public LocalDate mondayOf(int week) {
        return firstMonday.plusWeeks(clamp(week) - 1L);
    }

    /** 指定教学周的周日。 */
    public LocalDate sundayOf(int week) {
        return mondayOf(week).plusDays(6);
    }

    /** 用于标题渲染的学期信息，例如「2026-2027 学年第一学期 · 区块链252 · 第 9 周」。 */
    public String titleOf(int week) {
        return name + " · " + className + " · 第 " + week + " 周";
    }

    /** 用于标题渲染的日期区间，例如「2026-11-02 ~ 2026-11-08」。 */
    public String rangeOf(int week) {
        return mondayOf(week).toString() + " ~ " + sundayOf(week).toString();
    }
}
