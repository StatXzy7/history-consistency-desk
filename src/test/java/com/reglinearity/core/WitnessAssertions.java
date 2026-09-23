package com.reglinearity.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试专用的独立见证校验器：不依赖分析器自身的重建逻辑，
 * 直接按定义验证一个合法顺序（1）含全部已返回调用且每个只出现一次，
 * （2）保持非重叠先后关系，（3）逐步执行结果与记录的返回值及寄存器状态相符。
 */
final class WitnessAssertions {

    private WitnessAssertions() {
    }

    static void assertValidWitness(History history, AnalysisResult result) {
        assertEquals(Verdict.CONSISTENT, result.verdict());
        // 全部调用都是被忽略的未返回 read 时，见证为空是合法的。
        List<LinearizationStep> steps = result.witness().orElse(java.util.List.of());

        long value = RegisterSpec.INITIAL_VALUE;
        Set<Integer> placed = new HashSet<>();
        for (LinearizationStep step : steps) {
            int idx = step.operation().index();
            // 1) 每个调用至多出现一次
            assertTrue(placed.add(idx), "调用 " + idx + " 在见证中重复出现");

            // 2) 前序（非重叠且先发生）调用必须已在更早的位置
            for (int p : history.predecessors(idx)) {
                assertTrue(placed.contains(p),
                        "见证违反非重叠先后关系：" + history.operation(p).label()
                                + " 必须先于 " + history.operation(idx).label());
            }

            // 3) 寄存器值链与步骤标注一致
            assertEquals(value, step.valueBefore(), "valueBefore 与逐步模拟不符");
            RegisterSpec.Transition t = RegisterSpec.apply(step.operation(), value);
            assertTrue(t.legal(), "见证中的步骤按规范不合法");
            assertEquals(t.nextValue(), step.valueAfter(), "valueAfter 与规范不符");

            // 4) 已返回调用的返回值必须与记录一致（apply 已检查，这里再显式核对补全标记）
            Operation op = step.operation();
            if (op.completed()) {
                assertFalse(step.completedPending(), "已返回调用不应被标记为补全");
            }
            value = step.valueAfter();
        }

        // 5) 所有已返回调用必须出现，不能丢弃；未返回调用要么在见证中，要么在 droppedPending 中
        Set<Integer> dropped = new HashSet<>(result.droppedPending());
        for (int i = 0; i < history.size(); i++) {
            Operation op = history.operation(i);
            if (op.completed()) {
                assertTrue(placed.contains(i), "已返回调用 " + op.label() + " 被丢弃，违反规范");
            } else {
                assertTrue(placed.contains(i) || dropped.contains(i),
                        "未返回调用 " + op.label() + " 既未补全也未声明忽略");
            }
            assertFalse(op.completed() && dropped.contains(i), "已返回调用出现在丢弃列表中");
        }
        // 丢弃列表里的调用不得同时出现在见证中
        for (int d : dropped) {
            assertFalse(placed.contains(d), "调用 " + d + " 同时被补全和丢弃");
        }
    }
}
