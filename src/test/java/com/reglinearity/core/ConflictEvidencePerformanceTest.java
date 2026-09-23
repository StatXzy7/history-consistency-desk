package com.reglinearity.core;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冲突证据构造的性能回归测试。
 *
 * <p>历史含 14 个与冲突操作区间重叠的未返回调用。旧实现判定不一致后会用一次
 * 无预算、无记忆化的二次 DFS 复算前缀，在此类历史上阶乘爆炸（实测 6 秒以上）。
 * 修复后前缀在主搜索路径上直接快照，整个分析仍受预算约束、毫秒级完成。</p>
 */
class ConflictEvidencePerformanceTest {

    private final HistoryParser parser = new HistoryParser();

    private History historyWithManyOverlappingPending(int pendingCount) {
        StringBuilder sb = new StringBuilder();
        long seq = 1;
        // 两个相互冲突的 add(1) 先调用
        sb.append(seq++).append(" INVOKE C1 1 add 1\n");
        sb.append(seq++).append(" INVOKE C2 1 add 1\n");
        // 大量未返回调用在两个 add 返回之前交错调用，全部区间重叠
        for (int p = 1; p <= pendingCount; p++) {
            sb.append(seq++).append(" INVOKE P").append(p).append(" 1 read\n");
        }
        sb.append(seq++).append(" RETURN C1 1 1\n");
        sb.append(seq).append(" RETURN C2 1 1\n");
        ParseOutcome o = parser.parse(sb.toString());
        assertTrue(o.isValid(), o.error().orElse(""));
        return o.history().orElseThrow();
    }

    @Test
    @DisplayName("16 操作含 14 个重叠未返回调用的不一致历史：快速给出冲突且前缀非空")
    void inconsistentHistoryWithManyPendingIsFast() {
        History h = historyWithManyOverlappingPending(History.MAX_OPERATIONS - 2);
        assertEquals(History.MAX_OPERATIONS, h.size());

        AnalysisResult r = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> new LinearizabilityAnalyzer().analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET));

        assertEquals(Verdict.INCONSISTENT, r.verdict());
        ConflictReport c = r.conflict().orElseThrow();
        assertFalse(c.blockedIndices().isEmpty(), "冲突报告必须指出至少一个冲突操作");
        assertFalse(c.prefix().isEmpty(), "冲突前缀应由主搜索路径直接快照得到，不能为空");
        // 前缀中的每一步都必须是合法转移
        long value = RegisterSpec.INITIAL_VALUE;
        for (LinearizationStep step : c.prefix()) {
            assertEquals(value, step.valueBefore());
            RegisterSpec.Transition t = RegisterSpec.apply(step.operation(), value);
            assertTrue(t.legal());
            assertEquals(t.nextValue(), step.valueAfter());
            value = step.valueAfter();
        }
        // 14 个无关未返回 read 已在搜索前忽略：搜索节点数极小（不会枚举 2^14 子集），
        // 且冲突只涉及两个 add（被忽略的 read 不属于冲突操作）。
        assertTrue(r.searchedNodes() < 50,
                "未返回 read 应被剪枝，实际搜索节点数 " + r.searchedNodes());
        for (int idx : c.blockedIndices()) {
            assertEquals(OpKind.ADD, h.operation(idx).kind());
        }
    }
}
