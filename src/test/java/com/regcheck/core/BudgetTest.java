package com.regcheck.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetTest {

    private final LinearizabilityChecker checker = new LinearizabilityChecker();

    @Test
    void undecidedReportsExploredStatesWithinBudget() {
        History h = Samples.lostUpdate();
        // 预算 1 时只能展开根状态，必然未判定
        CheckResult.Undecided u = assertInstanceOf(CheckResult.Undecided.class,
                checker.check(h, 1));
        assertEquals(1, u.budget());
        assertTrue(u.exploredStates() <= 1,
                "探索状态数不能超过预算");
        assertEquals(1, u.exploredStates());
    }

    @Test
    void zeroOrNegativeBudgetOnNonTrivialHistoryIsUndecided() {
        History h = Samples.lostUpdate();
        assertInstanceOf(CheckResult.Undecided.class, checker.check(h, 0));
    }

    @Test
    void emptyWitnessNeedsNoBudget() {
        // 单条未返回调用：可直接忽略，无需展开任何状态
        History h = new History(List.of(
                new Call(1, "A", Operation.read(), 1, null, null)));
        assertInstanceOf(CheckResult.Linearizable.class, checker.check(h, 0));
    }

    @Test
    void increasingBudgetEventuallyDecides() {
        History h = Samples.lostUpdate();
        assertInstanceOf(CheckResult.Undecided.class, checker.check(h, 1));
        assertInstanceOf(CheckResult.NotLinearizable.class, checker.check(h, 1000));
    }

    @Test
    void repeatedRunsAreIdentical() {
        History h = Samples.casRace();
        String first = ResultText.format(checker.check(h, 1));
        for (int i = 0; i < 5; i++) {
            assertEquals(first, ResultText.format(checker.check(h, 1)));
        }
        String full = ResultText.format(checker.check(h, 1_000_000));
        for (int i = 0; i < 5; i++) {
            assertEquals(full, ResultText.format(checker.check(h, 1_000_000)));
        }
    }
}
