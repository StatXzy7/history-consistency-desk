package com.reglinearity.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用“小历史的全部合法排列”独立核对分析器结论。
 *
 * <p>本测试不使用生产代码里的任何判定逻辑：</p>
 * <ul>
 *   <li>独立生成调用历史（区间、操作类型、参数、返回值），再转成标准文本交给真实解析器；</li>
 *   <li>独立根据区间重算非重叠先后关系；</li>
 *   <li>独立枚举保持先后关系的<b>全部排列</b>，并用独立抄写的寄存器规范逐步模拟
 *       （未返回调用同时尝试补全与丢弃）；</li>
 *   <li>把“是否存在合法排列”的结论与 {@link LinearizabilityAnalyzer} 的三态结论比对，
 *       并对每个 CONSISTENT 结论独立复核见证。</li>
 * </ul>
 *
 * <p>随机生成使用固定种子，结论可重复。</p>
 */
class PermutationOracleTest {

    // ------------------------------------------------------------------
    // 独立的测试用领域模型（与生产代码结构无关）
    // ------------------------------------------------------------------

    enum K {READ, ADD, CAS}

    static final class GenOp {
        final int id;
        final String client;
        final K kind;
        final long d;      // add 的增量或 cas 的期望值
        final Long d2;     // cas 的目标值
        final Long invokePos;
        final Long returnPos; // null = 未返回
        final Long intResult;  // 已返回 read/add 的记录结果
        final Boolean boolResult; // 已返回 cas 的记录结果

        GenOp(int id, String client, K kind, long d, Long d2,
              Long invokePos, Long returnPos, Long intResult, Boolean boolResult) {
            this.id = id;
            this.client = client;
            this.kind = kind;
            this.d = d;
            this.d2 = d2;
            this.invokePos = invokePos;
            this.returnPos = returnPos;
            this.intResult = intResult;
            this.boolResult = boolResult;
        }

        boolean pending() {
            return returnPos == null;
        }
    }

    // ------------------------------------------------------------------
    // 独立的全排列 oracle
    // ------------------------------------------------------------------

    private final long[] deltas = {-2, -1, 1, 2, 3};
    private final long[] casExpected = {0, 1, 2};
    private final long[] casDesired = {1, 2, 3};

    /** 独立重算前序：a 已返回且返回位置 <= b 的调用位置。 */
    private boolean precedes(GenOp a, GenOp b) {
        return a.returnPos != null && a.returnPos <= b.invokePos;
    }

    /**
     * 枚举保持非重叠先后关系的全部排列；未返回调用尝试“补全”与“丢弃”。
     * 规范在此独立抄写一遍，不复用 {@link RegisterSpec}。
     */
    private boolean oracleExplainable(List<GenOp> ops) {
        int n = ops.size();
        boolean[] placed = new boolean[n];
        return oracleSolve(ops, placed, 0L);
    }

    private boolean oracleSolve(List<GenOp> ops, boolean[] placed, long value) {
        int remaining = 0;
        for (boolean p : placed) {
            if (!p) {
                remaining++;
            }
        }
        if (remaining == 0) {
            return true;
        }
        int n = ops.size();
        for (int i = 0; i < n; i++) {
            if (placed[i]) {
                continue;
            }
            GenOp op = ops.get(i);
            boolean predsOk = true;
            for (int j = 0; j < n; j++) {
                if (!placed[j] && precedes(ops.get(j), op)) {
                    predsOk = false;
                    break;
                }
            }
            if (!predsOk) {
                continue;
            }

            Long nextValue = simulate(op, value);
            if (nextValue != null) {
                placed[i] = true;
                if (oracleSolve(ops, placed, nextValue)) {
                    placed[i] = false;
                    return true;
                }
                placed[i] = false;
            }
            if (op.pending()) {
                // 丢弃分支：值不变
                placed[i] = true;
                if (oracleSolve(ops, placed, value)) {
                    placed[i] = false;
                    return true;
                }
                placed[i] = false;
            }
        }
        return false;
    }

    /** 独立规范模拟：返回步骤后的寄存器值；null 表示该步不合法。 */
    private Long simulate(GenOp op, long value) {
        switch (op.kind) {
            case READ:
                if (op.intResult != null && op.intResult != value) {
                    return null;
                }
                return value;
            case ADD: {
                long next = value + op.d;
                // 溢出检测（独立实现）
                if (((value ^ next) & (op.d ^ next)) < 0) {
                    return null;
                }
                if (op.intResult != null && op.intResult != next) {
                    return null;
                }
                return next;
            }
            case CAS: {
                boolean success = value == op.d;
                long next = success ? op.d2 : value;
                if (op.boolResult != null && op.boolResult != success) {
                    return null;
                }
                return next;
            }
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------
    // 历史生成
    // ------------------------------------------------------------------

    private static final K[] KINDS = K.values();

    /** 生成操作骨架（不含时间位置与结果）。 */
    private List<GenOp> skeleton(Random rnd, int k) {
        List<GenOp> ops = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            K kind = KINDS[rnd.nextInt(KINDS.length)];
            long d;
            Long d2;
            switch (kind) {
                case ADD -> {
                    d = deltas[rnd.nextInt(deltas.length)];
                    d2 = null;
                }
                case CAS -> {
                    d = casExpected[rnd.nextInt(casExpected.length)];
                    d2 = casDesired[rnd.nextInt(casDesired.length)];
                }
                default -> {
                    d = 0;
                    d2 = null;
                }
            }
            ops.add(new GenOp(i, "C" + i, kind, d, d2, null, null, null, null));
        }
        return ops;
    }

    /**
     * 给操作赋予事件位置。
     *
     * @param mode CONCURRENT=全部区间重叠；WORDS=随机合法事件词；SEQUENTIAL=严格先后
     */
    private List<GenOp> assignTimingAndCompletions(List<GenOp> skeleton, String mode,
                                                   Random rnd, double pendingProb) {
        int k = skeleton.size();
        boolean[] pending = new boolean[k];
        int completedCount = 0;
        for (int i = 0; i < k; i++) {
            pending[i] = mode.equals("CONCURRENT") || mode.equals("WORDS")
                    ? rnd.nextDouble() < pendingProb : false;
            if (!pending[i]) {
                completedCount++;
            }
        }

        long[] invokeAt = new long[k];
        Long[] returnAt = new Long[k];

        switch (mode) {
            case "CONCURRENT" -> {
                // 调用占位置 1..k；返回（若有）占 k+1 起，保证两两重叠
                for (int i = 0; i < k; i++) {
                    invokeAt[i] = i + 1L;
                }
                long pos = k + 1L;
                for (int i = 0; i < k; i++) {
                    if (!pending[i]) {
                        returnAt[i] = pos++;
                    }
                }
            }
            case "SEQUENTIAL" -> {
                long pos = 1;
                for (int i = 0; i < k; i++) {
                    invokeAt[i] = pos++;
                    returnAt[i] = pos++;
                }
            }
            default -> { // WORDS：在“调用先于自身返回”的约束下随机生成事件词
                boolean[] invoked = new boolean[k];
                boolean[] returned = new boolean[k];
                long pos = 1;
                int invokedCount = 0;
                int returnedCount = 0;
                while (returnedCount < completedCount) {
                    List<Integer> canInvoke = new ArrayList<>();
                    List<Integer> canReturn = new ArrayList<>();
                    for (int i = 0; i < k; i++) {
                        if (!invoked[i]) {
                            canInvoke.add(i);
                        } else if (!returned[i] && !pending[i]) {
                            canReturn.add(i);
                        }
                    }
                    // 还有未调用时可在“调用/返回”间随机；否则只能返回
                    int choice;
                    if (canInvoke.isEmpty()) {
                        choice = 1;
                    } else if (canReturn.isEmpty()) {
                        choice = 0;
                    } else {
                        choice = rnd.nextInt(2);
                    }
                    int i = choice == 0
                            ? canInvoke.get(rnd.nextInt(canInvoke.size()))
                            : canReturn.get(rnd.nextInt(canReturn.size()));
                    if (choice == 0) {
                        invoked[i] = true;
                        invokeAt[i] = pos;
                        invokedCount++;
                    } else {
                        returned[i] = true;
                        returnAt[i] = pos;
                        returnedCount++;
                    }
                    pos++;
                }
                // 始终未返回的操作，其调用可能还没发出（极端词）；补放剩余调用
                for (int i = 0; i < k; i++) {
                    if (!invoked[i]) {
                        invokeAt[i] = pos++;
                    }
                }
            }
        }

        List<GenOp> result = new ArrayList<>();
        for (GenOp op : skeleton) {
            result.add(new GenOp(op.id, op.client, op.kind, op.d, op.d2,
                    invokeAt[op.id], returnAt[op.id], null, null));
        }
        return result;
    }

    /** 随机（与一致性无关地）填返回值，制造一致/不一致混合样本。 */
    private List<GenOp> randomResults(List<GenOp> ops, Random rnd) {
        long[] readDomain = {-1, 0, 1, 2, 3};
        List<GenOp> out = new ArrayList<>();
        for (GenOp op : ops) {
            if (op.pending()) {
                out.add(op);
                continue;
            }
            Long intRes = null;
            Boolean boolRes = null;
            switch (op.kind) {
                case READ -> intRes = readDomain[rnd.nextInt(readDomain.length)];
                case ADD -> {
                    long[] pool = {-1, 0, 1, op.d, op.d + 1, op.d + 2};
                    intRes = pool[rnd.nextInt(pool.length)];
                }
                case CAS -> boolRes = rnd.nextBoolean();
            }
            out.add(new GenOp(op.id, op.client, op.kind, op.d, op.d2,
                    op.invokePos, op.returnPos, intRes, boolRes));
        }
        return out;
    }

    /**
     * “植入一致”模式：随机选一个保持先后关系的生效顺序，按规范模拟并记录返回值，
     * 保证 oracle 必判一致。
     */
    private List<GenOp> plantedConsistentResults(List<GenOp> ops, Random rnd) {
        int n = ops.size();
        boolean[] placed = new boolean[n];
        long[] valueBefore = new long[n];
        long value = 0;
        for (int step = 0; step < n; step++) {
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (placed[i]) {
                    continue;
                }
                boolean predsOk = true;
                for (int j = 0; j < n; j++) {
                    if (!placed[j] && precedes(ops.get(j), ops.get(i))) {
                        predsOk = false;
                        break;
                    }
                }
                if (predsOk) {
                    candidates.add(i);
                }
            }
            int i = candidates.get(rnd.nextInt(candidates.size()));
            GenOp op = ops.get(i);
            valueBefore[i] = value;
            value = switch (op.kind) {
                case READ -> value;
                case ADD -> value + op.d;
                case CAS -> value == op.d ? op.d2 : value;
            };
            placed[i] = true;
        }

        List<GenOp> out = new ArrayList<>();
        for (GenOp op : ops) {
            if (op.pending()) {
                out.add(op);
                continue;
            }
            long v = valueBefore[op.id];
            Long intRes = null;
            Boolean boolRes = null;
            switch (op.kind) {
                case READ -> intRes = v;
                case ADD -> intRes = v + op.d;
                case CAS -> boolRes = v == op.d;
            }
            out.add(new GenOp(op.id, op.client, op.kind, op.d, op.d2,
                    op.invokePos, op.returnPos, intRes, boolRes));
        }
        return out;
    }

    /** 把生成的历史转成生产解析器接受的标准文本。 */
    private String toText(List<GenOp> ops) {
        record Event(long pos, String line) {
        }
        List<Event> events = new ArrayList<>();
        for (GenOp op : ops) {
            String args = switch (op.kind) {
                case READ -> "read";
                case ADD -> "add " + op.d;
                case CAS -> "cas " + op.d + " " + op.d2;
            };
            events.add(new Event(op.invokePos, op.invokePos + " INVOKE " + op.client
                    + " 1 " + args));
            if (!op.pending()) {
                String result = switch (op.kind) {
                    case READ, ADD -> String.valueOf(op.intResult);
                    case CAS -> String.valueOf(op.boolResult);
                };
                events.add(new Event(op.returnPos, op.returnPos + " RETURN " + op.client
                        + " 1 " + result));
            }
        }
        events.sort((a, b) -> Long.compare(a.pos(), b.pos()));
        StringBuilder sb = new StringBuilder();
        for (Event e : events) {
            sb.append(e.line()).append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 核心交叉核对
    // ------------------------------------------------------------------

    private void crossCheck(List<GenOp> ops) {
        boolean oracle = oracleExplainable(ops);
        String text = toText(ops);
        ParseOutcome parsed = new HistoryParser().parse(text);
        assertTrue(parsed.isValid(),
                "生成器应当产出合法输入，但解析报错：" + parsed.error().orElse("") + "\n" + text);
        History h = parsed.history().orElseThrow();
        // History 下标按调用序号排列，与生成器 id 可能不同（随机事件词下调用可乱序），
        // 按客户端标识 "C<id>" 建立映射。
        java.util.Map<String, GenOp> byClient = new java.util.HashMap<>();
        for (GenOp op : ops) {
            byClient.put(op.client, op);
        }
        // 独立区间关系也要与 History 预计算的前序一致
        for (int i = 0; i < h.size(); i++) {
            GenOp b = byClient.get(h.operation(i).client());
            for (int j = 0; j < h.size(); j++) {
                if (i == j) {
                    continue;
                }
                GenOp a = byClient.get(h.operation(j).client());
                assertEquals(precedes(a, b), h.predecessors(i).contains(j),
                        "前序关系计算分歧：\n" + text);
            }
        }

        AnalysisResult r = new LinearizabilityAnalyzer().analyze(h, LinearizabilityAnalyzer.DEFAULT_BUDGET);
        assertEquals(oracle, r.verdict() == Verdict.CONSISTENT,
                "分析器结论与全排列 oracle 分歧。oracle=" + oracle
                        + " analyzer=" + r.verdict() + "\n" + text);
        if (r.verdict() == Verdict.CONSISTENT) {
            WitnessAssertions.assertValidWitness(h, r);
        }
    }

    @Test
    @DisplayName("全重叠区间：随机结果与植入一致两类样本均与全排列 oracle 一致")
    void concurrentHistories() {
        int cases = 0;
        for (int k = 1; k <= 5; k++) {
            Random rnd = new Random(1000L + k);
            for (int t = 0; t < 200; t++) {
                crossCheck(randomResults(
                        assignTimingAndCompletions(skeleton(rnd, k), "CONCURRENT", rnd, 0.30), rnd));
                cases++;
            }
            for (int t = 0; t < 100; t++) {
                crossCheck(plantedConsistentResults(
                        assignTimingAndCompletions(skeleton(rnd, k), "CONCURRENT", rnd, 0.30), rnd));
                cases++;
            }
        }
        assertTrue(cases > 0);
    }

    @Test
    @DisplayName("随机合法事件词（含未返回调用）：结论与全排列 oracle 一致")
    void randomEventWords() {
        int cases = 0;
        for (int k = 1; k <= 5; k++) {
            Random rnd = new Random(2000L + k);
            for (int t = 0; t < 100; t++) {
                crossCheck(randomResults(
                        assignTimingAndCompletions(skeleton(rnd, k), "WORDS", rnd, 0.30), rnd));
                cases++;
            }
            for (int t = 0; t < 60; t++) {
                crossCheck(plantedConsistentResults(
                        assignTimingAndCompletions(skeleton(rnd, k), "WORDS", rnd, 0.25), rnd));
                cases++;
            }
        }
        assertTrue(cases > 0);
    }

    @Test
    @DisplayName("严格顺序区间：先后约束下结论与全排列 oracle 一致")
    void sequentialHistories() {
        for (int k = 1; k <= 5; k++) {
            Random rnd = new Random(3000L + k);
            for (int t = 0; t < 40; t++) {
                crossCheck(randomResults(
                        assignTimingAndCompletions(skeleton(rnd, k), "SEQUENTIAL", rnd, 0), rnd));
            }
            for (int t = 0; t < 60; t++) {
                crossCheck(plantedConsistentResults(
                        assignTimingAndCompletions(skeleton(rnd, k), "SEQUENTIAL", rnd, 0), rnd));
            }
        }
    }

    @Test
    @DisplayName("穷举网格：k=2 全重叠，固定参数，枚举全部返回值组合，逐一与 oracle 核对")
    void exhaustiveGridK2() {
        int checked = 0;
        for (K k1 : KINDS) {
            for (K k2 : KINDS) {
                for (Object r1 : resultOptions(k1)) {
                    for (Object r2 : resultOptions(k2)) {
                        GenOp a = gridOp(0, k1, 1L, 3L, r1);
                        GenOp b = gridOp(1, k2, 2L, 4L, r2);
                        crossCheck(List.of(a, b));
                        checked++;
                    }
                }
            }
        }
        // 2*2 + 2*3 + 2*2 + 3*2 + 3*3 + 3*2 + 2*2 + 2*3 + 2*2 = 49
        assertEquals(49, checked);
    }

    /** 固定参数下各操作类型的记录返回值域：read∈{0,1}，add(1)∈{0,1,2}，cas(0,1)∈{true,false}。 */
    private Object[] resultOptions(K kind) {
        return switch (kind) {
            case READ -> new Object[]{0L, 1L};
            case ADD -> new Object[]{0L, 1L, 2L};
            case CAS -> new Object[]{Boolean.TRUE, Boolean.FALSE};
        };
    }

    private GenOp gridOp(int id, K kind, long invoke, long ret, Object result) {
        return switch (kind) {
            case READ -> new GenOp(id, "C" + id, K.READ, 0, null,
                    invoke, ret, (Long) result, null);
            case ADD -> new GenOp(id, "C" + id, K.ADD, 1, null,
                    invoke, ret, (Long) result, null);
            case CAS -> new GenOp(id, "C" + id, K.CAS, 0, 1L,
                    invoke, ret, null, (Boolean) result);
        };
    }
}
