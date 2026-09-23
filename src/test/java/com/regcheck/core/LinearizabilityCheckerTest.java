package com.regcheck.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearizabilityCheckerTest {

    private final LinearizabilityChecker checker = new LinearizabilityChecker();

    @Test
    void sequentialHistoryIsLinearizable() {
        History h = new History(List.of(
                new Call(1, "A", Operation.add(2), 1, 2L, 2L),
                new Call(2, "A", Operation.add(3), 3, 4L, 5L),
                new Call(3, "A", Operation.read(), 5, 6L, 5L)));
        CheckResult result = checker.check(h);
        assertInstanceOf(CheckResult.Linearizable.class, result);
    }

    @Test
    void overlappingReadCanLinearizeBeforeLongerAdd() {
        // 按开始时间排序会误判：A 先开始，但 READ 与 ADD 重叠，
        // READ 可以在 ADD 之前生效并读到 0。
        History h = new History(List.of(
                new Call(1, "A", Operation.add(1), 1, 6L, 1L),
                new Call(2, "B", Operation.read(), 2, 3L, 0L)));
        assertInstanceOf(CheckResult.Linearizable.class, checker.check(h));
    }

    @Test
    void overlappingReadCanAlsoLinearizeAfterAdd() {
        History h = new History(List.of(
                new Call(1, "A", Operation.add(1), 1, 6L, 1L),
                new Call(2, "B", Operation.read(), 2, 3L, 1L)));
        assertInstanceOf(CheckResult.Linearizable.class, checker.check(h));
    }

    @Test
    void nonOverlappingOrderIsForced() {
        // ADD(1) 已在 READ 开始前返回，READ 必须读到 1，读到 0 不合法。
        History h = new History(List.of(
                new Call(1, "A", Operation.add(1), 1, 2L, 1L),
                new Call(2, "B", Operation.read(), 3, 4L, 0L)));
        assertInstanceOf(CheckResult.NotLinearizable.class, checker.check(h));
    }

    @Test
    void lostUpdateIsNotLinearizable() {
        CheckResult r = checker.check(Samples.lostUpdate());
        assertInstanceOf(CheckResult.NotLinearizable.class, r);
        // 冲突集中的调用必须都是已返回调用（不能被丢弃）
        for (Call c : ((CheckResult.NotLinearizable) r).conflictCalls()) {
            assertTrue(c.isCompleted());
        }
    }

    @Test
    void casRaceIsNotLinearizable() {
        assertInstanceOf(CheckResult.NotLinearizable.class,
                checker.check(Samples.casRace()));
    }

    @Test
    void casFailureCanBeLinearizedByOverlappingAdd() {
        History h = Samples.correctInterleaving();
        assertInstanceOf(CheckResult.Linearizable.class, checker.check(h));
    }

    @Test
    void pendingCallCanBeCompletedInWitness() {
        CheckResult r = checker.check(Samples.pendingCall());
        CheckResult.Linearizable lin = assertInstanceOf(
                CheckResult.Linearizable.class, r);
        boolean hasCompletedPending = lin.witness().stream()
                .anyMatch(WitnessStep::completedInWitness);
        assertTrue(hasCompletedPending, "见证序列应补全未返回的 ADD");
    }

    @Test
    void pendingCallMayBeIgnored() {
        // 未返回的 CAS 与 READ 重叠，忽略它即可；READ 读到 0 合法。
        History h = new History(List.of(
                new Call(1, "A", Operation.cas(0, 9), 1, null, null),
                new Call(2, "B", Operation.read(), 2, 3L, 0L)));
        assertInstanceOf(CheckResult.Linearizable.class, checker.check(h));
    }

    @Test
    void pendingCallCannotExplainImpossibleRead() {
        // READ 声称读到 5，但唯一可能的写操作是未返回的 ADD(3)，
        // 即使补全也到不了 5。
        History h = new History(List.of(
                new Call(1, "A", Operation.add(3), 1, null, null),
                new Call(2, "B", Operation.read(), 2, 3L, 5L)));
        assertInstanceOf(CheckResult.NotLinearizable.class, checker.check(h));
    }

    @Test
    void witnessIsStepwiseConsistentAndValid() {
        CheckResult.Linearizable lin = (CheckResult.Linearizable)
                checker.check(Samples.correctInterleaving());
        long expectedBefore = 0;
        for (WitnessStep step : lin.witness()) {
            assertEquals(expectedBefore, step.valueBefore());
            expectedBefore = step.valueAfter();
            Call c = step.call();
            // 已返回调用在见证中使用的结果必须与记录一致
            if (c.isCompleted()) {
                assertEquals(c.result(), normalize(step.observedResult()));
            }
        }
        // 所有已返回调用必须出现在见证中
        List<Integer> witnessIds = lin.witness().stream()
                .map(s -> s.call().id()).sorted().toList();
        assertTrue(witnessIds.containsAll(List.of(1, 2, 3)));
    }

    @Test
    void budgetExhaustionIsUndecidedNotRejection() {
        // 预算为 1，且需要多于一个调用生效，必然无法完成搜索
        CheckResult r1 = checker.check(Samples.lostUpdate(), 1);
        assertInstanceOf(CheckResult.Undecided.class, r1);
        // 充足预算下仍能判定为不可线性化
        CheckResult r2 = checker.check(Samples.lostUpdate(), 1_000_000);
        assertInstanceOf(CheckResult.NotLinearizable.class, r2);
    }

    @Test
    void resultsAreDeterministic() {
        History h = Samples.correctInterleaving();
        Object a = checker.check(h, 100_000);
        Object b = checker.check(h, 100_000);
        assertEquals(a, b);
    }

    @Test
    void maxCallsBoundaryAccepted() {
        int n = LinearizabilityChecker.MAX_CALLS;
        java.util.List<Call> calls = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            calls.add(new Call(i, "A", Operation.add(1), i * 2L - 1, i * 2L,
                    (long) i));
        }
        assertInstanceOf(CheckResult.Linearizable.class,
                checker.check(new History(calls)));
    }

    @Test
    void moreThanMaxCallsIsInvalid() {
        int n = LinearizabilityChecker.MAX_CALLS + 1;
        java.util.List<Call> calls = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            calls.add(new Call(i, "A", Operation.read(), i * 2L - 1, i * 2L, 0L));
        }
        CheckResult r = checker.check(new History(calls));
        CheckResult.Invalid invalid =
                assertInstanceOf(CheckResult.Invalid.class, r);
        assertFalse(invalid.errors().isEmpty());
    }

    private static Object normalize(Object o) {
        return o instanceof Number n ? n.longValue() : o;
    }
}
