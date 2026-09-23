package com.reglinearity.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分析器的场景测试：正常顺序、重叠竞争（丢失更新、CAS 竞争、可解释重叠）、
 * 未返回调用的忽略与补全、非重叠先后约束、冲突报告内容。
 */
class LinearizabilityAnalyzerTest {

    private final HistoryParser parser = new HistoryParser();
    private final LinearizabilityAnalyzer analyzer = new LinearizabilityAnalyzer();

    private History history(String text) {
        ParseOutcome o = parser.parse(text);
        assertTrue(o.isValid(), o.error().orElse(""));
        return o.history().orElseThrow();
    }

    private AnalysisResult run(String text) {
        return analyzer.analyze(history(text), LinearizabilityAnalyzer.DEFAULT_BUDGET);
    }

    @Nested
    @DisplayName("正常顺序")
    class Sequential {

        @Test
        @DisplayName("单个 add 返回新值，可解释且逐步值正确")
        void singleAdd() {
            AnalysisResult r = run("""
                    1 INVOKE C1 1 add 5
                    2 RETURN C1 1 5
                    """);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(history("""
                    1 INVOKE C1 1 add 5
                    2 RETURN C1 1 5
                    """), r);
            assertEquals(5L, r.witness().orElseThrow().get(0).valueAfter());
        }

        @Test
        @DisplayName("顺序的 add/read/cas 链：0 -> 3 -> 读到 3 -> cas(3,10) 成功")
        void sequentialChain() {
            String text = """
                    1 INVOKE C1 1 add 3
                    2 RETURN C1 1 3
                    3 INVOKE C2 1 read
                    4 RETURN C2 1 3
                    5 INVOKE C3 1 cas 3 10
                    6 RETURN C3 1 true
                    """;
            AnalysisResult r = run(text);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(history(text), r);
            List<LinearizationStep> steps = r.witness().orElseThrow();
            assertEquals(List.of(3L, 3L, 10L),
                    steps.stream().map(LinearizationStep::valueAfter).toList());
        }

        @Test
        @DisplayName("cas 失败路径：寄存器为 5 时 cas(0,1) 必须返回 false，值不变")
        void failedCasIsConsistent() {
            String text = """
                    1 INVOKE C1 1 add 5
                    2 RETURN C1 1 5
                    3 INVOKE C2 1 cas 0 1
                    4 RETURN C2 1 false
                    """;
            AnalysisResult r = run(text);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(history(text), r);
        }

        @Test
        @DisplayName("非重叠顺序不能倒置：先 add(5) 返回后，read 不可能返回 0")
        void nonOverlapForbidsReordering() {
            String text = """
                    1 INVOKE C1 1 add 5
                    2 RETURN C1 1 5
                    3 INVOKE C2 1 read
                    4 RETURN C2 1 0
                    """;
            AnalysisResult r = run(text);
            assertEquals(Verdict.INCONSISTENT, r.verdict());
            // 冲突必须点到 read，且不是因为预算
            ConflictReport c = r.conflict().orElseThrow();
            assertTrue(c.reasons().stream().anyMatch(s -> s.contains("读取返回 0")));
        }

        @Test
        @DisplayName("空历史平凡一致")
        void emptyHistory() {
            AnalysisResult r = analyzer.analyze(History.of(List.of()));
            assertEquals(Verdict.CONSISTENT, r.verdict());
            assertTrue(r.witness().orElse(List.of()).isEmpty());
        }
    }

    @Nested
    @DisplayName("重叠竞争")
    class Concurrent {

        @Test
        @DisplayName("正确交错：读先返回却返回重叠中 add 的新值，可解释")
        void readCanLinearizeAfterOverlappingAdd() {
            String text = """
                    1 INVOKE C1 1 add 10
                    2 INVOKE C2 1 read
                    3 RETURN C2 1 10
                    4 RETURN C1 1 10
                    """;
            History h = history(text);
            AnalysisResult r = analyzer.analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(h, r);
            // 见证顺序必须与“返回时间顺序”不同：read(返回序号3) 排在 add(返回序号4) 之后
            List<LinearizationStep> steps = r.witness().orElseThrow();
            assertEquals("add", steps.get(0).operation().kind().keyword());
            assertEquals("read", steps.get(1).operation().kind().keyword());
        }

        @Test
        @DisplayName("丢失更新：两个重叠 add(1) 都返回 1，不可解释")
        void lostUpdate() {
            String text = """
                    1 INVOKE C1 1 add 1
                    2 INVOKE C2 1 add 1
                    3 RETURN C1 1 1
                    4 RETURN C2 1 1
                    """;
            AnalysisResult r = run(text);
            assertEquals(Verdict.INCONSISTENT, r.verdict());
            ConflictReport c = r.conflict().orElseThrow();
            assertFalse(c.blockedIndices().isEmpty());
            assertTrue(c.reasons().stream().anyMatch(s -> s.contains("原子相加应得到 2")));
        }

        @Test
        @DisplayName("CAS 竞争：两个重叠 cas(0,1) 都成功，不可解释")
        void doubleSuccessfulCas() {
            String text = """
                    1 INVOKE C1 1 cas 0 1
                    2 INVOKE C2 1 cas 0 1
                    3 RETURN C1 1 true
                    4 RETURN C2 1 true
                    """;
            AnalysisResult r = run(text);
            assertEquals(Verdict.INCONSISTENT, r.verdict());
            assertTrue(r.conflict().orElseThrow().reasons().stream()
                    .anyMatch(s -> s.contains("返回 true") && s.contains("不等于期望值")));
        }

        @Test
        @DisplayName("CAS 竞争的合法形态：一个 true 一个重叠 false，可解释")
        void casRaceOneWinner() {
            String text = """
                    1 INVOKE C1 1 cas 0 1
                    2 INVOKE C2 1 cas 0 1
                    3 RETURN C1 1 true
                    4 RETURN C2 1 false
                    """;
            History h = history(text);
            AnalysisResult r = analyzer.analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(h, r);
        }

        @Test
        @DisplayName("三个重叠 add 的合法结果：返回值构成某个排列的前缀和")
        void threeConcurrentAddsConsistent() {
            String text = """
                    1 INVOKE C1 1 add 2
                    2 INVOKE C2 1 add 3
                    3 INVOKE C3 1 add 5
                    4 RETURN C1 1 2
                    5 RETURN C2 1 5
                    6 RETURN C3 1 10
                    """;
            // 生效顺序 +2 -> +3 -> +5：逐步值 2, 5, 10，与各方返回值吻合
            History h = history(text);
            AnalysisResult r = analyzer.analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(h, r);
            assertEquals(List.of(2L, 5L, 10L),
                    r.witness().orElseThrow().stream().map(LinearizationStep::valueAfter).toList());
        }

        @Test
        @DisplayName("三个重叠 add 的返回值集合无法构成前缀和：不可解释")
        void threeConcurrentAddsInconsistent() {
            String text = """
                    1 INVOKE C1 1 add 2
                    2 INVOKE C2 1 add 3
                    3 INVOKE C3 1 add 5
                    4 RETURN C1 1 2
                    5 RETURN C2 1 2
                    6 RETURN C3 1 2
                    """;
            AnalysisResult r = run(text);
            assertEquals(Verdict.INCONSISTENT, r.verdict());
        }
    }

    @Nested
    @DisplayName("未返回调用")
    class Pending {

        @Test
        @DisplayName("未返回的重叠 add 与返回 0 的 read 可解释：add 可放在 read 之后补全")
        void pendingOverlappingOperationsConsistent() {
            String text = """
                    1 INVOKE C1 1 add 5
                    2 INVOKE C2 1 read
                    3 RETURN C2 1 0
                    """;
            History h = history(text);
            AnalysisResult r = analyzer.analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(h, r);
            // read=0 在前，未返回 add(5) 放在最后补全：所有调用都有交代
            assertEquals(2, r.witness().orElseThrow().size() + r.droppedPending().size());
        }

        @Test
        @DisplayName("未返回 add 若补全会溢出：必须走“忽略”分支，已返回历史仍一致")
        void pendingAddMustBeDroppedOnOverflow() {
            // add(5)->5、read->5 都已结束；最后一个未返回 add(MAX_VALUE) 一旦补全必溢出，只能忽略
            String text = "1 INVOKE C1 1 add 5\n"
                    + "2 RETURN C1 1 5\n"
                    + "3 INVOKE C2 1 read\n"
                    + "4 RETURN C2 1 5\n"
                    + "5 INVOKE C3 1 add " + Long.MAX_VALUE + "\n";
            History h = history(text);
            AnalysisResult r = analyzer.analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(h, r);
            assertEquals(List.of(2), r.droppedPending());
        }

        @Test
        @DisplayName("未返回调用也可以在解释中补全：read=5 迫使未返回的 cas(0,5) 必须先生效")
        void pendingCanBeCompleted() {
            // 未返回 cas(0,5) 与 read 重叠；read 返回 5，从初值 0 出发必须让 cas 补全成功后读
            String text = """
                    1 INVOKE C1 1 cas 0 5
                    2 INVOKE C2 1 read
                    3 RETURN C2 1 5
                    """;
            History h = history(text);
            AnalysisResult r = analyzer.analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(h, r);
            // cas 必然是被补全的那一步，且必须排在 read 之前
            List<LinearizationStep> steps = r.witness().orElseThrow();
            assertEquals(OpKind.CAS, steps.get(0).operation().kind());
            assertTrue(steps.get(0).completedPending());
            assertEquals(OpKind.READ, steps.get(1).operation().kind());
        }

        @Test
        @DisplayName("未返回 read 是空操作：分析时直接忽略，并出现在忽略列表中")
        void pendingReadIsIgnoredAsNoOp() {
            String text = """
                    1 INVOKE C1 1 add 7
                    2 INVOKE C2 1 read
                    3 RETURN C1 1 7
                    """;
            History h = history(text);
            AnalysisResult r = analyzer.analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            WitnessAssertions.assertValidWitness(h, r);
            assertEquals(List.of(1), r.droppedPending());
        }

        @Test
        @DisplayName("只有未返回调用的历史平凡可解释（全部补全或全部忽略均可）")
        void onlyPendingOperations() {
            String text = """
                    1 INVOKE C1 1 add 5
                    2 INVOKE C2 1 cas 0 1
                    """;
            AnalysisResult r = run(text);
            assertEquals(Verdict.CONSISTENT, r.verdict());
            // 没有任何已返回约束，每个未返回调用都必须有交代（补全或忽略）
            int accounted = r.witness().orElseThrow().size() + r.droppedPending().size();
            assertEquals(2, accounted);
        }

        @Test
        @DisplayName("未返回调用不能挽救已返回调用之间的冲突")
        void pendingDoesNotHideConflict() {
            String text = """
                    1 INVOKE C1 1 add 1
                    2 INVOKE C2 1 add 1
                    3 INVOKE C3 1 read
                    4 RETURN C1 1 1
                    5 RETURN C2 1 1
                    """;
            AnalysisResult r = run(text);
            assertEquals(Verdict.INCONSISTENT, r.verdict());
        }
    }
}
