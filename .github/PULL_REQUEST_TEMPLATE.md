## 这个 PR 做了什么

<!-- 一两句话说清改动。例如：新增 room 命令，按教室名反查该教室一周的占用情况。 -->

## 关联 Issue

<!-- 用 Closes / Fixes 自动关闭对应 Issue，没有关联 Issue 请说明原因 -->

Closes #

## 改动类型

- [ ] 新功能（feat）
- [ ] 缺陷修复（fix）
- [ ] 文档（docs）
- [ ] 重构（refactor）
- [ ] 测试与验证（test）
- [ ] 排版 / 格式（style）
- [ ] 构建脚本或杂项（chore）

## 改动清单

<!-- 逐条列出改动的文件与要点，方便评审快速定位 -->

| 文件 | 改动说明 |
| --- | --- |
| `src/timetable/...` |  |
| `docs/...` |  |

## 验证方式

### 执行过的命令

```bash
javac -encoding UTF-8 -sourcepath src -d out src/timetable/Main.java
java -cp out timetable.Main all 9
```

### 输出对比（若输出格式有变化，请务必提供）

改动前：

```
```

改动后：

```
```

## 自检清单

- [ ] 已通过 `javac -encoding UTF-8 -sourcepath src -d out src/timetable/Main.java` 编译，无警告无报错
- [ ] `all`、`day`、`search`、`free`、`idle`、`conflict`、`rooms`、`help` 均能正常运行
- [ ] 表格列对齐正常，中文字符宽度计算无错位
- [ ] 参数错误、数据文件缺失等异常路径有明确提示，退出码符合约定（1 = 运行失败，2 = 参数错误）
- [ ] 未引入任何第三方依赖，未新增 Maven / Gradle 配置文件
- [ ] 未提交 `out/`、`*.class`、IDE 配置等被 `.gitignore` 忽略的内容
- [ ] 已同步更新 `README.md` / `docs/USAGE.md` / `docs/DESIGN.md`（如涉及）
- [ ] 已在 `CHANGELOG.md` 的 `[Unreleased]` 下补充本次改动
- [ ] 一个 PR 只解决一个问题，未夹带无关改动

## 对使用者的影响

<!-- 是否会改变已有命令的输出格式？是否需要用户更新数据文件？无影响可写"无" -->

## 备注

<!-- 遗留问题、后续计划、想请评审重点看的地方 -->
