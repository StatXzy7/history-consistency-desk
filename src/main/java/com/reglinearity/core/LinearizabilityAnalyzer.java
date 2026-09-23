package com.reglinearity.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 单寄存器调用历史的线性一致性（顺序规范一致性）分析器。
 *
 * <p>判定问题：能否把每次调用视为在其调用/返回区间内的某个时间点上瞬时生效的原子操作，
 * 使得所有已返回调用的返回值与寄存器规范（初值 0）相符。区间不重叠的调用必须保持先后，
 * 区间重叠的调用可按任意满足规范的顺序解释——既不是只按开始时间排，也不是只按返回时间排。</p>
 *
 * <h2>搜索算法</h2>
 * <p>Wing–Gong 风格的状态空间搜索：状态为（尚未安排的调用集合，当前寄存器值）。
 * 一个调用“可安排”当且仅当它的所有前序调用（返回序号 ≤ 其调用序号）都已安排。
 * 已返回调用必须以其记录的返回值安排；未返回调用可以按任意合法返回值补全安排，
 * 也可以整体忽略（从剩余集合中丢弃）。对失败状态做记忆化剪枝。</p>
 *
 * <h2>预算</h2>
 * <p>访问节点数超过预算立即返回 {@link Verdict#UNDECIDED}——“未判定”绝不能被当作
 * “不存在解释”。冲突证据的前缀在主搜索路径上直接快照得到，不需要第二次搜索，
 * 因此预算对整个分析（含证据构造）都生效。枚举不含随机源或依赖哈希迭代顺序的分支，
 * 同一输入结论可重复。</p>
 *
 * <p>分析器本身无状态、可被多线程共享；每次分析的全部可变状态都在私有的
 * {@link SearchContext} 中。纯逻辑，不依赖 Swing。</p>
 */
public final class LinearizabilityAnalyzer {

    /** 默认搜索预算（访问的搜索节点数）。 */
    public static final long DEFAULT_BUDGET = 200_000L;

    private static final long ALL_BITS_MASK = 0xFFFFFFFFFFFFFFFFL;

    public AnalysisResult analyze(History history) {
        return analyze(history, DEFAULT_BUDGET);
    }

    public AnalysisResult analyze(History history, long requestedBudget) {
        if (requestedBudget < 1) {
            throw new IllegalArgumentException("预算必须为正整数，实际为 " + requestedBudget);
        }
        if (history.size() == 0) {
            return AnalysisResult.trivialConsistent(requestedBudget);
        }
        return new SearchContext(history, requestedBudget).run();
    }

    // ------------------------------------------------------------------
    // 每次分析独立的可变上下文（不对外发布，无并发共享）
    // ------------------------------------------------------------------

    private static final class SearchContext {
        private final History history;
        private final int n;
        private final long budget;

        private long nodes;
        private boolean aborted;
        private final Set<StateKey> failed = new HashSet<>();
        private final java.util.HashMap<StateKey, Choice> parent = new java.util.HashMap<>();

        /** 当前 DFS 路径上已放置（不含丢弃）的步骤。 */
        private final List<LinearizationStep> currentPath = new ArrayList<>();

        /**
         * 未返回 read 的下标集合（位集）。这类调用严格无关：没有返回值约束、
         * read 不改变寄存器值、且未返回调用不可能成为任何操作的前序——
         * 补全它是一次空操作，因此直接忽略（规范允许忽略未返回调用），
         * 既不损失完备性，也避免对其补全/丢弃的组合爆炸。
         */
        private final long irrelevantPendingReadBits;

        /** 已找到的最深死路证据（放置步数最大；首个达到该深度的优先，保证确定性）。 */
        private int bestDepth = -1;
        private long bestRemaining;
        private long bestValue;
        private List<LinearizationStep> bestPrefix = List.of();

        SearchContext(History history, long budget) {
            this.history = history;
            this.n = history.size();
            this.budget = budget;
            long readBits = 0L;
            for (int i = 0; i < n; i++) {
                Operation op = history.operation(i);
                if (!op.completed() && op.kind() == OpKind.READ) {
                    readBits |= 1L << i;
                }
            }
            this.irrelevantPendingReadBits = readBits;
        }

        AnalysisResult run() {
            long allBits = n == Long.SIZE ? ALL_BITS_MASK : (1L << n) - 1L;
            long initialBits = allBits & ~irrelevantPendingReadBits;
            boolean ok;
            try {
                ok = search(initialBits, RegisterSpec.INITIAL_VALUE);
            } catch (BudgetExhausted ex) {
                return AnalysisResult.undecided(nodes, budget);
            }
            if (ok) {
                return buildConsistent(initialBits);
            }
            if (aborted) {
                return AnalysisResult.undecided(nodes, budget);
            }
            return AnalysisResult.inconsistent(buildConflict(), budget);
        }

        /**
         * @param remaining 尚未安排（含可丢弃的未返回调用）的调用位集
         * @param value     当前寄存器值
         * @return 是否存在合法补全
         */
        private boolean search(long remaining, long value) {
            nodes++;
            if (nodes > budget) {
                aborted = true;
                throw BudgetExhausted.INSTANCE;
            }
            if (remaining == 0L) {
                return true;
            }
            StateKey key = new StateKey(remaining, value);
            if (failed.contains(key)) {
                return false;
            }

            // 1) 已返回调用：必须安排。按稳定下标序枚举，保证结论与见证可重复。
            List<Integer> availableCompleted = new ArrayList<>();
            List<Integer> availablePending = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!bitSet(remaining, i) || !predecessorsDone(i, remaining)) {
                    continue;
                }
                if (history.operation(i).completed()) {
                    availableCompleted.add(i);
                } else {
                    availablePending.add(i);
                }
            }

            for (int i : availableCompleted) {
                RegisterSpec.Transition t = RegisterSpec.apply(history.operation(i), value);
                if (t.legal()) {
                    long next = remaining & ~(1L << i);
                    pushStep(i, value, t.nextValue(), false);
                    boolean solved = search(next, t.nextValue());
                    popStep();
                    if (solved) {
                        parent.put(key, Choice.place(i, value, t.nextValue()));
                        return true;
                    }
                    if (aborted) {
                        return false;
                    }
                }
            }

            // 2) 未返回调用：优先在解释中补全安排，其次整体忽略。两条都尝试，保证完备。
            for (int i : availablePending) {
                RegisterSpec.Transition t = RegisterSpec.apply(history.operation(i), value);
                if (t.legal()) {
                    long next = remaining & ~(1L << i);
                    pushStep(i, value, t.nextValue(), true);
                    boolean solved = search(next, t.nextValue());
                    popStep();
                    if (solved) {
                        parent.put(key, Choice.completePending(i, value, t.nextValue()));
                        return true;
                    }
                    if (aborted) {
                        return false;
                    }
                }
                // 丢弃分支：寄存器值不变，也不产生线性化步骤。
                long dropped = remaining & ~(1L << i);
                if (search(dropped, value)) {
                    parent.put(key, Choice.drop(i, value));
                    return true;
                }
                if (aborted) {
                    return false;
                }
            }

            // 3) 记录最深死路作为冲突证据（直接快照当前路径，无需二次搜索）。
            int depth = currentPath.size();
            if (depth > bestDepth) {
                bestDepth = depth;
                bestRemaining = remaining;
                bestValue = value;
                bestPrefix = List.copyOf(currentPath);
            }
            failed.add(key);
            return false;
        }

        private void pushStep(int index, long valueBefore, long valueAfter, boolean pendingCompletion) {
            currentPath.add(new LinearizationStep(
                    history.operation(index), valueBefore, valueAfter, pendingCompletion));
        }

        private void popStep() {
            currentPath.remove(currentPath.size() - 1);
        }

        private boolean predecessorsDone(int i, long remaining) {
            for (int p : history.predecessors(i)) {
                if (bitSet(remaining, p)) {
                    return false;
                }
            }
            return true;
        }

        // ------------------------------------------------------------------
        // 见证重建
        // ------------------------------------------------------------------

        private AnalysisResult buildConsistent(long initialBits) {
            List<LinearizationStep> steps = new ArrayList<>();
            List<Integer> dropped = new ArrayList<>();
            // 搜索开始前直接忽略的未返回 read
            for (int i = 0; i < n; i++) {
                if (bitSet(irrelevantPendingReadBits, i)) {
                    dropped.add(i);
                }
            }
            StateKey current = new StateKey(initialBits, RegisterSpec.INITIAL_VALUE);
            Set<StateKey> guard = new HashSet<>();
            while (current.remaining() != 0L) {
                if (!guard.add(current)) {
                    // 理论上不可达：父子关系构成无环链。防御性处理，避免内部错误造成死循环。
                    throw new IllegalStateException("见证重建出现环，属于内部错误。");
                }
                Choice c = parent.get(current);
                if (c == null) {
                    throw new IllegalStateException("见证链断裂，属于内部错误。");
                }
                if (c.action == Action.DROP) {
                    dropped.add(c.opIndex);
                } else {
                    Operation op = history.operation(c.opIndex);
                    steps.add(new LinearizationStep(op, c.valueBefore, c.valueAfter,
                            c.action == Action.COMPLETE_PENDING));
                }
                long nextRemaining = current.remaining() & ~(1L << c.opIndex);
                long nextValue = c.action == Action.DROP ? current.value() : c.valueAfter;
                current = new StateKey(nextRemaining, nextValue);
            }
            return AnalysisResult.consistent(steps, dropped, nodes, budget);
        }

        // ------------------------------------------------------------------
        // 冲突证据（前缀来自主搜索路径快照）
        // ------------------------------------------------------------------

        private ConflictReport buildConflict() {
            List<Integer> blocked = new ArrayList<>();
            List<String> reasons = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!bitSet(bestRemaining, i)) {
                    continue;
                }
                Operation op = history.operation(i);
                if (!op.completed() || !predecessorsDone(i, bestRemaining)) {
                    continue;
                }
                RegisterSpec.Transition t = RegisterSpec.apply(op, bestValue);
                if (!t.legal()) {
                    blocked.add(i);
                    reasons.add(describeRejection(op, bestValue));
                }
            }
            return new ConflictReport(bestPrefix, blocked, reasons, nodes);
        }

        /** 描述某个已返回调用为何无法在当前寄存器值上原子执行。 */
        private String describeRejection(Operation op, long current) {
            String label = op.label() + " " + op.kind().keyword();
            switch (op.kind()) {
                case READ:
                    return label + "：读取返回 " + op.resultInteger().getAsLong()
                            + "，但此刻寄存器值为 " + current + "，原子读必须返回当前值。";
                case ADD: {
                    long delta = op.arg(0);
                    if (RegisterSpec.addsOverflow(current, delta)) {
                        return label + "(" + delta + ")：加法发生 long 溢出，无法在整数寄存器上解释。";
                    }
                    long expectedNew = current + delta;
                    return label + "(" + delta + ")：返回新值 " + op.resultInteger().getAsLong()
                            + "，但从当前值 " + current + " 原子相加应得到 " + expectedNew + "。";
                }
                case CAS: {
                    long expected = op.arg(0);
                    long desired = op.arg(1);
                    boolean claimed = op.resultBoolean().orElseThrow();
                    if (claimed && current != expected) {
                        return label + "(" + expected + ", " + desired + ")：返回 true（声称交换成功），"
                                + "但此刻寄存器值为 " + current + "，不等于期望值 " + expected + "。";
                    }
                    if (!claimed && current == expected) {
                        return label + "(" + expected + ", " + desired + ")：返回 false（声称交换失败），"
                                + "但此刻寄存器值为 " + current + "，恰等于期望值 " + expected
                                + "，原子 cas 必须成功。";
                    }
                    return label + "：cas 返回值与当前寄存器值 " + current + " 矛盾。";
                }
                default:
                    return label + "：不合法。";
            }
        }
    }

    private static boolean bitSet(long bits, int i) {
        return ((bits >>> i) & 1L) == 1L;
    }

    // ------------------------------------------------------------------
    // 内部类型
    // ------------------------------------------------------------------

    /** 搜索状态：剩余调用位集 + 当前寄存器值。 */
    private record StateKey(long remaining, long value) {
    }

    private enum Action {
        PLACE_COMPLETED,
        COMPLETE_PENDING,
        DROP
    }

    /** 父状态到子状态的一个选择，用于重建见证。 */
    private static final class Choice {
        final int opIndex;
        final Action action;
        final long valueBefore;
        final long valueAfter;

        private Choice(int opIndex, Action action, long valueBefore, long valueAfter) {
            this.opIndex = opIndex;
            this.action = action;
            this.valueBefore = valueBefore;
            this.valueAfter = valueAfter;
        }

        static Choice place(int i, long before, long after) {
            return new Choice(i, Action.PLACE_COMPLETED, before, after);
        }

        static Choice completePending(int i, long before, long after) {
            return new Choice(i, Action.COMPLETE_PENDING, before, after);
        }

        static Choice drop(int i, long value) {
            return new Choice(i, Action.DROP, value, value);
        }
    }

    /** 预算耗尽的内部控制信号，避免返回值歧义。 */
    private static final class BudgetExhausted extends RuntimeException {
        static final BudgetExhausted INSTANCE = new BudgetExhausted();

        private BudgetExhausted() {
            super(null, null, false, false);
        }
    }
}
