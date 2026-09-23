package com.reglinearity.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;

import com.reglinearity.core.AnalysisResult;
import com.reglinearity.core.ConsistencyService;
import com.reglinearity.core.History;
import com.reglinearity.core.LinearizabilityAnalyzer;
import com.reglinearity.sample.Samples;

/**
 * 主窗口：左侧历史编辑器，右侧上方客户端时间线，下方为操作约束与分析结论。
 */
public final class AnalyzerFrame extends JFrame {

    private final ConsistencyService service = new ConsistencyService();

    private final JTextArea editor = new JTextArea();
    private final JSpinner budgetSpinner =
            new JSpinner(new SpinnerNumberModel(
                    LinearizabilityAnalyzer.DEFAULT_BUDGET, 1L, 50_000_000L, 10_000L));
    private final JComboBox<Samples.Sample> sampleBox = new JComboBox<>(
            Samples.all().toArray(new Samples.Sample[0]));
    private final JButton analyzeButton = new JButton("分析");
    private final JButton loadSampleButton = new JButton("载入样例");
    private final JLabel statusLabel = new JLabel(" ");

    private final TimelinePanel timeline = new TimelinePanel();
    private final ConstraintPane constraintPane = new ConstraintPane();
    private final ResultPane resultPane = new ResultPane();

    private History currentHistory;
    private AnalysisResult currentResult;

    public AnalyzerFrame() {
        super("寄存器线性一致性分析器 — 单对象 · 最多 16 个调用");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 820);
        setLocationRelativeTo(null);
        getContentPane().setBackground(UiTheme.BACKGROUND);

        buildContent();
        wireEvents();

        editor.setText(Samples.CORRECT_INTERLEAVING.text());
        sampleBox.setSelectedItem(Samples.CORRECT_INTERLEAVING);
        constraintPane.showNothing(null);
        resultPane.showIdle();

        // 启动即分析一次，让时间线与结论区直接呈现内置样例的结果
        javax.swing.SwingUtilities.invokeLater(this::runAnalysis);
    }

    private void buildContent() {
        add(buildToolbar(), BorderLayout.NORTH);

        // 左侧：输入编辑器
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(UiTheme.BACKGROUND);
        JLabel inputTitle = sectionLabel("调用历史（每行一条记录，# 为注释）");
        editor.setFont(UiTheme.MONO.deriveFont(13f));
        editor.setBackground(UiTheme.SURFACE);
        editor.setForeground(UiTheme.TEXT);
        editor.setCaretColor(UiTheme.TEXT);
        editor.setSelectedTextColor(Color.WHITE);
        editor.setSelectionColor(new Color(0x3B, 0x5B, 0x8A));
        editor.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane editorScroll = new JScrollPane(editor);
        editorScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        leftPanel.add(inputTitle, BorderLayout.NORTH);
        leftPanel.add(editorScroll, BorderLayout.CENTER);

        // 右上：时间线
        JPanel timelinePanel = new JPanel(new BorderLayout());
        timelinePanel.setBackground(UiTheme.BACKGROUND);
        timelinePanel.add(sectionLabel("客户端时间线（色条=调用区间；红带=重叠区间；圆点数字=候选顺序位置）"),
                BorderLayout.NORTH);
        JScrollPane timelineScroll = new JScrollPane(timeline);
        timelineScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        timelineScroll.getViewport().setBackground(UiTheme.BACKGROUND);
        timelinePanel.add(timelineScroll, BorderLayout.CENTER);

        // 右下：约束 + 结论
        JSplitPane bottomSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                wrapSection("所选操作的约束", constraintPane),
                wrapSection("分析结论", resultPane));
        bottomSplit.setResizeWeight(0.38);
        bottomSplit.setBorder(null);
        styleSplit(bottomSplit);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                timelinePanel, bottomSplit);
        rightSplit.setResizeWeight(0.45);
        rightSplit.setBorder(null);
        styleSplit(rightSplit);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftPanel, rightSplit);
        mainSplit.setResizeWeight(0.32);
        mainSplit.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        styleSplit(mainSplit);
        add(mainSplit, BorderLayout.CENTER);

        add(buildStatusBar(), BorderLayout.SOUTH);
    }

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.setBackground(UiTheme.BACKGROUND);

        styleButton(analyzeButton, UiTheme.ACCENT, true);
        styleButton(loadSampleButton, UiTheme.SURFACE_HIGHLIGHT, false);

        bar.add(new DarkLabel("内置样例："));
        sampleBox.setPreferredSize(new Dimension(300, 28));
        sampleBox.setBackground(UiTheme.SURFACE);
        sampleBox.setForeground(UiTheme.TEXT);
        bar.add(sampleBox);
        bar.add(loadSampleButton);

        bar.add(separator());
        bar.add(new DarkLabel("搜索预算（节点数）："));
        budgetSpinner.setPreferredSize(new Dimension(130, 28));
        budgetSpinner.setBackground(UiTheme.SURFACE);
        budgetSpinner.setForeground(UiTheme.TEXT);
        bar.add(budgetSpinner);
        bar.add(analyzeButton);
        return bar;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new GridLayout(1, 1));
        bar.setBackground(UiTheme.SURFACE);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        statusLabel.setForeground(UiTheme.TEXT_MUTED);
        statusLabel.setFont(UiTheme.UI.deriveFont(12f));
        bar.add(statusLabel);
        return bar;
    }

    private void wireEvents() {
        loadSampleButton.addActionListener(e -> {
            Samples.Sample sample = (Samples.Sample) sampleBox.getSelectedItem();
            if (sample != null) {
                editor.setText(sample.text());
                statusLabel.setText(sample.description());
            }
        });

        analyzeButton.addActionListener(e -> runAnalysis());

        timeline.addSelectionListener(index -> {
            if (currentHistory != null && index >= 0) {
                constraintPane.show(currentHistory, currentResult, index);
            } else {
                constraintPane.showNothing(currentHistory);
            }
        });
    }

    private void runAnalysis() {
        final String text = editor.getText();
        final long budget = ((Number) budgetSpinner.getValue()).longValue();
        analyzeButton.setEnabled(false);
        statusLabel.setText("分析中……");

        // 搜索放入后台线程，避免大预算时卡界面
        SwingWorker<ConsistencyService.ServiceResponse, Void> worker =
                new SwingWorker<>() {
                    @Override
                    protected ConsistencyService.ServiceResponse doInBackground() {
                        return service.analyze(text, budget);
                    }

                    @Override
                    protected void done() {
                        try {
                            ConsistencyService.ServiceResponse response = get();
                            present(response);
                        } catch (Exception ex) {
                            resultPane.showIllegal("内部错误：" + ex.getMessage());
                            statusLabel.setText("分析失败。");
                        } finally {
                            analyzeButton.setEnabled(true);
                        }
                    }
                };
        worker.execute();
    }

    private void present(ConsistencyService.ServiceResponse response) {
        if (response.isIllegalInput()) {
            currentHistory = null;
            currentResult = null;
            timeline.setHistoryAndResult(null, null);
            constraintPane.showNothing(null);
            resultPane.showIllegal(response.illegalError());
            statusLabel.setText("输入非法 — 未进行一致性判定。");
            return;
        }

        currentHistory = response.history();
        currentResult = response.result();
        timeline.setHistoryAndResult(currentHistory, currentResult);
        constraintPane.showNothing(currentHistory);

        // 若不一致，默认选中第一个冲突操作，方便直接查看原因
        if (currentResult.verdict() == com.reglinearity.core.Verdict.INCONSISTENT) {
            List<Integer> blocked = currentResult.conflict()
                    .map(c -> c.blockedIndices()).orElse(List.of());
            if (!blocked.isEmpty()) {
                timeline.setSelectedIndex(blocked.get(0));
            }
        }
        resultPane.showResult(currentHistory, currentResult);

        statusLabel.setText(switch (currentResult.verdict()) {
            case CONSISTENT -> "可解释：已给出一个合法原子顺序及逐步寄存器值。";
            case INCONSISTENT -> "不可解释：已穷举预算内全部候选顺序，展示了冲突操作与原因。";
            case UNDECIDED -> "未判定：预算耗尽（" + currentResult.searchedNodes()
                    + " 个节点），不代表不存在解释。";
        });
    }

    // ------------------------------------------------------------------
    // 样式小工具
    // ------------------------------------------------------------------

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(UiTheme.TEXT_MUTED);
        label.setFont(UiTheme.UI.deriveFont(12f));
        label.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
        label.setBackground(UiTheme.BACKGROUND);
        label.setOpaque(true);
        return label;
    }

    private static JPanel wrapSection(String title, java.awt.Component content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiTheme.BACKGROUND);
        panel.add(sectionLabel(title), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static void styleButton(JButton button, Color base, boolean emphasized) {
        button.setBackground(base);
        button.setForeground(emphasized ? Color.WHITE : UiTheme.TEXT);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
        button.setFont(UiTheme.UI_BOLD);
    }

    private static JLabel separator() {
        JLabel label = new JLabel("  |  ");
        label.setForeground(UiTheme.BORDER);
        return label;
    }

    private static void styleSplit(JSplitPane split) {
        split.setBackground(UiTheme.BACKGROUND);
        split.setDividerSize(6);
    }

    /** 深色底上的普通标签。 */
    private static final class DarkLabel extends JLabel {
        DarkLabel(String text) {
            super(text);
            setForeground(UiTheme.TEXT);
            setFont(UiTheme.UI);
        }
    }
}
