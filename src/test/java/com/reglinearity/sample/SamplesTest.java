package com.reglinearity.sample;

import java.util.List;

import com.reglinearity.core.AnalysisResult;
import com.reglinearity.core.ConsistencyService;
import com.reglinearity.core.LinearizabilityAnalyzer;
import com.reglinearity.core.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 内置样例必须与其教学意图一致：可解释/不可解释各归其位。 */
class SamplesTest {

    private final ConsistencyService service = new ConsistencyService();

    @Test
    @DisplayName("三个必需样例齐备且结论符合命名")
    void builtInSamplesHaveIntendedVerdicts() {
        ConsistencyService.ServiceResponse r1 = service.analyze(
                Samples.CORRECT_INTERLEAVING.text(), LinearizabilityAnalyzer.DEFAULT_BUDGET);
        assertFalse(r1.isIllegalInput());
        assertEquals(Verdict.CONSISTENT, r1.result().verdict());

        ConsistencyService.ServiceResponse r2 = service.analyze(
                Samples.LOST_UPDATE.text(), LinearizabilityAnalyzer.DEFAULT_BUDGET);
        assertFalse(r2.isIllegalInput());
        assertEquals(Verdict.INCONSISTENT, r2.result().verdict());

        ConsistencyService.ServiceResponse r3 = service.analyze(
                Samples.CAS_RACE.text(), LinearizabilityAnalyzer.DEFAULT_BUDGET);
        assertFalse(r3.isIllegalInput());
        assertEquals(Verdict.INCONSISTENT, r3.result().verdict());
    }

    @Test
    @DisplayName("未返回样例可解释，且未返回 read 被忽略或补全")
    void pendingSampleConsistent() {
        ConsistencyService.ServiceResponse r = service.analyze(
                Samples.PENDING_READ.text(), LinearizabilityAnalyzer.DEFAULT_BUDGET);
        assertFalse(r.isIllegalInput());
        AnalysisResult result = r.result();
        assertEquals(Verdict.CONSISTENT, result.verdict());
        int accounted = result.witness().orElse(List.of()).size() + result.droppedPending().size();
        assertEquals(2, accounted);
    }

    @Test
    @DisplayName("样例文本渲染包含结论与逐步寄存器值")
    void renderContainsStepwiseValues() {
        ConsistencyService.ServiceResponse r = service.analyze(
                Samples.CORRECT_INTERLEAVING.text(), LinearizabilityAnalyzer.DEFAULT_BUDGET);
        String text = ConsistencyService.render(r.history(), r.result());
        assert text.contains("结论：可解释");
        assert text.contains("0 -> 10") || text.contains("[0 -> 10]");
    }
}
