package com.reglinearity.core;

import java.util.List;

/**
 * 不一致时的冲突说明：记录搜索走得最远的一条死路，
 * 展示该前缀、无法安排的已返回调用及其具体冲突原因。
 *
 * <p>不承诺是最小反例——仅要求给出可人工核对的冲突证据。
 * 不可变值对象。</p>
 */
public final class ConflictReport {

    private final List<LinearizationStep> prefix;
    private final List<Integer> blockedIndices;
    private final List<String> reasons;
    private final long searchedNodes;

    public ConflictReport(List<LinearizationStep> prefix,
                          List<Integer> blockedIndices,
                          List<String> reasons,
                          long searchedNodes) {
        this.prefix = List.copyOf(prefix);
        this.blockedIndices = List.copyOf(blockedIndices);
        this.reasons = List.copyOf(reasons);
        this.searchedNodes = searchedNodes;
    }

    /** 死路处已经安排好的合法前缀（逐步寄存器值可直接展示）。 */
    public List<LinearizationStep> prefix() {
        return prefix;
    }

    /** 该前缀下所有“可安排”（前序已满足）但执行不合法的已返回调用下标。 */
    public List<Integer> blockedIndices() {
        return blockedIndices;
    }

    public List<String> reasons() {
        return reasons;
    }

    public long searchedNodes() {
        return searchedNodes;
    }
}
