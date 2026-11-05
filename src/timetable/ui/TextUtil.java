package timetable.ui;

import java.util.List;

/**
 * 控制台排版工具。
 *
 * <p>Java 的 {@code String.length()} 返回的是 UTF-16 代码单元数，一个汉字算 1，
 * 但在等宽字体终端里汉字占 2 个字符宽。若直接用 {@code String.format("%-10s")}
 * 对齐中英混排文本，课表会变成锯齿状，因此本项目自行实现了「显示宽度」计算。</p>
 */
public final class TextUtil {

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    private TextUtil() {
    }

    /** 计算字符串在等宽终端下的显示宽度，东亚宽字符按 2 计。 */
    public static int width(String text) {
        if (text == null) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            w += charWidth(cp);
            i += Character.charCount(cp);
        }
        return w;
    }

    /** 单个码点的显示宽度。 */
    public static int charWidth(int cp) {
        if (cp < 0x1100) {
            return 1;
        }
        boolean wide = cp <= 0x115F
                || cp == 0x2329 || cp == 0x232A
                || (cp >= 0x2E80 && cp <= 0xA4CF && cp != 0x303F)
                || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFE30 && cp <= 0xFE6F)
                || (cp >= 0xFF00 && cp <= 0xFF60)
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x20000 && cp <= 0x3FFFD);
        return wide ? 2 : 1;
    }

    public static String spaces(int n) {
        return n <= 0 ? "" : " ".repeat(n);
    }

    public static String repeat(char c, int n) {
        return n <= 0 ? "" : String.valueOf(c).repeat(n);
    }

    /** 右侧补空格，使显示宽度达到目标值。 */
    public static String padRight(String text, int targetWidth) {
        int pad = targetWidth - width(text);
        return pad <= 0 ? text : text + spaces(pad);
    }

    /** 左侧补空格。 */
    public static String padLeft(String text, int targetWidth) {
        int pad = targetWidth - width(text);
        return pad <= 0 ? text : spaces(pad) + text;
    }

    /** 居中补空格，多余的空格优先放在右侧。 */
    public static String padCenter(String text, int targetWidth) {
        int pad = targetWidth - width(text);
        if (pad <= 0) {
            return text;
        }
        int left = pad / 2;
        return spaces(left) + text + spaces(pad - left);
    }

    /** 按显示宽度截断，超出时以省略号结尾。 */
    public static String truncate(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (width(text) <= maxWidth) {
            return text;
        }
        if (maxWidth <= 1) {
            return "…";
        }
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int cw = charWidth(cp);
            if (w + cw > maxWidth - 1) {
                break;
            }
            sb.appendCodePoint(cp);
            w += cw;
            i += Character.charCount(cp);
        }
        return sb.append('…').toString();
    }

    /** 用逗号把列表拼成一行，超出最大宽度时自动换行续接。 */
    public static String wrapJoin(List<String> items, String separator, int maxWidth) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder line = new StringBuilder();
        StringBuilder out = new StringBuilder();
        int lineWidth = 0;
        for (int i = 0; i < items.size(); i++) {
            String token = items.get(i) + (i == items.size() - 1 ? "" : separator);
            int tokenWidth = width(token);
            if (lineWidth > 0 && lineWidth + tokenWidth > maxWidth) {
                out.append(line).append('\n');
                line.setLength(0);
                lineWidth = 0;
                line.append("  ");
                lineWidth = 2;
            }
            line.append(token);
            lineWidth += tokenWidth;
        }
        out.append(line);
        return out.toString();
    }

    /**
     * 渲染一张 Unicode 制表符表格。
     *
     * @param headers 表头
     * @param rows    数据行，列数需与表头一致，缺列按空字符串处理
     * @param aligns  每列对齐方式，{@code null} 表示全部左对齐
     * @param caps    每列最大显示宽度（0 表示不限制），{@code null} 表示不限制
     */
    public static String table(String[] headers, List<String[]> rows, int[] aligns, int[] caps) {
        return table(headers, rows, aligns, caps, null);
    }

    /**
     * 渲染 Unicode 制表符表格（支持强制列宽）。
     * <p>强制列宽用于「课表网格」这类要求各列等宽的场合：先按内容自适应，再用 fixed 覆盖。</p>
     *
     * @param fixed 每列强制显示宽度（0 或负值表示沿用自适应结果），{@code null} 表示全部自适应
     */
    public static String table(String[] headers, List<String[]> rows, int[] aligns, int[] caps, int[] fixed) {
        int cols = headers.length;
        int[] widths = new int[cols];
        for (int c = 0; c < cols; c++) {
            widths[c] = width(headers[c]);
        }
        for (String[] row : rows) {
            for (int c = 0; c < cols; c++) {
                String cell = cellAt(row, c);
                widths[c] = Math.max(widths[c], width(cell));
            }
        }
        for (int c = 0; c < cols && caps != null; c++) {
            if (caps[c] > 0 && widths[c] > caps[c]) {
                widths[c] = caps[c];
            }
        }
        for (int c = 0; c < cols && fixed != null; c++) {
            if (fixed[c] > 0) {
                widths[c] = fixed[c];
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(border(widths, '┌', '┬', '┐')).append('\n');
        sb.append(rowLine(headers, widths, aligns)).append('\n');
        sb.append(border(widths, '├', '┼', '┤')).append('\n');
        for (String[] row : rows) {
            sb.append(rowLine(row, widths, aligns)).append('\n');
        }
        sb.append(border(widths, '└', '┴', '┘'));
        return sb.toString();
    }

    private static String cellAt(String[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return "";
        }
        return row[index];
    }

    private static String border(int[] widths, char left, char middle, char right) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int c = 0; c < widths.length; c++) {
            sb.append(repeat('─', widths[c] + 2));
            sb.append(c == widths.length - 1 ? right : middle);
        }
        return sb.toString();
    }

    private static String rowLine(String[] cells, int[] widths, int[] aligns) {
        StringBuilder sb = new StringBuilder();
        sb.append('│');
        for (int c = 0; c < widths.length; c++) {
            String text = truncate(cellAt(cells, c), widths[c]);
            int align = aligns == null ? ALIGN_LEFT : aligns[c];
            if (align == ALIGN_CENTER) {
                text = padCenter(text, widths[c]);
            } else if (align == ALIGN_RIGHT) {
                text = padLeft(text, widths[c]);
            } else {
                text = padRight(text, widths[c]);
            }
            sb.append(' ').append(text).append(' ').append('│');
        }
        return sb.toString();
    }
}
