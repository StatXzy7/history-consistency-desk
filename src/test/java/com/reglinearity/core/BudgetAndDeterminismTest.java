package com.reglinearity.core;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预算语义与可重复性测试。
 *
 * <p>关键不变式：预算耗尽只能得到 {@link Verdict#UNDECIDED}，
 * 绝不能把“未搜索完”报告成“不存在解释”；同一输入重复分析结论必须一致。</p>
 */
class BudgetAndDeterminismTest {

    private final HistoryParser parser = new HistoryParser();

    private History history(String text) {
        ParseOutcome o = parser.parse(text);
        assertTrue(o.isValid(), o.error().orElse(""));
        return o.history().orElseThrow();
    }

    /** 一个需要多个搜索节点才能定论的不一致历史（丢失更新，外加几个并发操作）。 */
    private final String inconsistentText = """
            1 INVOKE C1 1 add 1
            2 INVOKE C2 1 add 1
            3 INVOKE C3 1 add 1
            4 RETURN C1 1 1
            5 RETURN C2 1 1
            6 RETURN C3 1 1
            """;

    private final String consistentText = """
            1 INVOKE C1 1 add 1
            2 INVOKE C2 1 add 1
            3 INVOKE C3 1 add 1
            4 RETURN C1 1 1
            5 RETURN C2 1 2
            6 RETURN C3 1 3
            """;

    @Test
    @DisplayName("预算为 1 时多步历史无法搜完：返回未判定而非不一致")
    void tinyBudgetYieldsUndecidedNotInconsistent() {
        AnalysisResult r = new LinearizabilityAnalyzer().analyze(history(inconsistentText), 1);
        assertEquals(Verdict.UNDECIDED, r.verdict());
        assertTrue(r.searchedNodes() >= 1);
        // 同样输入给足预算后必须有定论，且确为不一致——证明未判定不等于不一致
        AnalysisResult full = new LinearizabilityAnalyzer()
                .analyze(history(inconsistentText), LinearizabilityAnalyzer.DEFAULT_BUDGET);
        assertEquals(Verdict.INCONSISTENT, full.verdict());
    }

    @Test
    @DisplayName("预算极小但恰好命中见证：一致历史仍判一致（预算只截断搜索，不制造假阴性之外的结果）")
    void budgetLargerThanSearchFindsWitness() {
        AnalysisResult r = new LinearizabilityAnalyzer()
                .analyze(history(consistentText), 100_000);
        assertEquals(Verdict.CONSISTENT, r.verdict());
        WitnessAssertions.assertValidWitness(history(consistentText), r);
    }

    @Test
    @DisplayName("一致历史在极小预算下也可能未判定；放大预算后结论转为一致")
    void undecidedCanBecomeConsistentWithMoreBudget() {
        History h = history(consistentText);
        LinearizabilityAnalyzer a = new LinearizabilityAnalyzer();
        AnalysisResult tiny = a.analyze(h, 1);
        // 预算 1 时要么未判定，要么（搜索极浅的情形）不可能定论；这里只约束不得报不一致
        assertNotEquals(Verdict.INCONSISTENT, tiny.verdict());
        if (tiny.verdict() == Verdict.UNDECIDED) {
            AnalysisResult full = new LinearizabilityAnalyzer().analyze(h, 100_000);
            assertEquals(Verdict.CONSISTENT, full.verdict());
        }
    }

    @Test
    @DisplayName("预算必须为正整数")
    void budgetMustBePositive() {
        History h = History.of(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new LinearizabilityAnalyzer().analyze(h, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearizabilityAnalyzer().analyze(h, -5));
    }

    @Test
    @DisplayName("同一输入重复分析：结论、见证顺序、冲突原因完全可重复")
    void resultsAreRepeatable() {
        History bad = history(inconsistentText);
        AnalysisResult first = new LinearizabilityAnalyzer().analyze(bad, 50_000);
        AnalysisResult second = new LinearizabilityAnalyzer().analyze(bad, 50_000);
        assertEquals(first.verdict(), second.verdict());
        assertEquals(first.searchedNodes(), second.searchedNodes());
        List<String> reasons1 = first.conflict().orElseThrow().reasons();
        List<String> reasons2 = second.conflict().orElseThrow().reasons();
        assertEquals(reasons1, reasons2);

        History good = history(consistentText);
        AnalysisResult w1 = new LinearizabilityAnalyzer().analyze(good, 50_000);
        AnalysisResult w2 = new LinearizabilityAnalyzer().analyze(good, 50_000);
        assertEquals(w1.verdict(), w2.verdict());
        List<String> labels1 = witnessLabels(w1);
        List<String> labels2 = witnessLabels(w2);
        assertEquals(labels1, labels2);
    }

    @Test
    @DisplayName("同一分析器实例重复使用互不串扰")
    void analyzerReuseIsClean() {
        LinearizabilityAnalyzer analyzer = new LinearizabilityAnalyzer();
        History good = history(consistentText);
        History bad = history(inconsistentText);
        assertEquals(Verdict.CONSISTENT, analyzer.analyze(good, 50_000).verdict());
        assertEquals(Verdict.INCONSISTENT, analyzer.analyze(bad, 50_000).verdict());
        assertEquals(Verdict.CONSISTENT, analyzer.analyze(good, 50_000).verdict());
        assertEquals(Verdict.INCONSISTENT, analyzer.analyze(bad, 50_000).verdict());
    }

    private static List<String> witnessLabels(AnalysisResult r) {
        return r.witness().orElseThrow().stream()
                .map(s -> s.operation().label() + ":" + s.operation().kind().keyword())
                .toList();
    }
}
