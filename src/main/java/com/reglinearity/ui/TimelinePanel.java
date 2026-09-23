package com.reglinearity.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.Scrollable;

import com.reglinearity.core.AnalysisResult;
import com.reglinearity.core.History;
import com.reglinearity.core.LinearizationStep;
import com.reglinearity.core.Operation;

/**
 * 客户端时间线面板：每个客户端一行，调用区间用色条绘制。
 *
 * <ul>
 *   <li>非重叠先序关系用箭头标注；重叠区间在背景画红色阴影带；</li>
 *   <li>候选顺序（见证或冲突前缀）在区间内标出线性化位置序号；</li>
 *   <li>点击区间选择操作，选中项高亮，并通知外部展示其约束。</li>
 * </ul>
 *
 * <p>实现 {@link Scrollable} 并跟踪视口宽度：横向始终铺满、不出现水平滚动条，
 * 客户端行很多时只纵向滚动。</p>
 */
final class TimelinePanel extends JPanel implements Scrollable {

    private static final int MARGIN_LEFT = 70;
    private static final int MARGIN_RIGHT = 24;
    private static final int MARGIN_TOP = 34;
    private static final int ROW_HEIGHT = 52;
    private static final int BAR_THICKNESS = 18;
    private static final int AXIS_HEIGHT = 26;

    private History history;
    private AnalysisResult result;
    private int selectedIndex = -1;
    private final Map<Integer, Integer> linearizationPosition = new HashMap<>();
    private final Set<Integer> conflictBlocked = new HashSet<>();
    private final java.util.List<SelectionListener> listeners = new java.util.ArrayList<>();

    TimelinePanel() {
        setBackground(UiTheme.BACKGROUND);
        setPreferredSize(new Dimension(900, 220));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int hit = hitTest(e.getX(), e.getY());
                setSelectedIndex(hit);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setCursor(hitTest(e.getX(), e.getY()) >= 0
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void setHistoryAndResult(History history, AnalysisResult result) {
        this.history = history;
        this.result = result;
        this.selectedIndex = -1;
        rebuildAnnotations();
        // 宽度交给 Scrollable 跟踪视口；高度按客户端行数确定，纵向可滚动
        int rows = history == null ? 0 : history.clients().size();
        setPreferredSize(new Dimension(800, MARGIN_TOP + Math.max(2, rows) * ROW_HEIGHT + AXIS_HEIGHT));
        revalidate();
        repaint();
    }

    void setSelectedIndex(int index) {
        this.selectedIndex = index;
        repaint();
        for (SelectionListener l : listeners) {
            l.selectionChanged(index);
        }
    }

    void addSelectionListener(SelectionListener listener) {
        listeners.add(listener);
    }

    private void rebuildAnnotations() {
        linearizationPosition.clear();
        conflictBlocked.clear();
        if (history == null || result == null) {
            return;
        }
        List<LinearizationStep> steps;
        if (result.verdict() == com.reglinearity.core.Verdict.CONSISTENT) {
            steps = result.witness().orElse(List.of());
        } else if (result.verdict() == com.reglinearity.core.Verdict.INCONSISTENT) {
            steps = result.conflict().map(c -> c.prefix()).orElse(List.of());
            conflictBlocked.addAll(result.conflict().map(c -> c.blockedIndices()).orElse(List.of()));
        } else {
            steps = List.of();
        }
        for (int i = 0; i < steps.size(); i++) {
            linearizationPosition.put(steps.get(i).operation().index(), i + 1);
        }
    }

    // ------------------------------------------------------------------
    // 坐标映射
    // ------------------------------------------------------------------

    private long seqSpan() {
        if (history == null || history.size() == 0) {
            return 1;
        }
        long lo = history.minSeq();
        long hi = history.maxSeq();
        return Math.max(1, hi - lo + 1);
    }

    private int xOf(long seq) {
        int usable = Math.max(100, getWidth() - MARGIN_LEFT - MARGIN_RIGHT);
        double frac = (seq - (history == null ? 0 : history.minSeq())) / (double) seqSpan();
        return MARGIN_LEFT + (int) Math.round(frac * (usable - 1)) + 4;
    }

    private int rowOf(String client) {
        List<String> clients = history.clients();
        for (int i = 0; i < clients.size(); i++) {
            if (clients.get(i).equals(client)) {
                return i;
            }
        }
        return 0;
    }

    private Rectangle2D barBounds(int index) {
        Operation op = history.operation(index);
        int row = rowOf(op.client());
        int y = MARGIN_TOP + row * ROW_HEIGHT + (ROW_HEIGHT - BAR_THICKNESS) / 2;
        int x1 = xOf(op.invokeSeq());
        int x2 = op.completed() ? xOf(op.returnSeq().getAsLong()) : getWidth() - MARGIN_RIGHT;
        return new Rectangle2D.Float(x1, y, Math.max(6, x2 - x1), BAR_THICKNESS);
    }

    private int hitTest(int x, int y) {
        if (history == null) {
            return -1;
        }
        for (int i = 0; i < history.size(); i++) {
            if (barBounds(i).contains(x, y)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        if (history == null || history.size() == 0) {
            g2.setColor(UiTheme.TEXT_MUTED);
            g2.setFont(UiTheme.UI);
            g2.drawString("输入合法历史后在此显示客户端时间线。", MARGIN_LEFT, MARGIN_TOP + 20);
            g2.dispose();
            return;
        }

        drawOverlapBands(g2);
        drawRowsAndAxis(g2);
        drawPrecedenceArrows(g2);
        drawBars(g2);
        g2.dispose();
    }

    private void drawOverlapBands(Graphics2D g2) {
        // 以选中操作为中心画出与其重叠的区间阴影；未选中时给所有互相重叠对画浅色带
        g2.setColor(UiTheme.OVERLAP_FILL);
        for (int i = 0; i < history.size(); i++) {
            if (selectedIndex >= 0 && i != selectedIndex) {
                continue;
            }
            Operation op = history.operation(i);
            for (int j : history.overlaps(i)) {
                if (j < i) {
                    continue;
                }
                Operation other = history.operation(j);
                long bandStart = Math.max(op.invokeSeq(), other.invokeSeq());
                long bandEnd = Math.min(
                        op.completed() ? op.returnSeq().getAsLong() : Long.MAX_VALUE,
                        other.completed() ? other.returnSeq().getAsLong() : Long.MAX_VALUE);
                if (bandEnd == Long.MAX_VALUE) {
                    bandEnd = history.maxSeq() + 1;
                }
                int x1 = xOf(bandStart);
                int x2 = xOf(bandEnd);
                int top = MARGIN_TOP;
                int bottom = MARGIN_TOP + history.clients().size() * ROW_HEIGHT;
                g2.fillRect(x1, top, Math.max(2, x2 - x1), bottom - top);
            }
        }
    }

    private void drawRowsAndAxis(Graphics2D g2) {
        List<String> clients = history.clients();
        g2.setFont(UiTheme.UI_BOLD);
        for (int row = 0; row < clients.size(); row++) {
            int y = MARGIN_TOP + row * ROW_HEIGHT;
            g2.setColor(UiTheme.SURFACE);
            g2.fillRect(0, y, getWidth(), ROW_HEIGHT);
            g2.setColor(UiTheme.BORDER);
            g2.drawLine(0, y, getWidth(), y);

            g2.setColor(UiTheme.TEXT);
            g2.drawString(clients.get(row), 12, y + ROW_HEIGHT / 2 + 5);

            // 行内时间轴
            int axisY = y + ROW_HEIGHT / 2;
            g2.setColor(UiTheme.BORDER);
            g2.drawLine(MARGIN_LEFT, axisY, getWidth() - MARGIN_RIGHT, axisY);
        }

        // 顶部事件序号刻度
        long lo = history.minSeq();
        long span = seqSpan();
        int ticks = (int) Math.min(span, 12);
        g2.setFont(UiTheme.MONO.deriveFont(11f));
        g2.setColor(UiTheme.TEXT_MUTED);
        for (int t = 0; t <= ticks; t++) {
            long seq = lo + Math.round((double) t / ticks * (span - 1));
            int x = xOf(seq);
            g2.drawLine(x, MARGIN_TOP - 8, x, MARGIN_TOP - 4);
            g2.drawString(Long.toString(seq), x - 6, MARGIN_TOP - 12);
        }
    }

    private void drawPrecedenceArrows(Graphics2D g2) {
        // 仅画与选中操作相关的前序箭头，避免画面过密
        if (selectedIndex < 0) {
            return;
        }
        Operation sel = history.operation(selectedIndex);
        Stroke dashed = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{5f, 4f}, 0f);
        g2.setStroke(dashed);
        g2.setColor(UiTheme.WARN);
        int selRow = rowOf(sel.client());
        int selY = MARGIN_TOP + selRow * ROW_HEIGHT + ROW_HEIGHT / 2;
        for (int p : history.predecessors(selectedIndex)) {
            Operation pred = history.operation(p);
            int predRow = rowOf(pred.client());
            int predY = MARGIN_TOP + predRow * ROW_HEIGHT + ROW_HEIGHT / 2;
            int x1 = xOf(pred.returnSeq().getAsLong());
            int x2 = xOf(sel.invokeSeq());
            g2.drawLine(x1, predY, x2, selY);
            g2.fillOval(x2 - 3, selY - 3, 6, 6);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    private void drawBars(Graphics2D g2) {
        for (int i = 0; i < history.size(); i++) {
            Operation op = history.operation(i);
            Rectangle2D bar = barBounds(i);
            boolean selected = i == selectedIndex;
            Color base = UiTheme.operationColor(op.kind());

            // 未返回调用：画成半透明 + 右端虚线延伸
            float alpha = op.completed() ? 1f : 0.55f;
            g2.setColor(withAlpha(base, alpha));
            g2.fill(bar);

            if (conflictBlocked.contains(i)) {
                g2.setColor(UiTheme.BAD);
                g2.setStroke(new BasicStroke(2.6f));
                g2.draw(bar);
                g2.setStroke(new BasicStroke(1f));
            } else if (selected) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.2f));
                g2.draw(bar);
                g2.setStroke(new BasicStroke(1f));
            } else {
                g2.setColor(UiTheme.BORDER);
                g2.draw(bar);
            }

            // 调用/返回端点
            int xStart = (int) bar.getX();
            g2.setColor(base);
            g2.fillOval(xStart - 4, (int) bar.getCenterY() - 4, 8, 8);
            if (op.completed()) {
                int xEnd = (int) (bar.getX() + bar.getWidth());
                g2.setColor(base.darker());
                g2.fillRect(xEnd - 2, (int) bar.getY() - 3, 4, (int) bar.getHeight() + 6);
            } else {
                // 未返回：右端画虚线箭头状收尾
                int xEnd = (int) (bar.getX() + bar.getWidth());
                Stroke dashed = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{4f, 3f}, 0f);
                g2.setStroke(dashed);
                g2.setColor(withAlpha(base, 0.9f));
                g2.drawLine(xEnd, (int) bar.getCenterY(),
                        Math.min(getWidth() - MARGIN_RIGHT, xEnd + 14), (int) bar.getCenterY());
                g2.setStroke(new BasicStroke(1f));
            }

            // 线性化位置标记
            Integer pos = linearizationPosition.get(i);
            if (pos != null) {
                int cx = op.completed()
                        ? (int) ((bar.getX() + bar.getWidth()) / 2)
                        : (int) bar.getCenterX();
                int cy = (int) bar.getCenterY();
                g2.setColor(new Color(0x14, 0x18, 0x20));
                g2.fillOval(cx - 9, cy - 9, 18, 18);
                g2.setColor(base);
                g2.drawOval(cx - 9, cy - 9, 18, 18);
                g2.setColor(UiTheme.TEXT);
                g2.setFont(UiTheme.MONO_BOLD.deriveFont(11f));
                String label = Integer.toString(pos);
                java.awt.FontMetrics fm = g2.getFontMetrics();
                g2.drawString(label, cx - fm.stringWidth(label) / 2, cy + 4);
            }

            // 行内标签
            g2.setFont(UiTheme.MONO.deriveFont(11f));
            g2.setColor(UiTheme.TEXT_MUTED);
            String tag = op.label() + " " + op.kind().keyword();
            int tagY = (int) (bar.getY() - 4);
            g2.drawString(tag, (int) bar.getX(), tagY);
        }
    }

    private static Color withAlpha(Color color, float alpha) {
        return new Color(color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, alpha);
    }

    /** 操作选择变化监听。 */
    @FunctionalInterface
    interface SelectionListener {
        void selectionChanged(int operationIndex);
    }

    // ------------------------------------------------------------------
    // Scrollable：横向铺满视口（不出现水平滚动条），纵向按行高滚动
    // ------------------------------------------------------------------

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
        return ROW_HEIGHT;
    }

    @Override
    public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
        return Math.max(1, visibleRect.height - ROW_HEIGHT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
