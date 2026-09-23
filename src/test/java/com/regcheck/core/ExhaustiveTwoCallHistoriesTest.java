package com.regcheck.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 对两条调用的历史空间做系统性穷举（而非仅随机抽样）：
 * 枚举 1..4 事件位置上所有合法的调用/返回配对结构、
 * 操作菜单的全部组合、观察结果的全部组合，
 * 把回溯检查器与独立的暴力全排列实现逐条核对。
 */
class ExhaustiveTwoCallHistoriesTest {

    private static final Operation[] OPS = {
            Operation.read(),
            Operation.add(-1),
            Operation.add(1),
            Operation.cas(0, 1),
            Operation.cas(1, -1)
    };
    private static final Long[] INT_RESULTS = {-1L, 0L, 1L, 2L};
    private static final Boolean[] BOOL_RESULTS = {Boolean.FALSE, Boolean.TRUE};

    private final LinearizabilityChecker checker = new LinearizabilityChecker();
    private int compared;

    @Test
    void everyTwoCallHistoryIsCheckedCorrectly() {
        // 枚举事件结构：从 4 个位置中选 2 个作为调用位置（调用按位置归给 #1、#2），
        // 其余 2 个位置作为返回位置，并枚举把返回位置合法分配给两个调用的双射。
        for (int invMask = 1; invMask < 15; invMask++) {
            if (Integer.bitCount(invMask) != 2) {
                continue;
            }
            int[] invPos = positionsOf(invMask, true);
            int[] respPos = positionsOf(invMask, false);
            // 双射：perm[k] 表示第 k 个返回位置属于哪个调用（0/1）
            int[][] perms = {{0, 1}, {1, 0}};
            for (int[] perm : perms) {
                int respOfCall1 = respPos[indexOf(perm, 0)];
                int respOfCall2 = respPos[indexOf(perm, 1)];
                if (invPos[0] >= respOfCall1 || invPos[1] >= respOfCall2) {
                    continue; // 返回必须晚于本调用的调用事件
                }
                enumerateOpsAndResults(invPos[0], respOfCall1,
                        invPos[1], respOfCall2);
            }
        }
        // 3 个合法事件结构 × 256 种（操作，结果）组合 = 768 条历史全部核对
        assertFalse(compared < 700, "穷举用例数过少: " + compared);
    }

    private void enumerateOpsAndResults(int inv1, int resp1, int inv2, int resp2) {
        for (Operation op1 : OPS) {
            for (Object result1 : resultsFor(op1)) {
                for (Operation op2 : OPS) {
                    for (Object result2 : resultsFor(op2)) {
                        History h = new History(List.of(
                                new Call(1, "A", op1, inv1, (long) resp1, result1),
                                new Call(2, "B", op2, inv2, (long) resp2, result2)));
                        boolean expected = BruteForce.isLinearizable(h);
                        CheckResult r = checker.check(h, 10_000);
                        assertFalse(r instanceof CheckResult.Undecided,
                                "两调用历史不应耗尽预算");
                        assertEquals(expected,
                                r instanceof CheckResult.Linearizable,
                                "与暴力核对不一致: " + h);
                        compared++;
                    }
                }
            }
        }
    }

    private static Object[] resultsFor(Operation op) {
        return op.kind() == OpKind.CAS ? BOOL_RESULTS : INT_RESULTS;
    }

    private static int[] positionsOf(int mask, boolean set) {
        List<Integer> pos = new ArrayList<>();
        for (int p = 1; p <= 4; p++) {
            boolean on = (mask & (1 << (p - 1))) != 0;
            if (on == set) {
                pos.add(p);
            }
        }
        return pos.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int indexOf(int[] arr, int value) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        throw new AssertionError();
    }
}
