package com.reglinearity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析 + 分析的门面：界面与命令行共用的入口，保证“非法输入”与“不一致”始终分流。
 */
public final class ConsistencyService {

    private final HistoryParser parser = new HistoryParser();
    private final LinearizabilityAnalyzer analyzer = new LinearizabilityAnalyzer();

    /**
     * 分析一段调用历史文本。
     *
     * @param text   原始记录文本
     * @param budget 搜索预算（访问节点数上限）
     * @return 非法输入的错误信息，或合法历史的分析结果
     */
    public ServiceResponse analyze(String text, long budget) {
        ParseOutcome outcome = parser.parse(text);
        if (!outcome.isValid()) {
            return ServiceResponse.illegal(outcome.error().orElse("输入非法。"));
        }
        AnalysisResult result = analyzer.analyze(outcome.history().orElseThrow(), budget);
        return ServiceResponse.analyzed(outcome.history().orElseThrow(), result);
    }

    public HistoryParser parser() {
        return parser;
    }

    /** 服务层响应：要么输入非法，要么携带历史与分析结果。 */
    public static final class ServiceResponse {
        private final String illegalError;
        private final History history;
        private final AnalysisResult result;

        private ServiceResponse(String illegalError, History history, AnalysisResult result) {
            this.illegalError = illegalError;
            this.history = history;
            this.result = result;
        }

        static ServiceResponse illegal(String message) {
            return new ServiceResponse(message, null, null);
        }

        static ServiceResponse analyzed(History history, AnalysisResult result) {
            return new ServiceResponse(null, history, result);
        }

        public boolean isIllegalInput() {
            return illegalError != null;
        }

        public String illegalError() {
            return illegalError;
        }

        public History history() {
            return history;
        }

        public AnalysisResult result() {
            return result;
        }
    }

    /**
     * 把分析结果渲染为纯文本（命令行输出与界面文本视图共用）。
     */
    public static String render(History history, AnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        Verdict v = result.verdict();
        sb.append("结论：").append(v.displayName()).append(System.lineSeparator());
        sb.append("搜索节点数：").append(result.searchedNodes());
        if (v == Verdict.UNDECIDED) {
            sb.append("（预算 ").append(result.budget()).append("，未搜索完，不能据此判定不存在解释）");
        }
        sb.append(System.lineSeparator());

        if (v == Verdict.CONSISTENT) {
            renderWitness(sb, history, result);
        } else if (v == Verdict.INCONSISTENT) {
            result.conflict().ifPresent(c -> renderConflict(sb, history, c));
        }
        return sb.toString();
    }

    private static void renderWitness(StringBuilder sb, History history, AnalysisResult result) {
        List<LinearizationStep> steps = result.witness().orElseGet(List::of);
        sb.append(System.lineSeparator())
          .append("合法顺序（共 ").append(steps.size()).append(" 步，寄存器初值 ")
          .append(RegisterSpec.INITIAL_VALUE).append("）：").append(System.lineSeparator());
        long value = RegisterSpec.INITIAL_VALUE;
        int pos = 1;
        for (LinearizationStep step : steps) {
            Operation op = step.operation();
            sb.append(String.format("%2d. %-8s %s", pos, op.label(), op.kind().keyword()));
            sb.append(renderArgs(op));
            sb.append("  [").append(step.valueBefore()).append(" -> ").append(step.valueAfter()).append(']');
            sb.append(renderStepReturn(op));
            if (step.completedPending()) {
                sb.append("  （未返回调用，按此返回值补全）");
            }
            sb.append(System.lineSeparator());
            value = step.valueAfter();
            pos++;
        }
        sb.append("最终寄存器值：").append(value).append(System.lineSeparator());
        if (!result.droppedPending().isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (int i : result.droppedPending()) {
                labels.add(history.operation(i).label());
            }
            sb.append("被忽略的未返回调用：").append(String.join("、", labels)).append(System.lineSeparator());
        }
    }

    private static void renderConflict(StringBuilder sb, History history, ConflictReport c) {
        sb.append(System.lineSeparator())
          .append("冲突说明（非最小反例）：以下合法前缀之后，所有可安排的已返回调用都无法执行。")
          .append(System.lineSeparator());
        for (int k = 0; k < c.prefix().size(); k++) {
            LinearizationStep step = c.prefix().get(k);
            Operation op = step.operation();
            sb.append(String.format("  %2d. %-8s %s%s  [%d -> %d]%n",
                    k + 1, op.label(), op.kind().keyword(), renderArgs(op),
                    step.valueBefore(), step.valueAfter()));
        }
        sb.append("  当前寄存器值：").append(currentValue(c)).append(System.lineSeparator());
        sb.append("  冲突操作：").append(System.lineSeparator());
        List<Integer> blocked = c.blockedIndices();
        for (int k = 0; k < blocked.size(); k++) {
            Operation op = history.operation(blocked.get(k));
            sb.append("    - ").append(op.label()).append(' ').append(op.signature())
              .append(System.lineSeparator());
            sb.append("      原因：").append(c.reasons().get(k)).append(System.lineSeparator());
        }
        List<Integer> unfinished = unfinishedPending(history, c);
        if (!unfinished.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (int i : unfinished) {
                labels.add(history.operation(i).label());
            }
            sb.append("  其余未返回调用（可忽略或补全，仍不能化解上述已返回调用的冲突）：")
              .append(String.join("、", labels)).append(System.lineSeparator());
        }
    }

    private static long currentValue(ConflictReport c) {
        if (c.prefix().isEmpty()) {
            return RegisterSpec.INITIAL_VALUE;
        }
        return c.prefix().get(c.prefix().size() - 1).valueAfter();
    }

    private static List<Integer> unfinishedPending(History history, ConflictReport c) {
        java.util.Set<Integer> placed = new java.util.HashSet<>();
        for (LinearizationStep s : c.prefix()) {
            placed.add(s.operation().index());
        }
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            Operation op = history.operation(i);
            if (!op.completed() && !placed.contains(i)) {
                result.add(i);
            }
        }
        return result;
    }

    private static String renderArgs(Operation op) {
        long[] args = op.args();
        if (args.length == 0) {
            return "()";
        }
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i]);
        }
        return sb.append(')').toString();
    }

    private static String renderStepReturn(Operation op) {
        if (!op.completed()) {
            return "";
        }
        if (op.kind().resultType() == OpKind.ResultType.BOOLEAN) {
            return "  返回 " + op.resultBoolean().orElseThrow();
        }
        return "  返回 " + op.resultInteger().orElseThrow();
    }
}
