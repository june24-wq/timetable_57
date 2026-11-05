package timetable.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import timetable.model.Course;
import timetable.model.Weekday;

/**
 * 课程数据仓库。
 *
 * <p>职责单一：把 {@code data/courses.csv} 变成不可变的 {@link Course} 列表，
 * 并汇总数据中出现过的教室集合（空教室查询需要「教室全集」才能算出补集）。</p>
 *
 * <p>容错约定：以 {@code #} 开头的行是注释；首个非注释行若是表头（第 6 列不是数字）
 * 会被自动跳过；其余每行必须包含 10 列，列数或数值非法时抛出带行号的异常，
 * 便于快速定位脏数据。</p>
 */
public final class CourseRepository {

    private static final int EXPECTED_COLUMNS = 10;

    private final Path source;
    private final List<Course> courses;
    private final Set<String> rooms;

    private CourseRepository(Path source, List<Course> courses, Set<String> rooms) {
        this.source = source;
        this.courses = Collections.unmodifiableList(courses);
        this.rooms = Collections.unmodifiableSet(rooms);
    }

    /**
     * 从磁盘加载课程数据。
     *
     * @param file CSV 文件路径
     * @return 仓库实例
     * @throws IOException 文件不存在或读取失败
     */
    public static CourseRepository load(Path file) throws IOException {
        if (file == null) {
            throw new IOException("未指定课程数据文件");
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("课程数据文件不存在：" + file.toAbsolutePath()
                    + "（请在项目根目录执行命令，或使用 --data 指定路径）");
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Course> courses = new ArrayList<>();
        Set<String> rooms = new LinkedHashSet<>();
        boolean headerSkipped = false;

        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String raw = lines.get(i);
            if (lineNo == 1 && !raw.isEmpty() && raw.charAt(0) == '\uFEFF') {
                raw = raw.substring(1);
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                Course course = toCourse(line);
                if (course == null) {
                    if (!headerSkipped) {
                        headerSkipped = true;
                        continue;
                    }
                    throw new IllegalArgumentException("数据行缺少必要字段");
                }
                courses.add(course);
                rooms.add(course.getRoom());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("课程数据第 " + lineNo + " 行解析失败：" + e.getMessage(), e);
            }
        }

        if (courses.isEmpty()) {
            throw new IOException("课程数据文件为空或没有有效数据行：" + file.toAbsolutePath());
        }
        return new CourseRepository(file, courses, rooms);
    }

    /**
     * 单行转课程对象。
     *
     * @return 解析成功返回课程；若是表头行返回 {@code null}
     */
    private static Course toCourse(String line) {
        List<String> fields = CsvParser.splitLine(line);
        if (fields.size() < EXPECTED_COLUMNS) {
            throw new IllegalArgumentException("需要 " + EXPECTED_COLUMNS + " 列，实际 " + fields.size() + " 列");
        }
        int startPeriod;
        try {
            startPeriod = Integer.parseInt(fields.get(5));
        } catch (NumberFormatException e) {
            return null;
        }
        int endPeriod = Integer.parseInt(fields.get(6));
        int startWeek = Integer.parseInt(fields.get(7));
        int endWeek = Integer.parseInt(fields.get(8));
        return new Course(
                fields.get(0),
                fields.get(1),
                fields.get(2),
                fields.get(3),
                Weekday.parse(fields.get(4)),
                startPeriod,
                endPeriod,
                startWeek,
                endWeek,
                fields.get(9)
        );
    }

    public Path getSource() {
        return source;
    }

    public List<Course> getAll() {
        return courses;
    }

    /** 数据中出现过的全部教室（按首次出现顺序，便于按楼栋聚集展示）。 */
    public Set<String> getRooms() {
        return rooms;
    }

    public int size() {
        return courses.size();
    }
}
