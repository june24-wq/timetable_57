package timetable;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import timetable.data.CourseRepository;
import timetable.model.Periods;
import timetable.model.Weekday;
import timetable.service.Semester;
import timetable.service.TimetableService;
import timetable.ui.TextUtil;
import timetable.ui.TimetablePrinter;

/**
 * 命令行入口。
 *
 * <p>参数解析采用「命令 + 位置参数 + 可选 --data」的极简形式，不引入任何第三方 CLI 框架，
 * 保证项目只用 JDK 即可编译运行。</p>
 *
 * <pre>
 * java -cp out timetable.Main all 9
 * java -cp out timetable.Main day 周四
 * </pre>
 */
public final class Main {

    private static final String VERSION = "1.0.0";
    private static final String DEFAULT_DATA = "data/courses.csv";

    private Main() {
    }

    public static void main(String[] args) {
        forceUtf8Console();
        try {
            run(args);
        } catch (IllegalArgumentException e) {
            System.out.println("[参数错误] " + e.getMessage());
            System.out.println();
            printHelp();
            System.exit(2);
        } catch (Exception e) {
            System.out.println("[运行失败] " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 强制以 UTF-8 输出。
     *
     * <p>Windows 控制台默认代码页是 GBK，而源文件与数据文件均为 UTF-8，
     * 不统一编码会出现中文乱码；这里显式包裹 {@link FileDescriptor#out}，
     * 与 run.bat 中的 {@code chcp 65001} 配合使用。</p>
     */
    private static void forceUtf8Console() {
        try {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            // 输出流被重定向时不强求编码，保持 JVM 默认行为
        }
    }

    private static void run(String[] args) throws Exception {
        List<String> argv = new ArrayList<>();
        String dataFile = DEFAULT_DATA;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--data".equals(arg) || "-d".equals(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("--data 后面需要跟上数据文件路径");
                }
                dataFile = args[++i];
            } else if ("--version".equals(arg) || "-v".equals(arg)) {
                System.out.println("Timetable CLI " + VERSION + "（纯 JDK 实现，无第三方依赖）");
                return;
            } else {
                argv.add(arg);
            }
        }

        String command = argv.isEmpty() ? "help" : argv.get(0).toLowerCase();

        if ("help".equals(command) || "-h".equals(command) || "--help".equals(command)) {
            printHelp();
            return;
        }

        CourseRepository repository = CourseRepository.load(Paths.get(dataFile));
        TimetableService service = new TimetableService(repository);
        Semester semester = Semester.defaultSemester();

        switch (command) {
            case "all":
            case "week": {
                int week = resolveWeek(optional(argv, 1), semester);
                printWeekHint(semester, week);
                TimetablePrinter.printWeek(service, semester, week);
                break;
            }
            case "current":
            case "now": {
                int week = semester.currentWeek();
                printWeekHint(semester, week);
                TimetablePrinter.printWeek(service, semester, week);
                break;
            }
            case "day": {
                Weekday day = Weekday.parse(required(argv, 1, "day 命令需要指定星期，例如：day 周四"));
                int week = resolveWeek(optional(argv, 2), semester);
                printWeekHint(semester, week);
                TimetablePrinter.printDay(service, semester, day, week);
                break;
            }
            case "search":
            case "find": {
                String keyword = required(argv, 1, "search 命令需要给出关键字，例如：search 数据结构");
                int week = resolveWeek(optional(argv, 2), semester);
                TimetablePrinter.printSearch(service.search(keyword, week), keyword, semester, week);
                break;
            }
            case "free":
            case "rooms-free": {
                Weekday day = Weekday.parse(required(argv, 1, "free 命令需要指定星期，例如：free 周四 3"));
                int period = parsePeriod(required(argv, 2, "free 命令需要指定节次，例如：free 周四 3"));
                int week = resolveWeek(optional(argv, 3), semester);
                TimetablePrinter.printFree(service, semester, day, period, week);
                break;
            }
            case "idle":
            case "spare": {
                Weekday day = Weekday.parse(required(argv, 1, "idle 命令需要指定星期，例如：idle 周四"));
                int week = resolveWeek(optional(argv, 2), semester);
                TimetablePrinter.printIdle(service, semester, day, week);
                break;
            }
            case "conflict":
            case "check": {
                int week = resolveWeek(optional(argv, 1), semester);
                TimetablePrinter.printConflicts(service.conflicts(week), semester, week);
                break;
            }
            case "rooms":
            case "room": {
                TimetablePrinter.printRooms(service.rooms());
                break;
            }
            default:
                throw new IllegalArgumentException("未知命令：" + argv.get(0));
        }
    }

    /** 若系统日期不在学期内，给出一次温和提示，不影响主流程。 */
    private static void printWeekHint(Semester semester, int week) {
        LocalDate today = LocalDate.now();
        if (!semester.inTerm(today)) {
            System.out.println("提示：当前日期 " + today + " 不在学期区间内，已按第 " + week + " 周展示。");
            System.out.println();
        }
    }

    private static String optional(List<String> argv, int index) {
        return index < argv.size() ? argv.get(index) : null;
    }

    private static String required(List<String> argv, int index, String message) {
        String value = optional(argv, index);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** 解析周次参数：缺省或 now/当前 时取当前教学周。 */
    private static int resolveWeek(String raw, Semester semester) {
        if (raw == null || raw.trim().isEmpty()) {
            return semester.currentWeek();
        }
        String value = raw.trim();
        if ("now".equalsIgnoreCase(value) || "当前".equals(value) || "curr".equalsIgnoreCase(value)) {
            return semester.currentWeek();
        }
        int week;
        try {
            week = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("周次必须是 1-" + semester.getTotalWeeks() + " 之间的整数，当前为：" + raw);
        }
        if (week < 1 || week > semester.getTotalWeeks()) {
            throw new IllegalArgumentException("周次超出学期范围，应在 1-" + semester.getTotalWeeks() + " 之间，当前为：" + week);
        }
        return week;
    }

    private static int parsePeriod(String raw) {
        int period;
        try {
            period = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("节次必须是 1-" + Periods.LAST + " 之间的整数，当前为：" + raw);
        }
        if (!Periods.isValid(period)) {
            throw new IllegalArgumentException("节次超出范围，应在 1-" + Periods.LAST + " 之间，当前为：" + period);
        }
        return period;
    }

    private static void printHelp() {
        System.out.println(TextUtil.repeat('═', 96));
        System.out.println(" 课程表查询工具 Timetable CLI v" + VERSION + "　（纯 JDK 实现 · MIT License · 作者：王朋）");
        System.out.println(TextUtil.repeat('═', 96));
        System.out.println(" 用法：java -cp out timetable.Main <命令> [参数] [--data 数据文件]");
        System.out.println();
        String[][] items = {
                {"all [周次]", "查看整周课表，周次省略时使用当前教学周", "all 9"},
                {"current", "查看当前教学周的课表", "current"},
                {"day <星期> [周次]", "查看某一天的详细课表", "day 周四"},
                {"search <关键字> [周次]", "按课程名 / 教师 / 教室 / 编号模糊搜索", "search 区块链"},
                {"free <星期> <节次> [周次]", "查询指定时段的空教室", "free 周四 3"},
                {"idle <星期> [周次]", "统计某一天的空闲时段与可用教室数", "idle 周四"},
                {"conflict [周次]", "检测时间冲突与教室冲突", "conflict 9"},
                {"rooms", "列出数据中出现的全部教室", "rooms"},
                {"help", "显示本帮助", "help"}
        };
        String[] headers = {"命令", "说明", "示例"};
        int[] aligns = {TextUtil.ALIGN_LEFT, TextUtil.ALIGN_LEFT, TextUtil.ALIGN_LEFT};
        int[] caps = {30, 42, 24};
        List<String[]> rows = new ArrayList<>();
        for (String[] item : items) {
            rows.add(item);
        }
        System.out.println(TextUtil.table(headers, rows, aligns, caps));
        System.out.println();
        System.out.println(" 说明：星期支持「周一 / 星期一 / 1」等写法；周次范围 1-16；节次范围 1-12。");
        System.out.println(" 数据：默认读取 data/courses.csv，可用 --data <路径> 指定其它数据文件。");
    }
}
