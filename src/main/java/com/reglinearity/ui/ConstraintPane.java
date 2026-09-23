package com.reglinearity.ui;

import java.awt.Color;
import java.util.List;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.reglinearity.core.AnalysisResult;
import com.reglinearity.core.History;
import com.reglinearity.core.OpKind;
import com.reglinearity.core.Operation;

/**
 * 选中操作的约束详情：区间、必须先于/可与之重排的调用、记录的返回值与候选顺序中的位置。
 */
final class ConstraintPane extends JTextPane {

    ConstraintPane() {
        setEditable(false);
        setBackground(UiTheme.SURFACE);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 10, 8, 10));
        setFont(UiTheme.MONO);
    }

    void showNothing(History history) {
        StyledDocument doc = getStyledDocument();
        clear();
        if (history == null || history.size() == 0) {
            appendLine("点击时间线上的调用区间，查看它的区间约束与候选顺序位置。", UiTheme.TEXT_MUTED);
            return;
        }
        appendLine("共 " + history.size() + " 个调用。点击任一调用区间查看：", UiTheme.TEXT_MUTED);
        appendLine(" · 哪些调用与它非重叠、必须保序；", UiTheme.TEXT_MUTED);
        appendLine(" · 哪些调用与它重叠、可以重排；", UiTheme.TEXT_MUTED);
        appendLine(" · 它在候选原子顺序中的生效位置。", UiTheme.TEXT_MUTED);
    }

    void show(History history, AnalysisResult result, int index) {
        clear();
        Operation op = history.operation(index);
        appendLine(op.label() + "  " + op.signature(), UiTheme.TEXT);
        append("客户端：", UiTheme.TEXT_MUTED);
        appendLine(op.client(), UiTheme.TEXT);
        append("操作：", UiTheme.TEXT_MUTED);
        appendLine(op.kind().keyword() + " — " + op.kind().description(),
                UiTheme.operationColor(op.kind()));
        append("调用事件序号：", UiTheme.TEXT_MUTED);
        appendLine(Long.toString(op.invokeSeq()), UiTheme.TEXT);
        append("返回事件序号：", UiTheme.TEXT_MUTED);
        appendLine(op.completed() ? Long.toString(op.returnSeq().getAsLong()) : "（无，未返回调用）",
                op.completed() ? UiTheme.TEXT : UiTheme.WARN);
        appendLine("", UiTheme.TEXT);

        List<Integer> preds = history.predecessors(index);
        append("必须先于本调用（区间不重叠）：", UiTheme.WARN);
        appendLine(preds.isEmpty() ? "无" : joinLabels(history, preds), UiTheme.TEXT);

        List<Integer> overlaps = history.overlaps(index);
        append("与本调用重叠（可任意合法重排）：", UiTheme.ACCENT);
        appendLine(overlaps.isEmpty() ? "无" : joinLabels(history, overlaps), UiTheme.TEXT);

        if (result != null) {
            appendLine("", UiTheme.TEXT);
            annotateOrdering(history, result, index, op);
        }
    }

    private void annotateOrdering(History history, AnalysisResult result, int index, Operation op) {
        switch (result.verdict()) {
            case CONSISTENT -> {
                java.util.List<com.reglinearity.core.LinearizationStep> steps =
                        result.witness().orElse(java.util.List.of());
                int pos = -1;
                for (int i = 0; i < steps.size(); i++) {
                    if (steps.get(i).operation().index() == index) {
                        pos = i + 1;
                        break;
                    }
                }
                if (pos > 0) {
                    append("候选顺序中的位置：", UiTheme.OK);
                    appendLine("第 " + pos + " / " + steps.size() + " 步", UiTheme.TEXT);
                    if (!op.completed()) {
                        appendLine("（该调用未返回，在本解释中按规范补全了返回值）", UiTheme.WARN);
                    }
                } else if (!op.completed() && result.droppedPending().contains(index)) {
                    appendLine("该未返回调用在当前解释中被忽略。", UiTheme.WARN);
                }
            }
            case INCONSISTENT -> {
                int prefixSize = result.conflict().map(c -> c.prefix().size()).orElse(0);
                if (result.conflict().map(c -> c.blockedIndices().contains(index)).orElse(false)) {
                    appendLine("该调用是冲突操作之一：在上述合法前缀的寄存器值上，"
                            + "它无法作为原子步骤执行。", UiTheme.BAD);
                } else {
                    appendLine("不可解释；冲突前缀共 " + prefixSize + " 步。", UiTheme.TEXT_MUTED);
                }
            }
            case UNDECIDED -> appendLine(
                    "预算耗尽，尚未搜索到该调用参与的任何完整顺序，不代表不存在解释。",
                    UiTheme.WARN);
        }
    }

    private static String joinLabels(History history, List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < indices.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            Operation op = history.operation(indices.get(i));
            sb.append(op.label()).append('(').append(op.kind().keyword()).append(')');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 富文本小工具
    // ------------------------------------------------------------------

    private void clear() {
        setStyledDocument(new javax.swing.text.DefaultStyledDocument());
    }

    private void append(String text, Color color) {
        StyledDocument doc = getStyledDocument();
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, color);
        StyleConstants.setFontFamily(attr, UiTheme.MONO.getFamily());
        StyleConstants.setFontSize(attr, 13);
        try {
            doc.insertString(doc.getLength(), text, attr);
        } catch (javax.swing.text.BadLocationException ignored) {
            // 文档由本组件完全控制，不会发生。
        }
    }

    private void appendLine(String text, Color color) {
        append(text + System.lineSeparator(), color);
    }
}
