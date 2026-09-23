package com.regcheck.ui;

import com.regcheck.core.Call;
import com.regcheck.core.CheckResult;
import com.regcheck.core.History;
import com.regcheck.core.LinearizabilityChecker;
import com.regcheck.core.ResultText;
import com.regcheck.core.Samples;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 主窗口：调用表 + 客户端时间线 + 约束视图 + 分析结果。 */
public final class AppFrame extends JFrame {

    private final CallTableModel tableModel = new CallTableModel();
    private final JTable table = new JTable(tableModel);
    private final TimelinePanel timeline = new TimelinePanel();
    private final JTextArea constraintsArea = new JTextArea(6, 30);
    private final JTextArea resultArea = new JTextArea(10, 40);
    private final JComboBox<String> sampleCombo =
            new JComboBox<>(Samples.all().keySet().toArray(new String[0]));
    private final JTextField budgetField =
            new JTextField(String.valueOf(LinearizabilityChecker.DEFAULT_BUDGET), 10);
    private final JButton analyzeButton = new JButton("分析");
    private JSplitPane center;
    private JSplitPane rightSplit;
    private JSplitPane bottomSplit;

    public static void open() {
        AppFrame frame = new AppFrame();
        frame.setVisible(true);
    }

    private AppFrame() {
        super("寄存器并发历史线性化分析");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);

        constraintsArea.setEditable(false);
        constraintsArea.setLineWrap(true);
        constraintsArea.setWrapStyleWord(true);
        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        table.getSelectionModel().addListSelectionListener(e -> refreshDetail());
        timeline.setOnSelect(id -> selectCallById(id));

        loadSample((String) sampleCombo.getSelectedItem());
        setSize(1180, 760);
        setLocationRelativeTo(null);

        // 窗口实现后按比例放置分隔条（此时比例定位才生效）
        javax.swing.SwingUtilities.invokeLater(() -> {
            center.setDividerLocation(0.34);
            rightSplit.setDividerLocation(0.58);
            bottomSplit.setDividerLocation(0.42);
        });

        String auto = System.getProperty("regcheck.autoAnalyze");
        if (auto != null) {
            if (!auto.isBlank() && Samples.all().containsKey(auto)) {
                sampleCombo.setSelectedItem(auto);
                loadSample(auto);
            }
            if (tableModel.getRowCount() > 0) {
                table.getSelectionModel().setSelectionInterval(0, 0);
            }
            runAnalysis();
        }
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.add(new JLabel("内置样例:"));
        bar.add(sampleCombo);
        JButton load = new JButton("载入样例");
        load.addActionListener(e -> loadSample((String) sampleCombo.getSelectedItem()));
        bar.add(load);
        JButton addRow = new JButton("添加调用");
        addRow.addActionListener(e -> tableModel.addRow());
        bar.add(addRow);
        JButton removeRow = new JButton("删除选中");
        removeRow.addActionListener(
                e -> tableModel.removeRow(table.getSelectedRow()));
        bar.add(removeRow);
        bar.add(new JLabel("预算:"));
        bar.add(budgetField);
        analyzeButton.addActionListener(e -> runAnalysis());
        bar.add(analyzeButton);
        return bar;
    }

    private JSplitPane buildCenter() {
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder(
                "调用记录（返回序号/结果留空 = 未返回）"));
        tableScroll.setMinimumSize(new Dimension(200, 100));

        JScrollPane timelineScroll = new JScrollPane(timeline);
        timelineScroll.setBorder(BorderFactory.createTitledBorder(
                "客户端时间线（橙 = 与选中重叠，红 = 选中，虚线 = 未返回，[n] = 候选顺序位置）"));
        timelineScroll.setMinimumSize(new Dimension(200, 120));

        JScrollPane constraintsScroll = new JScrollPane(constraintsArea);
        constraintsScroll.setBorder(
                BorderFactory.createTitledBorder("选中调用的约束"));
        constraintsScroll.setMinimumSize(new Dimension(120, 80));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("分析结果"));
        resultScroll.setMinimumSize(new Dimension(120, 80));

        bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                constraintsScroll, resultScroll);
        bottomSplit.setResizeWeight(0.4);
        bottomSplit.setMinimumSize(new Dimension(0, 0));

        rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                timelineScroll, bottomSplit);
        rightSplit.setResizeWeight(0.58);
        rightSplit.setMinimumSize(new Dimension(0, 0));

        center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                tableScroll, rightSplit);
        center.setResizeWeight(0.0);
        center.setMinimumSize(new Dimension(0, 0));
        return center;
    }

    private void loadSample(String name) {
        History h = Samples.all().get(name);
        if (h != null) {
            tableModel.setCalls(h.calls());
            resultArea.setText("");
            refreshDetail();
        }
    }

    private void selectCallById(int id) {
        List<Call> calls = tableModel.getCalls();
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).id() == id) {
                table.getSelectionModel().setSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private void refreshDetail() {
        List<Call> calls = tableModel.getCalls();
        int row = table.getSelectedRow();
        Call selected = row >= 0 && row < calls.size() ? calls.get(row) : null;
        timeline.setData(calls, selected == null ? null : selected.id(), null);
        constraintsArea.setText(selected == null
                ? "在表中或时间线上选择一个调用查看约束。"
                : describeConstraints(selected, calls));
    }

    /** 列出选中调用的硬约束：必须先于它、必须晚于它、与它重叠的调用。 */
    static String describeConstraints(Call sel, List<Call> all) {
        StringBuilder sb = new StringBuilder();
        sb.append(sel.label()).append("\n  区间: [").append(sel.invokeSeq())
                .append(", ").append(sel.responseSeq() == null ? "未返回"
                        : sel.responseSeq()).append("]\n\n");
        StringBuilder before = new StringBuilder();
        StringBuilder after = new StringBuilder();
        StringBuilder overlap = new StringBuilder();
        for (Call c : all) {
            if (c.id() == sel.id()) {
                continue;
            }
            if (c.responseSeq() != null && c.responseSeq() < sel.invokeSeq()) {
                before.append("  #").append(c.id()).append(' ')
                        .append(c.client()).append('\n');
            } else if (sel.responseSeq() != null
                    && sel.responseSeq() < c.invokeSeq()) {
                after.append("  #").append(c.id()).append(' ')
                        .append(c.client()).append('\n');
            } else {
                overlap.append("  #").append(c.id()).append(' ')
                        .append(c.client()).append('\n');
            }
        }
        sb.append("必须先于它生效:\n").append(before.length() == 0 ? "  （无）\n" : before);
        sb.append("必须晚于它生效:\n").append(after.length() == 0 ? "  （无）\n" : after);
        sb.append("与它重叠（顺序可任意解释）:\n")
                .append(overlap.length() == 0 ? "  （无）\n" : overlap);
        return sb.toString();
    }

    private void runAnalysis() {
        final History history;
        final long budget;
        try {
            history = tableModel.toHistory();
            budget = Long.parseLong(budgetField.getText().trim());
            if (budget <= 0) {
                throw new NumberFormatException("预算必须为正数");
            }
        } catch (RuntimeException e) {
            resultArea.setText("输入无法解析: " + e.getMessage());
            return;
        }
        analyzeButton.setEnabled(false);
        resultArea.setText("分析中……");
        new SwingWorker<CheckResult, Void>() {
            @Override
            protected CheckResult doInBackground() {
                return new LinearizabilityChecker().check(history, budget);
            }

            @Override
            protected void done() {
                analyzeButton.setEnabled(true);
                try {
                    CheckResult result = get();
                    resultArea.setText(ResultText.format(result));
                    showWitnessOnTimeline(result);
                } catch (Exception e) {
                    resultArea.setText("分析失败: " + e.getMessage());
                }
            }
        }.execute();
    }

    private void showWitnessOnTimeline(CheckResult result) {
        List<Call> calls = tableModel.getCalls();
        int row = table.getSelectedRow();
        Integer selectedId = row >= 0 && row < calls.size()
                ? calls.get(row).id() : null;
        Map<Integer, Integer> positions = null;
        if (result instanceof CheckResult.Linearizable lin) {
            positions = new HashMap<>();
            for (int i = 0; i < lin.witness().size(); i++) {
                positions.put(lin.witness().get(i).call().id(), i + 1);
            }
        }
        timeline.setData(calls, selectedId, positions);
    }
}
