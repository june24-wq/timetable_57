# 贡献指南

感谢你愿意为 Timetable CLI 出一份力。本项目体量不大，但希望每一次改动都清晰、可回溯。以下约定请在动手前先读一遍。

## 1. 行为准则

参与本项目的讨论、提交与评审，即表示你同意遵守 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)。简单说：对事不对人，不攻击、不歧视、不刷屏。

## 2. 开始之前

### 2.1 先开 Issue

除明显的错别字、注释修正外，请先提交 Issue 说明你发现的问题或想做的功能，避免多人重复造轮子。Issue 模板已提供：

- 缺陷 → [.github/ISSUE_TEMPLATE/bug_report.md](.github/ISSUE_TEMPLATE/bug_report.md)
- 新功能 → [.github/ISSUE_TEMPLATE/feature_request.md](.github/ISSUE_TEMPLATE/feature_request.md)

Issue 中请尽量写清：使用的 JDK 版本、执行的具体命令、期望结果与实际结果。如果有输出截图或文本片段，直接贴在代码块里。

### 2.2 认领任务

在 Issue 下留言"我来处理"，等待维护者确认或直接提交 PR 均可。若长时间无人跟进，欢迎直接提 PR。

## 3. 开发环境

```bash
git clone <仓库地址> timetable_57
cd timetable_57

# 编译
javac -encoding UTF-8 -sourcepath src -d out src/timetable/Main.java

# 运行
java -cp out timetable.Main all 9
```

本项目**不引入任何第三方依赖**，也不使用 Maven / Gradle。这是刻意保持的约束：任何提案若需要引入外部库，请在 Issue 中说明理由，维护者会优先考虑用 JDK 原生 API 实现。

## 4. 分支与提交规范

### 4.1 分支命名

| 类型 | 命名格式 | 示例 |
| --- | --- | --- |
| 新功能 | `feature/<简述>` | `feature/room-query` |
| 缺陷修复 | `fix/<简述>` | `fix/week-range-check` |
| 文档 | `docs/<简述>` | `docs/usage-examples` |
| 重构 | `refactor/<简述>` | `refactor/printer-split` |

主干为 `main`，请勿直接在 `main` 上提交。

### 4.2 提交信息

采用 `<类型>: <描述>` 的格式，描述用中文或英文均可，一句话讲清做了什么：

```
feat: 新增 room 命令按教室反查课表
fix: 修复第 17 周查询越界未提示的问题
docs: 补充 USAGE.md 中 idle 命令的示例输出
refactor: 将表格渲染逻辑从 TimetablePrinter 抽到 TextUtil
test: 补充星期解析的边界用例
```

类型取值：`feat` / `fix` / `docs` / `refactor` / `test` / `style` / `chore`。

一次提交只做一件事。若同时改了功能和格式，请拆成两个提交。

## 5. 代码风格

项目未强制引入格式化工具，但请保持与现有代码一致：

- **缩进** 4 个空格，不使用 Tab；行宽尽量控制在 120 字符以内。
- **命名**：类名 `UpperCamelCase`，方法与变量 `lowerCamelCase`，常量 `UPPER_SNAKE_CASE`。
- **注释**：公开类与方法必须有 Javadoc，说明"为什么这样做"而不只是"做了什么"；行内注释解释不直观的算法（例如显示宽度计算、冲突判定）。
- **不可变优先**：数据实体（如 `Course`）请保持不可变，字段 `private final`，通过构造器校验入参。
- **分层不被穿透**：`ui` 层不得直接读文件，`data` 层不得直接 `System.out.println`，查询逻辑一律写在 `service` 层。
- **异常处理**：面向用户的错误用统一的提示格式输出，不要直接把堆栈打到控制台；退出码约定见 `docs/DESIGN.md`。

## 6. 新增命令的步骤

以新增 `room <教室名>` 为例，通常只需改三处：

1. `service/TimetableService.java`：新增查询方法，返回 `List<Course>` 或统计结果，不涉及输出格式。
2. `ui/TimetablePrinter.java`：新增渲染方法，复用 `TextUtil.table()`。
3. `Main.java`：在命令分发处加分支，并在 `printHelp()` 里补一行说明。

若新增命令需要额外参数，请在 `Main` 的参数解析中做好缺参提示与越界校验，并保持与现有命令一致的错误格式（`[参数错误] ...`）。

## 7. 自测清单

提交 PR 前请自行过一遍：

- [ ] `javac -encoding UTF-8 -sourcepath src -d out src/timetable/Main.java` 无警告无报错。
- [ ] `java -cp out timetable.Main all 9` 输出的表格列对齐，无错行。
- [ ] `java -cp out timetable.Main day 周四`、`search 张伟`、`free 周四 3`、`idle 周四`、`conflict 9`、`rooms`、`help` 均能正常输出。
- [ ] `java -cp out timetable.Main conflict 9 --data data/courses_conflict_demo.csv` 能检出 2 条时间冲突与 1 条教室冲突。
- [ ] 非法输入有明确提示：`java -cp out timetable.Main all 20`、`java -cp out timetable.Main foo`、`--data` 指向不存在的文件。
- [ ] 改动了功能或输出格式时，同步更新 `README.md`、`docs/USAGE.md`、`CHANGELOG.md`。
- [ ] 没有提交 `out/` 目录、`.class` 文件、IDE 配置等应被 `.gitignore` 忽略的内容。

## 8. Pull Request

PR 模板见 [.github/PULL_REQUEST_TEMPLATE.md](.github/PULL_REQUEST_TEMPLATE.md)。提交时请：

1. 标题与提交信息风格一致。
2. 正文说明关联的 Issue（`Closes #12`）、改动点、验证方式。
3. 若输出格式有变化，附上改动前后的输出对比。
4. 一个 PR 只解决一个问题；涉及多个独立改动请拆开提交。

评审意见会在 PR 中直接讨论，请保持关注。若两周内无回应，可以在 Issue 里 @ 维护者提醒。

## 9. 关于数据文件

`data/courses.csv` 中的课程数据来自作者所在班级的真实课表，仅作为演示。**请勿在 PR 中提交他人的真实个人信息**（如完整教师姓名、联系方式）。若你想补充示例数据，请使用虚构姓名，或明确标注为示例。

## 10. 许可证

向本项目提交代码即表示你同意以 [MIT License](LICENSE) 授权你的贡献。
