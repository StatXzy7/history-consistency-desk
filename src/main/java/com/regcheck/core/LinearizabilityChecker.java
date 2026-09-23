package com.regcheck.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单对象（初值为零的整数寄存器）可线性化检查器。
 *
 * <p>语义：READ 返回当前值；ADD(delta) 返回加后的新值；CAS(expected, newValue)
 * 在当前值等于期望值时写入新值并返回 true，否则返回 false。
 *
 * <p>算法：对“寄存器值 + 已生效调用集合”做确定性深度优先搜索。
 * 非重叠调用之间的先后关系（a 的返回序号 &lt; b 的调用序号 ⇒ a 必须先生效）
 * 作为硬约束；重叠调用允许按任意满足语义的顺序生效。
 * 没有返回的调用可以被忽略，也可以在见证序列中补全；已返回调用必须全部出现。
 *
 * <p>预算：最多展开 {@code budget} 个搜索状态，耗尽时返回
 * {@link CheckResult.Undecided}，绝不把未搜索完当作不存在解释。
 * 同一输入与同一预算下结果完全确定（候选按操作编号升序尝试）。
 */
public final class LinearizabilityChecker {

    /** 单次分析允许的最大调用数。 */
    public static final int MAX_CALLS = 16;

    /** 默认搜索预算（展开的状态数）。 */
    public static final long DEFAULT_BUDGET = 1_000_000L;

    public CheckResult check(History history) {
        return check(history, DEFAULT_BUDGET);
    }

    public CheckResult check(History history, long budget) {
        ValidationResult validation = HistoryValidator.validate(history);
        if (!validation.isValid()) {
            return new CheckResult.Invalid(validation.errors());
        }

        List<Call> calls = new ArrayList<>(history.calls());
        calls.sort(Comparator.comparingInt(Call::id));
        int n = calls.size();

        // mustPrecede[j] 的第 i 位置位表示 calls[i] 必须先于 calls[j] 生效。
        int[] mustPrecede = new int[n];
        int completedMask = 0;
        for (int i = 0; i < n; i++) {
            Call a = calls.get(i);
            if (a.isCompleted()) {
                completedMask |= 1 << i;
                for (int j = 0; j < n; j++) {
                    if (i != j && a.responseSeq() < calls.get(j).invokeSeq()) {
                        mustPrecede[j] |= 1 << i;
                    }
                }
            }
        }

        Search search = new Search(calls, mustPrecede, completedMask, budget);
        List<WitnessStep> witness = search.solve();
        if (witness != null) {
            return new CheckResult.Linearizable(witness);
        }
        if (search.budgetExhausted) {
            return new CheckResult.Undecided(search.explored, budget);
        }
        return buildConflict(history, calls, budget);
    }

    /**
     * 不可线性化时，给出一组相互冲突的已返回调用。
     * 采用贪心缩减（不保证最小反例）：每轮按“先观察者、后写操作”的顺序
     * 尝试移除一个已返回调用，若移除后仍不可线性化则真正移除。
     * 这样能优先保留写操作之间的直接冲突（如两个 CAS 同时成功）。
     * 未返回的调用不影响可判定性（总可以被忽略），因此不参与冲突集。
     */
    private CheckResult buildConflict(History original, List<Call> sortedCalls,
                                      long budget) {
        List<Call> core = new ArrayList<>();
        for (Call c : sortedCalls) {
            if (c.isCompleted()) {
                core.add(c);
            }
        }
        boolean removedAny = true;
        while (removedAny) {
            removedAny = false;
            List<Call> candidates = core.stream()
                    .sorted(Comparator.comparingInt(LinearizabilityChecker::removalPriority))
                    .toList();
            for (Call candidate : candidates) {
                List<Call> trial = new ArrayList<>(core);
                trial.remove(candidate);
                CheckResult r = check(new History(trial), budget);
                if (r instanceof CheckResult.NotLinearizable) {
                    core = trial; // 移除后仍冲突，继续缩减
                    removedAny = true;
                    break;
                }
                if (r instanceof CheckResult.Undecided) {
                    return new CheckResult.NotLinearizable(List.copyOf(core),
                            explainConflict(core)
                                    + "（缩减反例时搜索预算耗尽，保留当前冲突集）");
                }
            }
        }
        return new CheckResult.NotLinearizable(List.copyOf(core),
                explainConflict(core));
    }

    /** READ 最先被尝试移除，失败的 CAS 次之，ADD/成功的 CAS 最后。 */
    private static int removalPriority(Call c) {
        return switch (c.op().kind()) {
            case READ -> 0;
            case CAS -> Boolean.FALSE.equals(c.result()) ? 1 : 3;
            case ADD -> 2;
        };
    }

    private static String explainConflict(List<Call> core) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下 ").append(core.size())
                .append(" 个已返回调用构成冲突——即使把其他调用全部移除，"
                        + "它们自身也不存在满足寄存器语义的合法顺序：\n");
        for (Call c : core) {
            sb.append("  - ").append(c.label())
                    .append("  [调用@").append(c.invokeSeq())
                    .append(" 返回@").append(c.responseSeq()).append("]\n");
        }
        sb.append("原因：在非重叠先后约束下，无论这些调用按什么顺序生效，"
                + "至少有一个调用的实际返回值与寄存器语义矛盾。");
        return sb.toString();
    }

    /** 一次状态转移的结果。 */
    private record Transition(long newValue, Object result) {
    }

    /** 计算调用在当前寄存器值下的语义结果；已返回调用还需核对实际返回值。 */
    private static Transition tryApply(Call call, long value) {
        Operation op = call.op();
        long newValue;
        Object semanticResult;
        switch (op.kind()) {
            case READ -> {
                newValue = value;
                semanticResult = value;
            }
            case ADD -> {
                newValue = value + op.arg1();
                semanticResult = newValue;
            }
            case CAS -> {
                boolean success = value == op.arg1();
                newValue = success ? op.arg2() : value;
                semanticResult = success;
            }
            default -> throw new IllegalStateException("未知操作 " + op.kind());
        }
        if (call.isCompleted() && !resultEquals(call.result(), semanticResult)) {
            return null;
        }
        return new Transition(newValue, semanticResult);
    }

    private static boolean resultEquals(Object observed, Object semantic) {
        if (observed instanceof Number num && semantic instanceof Number sem) {
            return num.longValue() == sem.longValue();
        }
        return observed.equals(semantic);
    }

    /** 确定性 DFS。状态 = (当前寄存器值, 已生效调用掩码)。 */
    private static final class Search {

        private final List<Call> calls;
        private final int[] mustPrecede;
        private final int completedMask;
        private long budget;
        private long explored;
        private boolean budgetExhausted;
        /** 已确认失败的状态（寄存器值, 掩码），避免重复搜索。 */
        private final Set<StateKey> failed = new HashSet<>();

        Search(List<Call> calls, int[] mustPrecede, int completedMask, long budget) {
            this.calls = calls;
            this.mustPrecede = mustPrecede;
            this.completedMask = completedMask;
            this.budget = budget;
        }

        List<WitnessStep> solve() {
            return dfs(0L, 0);
        }

        private List<WitnessStep> dfs(long value, int mask) {
            if ((mask & completedMask) == completedMask) {
                return new ArrayList<>(); // 所有已返回调用均已生效
            }
            if (budget <= 0) {
                budgetExhausted = true;
                return null;
            }
            budget--;
            explored++;

            StateKey key = new StateKey(value, mask);
            if (failed.contains(key)) {
                return null;
            }
            for (int i = 0; i < calls.size(); i++) {
                int bit = 1 << i;
                if ((mask & bit) != 0 || (mustPrecede[i] & ~mask) != 0) {
                    continue;
                }
                Call call = calls.get(i);
                Transition t = tryApply(call, value);
                if (t == null) {
                    continue;
                }
                List<WitnessStep> rest = dfs(t.newValue(), mask | bit);
                if (rest != null) {
                    rest.add(0, new WitnessStep(call, value, t.newValue(),
                            !call.isCompleted(), t.result()));
                    return rest;
                }
                if (budgetExhausted) {
                    return null; // 预算耗尽：向上传播，不缓存失败状态
                }
            }
            failed.add(key);
            return null;
        }
    }

    private record StateKey(long value, int mask) {
    }
}
