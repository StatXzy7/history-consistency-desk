package com.regcheck.ui;

import com.regcheck.core.Call;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * 时间线视图：每个客户端一行，区间条表示 [调用, 返回]，
 * 未返回的调用以虚线箭头延伸到右边界。与选中调用重叠的区间高亮为橙色，
 * 选中为红色。分析成功后，条内数字为该调用在候选顺序中的位置。
 */
public final class TimelinePanel extends JPanel implements Scrollable {

    private static final int LEFT_MARGIN = 150;
    private static final int RIGHT_MARGIN = 30;
    private static final int TOP_MARGIN = 12;
    private static final int ROW_HEIGHT = 36;
    private static final int BAR_HEIGHT = 20;
    private static final int AXIS_HEIGHT = 26;

    private static final Color NORMAL = new Color(0x5B8DEF);
    private static final Color OVERLAP = new Color(0xF0A13C);
    private static final Color SELECTED = new Color(0xD64545);
    private static final Color PENDING = new Color(0x9AA3AD);

    private List<Call> calls = List.of();
    private Integer selectedId;
    /** 操作编号 → 候选顺序中的位置（1 起）；null 表示尚无分析结果。 */
    private Map<Integer, Integer> witnessPositions;
    private IntConsumer onSelect = id -> { };

    /** 缓存的条形区域，用于鼠标点选。 */
    private final Map<java.awt.Shape, Integer> barShapes = new LinkedHashMap<>();

    public TimelinePanel() {
        setBackground(Color.WHITE);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                for (var entry : barShapes.entrySet()) {
                    if (entry.getKey().contains(e.getPoint())) {
                        onSelect.accept(entry.getValue());
                        return;
                    }
                }
            }
        });
    }

    public void setOnSelect(IntConsumer listener) {
        this.onSelect = listener;
    }

    public void setData(List<Call> newCalls, Integer newSelectedId,
                        Map<Integer, Integer> newWitnessPositions) {
        calls = List.copyOf(newCalls);
        selectedId = newSelectedId;
        witnessPositions = newWitnessPositions;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int clients = (int) calls.stream().map(Call::client).distinct().count();
        return new Dimension(600,
                TOP_MARGIN + Math.max(1, clients) * ROW_HEIGHT + AXIS_HEIGHT + 8);
    }

    // ---- Scrollable：宽度始终贴合视口，只在纵向需要时滚动 ----

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation,
                                          int direction) {
        return ROW_HEIGHT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation,
                                           int direction) {
        return ROW_HEIGHT * 3;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    /** 两个调用是否重叠（不存在严格的先后关系）。 */
    static boolean overlaps(Call a, Call b) {
        boolean aBeforeB = a.responseSeq() != null && a.responseSeq() < b.invokeSeq();
        boolean bBeforeA = b.responseSeq() != null && b.responseSeq() < a.invokeSeq();
        return !aBeforeB && !bBeforeA;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        barShapes.clear();
        if (calls.isEmpty()) {
            g2.setColor(Color.GRAY);
            g2.drawString("（无调用记录）", LEFT_MARGIN, TOP_MARGIN + 16);
            g2.dispose();
            return;
        }

        long minSeq = calls.stream().mapToLong(Call::invokeSeq).min().orElse(0);
        long maxSeq = calls.stream()
                .mapToLong(c -> c.responseSeq() != null ? c.responseSeq() : c.invokeSeq())
                .max().orElse(1);
        boolean hasPending = calls.stream().anyMatch(c -> c.responseSeq() == null);
        long lo = Math.min(0, minSeq - 1);
        long hi = maxSeq + (hasPending ? 2 : 1);

        List<String> clients = calls.stream().map(Call::client).distinct()
                .sorted().toList();
        Map<String, Integer> rowOf = new LinkedHashMap<>();
        for (int i = 0; i < clients.size(); i++) {
            rowOf.put(clients.get(i), i);
        }

        int width = getWidth() - LEFT_MARGIN - RIGHT_MARGIN;
        java.util.function.LongToDoubleFunction x = seq ->
                LEFT_MARGIN + width * (seq - lo) / (double) (hi - lo);

        // 交替行底色
        for (int i = 0; i < clients.size(); i++) {
            if (i % 2 == 0) {
                g2.setColor(new Color(0xF4F6F8));
                g2.fillRect(0, TOP_MARGIN + i * ROW_HEIGHT, getWidth(), ROW_HEIGHT);
            }
            g2.setColor(Color.DARK_GRAY);
            g2.drawString(clients.get(i), 12,
                    TOP_MARGIN + i * ROW_HEIGHT + ROW_HEIGHT / 2 + 5);
        }

        Call selected = calls.stream()
                .filter(c -> selectedId != null && c.id() == selectedId)
                .findFirst().orElse(null);

        List<Call> ordered = new ArrayList<>(calls);
        ordered.sort(Comparator.comparingLong(Call::invokeSeq));
        for (Call c : ordered) {
            int row = rowOf.get(c.client());
            int y = TOP_MARGIN + row * ROW_HEIGHT + (ROW_HEIGHT - BAR_HEIGHT) / 2;
            int x1 = (int) x.applyAsDouble(c.invokeSeq());
            int x2 = c.responseSeq() != null
                    ? (int) x.applyAsDouble(c.responseSeq())
                    : getWidth() - RIGHT_MARGIN + 10;
            x2 = Math.max(x2, x1 + 14);

            Color fill = colorFor(c, selected);
            RoundRectangle2D bar = new RoundRectangle2D.Float(
                    x1, y, x2 - x1, BAR_HEIGHT, 8, 8);
            g2.setColor(fill);
            g2.fill(bar);
            if (c.responseSeq() == null) {
                g2.setColor(fill.darker());
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
                g2.draw(bar);
                g2.setStroke(new BasicStroke(1f));
                g2.drawString("→", x2 + 2, y + BAR_HEIGHT - 6);
            } else {
                g2.setColor(fill.darker());
                g2.draw(bar);
            }
            barShapes.put(bar, c.id());

            // 条内标签：#编号 + 候选顺序位置
            StringBuilder tag = new StringBuilder("#").append(c.id());
            if (witnessPositions != null && witnessPositions.containsKey(c.id())) {
                tag.append('[').append(witnessPositions.get(c.id())).append(']');
            }
            g2.setColor(Color.WHITE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(tag.toString(), x1 + 5, y + BAR_HEIGHT - 6);
            g2.setColor(Color.GRAY);
            g2.drawString(c.op().toString(),
                    x1 + 6 + fm.stringWidth(tag.toString()), y + BAR_HEIGHT - 6);
        }

        // 底部事件序号轴
        int axisY = TOP_MARGIN + clients.size() * ROW_HEIGHT + 14;
        g2.setColor(Color.GRAY);
        g2.drawLine(LEFT_MARGIN, axisY, getWidth() - RIGHT_MARGIN, axisY);
        for (long s = lo + 1; s < hi; s++) {
            int tx = (int) x.applyAsDouble(s);
            g2.drawLine(tx, axisY, tx, axisY + 4);
            g2.drawString(Long.toString(s), tx - 4, axisY + 16);
        }
        g2.dispose();
    }

    private Color colorFor(Call c, Call selected) {
        if (selected != null && c.id() == selected.id()) {
            return SELECTED;
        }
        if (c.responseSeq() == null) {
            return PENDING;
        }
        if (selected != null && overlaps(c, selected)) {
            return OVERLAP;
        }
        return NORMAL;
    }
}
