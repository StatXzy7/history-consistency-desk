package com.reglinearity.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 校验通过的调用历史：至多 {@link #MAX_OPERATIONS} 个调用，按调用事件序号排序。
 *
 * <p>同时预计算分析与界面都需要的派生信息：</p>
 * <ul>
 *   <li>每个调用的“前序”集合——返回事件序号不大于本调用调用序号的其他调用，
 *       即与本调用区间不重叠且先发生的调用。线性化点必须保持这些先后关系；</li>
 *   <li>与本调用区间重叠的调用集合（供界面高亮与约束展示）。</li>
 * </ul>
 *
 * <p>不可变对象；{@link #operations()} 返回不可变列表。</p>
 */
public final class History {

    /** 工具支持的最大调用数：单对象、最多十六个调用。 */
    public static final int MAX_OPERATIONS = 16;

    private final List<Operation> operations;
    private final List<List<Integer>> predecessors;
    private final List<List<Integer>> overlaps;
    private final List<String> clients;
    private final long minSeq;
    private final long maxSeq;

    private History(List<Operation> operations,
                    List<List<Integer>> predecessors,
                    List<List<Integer>> overlaps,
                    List<String> clients,
                    long minSeq,
                    long maxSeq) {
        this.operations = Collections.unmodifiableList(operations);
        this.predecessors = predecessors;
        this.overlaps = overlaps;
        this.clients = Collections.unmodifiableList(clients);
        this.minSeq = minSeq;
        this.maxSeq = maxSeq;
    }

    /**
     * 由已经按调用序号排好序的调用列表构造历史，并预计算区间关系。
     *
     * @throws IllegalArgumentException 调用数超过 {@link #MAX_OPERATIONS}
     */
    public static History of(List<Operation> sortedOperations) {
        List<Operation> ops = new ArrayList<>(sortedOperations);
        if (ops.size() > MAX_OPERATIONS) {
            throw new IllegalArgumentException("最多支持 " + MAX_OPERATIONS + " 个调用，实际为 " + ops.size());
        }

        int n = ops.size();
        List<List<Integer>> preds = new ArrayList<>(n);
        List<List<Integer>> ovs = new ArrayList<>(n);
        Map<String, Boolean> clientFirst = new LinkedHashMap<>();
        long lo = Long.MAX_VALUE;
        long hi = Long.MIN_VALUE;

        for (int i = 0; i < n; i++) {
            Operation oi = ops.get(i);
            clientFirst.putIfAbsent(oi.client(), Boolean.TRUE);
            lo = Math.min(lo, oi.invokeSeq());
            hi = Math.max(hi, oi.invokeSeq());
            if (oi.completed()) {
                hi = Math.max(hi, oi.returnSeq().getAsLong());
            }
        }

        for (int i = 0; i < n; i++) {
            Operation oi = ops.get(i);
            List<Integer> pred = new ArrayList<>();
            List<Integer> ov = new ArrayList<>();
            long iStart = oi.invokeSeq();
            Long iEnd = oi.completed() ? oi.returnSeq().getAsLong() : null;
            for (int j = 0; j < n; j++) {
                if (j == i) {
                    continue;
                }
                Operation oj = ops.get(j);
                if (precedes(oj, oi)) {
                    pred.add(j);
                } else if (intervalsOverlap(oi, iStart, iEnd, oj)) {
                    ov.add(j);
                }
            }
            preds.add(Collections.unmodifiableList(pred));
            ovs.add(Collections.unmodifiableList(ov));
        }

        if (n == 0) {
            lo = 0;
            hi = 0;
        }
        return new History(ops,
                Collections.unmodifiableList(preds),
                Collections.unmodifiableList(ovs),
                List.copyOf(clientFirst.keySet()),
                lo, hi);
    }

    /**
     * 判断调用 {@code first} 是否严格先于 {@code second}（区间不重叠）：
     * {@code first} 已返回且其返回序号不大于 {@code second} 的调用序号。
     * 未返回调用没有右端点，不可能严格先于任何调用。
     */
    private static boolean precedes(Operation first, Operation second) {
        if (!first.completed()) {
            return false;
        }
        return first.returnSeq().getAsLong() <= second.invokeSeq();
    }

    /** 两个调用区间是否重叠（含端点相接之外的真实并发；端点相接由 precedes 处理）。 */
    private static boolean intervalsOverlap(Operation a, long aStart, Long aEnd, Operation b) {
        long bStart = b.invokeSeq();
        Long bEnd = b.completed() ? b.returnSeq().getAsLong() : null;
        // a 先结束（含端点）则不重叠；b 先结束（含端点）则不重叠。
        if (aEnd != null && aEnd <= bStart) {
            return false;
        }
        if (bEnd != null && bEnd <= aStart) {
            return false;
        }
        return true;
    }

    public List<Operation> operations() {
        return operations;
    }

    public Operation operation(int index) {
        return operations.get(index);
    }

    public int size() {
        return operations.size();
    }

    /** 下标 i 的前序调用下标（区间不重叠且先发生）。 */
    public List<Integer> predecessors(int i) {
        return predecessors.get(i);
    }

    /** 与下标 i 的调用区间重叠的调用下标。 */
    public List<Integer> overlaps(int i) {
        return overlaps.get(i);
    }

    /** 按首次出现顺序排列的客户端列表。 */
    public List<String> clients() {
        return clients;
    }

    public long minSeq() {
        return minSeq;
    }

    public long maxSeq() {
        return maxSeq;
    }
}
