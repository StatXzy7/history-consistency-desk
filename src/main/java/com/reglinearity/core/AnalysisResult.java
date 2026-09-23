package com.reglinearity.core;

import java.util.List;
import java.util.Optional;

/**
 * 分析结果：三态结论 + 对应证据。
 *
 * <ul>
 *   <li>CONSISTENT：{@link #witness()} 给出一个具体合法顺序及逐步寄存器值，
 *       {@link #droppedPending()} 列出被忽略的未返回调用；</li>
 *   <li>INCONSISTENT：{@link #conflict()} 给出冲突操作与原因（非最小反例）；</li>
 *   <li>UNDECIDED：只携带已搜索节点数与预算，不得当作不一致。</li>
 * </ul>
 *
 * <p>不可变值对象。</p>
 */
public final class AnalysisResult {

    private final Verdict verdict;
    private final List<LinearizationStep> witness;
    private final List<Integer> droppedPending;
    private final ConflictReport conflict;
    private final long searchedNodes;
    private final long budget;

    private AnalysisResult(Verdict verdict,
                           List<LinearizationStep> witness,
                           List<Integer> droppedPending,
                           ConflictReport conflict,
                           long searchedNodes,
                           long budget) {
        this.verdict = verdict;
        this.witness = witness == null ? List.of() : List.copyOf(witness);
        this.droppedPending = droppedPending == null ? List.of() : List.copyOf(droppedPending);
        this.conflict = conflict;
        this.searchedNodes = searchedNodes;
        this.budget = budget;
    }

    static AnalysisResult consistent(List<LinearizationStep> witness,
                                     List<Integer> droppedPending,
                                     long searchedNodes,
                                     long budget) {
        return new AnalysisResult(Verdict.CONSISTENT, witness, droppedPending, null,
                searchedNodes, budget);
    }

    static AnalysisResult inconsistent(ConflictReport conflict, long budget) {
        return new AnalysisResult(Verdict.INCONSISTENT, null, null, conflict,
                conflict.searchedNodes(), budget);
    }

    static AnalysisResult undecided(long searchedNodes, long budget) {
        return new AnalysisResult(Verdict.UNDECIDED, null, null, null, searchedNodes, budget);
    }

    public Verdict verdict() {
        return verdict;
    }

    public Optional<List<LinearizationStep>> witness() {
        return witness.isEmpty() ? Optional.empty() : Optional.of(witness);
    }

    /** 解释中被忽略（丢弃）的未返回调用下标。 */
    public List<Integer> droppedPending() {
        return droppedPending;
    }

    public Optional<ConflictReport> conflict() {
        return Optional.ofNullable(conflict);
    }

    public long searchedNodes() {
        return searchedNodes;
    }

    public long budget() {
        return budget;
    }

    /** 空历史（没有任何调用）平凡一致。 */
    static AnalysisResult trivialConsistent(long budget) {
        return new AnalysisResult(Verdict.CONSISTENT, List.of(), List.of(), null, 0, budget);
    }
}
