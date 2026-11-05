package timetable.service;

import timetable.model.Course;

/**
 * 一条冲突记录。
 *
 * <p>冲突分两类：</p>
 * <ul>
 *   <li>{@link Kind#TIME}：同一班级在同一时段被安排了两门课程（排课表自身的逻辑错误）；</li>
 *   <li>{@link Kind#ROOM}：同一教室在同一时段被两门课程占用（资源调度错误）。</li>
 * </ul>
 */
public final class Conflict {

    public enum Kind {
        TIME("时间冲突", "同一时段被安排了两门及以上课程"),
        ROOM("教室冲突", "同一教室在同一时段被多门课程占用");

        private final String label;
        private final String description;

        Kind(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }
    }

    private final Kind kind;
    private final Course left;
    private final Course right;

    public Conflict(Kind kind, Course left, Course right) {
        this.kind = kind;
        this.left = left;
        this.right = right;
    }

    public Kind getKind() {
        return kind;
    }

    public Course getLeft() {
        return left;
    }

    public Course getRight() {
        return right;
    }

    /** 人类可读的冲突描述。 */
    public String describe() {
        return "[" + kind.label() + "] " + left.getDay().label() + " " + left.periodLabel()
                + " " + left.getName() + "（" + left.getRoom() + "，" + left.weekLabel() + "）"
                + "  <->  "
                + right.getName() + "（" + right.getRoom() + "，" + right.weekLabel() + "）";
    }
}
