# 设计说明

本文记录 Timetable CLI 的分层设计、数据结构、核心算法与若干工程决策，供维护者与贡献者参考。使用方式请看 [USAGE.md](USAGE.md)，快速上手请看 [README.md](../README.md)。

## 1. 设计目标与取舍

| 目标 | 具体做法 | 代价 |
| --- | --- | --- |
| 零外部依赖 | 只用 JDK 标准库（`java.nio`、`java.time`、`java.util`），不用 Maven / Gradle | 需要手写 CSV 解析与表格渲染 |
| 数据可版本管理 | 课程表落在纯文本 CSV 里，改课表只改一行 | 每次查询都要重新解析文件（数据量小，可接受） |
| 输出可直接复用 | 表格用 Unicode 制表符，粘进 Markdown 代码块即可保持形状 | 必须自己处理汉字宽度，不能用 `String.length()` |
| 面向初学者 | 分层清晰、命名直白、关键算法加注释，不使用反射、注解、泛型技巧 | 代码偏"朴素"，不如框架式写法紧凑 |
| 命令行友好 | 统一的错误前缀、明确的退出码、命令可组合 `--data` | 需要自己写参数解析（约 40 行） |

## 2. 总体结构

```
                    ┌────────────────────────────┐
                    │        Main（入口）        │
                    │ 参数解析 / 命令分发 / 帮助 │
                    └─────────────┬──────────────┘
                                  │ 调用
             ┌────────────────────┼────────────────────┐
             ▼                    ▼                    ▼
     ┌───────────────┐   ┌─────────────────┐   ┌──────────────┐
     │      ui       │   │     service     │   │     data     │
     │ 渲染与排版    │◄──│ 查询与统计逻辑  │──►│ CSV 加载     │
     │ TextUtil      │   │ TimetableService│   │ CsvParser    │
     │ TimetablePrinter│ │ Semester        │   │ CourseRepo   │
     └───────────────┘   │ Conflict        │   └──────┬───────┘
                         └────────┬────────┘          │
                                  │ 使用              │ 构造
                                  ▼                   ▼
                         ┌──────────────────────────────────┐
                         │            model                 │
                         │ Weekday / Course / Periods       │
                         └──────────────────────────────────┘
```

依赖方向是单向的：`ui → service → data → model`。`model` 不依赖任何其它包，`data` 只依赖 `model`，这样模型与数据格式可以独立演进。

## 3. 数据模型

### 3.1 `Weekday`（星期枚举）

```java
public enum Weekday {
    MONDAY("周一", "星期一"),
    TUESDAY("周二", "星期二"),
    ...
    SUNDAY("周日", "星期日");
}
```

三个要点：

- `label()` 返回展示用简写（`周一`），`alias()` 返回全称（`星期一`）。
- `ordinal()` 直接被用于排序与"是否周末"判断（`>= 5` 即周六、周日），因为枚举声明顺序与 `LocalDate.getDayOfWeek()` 一致（周一为 0）。
- `parse(String raw)` 做宽松解析：先比对简写与全称，再尝试 `礼拜X` 形式，最后尝试数字 `1-7` 与英文缩写（`MON` / `mon`）。

### 3.2 `Course`（课程实体）

不可变对象，所有字段 `private final`，在构造器中完成校验：

```java
public Course(String id, String name, String teacher, String room, Weekday day,
              int startPeriod, int endPeriod, int startWeek, int endWeek, String type)
```

构造期校验规则：

| 校验项 | 规则 | 违规后果 |
| --- | --- | --- |
| 文本字段 | 非空白、不含换行 | 抛 `IllegalArgumentException` 并带上字段名 |
| 节次 | `1 ≤ startPeriod ≤ endPeriod ≤ 12` | 同上 |
| 周次 | `1 ≤ startWeek ≤ endWeek ≤ 16` | 同上 |

把校验放在构造器里，可以保证"内存中的 `Course` 一定是合法对象"，下游的查询、渲染代码不必到处写防御性判断。

领域方法（把业务语义收在实体上，而不是散落在服务层）：

| 方法 | 语义 |
| --- | --- |
| `activeIn(int week)` | 该课程在第 `week` 周是否开课（`startWeek ≤ week ≤ endWeek`） |
| `covers(int period)` | 该课程是否占用第 `period` 节 |
| `overlapsPeriodWith(Course other)` | 两门课节次区间是否相交：`max(start) ≤ min(end)` |
| `isFullTerm()` | 是否整学期课程（`1-16` 周） |
| `periodSpan()` / `periodLabel()` / `weekLabel()` | 生成 `第1-2节`、`第1-16周` 这类展示文本 |
| `matches(String keyword)` | 关键字是否命中课程名 / 教师 / 教室 / 编号（全转小写后 `contains`） |
| `compareTo(Course other)` | 按"星期 → 开始节次 → 课程编号"排序 |

### 3.3 `Periods`（节次工具）

集中管理"节次 → 上课时间"的映射，避免时间字符串散落在各处：

| 节次 | 时间 | 节次 | 时间 |
| --- | --- | --- | --- |
| 1 | 08:00-08:45 | 6 | 14:55-15:40 |
| 2 | 08:55-09:40 | 7 | 16:00-16:45 |
| 3 | 10:00-10:45 | 8 | 16:55-17:40 |
| 4 | 10:55-11:40 | 9 | 19:00-19:45 |
| 5 | 14:00-14:45 | 10 | 19:55-20:40 |

常量 `LAST = 12`（第 11、12 节为预留节次，数据中暂未使用），`partOfDay()` 把节次分为上午 / 下午 / 晚上三档，供图例分行展示。

### 3.4 `Semester`（学期）

```java
new Semester("2026-2027 学年第一学期", "区块链252", LocalDate.of(2026, 9, 7), 16)
```

构造器强制 `firstMonday` 必须是周一——这是"第 N 周"计算的锚点，如果不是周一，后面所有日期推算都会错位，因此宁可早失败。

关键方法：

- `weekOf(LocalDate date)`：`ChronoUnit.DAYS.between(firstMonday, date) / 7 + 1`，用 `Math.floorDiv` 处理开学前日期（返回 0 或负数）。
- `currentWeek()`：`clamp(weekOf(LocalDate.now()))`，把当前日期折算成教学周并夹到 `1-inclusive .. totalWeeks` 区间。
- `dateOf(Weekday day, int week)`：`firstMonday.plusWeeks(week-1).plusDays(day.ordinal())`。
- `titleOf(week)` / `rangeOf(week)`：生成 `第 9 周`、`2026-11-02 ~ 2026-11-08` 的展示文本。

以 2026-09-07 开学、总 16 周计算，第 9 周为 2026-11-02（周一）至 2026-11-08（周日），与程序实际输出一致。

## 4. 数据层

### 4.1 `CsvParser`

一个 60 余行的、只做一件事的状态机：按字符扫描，识别逗号分隔与双引号包裹，支持 `""` 转义为单个引号。之所以不引入第三方 CSV 库，是因为本项目数据格式可控（无换行字段、无嵌套引号），手写解析反而更透明、便于在注释中说明规则。

```java
public static List<String> splitLine(String line)
```

返回字段列表，调用方负责按下标取值与类型转换。

### 4.2 `CourseRepository`

职责边界：把文件变成对象集合，并做"跨行"级别的校验。

```java
public static CourseRepository load(Path csv)   // 读文件 → 跳注释 → 跳表头 → 逐行 toCourse
public List<Course> getAll()                    // 只读视图（Collections.unmodifiableList）
public Set<String> getRooms()                   // 数据中出现过的全部教室
public int size()
public Path getSource()
```

跨行校验：**课程编号唯一性**。同一编号出现两次时，提示重复编号与行号并终止加载。加载过程中收集"第几行出错"，报错信息形如：

```
[运行失败] 课程数据格式错误：data/courses.csv 第 5 行 —— 结束节次(2) 不能小于开始节次(4)
```

教室集合在加载时一次性收集，供 `free` / `freeRooms` 使用——这一点很重要：空教室查询的基数是"数据中出现过的教室全集"，而不是固定写死的教室清单。

## 5. 服务层

### 5.1 `TimetableService`

所有查询方法都遵循同一套参数约定：**先按周次过滤，再按星期 / 节次 / 关键字筛选**。这个顺序决定了"半学期课程不会误报"的正确性。

| 方法 | 说明 | 关键实现 |
| --- | --- | --- |
| `week(int week)` | 该周全部课程，已排序 | `filter(activeIn)` + `sort(naturalOrder)` |
| `day(Weekday, int week)` | 某天课程 | 在 `week()` 结果上按星期过滤 |
| `search(String, int week)` | 模糊搜索 | 在 `week()` 结果上 `matches()` |
| `activeAt(day, period, week)` | 某格上的所有课程 | 便于发现"同一格两门课"的异常数据 |
| `courseAt(day, period, week)` | 某格的代表课程 | 取 `activeAt` 的第一门，供网格渲染 |
| `maxPeriod(int week)` | 该周最晚到第几节 | 取 `max(endPeriod)`，下限 8，避免表格过短 |
| `freeRooms(day, period, week)` | 空闲教室 | 教室全集减去被占用的教室 |
| `busyRooms(day, period, week)` | 被占用教室及占用课程 | 用于 `free` 命令的"占用情况"段 |
| `occupiedPeriods` / `idlePeriods` | 某天被占 / 空闲节次列表 | 逐节次判断是否有课 |
| `conflicts(int week)` | 冲突列表 | 见 5.3 |
| `rooms()` | 教室清单 | 委托给 `CourseRepository` |

### 5.2 冲突模型 `Conflict`

```java
public record Conflict(Kind kind, Course left, Course right) { enum Kind { TIME, ROOM } }
```

（设计上本可用 `record` 一行表达，实现时选择了普通不可变类：字段 `private final` + 构造函数校验，因为需要在 `describe()` 中按类型拼装不同文案，用类比 record 更直观，也避免了把 `String` 拼装逻辑塞进紧凑构造器。）`describe()` 输出一行人类可读文本：

```
[时间冲突] 周二 第1-2节 Web前端开发（信工楼-401，第1-16周）  <->  区块链原理与技术（信工楼-401，第1-16周）
```

同一次比较可能同时产生 `TIME` 与 `ROOM` 两条记录：时间重叠是教室冲突的前提，两者分别计数，便于统计。

### 5.3 冲突检测算法

```
输入：该周生效的课程列表 courses（已按星期、节次排序）
输出：冲突列表

for i in 0 .. n-2:
    for j in i+1 .. n-1:
        a = courses[i], b = courses[j]
        if a.day != b.day: continue
        if !a.overlapsPeriodWith(b): continue
        add Conflict(TIME, a, b)
        if a.room == b.room: add Conflict(ROOM, a, b)
```

外层循环使用了排序带来的一个优化机会：若 `b.day` 已大于 `a.day`（按星期排序后一定成立），可提前 `break` 内层循环。当前实现保留了完整比较，n 约为 20 时耗时在微秒级，优先保证代码直白。

复杂度 O(n²)，n 为该周课程数。若将来数据量增大，可先按 `(星期, 节次)` 建桶再比较，复杂度降到 O(n·k)。

### 5.4 空教室计算

```
教室全集 R = repository.getRooms()
被占用集合 B = { c.room | c ∈ week(week), c.day = day, c.covers(period) }
空闲集合 F = R - B
```

注意 `c.covers(period)` 而非 `c.startPeriod == period`：一门课占 1-2 节，查询第 2 节时也应算作被占用。

## 6. 表现层

### 6.1 显示宽度计算

终端里汉字占 2 列，`String.length()` 返回的是 `char` 个数，两者不等。`TextUtil.width()` 逐个码点累加：

```java
public static int width(String text) {
    int w = 0;
    for (int i = 0; i < text.length(); ) {
        int cp = text.codePointAt(i);
        w += charWidth(cp);              // 东亚宽字符 2，其余 1
        i += Character.charCount(cp);
    }
    return w;
}
```

宽字符判定覆盖了 CJK 统一汉字、假名、韩文、全角符号等区间。因为按码点遍历，emoji 等代理对（surrogate pair）也不会被算成两个 1 宽字符。

### 6.2 表格渲染

`TextUtil.table()` 分三步：

1. **测量**：每列宽度 = 该列表头与所有单元格显示宽度的最大值。
2. **收缩**：按 `caps[]` 截断（超出部分替换为 `…`），按 `fixed[]` 强制列宽。
3. **绘制**：依次输出上边框、表头、分隔线、数据行、下边框；每行按 `aligns[]` 左/中/右补位。

`fixed[]` 是为课表网格新增的重载参数。最初版本各列宽度自适应，导致"周三"列比"周四"列窄一截，网格看起来参差；改为先统计全部单元格的最大内容宽度、再统一列宽后，网格变得规整。同时顶部横幅的宽度也改为"取表格首行的实际宽度"，让分隔线与表格严格等长。

### 6.3 输出结构

每种命令的输出都是同一个骨架，读起来有节奏感：

```
════════ 横幅（宽度 = 表格宽度）════════
 标题行
 副标题行（统计信息）
════════════════════════════════════════
表格 / 说明文字
补充段落（图例、明细、提示）
```

`printWeek` 额外输出节次时间图例（按上午/下午/晚上分行）与课程明细清单；`printFree` 输出占用情况与空闲教室两段，并用 `wrapJoin` 把长列表按固定宽度折行。

### 6.4 节次区间压缩

`ranges(List<Integer>)` 把 `[1,2,3,4,9,10]` 压缩成 `第1-4节、第9-10节`，避免出现"第1节、第2节、第3节、第4节"这样的冗长表达。做法是扫描相邻差值，连续段合并为 `start-end`，单节保持原样。

## 7. 入口层 `Main`

### 7.1 参数解析

不使用第三方 CLI 框架，`List<String> argv` 手动解析，规则简单：

- 第一个参数是命令名；`--data <路径>` 可以在任意位置出现，解析前先摘除。
- 其余参数按命令签名逐个取，`optional()` / `required()` 分别处理可省略与必填参数，缺参时抛出带用法提示的异常。

### 7.2 退出码约定

| 退出码 | 含义 | 触发场景 |
| --- | --- | --- |
| 0 | 执行成功 | 正常输出 |
| 1 | 运行失败 | 数据文件不存在、CSV 格式错误、编号重复 |
| 2 | 参数错误 | 未知命令、缺参、周次或节次越界 |

把"参数错误"和"运行失败"区分开，便于在脚本里判断是用法问题还是数据问题。

### 7.3 控制台编码

Windows 控制台默认代码页是 GBK，直接输出 UTF-8 中文会乱码。`Main` 启动时调用 `forceUtf8Console()` 用 UTF-8 重新包装 `System.out` / `System.err`：

```java
System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
```

配合 `run.bat` 里的 `chcp 65001`，中文在 Windows 与 Linux 下都能正确显示。

## 8. 构建与脚本

项目不使用构建工具，`javac` 直接编译：

```bash
javac -encoding UTF-8 -sourcepath src -d out src/timetable/Main.java
```

`-sourcepath` 让 javac 在编译 `Main` 时自动找到并编译它引用的其它包，因此只需指定一个入口文件。这条路径经过实测：11 个源文件、1664 行代码，编译产出 13 个 `.class`（含内部类）。

`run.bat` / `build.bat` 有两条值得记录的经验：

1. **不要用 `sources.txt` 传递源文件列表**。中文路径在 `javac @file` 与命令行编码转换之间容易被破坏，改用 `-sourcepath` 从根上规避。
2. **`.bat` 文件保持纯 ASCII**。`chcp 65001` 切换代码页后，`cmd.exe` 会按新代码页重新读取并定位脚本后续内容，若文件中混有 UTF-8 中文，行边界会错位，把注释内容当成命令执行（实测表现为 `'查看周四课表' is not recognized as an internal or external command`）。因此脚本中的提示信息改用英文，中文只出现在 Java 程序的输出里。

## 9. 测试与验证

项目未引入 JUnit（零依赖约束），采用"命令清单 + 真实输出比对"的方式验证。验证清单共 16 条命令，覆盖：

| 类别 | 覆盖点 |
| --- | --- |
| 正常路径 | `all`、`current`、`day`、`search`、`free`、`idle`、`conflict`、`rooms`、`help` |
| 边界与容错 | `all 1`（首周）、`all 16`（末周）、`all 20`（越界）、`day 星期一 9`（全称星期）、`free 周四 3`（跨两节的占用判断） |
| 异常路径 | 未知命令 `foo`、数据文件不存在、CSV 格式错误、重复课程编号 |
| 数据多样性 | 正常数据（无冲突）、冲突演示数据（2 条时间冲突 + 1 条教室冲突、1 门负样本不冲突） |
| 周次过滤 | 第 9 周 `day 周四` 应显示 Go 语言而非 Java 程序设计 |

其中冲突检测的验证数据 `courses_conflict_demo.csv` 是刻意设计的：在完整课表基础上追加 `D001`（与 `C004` 同周二 1-2 节、同教室 → 时间 + 教室双冲突）、`D002`（与 `C005` 同周二 3-4 节、不同教室 → 仅时间冲突）、`D003`（周四 9-10 节，无重叠 → 负样本，不应被检出）。这组数据能同时验证"检出能力"与"不误报能力"。

## 10. 扩展指引

| 想做的事 | 改动位置 |
| --- | --- |
| 加一门课 / 换班级课表 | 只改 `data/courses.csv`，无需改代码 |
| 新增查询命令 | `TimetableService` 加查询方法 → `TimetablePrinter` 加渲染 → `Main` 加分发分支与帮助文本 |
| 调整表格外观 | `TextUtil.table()` 与各 `printXxx` 的 `caps` 数组 |
| 换成真实教务数据源 | 实现一个新的 `XxxRepository`（接口形态与 `CourseRepository` 一致），`Main` 里替换加载方式 |
| 支持第 11-12 节 | `Periods` 补充时间映射，`Course` 的节次上限检查同步调整 |

加新命令时请同时更新 `docs/USAGE.md` 与 `CHANGELOG.md`，这是 [CONTRIBUTING.md](../CONTRIBUTING.md) 中的硬性要求。
