package com.regcheck.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用随机小历史（1–7 个调用）把回溯检查器与独立的暴力全排列实现互相核对。
 * 随机种子固定，保证结果可重复。
 */
class BruteForceCrossCheckTest {

    private static final long SEED = 20260924L;
    private static final int CASES = 400;
    private static final long BIG_BUDGET = 10_000_000L;

    private final LinearizabilityChecker checker = new LinearizabilityChecker();

    @Test
    void checkerAgreesWithBruteForceOnRandomSmallHistories() {
        Random rng = new Random(SEED);
        int linearizable = 0;
        int notLinearizable = 0;
        for (int i = 0; i < CASES; i++) {
            History h = randomHistory(rng);
            boolean expected = BruteForce.isLinearizable(h);
            CheckResult result = checker.check(h, BIG_BUDGET);
            assertFalse(result instanceof CheckResult.Undecided,
                    "预算充足时不应未判定: " + h);
            assertEquals(expected, result instanceof CheckResult.Linearizable,
                    "与暴力核对不一致: " + h);
            if (expected) {
                linearizable++;
                assertWitnessValid(h,
                        ((CheckResult.Linearizable) result).witness());
            } else {
                notLinearizable++;
            }
        }
        // 两类结论都必须真实出现，否则核对没有区分度
        assertTrue(linearizable > 50, "可线性化用例过少: " + linearizable);
        assertTrue(notLinearizable > 50, "不可线性化用例过少: " + notLinearizable);
    }

    /** 验证见证序列：初值 0、逐步值衔接、先后约束、已返回调用齐全且结果一致。 */
    static void assertWitnessValid(History h, List<WitnessStep> witness) {
        long value = 0;
        Set<Integer> seen = new HashSet<>();
        for (WitnessStep step : witness) {
            assertEquals(value, step.valueBefore(), "寄存器值应逐步衔接");
            value = step.valueAfter();
            Call c = step.call();
            assertTrue(seen.add(c.id()), "调用不应重复生效: " + c.id());
            if (c.isCompleted()) {
                Object observed = step.observedResult();
                if (observed instanceof Number n) {
                    assertEquals(((Number) c.result()).longValue(), n.longValue());
                } else {
                    assertEquals(c.result(), observed);
                }
            }
        }
        for (Call c : h.calls()) {
            if (c.isCompleted()) {
                assertTrue(seen.contains(c.id()),
                        "已返回调用必须出现在见证中: " + c.id());
            }
        }
        // 非重叠先后约束
        for (Call a : h.calls()) {
            if (a.responseSeq() == null) {
                continue;
            }
            for (Call b : h.calls()) {
                if (a.id() != b.id() && a.responseSeq() < b.invokeSeq()) {
                    assertTrue(indexOf(witness, a.id()) < indexOf(witness, b.id()),
                            "#" + a.id() + " 必须先于 #" + b.id());
                }
            }
        }
    }

    private static int indexOf(List<WitnessStep> witness, int id) {
        for (int i = 0; i < witness.size(); i++) {
            if (witness.get(i).call().id() == id) {
                return i;
            }
        }
        return Integer.MAX_VALUE; // 未返回调用可被忽略
    }

    // ---------- 随机历史生成 ----------

    private static final String[] CLIENTS = {"A", "B", "C"};

    private static History randomHistory(Random rng) {
        int n = 1 + rng.nextInt(7);
        List<Call> calls = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            calls.add(randomCall(i, rng));
        }
        // 约六成用例：按一个随机合法顺序模拟出“诚实”的结果，保证可线性化
        if (rng.nextDouble() < 0.6) {
            assignHonestResults(calls, rng);
        } else {
            assignRandomResults(calls, rng);
        }
        return new History(calls);
    }

    private static Call randomCall(int id, Random rng) {
        String client = CLIENTS[rng.nextInt(CLIENTS.length)];
        Operation op = switch (rng.nextInt(3)) {
            case 0 -> Operation.read();
            case 1 -> Operation.add(rng.nextInt(7) - 3);
            default -> Operation.cas(rng.nextInt(3), rng.nextInt(4));
        };
        long invoke = rng.nextInt(2 * id) + rng.nextInt(3);
        if (rng.nextDouble() < 0.2) {
            return new Call(id, client, op, invoke, null, null);
        }
        long response = invoke + 1 + rng.nextInt(3);
        return new Call(id, client, op, invoke, response, null);
    }

    /** 随机选一个合法生效顺序模拟，给已返回调用赋予与语义一致的结果。 */
    private static void assignHonestResults(List<Call> calls, Random rng) {
        List<Call> remaining = new ArrayList<>(calls);
        long value = 0;
        while (!remaining.isEmpty()) {
            List<Call> ready = remaining.stream()
                    .filter(c -> remaining.stream().noneMatch(a ->
                            a != c && a.responseSeq() != null
                                    && a.responseSeq() < c.invokeSeq()))
                    .toList();
            Call pick = ready.get(rng.nextInt(ready.size()));
            remaining.remove(pick);
            Object result = switch (pick.op().kind()) {
                case READ -> value;
                case ADD -> value + pick.op().arg1();
                case CAS -> value == pick.op().arg1();
            };
            value = switch (pick.op().kind()) {
                case READ -> value;
                case ADD -> value + pick.op().arg1();
                case CAS -> value == pick.op().arg1() ? pick.op().arg2() : value;
            };
            if (pick.isCompleted()) {
                replace(calls, new Call(pick.id(), pick.client(), pick.op(),
                        pick.invokeSeq(), pick.responseSeq(), result));
            }
        }
    }

    private static void assignRandomResults(List<Call> calls, Random rng) {
        for (Call c : calls) {
            if (!c.isCompleted()) {
                continue;
            }
            Object result = switch (c.op().kind()) {
                case READ, ADD -> (long) rng.nextInt(5);
                case CAS -> rng.nextBoolean();
            };
            replace(calls, new Call(c.id(), c.client(), c.op(),
                    c.invokeSeq(), c.responseSeq(), result));
        }
    }

    private static void replace(List<Call> calls, Call updated) {
        for (int i = 0; i < calls.size(); i++) {
            if (calls.get(i).id() == updated.id()) {
                calls.set(i, updated);
                return;
            }
        }
    }

    @Test
    void invalidHistoriesNeverReachConsistencyCheck() {
        // 非法输入必须报告 Invalid，而不是 NotLinearizable
        History dup = new History(List.of(
                new Call(1, "A", Operation.read(), 1, 2L, 0L),
                new Call(1, "B", Operation.read(), 3, 4L, 0L)));
        assertInstanceOf(CheckResult.Invalid.class, checker.check(dup));
    }
}
