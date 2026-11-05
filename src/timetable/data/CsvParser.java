package timetable.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 CSV 解析器。
 *
 * <p>手写而非引入第三方库，原因有二：一是项目约定零依赖；二是课程数据中的
 * 「教室」「课程名」偶尔会出现英文逗号（如 {@code 教2-105,东区}），需要支持
 * 双引号包裹与转义，简单的 {@code String.split(",")} 无法正确处理。</p>
 *
 * <p>支持的语法：</p>
 * <ul>
 *   <li>{@code a,b,c}：普通三列；</li>
 *   <li>{@code "a,1",b}：双引号内的逗号视为普通字符；</li>
 *   <li>{@code "他说""好""",b}：两个连续双引号表示一个字面双引号。</li>
 * </ul>
 */
public final class CsvParser {

    private CsvParser() {
    }

    /**
     * 将一行 CSV 文本切分为字段列表。
     *
     * @param line 单行文本（不含换行符）
     * @return 字段列表，字段顺序与列顺序一致
     * @throws IllegalArgumentException 双引号未闭合时抛出
     */
    public static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("CSV 双引号未闭合：" + line);
        }
        fields.add(current.toString().trim());
        return fields;
    }
}
