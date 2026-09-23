package com.reglinearity.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.reglinearity.core.AnalysisResult;
import com.reglinearity.core.ConflictReport;
import com.reglinearity.core.History;
import com.reglinearity.core.LinearizationStep;
import com.reglinearity.core.OpKind;
import com.reglinearity.core.Operation;
import com.reglinearity.core.RegisterSpec;
import com.reglinearity.core.Verdict;

/**
 * 分析结论视图：三态结论标题 + 候选顺序（逐步寄存器值）或冲突操作与原因。
 */
final class ResultPane extends JPanel {

    private final JTextPane textPane = new JTextPane();

    ResultPane() {
        super(new BorderLayout());
        textPane.setEditable(false);
        textPane.setBackground(UiTheme.SURFACE);
        textPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JScrollPane scroll = new JScrollPane(textPane);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
    }

    void showIdle() {
        clear();
        appendLine("寄存器线性一致性分析器", UiTheme.ACCENT, 16, true);
        appendLine("编辑左侧调用历史并点击“分析”。", UiTheme.TEXT_MUTED, 13, false);
        appendLine("", UiTheme.TEXT_MUTED, 13, false);
        appendLine("判定规则：", UiTheme.TEXT, 13, true);
        appendLine("· 每次调用视为其调用/返回区间内某个时刻的一次原子操作；", UiTheme.TEXT_MUTED, 13, false);
        appendLine("· 区间不重叠的调用必须保持先后，重叠调用可按任意满足规范的顺序解释；", UiTheme.TEXT_MUTED, 13, false);
        appendLine("· 已返回调用不能丢弃；未返回调用可以忽略或补全；", UiTheme.TEXT_MUTED, 13, false);
        appendLine("· 预算耗尽只报“未判定”，不会误报为不一致。", UiTheme.TEXT_MUTED, 13, false);
    }

    void showIllegal(String error) {
        clear();
        appendLine("输入非法（不进行一致性判定）", UiTheme.BAD, 15, true);
        appendLine("", UiTheme.TEXT, 13, false);
        appendLine(error, UiTheme.TEXT, 13, false);
        appendLine("", UiTheme.TEXT, 13, false);
        appendLine("非法记录与“不一致”是两回事：请先修正记录后再分析。", UiTheme.WARN, 13, false);
    }

    void showResult(History history, AnalysisResult result) {
        clear();
        switch (result.verdict()) {
            case CONSISTENT -> showConsistent(history, result);
            case INCONSISTENT -> showInconsistent(history, result);
            case UNDECIDED -> showUndecided(result);
        }
    }

    private void showConsistent(History history, AnalysisResult result) {
        appendLine("✓ 结论：可解释（一致）", UiTheme.OK, 15, true);
        appendStats(result);
        List<LinearizationStep> steps = result.witness().orElse(List.of());
        appendLine("", UiTheme.TEXT, 13, false);
        appendLine("一个合法的原子顺序（寄存器初值 " + RegisterSpec.INITIAL_VALUE + "）：",
                UiTheme.TEXT, 13, true);
        appendLine("", UiTheme.TEXT, 13, false);
        for (int i = 0; i < steps.size(); i++) {
            LinearizationStep step = steps.get(i);
            Operation op = step.operation();
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%2d. ", i + 1));
            sb.append(String.format("%-7s %-4s ", op.label(), op.kind().keyword()));
            sb.append(argsText(op));
            sb.append(String.format("   %d → %d", step.valueBefore(), step.valueAfter()));
            if (op.completed()) {
                sb.append("   返回 ").append(returnText(op));
            }
            appendLine(sb.toString(), UiTheme.operationColor(op.kind()), 13, false);
            if (step.completedPending()) {
                appendLine("    └ 未返回调用，在本解释中按此返回值补全", UiTheme.WARN, 12, false);
            }
        }
        long finalValue = steps.isEmpty()
                ? RegisterSpec.INITIAL_VALUE
                : steps.get(steps.size() - 1).valueAfter();
        appendLine("", UiTheme.TEXT, 13, false);
        appendLine("最终寄存器值：" + finalValue, UiTheme.OK, 13, true);
        if (!result.droppedPending().isEmpty()) {
            StringBuilder sb = new StringBuilder("被忽略的未返回调用：");
            for (int i = 0; i < result.droppedPending().size(); i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append(history.operation(result.droppedPending().get(i)).label());
            }
            appendLine(sb.toString(), UiTheme.WARN, 13, false);
        }
    }

    private void showInconsistent(History history, AnalysisResult result) {
        appendLine("✗ 结论：不可解释（不一致）", UiTheme.BAD, 15, true);
        appendStats(result);
        ConflictReport c = result.conflict().orElseThrow();
        appendLine("", UiTheme.TEXT, 13, false);
        appendLine("以下合法前缀之后，所有“可安排”的已返回调用都无法作为原子步骤执行"
                + "（非最小反例）：", UiTheme.TEXT, 13, true);
        appendLine("", UiTheme.TEXT, 13, false);
        for (int i = 0; i < c.prefix().size(); i++) {
            LinearizationStep step = c.prefix().get(i);
            Operation op = step.operation();
            String line = String.format("%2d. %-7s %-4s %s   %d → %d",
                    i + 1, op.label(), op.kind().keyword(), argsText(op),
                    step.valueBefore(), step.valueAfter());
            appendLine(line, UiTheme.operationColor(op.kind()), 13, false);
        }
        long currentValue = c.prefix().isEmpty()
                ? RegisterSpec.INITIAL_VALUE
                : c.prefix().get(c.prefix().size() - 1).valueAfter();
        appendLine("    当前寄存器值：" + currentValue, UiTheme.TEXT, 13, true);
        appendLine("", UiTheme.TEXT, 13, false);
        appendLine("冲突操作：", UiTheme.BAD, 13, true);
        for (int i = 0; i < c.blockedIndices().size(); i++) {
            Operation op = history.operation(c.blockedIndices().get(i));
            appendLine(" · " + op.label() + " " + op.signature(), UiTheme.BAD, 13, true);
            appendLine("   原因：" + c.reasons().get(i), UiTheme.TEXT, 13, false);
        }
    }

    private void showUndecided(AnalysisResult result) {
        appendLine("? 结论：未判定（预算耗尽）", UiTheme.WARN, 15, true);
        appendStats(result);
        appendLine("", UiTheme.TEXT, 13, false);
        appendLine("搜索在预算内未能完成，不能据此判定不存在解释。", UiTheme.TEXT, 13, true);
        appendLine("可调大预算后重新分析；预算只截断搜索，不会把“没搜完”当作“不一致”。",
                UiTheme.TEXT_MUTED, 13, false);
    }

    private void appendStats(AnalysisResult result) {
        appendLine("搜索节点数：" + result.searchedNodes()
                + (result.verdict() == Verdict.UNDECIDED
                ? "（预算 " + result.budget() + "）" : ""),
                UiTheme.TEXT_MUTED, 12, false);
    }

    private static String argsText(Operation op) {
        long[] args = op.args();
        if (args.length == 0) {
            return "()    ";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i]);
        }
        sb.append(')');
        while (sb.length() < 9) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String returnText(Operation op) {
        if (op.kind().resultType() == OpKind.ResultType.BOOLEAN) {
            return Boolean.toString(op.resultBoolean().orElseThrow());
        }
        return Long.toString(op.resultInteger().orElseThrow());
    }

    private void clear() {
        textPane.setStyledDocument(new javax.swing.text.DefaultStyledDocument());
    }

    private void appendLine(String text, Color color, int size, boolean bold) {
        StyledDocument doc = textPane.getStyledDocument();
        SimpleAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, color);
        StyleConstants.setFontFamily(attr, UiTheme.MONO.getFamily());
        StyleConstants.setFontSize(attr, size);
        StyleConstants.setBold(attr, bold);
        try {
            doc.insertString(doc.getLength(), text + System.lineSeparator(), attr);
        } catch (javax.swing.text.BadLocationException ignored) {
            // 文档由本组件完全控制。
        }
    }
}
