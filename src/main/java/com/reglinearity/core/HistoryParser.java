package com.reglinearity.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用历史文本解析器。
 *
 * <p>每行一条记录（{@code #} 起为注释）：</p>
 * <pre>
 * 1 INVOKE C1 1 add 5
 * 2 RETURN C1 1 5
 * 3 INVOKE C2 1 cas 0 1
 * 4 RETURN C2 1 true
 * 5 INVOKE C3 1 read
 * </pre>
 *
 * <p>校验分两层，且严格区分两类失败：</p>
 * <ol>
 *   <li><b>非法输入</b>（返回 {@link ParseOutcome#illegal}）：token 不全、整数不可解析、
 *       重复事件序号、同一 (客户端, 操作编号) 重复调用或重复返回、返回找不到调用、
 *       返回早于调用、操作类型/返回值类型错误、参数个数不符等；</li>
 *   <li><b>合法但不一致</b>：由 {@link LinearizabilityAnalyzer} 判定，解析器不做语义判断。</li>
 * </ol>
 */
public final class HistoryParser {

    /** 布尔返回值允许的字面量。 */
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    public ParseOutcome parse(String text) {
        if (text == null) {
            return ParseOutcome.illegal("输入为空。");
        }

        List<Record> records = new ArrayList<>();
        String[] rawLines = text.split("\\R", -1);
        for (int i = 0; i < rawLines.length; i++) {
            LineTokens line = LineTokens.split(i + 1, rawLines[i]);
            if (line.isBlank()) {
                continue;
            }
            String lexicalError = lexInto(line, records);
            if (lexicalError != null) {
                return ParseOutcome.illegal(lexicalError);
            }
        }

        if (records.isEmpty()) {
            return ParseOutcome.valid(History.of(List.of()));
        }
        return buildHistory(records);
    }

    /** 词法解析：把一行 token 转成 {@link Record}，任何词法问题立即以非法输入返回。 */
    private String lexInto(LineTokens line, List<Record> out) {
        List<String> t = line.tokens();
        int ln = line.lineNumber();
        if (t.size() < 4) {
            return "第 " + ln + " 行：字段不足，记录至少需要 “事件序号 INVOKE|RETURN 客户端 操作编号 …”。";
        }

        Long seq = parseLong(t.get(0));
        if (seq == null) {
            return "第 " + ln + " 行：事件序号 “" + t.get(0) + "” 不是整数。";
        }
        if (seq <= 0) {
            return "第 " + ln + " 行：事件序号必须为正整数，实际为 " + seq + "。";
        }

        String formText = t.get(1).toUpperCase();
        Record.Form form;
        try {
            form = Record.Form.valueOf(formText);
        } catch (IllegalArgumentException ex) {
            return "第 " + ln + " 行：第二条字段必须是 INVOKE 或 RETURN，实际为 “" + t.get(1) + "”。";
        }

        String client = t.get(2);
        if (client.isBlank()) {
            return "第 " + ln + " 行：客户端标识为空。";
        }

        Long opNo = parseLong(t.get(3));
        if (opNo == null) {
            return "第 " + ln + " 行：操作编号 “" + t.get(3) + "” 不是整数。";
        }
        if (opNo <= 0) {
            return "第 " + ln + " 行：操作编号必须为正整数，实际为 " + opNo + "。";
        }

        if (form == Record.Form.INVOKE) {
            return lexInvoke(ln, seq, client, opNo, t, out);
        }
        return lexReturn(ln, seq, client, opNo, t, out);
    }

    private String lexInvoke(int ln, long seq, String client, long opNo,
                             List<String> t, List<Record> out) {
        // seq INVOKE client opNo kind [args...]
        if (t.size() < 5) {
            return "第 " + ln + " 行：INVOKE 记录缺少操作类型（read/add/cas）。";
        }
        OpKind kind = OpKind.fromKeyword(t.get(4));
        if (kind == null) {
            return "第 " + ln + " 行：未知操作类型 “" + t.get(4) + "”，仅支持 read / add / cas。";
        }
        List<String> argTokens = t.subList(5, t.size());
        if (argTokens.size() != kind.argCount()) {
            return "第 " + ln + " 行：" + kind.keyword() + " 需要 " + kind.argCount()
                    + " 个参数，实际给出 " + argTokens.size() + " 个。";
        }
        Long[] args = new Long[kind.argCount()];
        for (int i = 0; i < args.length; i++) {
            Long v = parseLong(argTokens.get(i));
            if (v == null) {
                return "第 " + ln + " 行：" + kind.keyword() + " 的第 " + (i + 1)
                        + " 个参数 “" + argTokens.get(i) + "” 不是整数。";
            }
            args[i] = v;
        }
        out.add(Record.builder(ln, Record.Form.INVOKE)
                .seq(seq).client(client).opNo(opNo).kind(kind).args(args).build());
        return null;
    }

    private String lexReturn(int ln, long seq, String client, long opNo,
                             List<String> t, List<Record> out) {
        // seq RETURN client opNo result   —— 恰好 5 个 token
        if (t.size() < 5) {
            return "第 " + ln + " 行：RETURN 记录缺少返回值。";
        }
        if (t.size() > 5) {
            return "第 " + ln + " 行：RETURN 记录返回值之后存在多余字段 “"
                    + String.join(" ", t.subList(5, t.size())) + "”。";
        }
        String resultToken = t.get(4);
        Long asInt = parseLong(resultToken);
        if (asInt != null) {
            out.add(Record.returnInteger(ln, seq, client, opNo, asInt));
            return null;
        }
        String lowered = resultToken.toLowerCase();
        if (TRUE.equals(lowered) || FALSE.equals(lowered)) {
            out.add(Record.returnBoolean(ln, seq, client, opNo, Boolean.parseBoolean(lowered)));
            return null;
        }
        // 返回值既不是整数也不是布尔：词法类型错误，立即判非法。
        return "第 " + ln + " 行：返回值 “" + resultToken + "” 既不是整数也不是布尔值（true/false）。";
    }

    /**
     * 第二遍：按事件序号排序后配对调用/返回，做跨记录语义校验。
     * 先分别收集调用与返回再按键配对，这样“返回序号早于调用序号”能被准确识别，
     * 而不是被笼统报成“找不到调用”。
     */
    private ParseOutcome buildHistory(List<Record> records) {
        List<Record> sorted = new ArrayList<>(records);
        sorted.sort(Comparator.comparing(r -> r.seq().getAsLong()));

        Map<Long, Integer> seqUsed = new HashMap<>();
        for (Record r : sorted) {
            long seq = r.seq().getAsLong();
            if (seqUsed.containsKey(seq)) {
                return ParseOutcome.illegal("事件序号 " + seq + " 重复（第 "
                        + seqUsed.get(seq) + " 行与第 " + r.lineNumber() + " 行），事件序号必须唯一。");
            }
            seqUsed.put(seq, r.lineNumber());
        }

        Map<String, Record> invokes = new HashMap<>();
        Map<String, Integer> invokeLine = new HashMap<>();
        Map<String, List<Record>> returns = new HashMap<>();

        for (Record r : sorted) {
            String key = pairKey(r);
            if (r.form() == Record.Form.INVOKE) {
                if (invokes.containsKey(key)) {
                    return ParseOutcome.illegal("第 " + r.lineNumber() + " 行：客户端 "
                            + r.client().orElseThrow() + " 的操作编号 " + r.opNo().getAsLong()
                            + " 出现重复调用（首次调用在第 " + invokeLine.get(key) + " 行）。");
                }
                invokes.put(key, r);
                invokeLine.put(key, r.lineNumber());
            } else {
                returns.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
        }

        List<Operation> operations = new ArrayList<>();
        for (Map.Entry<String, List<Record>> entry : returns.entrySet()) {
            String key = entry.getKey();
            List<Record> rets = entry.getValue();
            Record invoke = invokes.get(key);
            if (invoke == null) {
                Record first = rets.get(0);
                return ParseOutcome.illegal("第 " + first.lineNumber() + " 行：返回记录 "
                        + first.client().orElseThrow() + "#" + first.opNo().getAsLong()
                        + " 找不到对应的调用记录。");
            }
            if (rets.size() > 1) {
                Record second = rets.get(1);
                return ParseOutcome.illegal("第 " + second.lineNumber() + " 行："
                        + second.client().orElseThrow() + "#" + second.opNo().getAsLong()
                        + " 存在多条返回记录（首次返回在第 " + rets.get(0).lineNumber() + " 行）。");
            }
            Record r = rets.get(0);
            String typeError = checkResultType(r, invoke);
            if (typeError != null) {
                return ParseOutcome.illegal(typeError);
            }
            if (r.seq().getAsLong() < invoke.seq().getAsLong()) {
                return ParseOutcome.illegal("第 " + r.lineNumber() + " 行："
                        + r.client().orElseThrow() + "#" + r.opNo().getAsLong()
                        + " 的返回事件序号 " + r.seq().getAsLong()
                        + " 早于其调用事件序号 " + invoke.seq().getAsLong() + "。");
            }
            operations.add(toOperation(operations.size(), invoke, r));
        }

        // 未配对的调用 = 未返回调用，可以进入分析（允许忽略或补全）。
        for (Map.Entry<String, Record> entry : invokes.entrySet()) {
            if (!returns.containsKey(entry.getKey())) {
                operations.add(toOperation(operations.size(), entry.getValue(), null));
            }
        }

        if (operations.size() > History.MAX_OPERATIONS) {
            return ParseOutcome.illegal("调用数量为 " + operations.size()
                    + "，超过单对象分析上限 " + History.MAX_OPERATIONS + " 个。");
        }

        operations.sort(Comparator
                .comparingLong((Operation op) -> op.invokeSeq())
                .thenComparingInt(Operation::index));
        // 排序后重新赋予稳定下标。
        List<Operation> reindexed = new ArrayList<>(operations.size());
        for (int i = 0; i < operations.size(); i++) {
            Operation op = operations.get(i);
            Operation.Builder b = Operation.builder(i)
                    .client(op.client())
                    .opNo(op.opNo())
                    .kind(op.kind())
                    .args(op.args())
                    .invokeSeq(op.invokeSeq())
                    .returnSeq(op.completed() ? op.returnSeq().getAsLong() : null)
                    .resultBoolean(op.resultBoolean().orElse(null));
            if (op.resultInteger().isPresent()) {
                b.resultInteger(op.resultInteger().getAsLong());
            }
            reindexed.add(b.build());
        }

        return ParseOutcome.valid(History.of(reindexed));
    }

    private static String pairKey(Record r) {
        return r.client().orElseThrow() + " " + r.opNo().getAsLong();
    }

    /** 校验返回值类型与操作声明的返回类型一致；CAS 仅接受布尔，read/add 仅接受整数。 */
    private String checkResultType(Record ret, Record invoke) {
        OpKind kind = invoke.kind().orElse(null);
        String label = ret.client().orElseThrow() + "#" + ret.opNo().getAsLong();
        if (kind == OpKind.READ || kind == OpKind.ADD) {
            if (ret.resultBoolean().isPresent()) {
                return "第 " + ret.lineNumber() + " 行：" + label + " 是 " + kind.keyword()
                        + " 操作，应返回整数，实际返回布尔值 " + ret.resultBoolean().orElseThrow() + "。";
            }
            if (ret.resultInteger().isEmpty()) {
                return "第 " + ret.lineNumber() + " 行：" + label + " 是 " + kind.keyword()
                        + " 操作，返回值类型错误（应为整数）。";
            }
        } else if (kind == OpKind.CAS) {
            if (ret.resultInteger().isPresent()) {
                return "第 " + ret.lineNumber() + " 行：" + label
                        + " 是 cas 操作，应返回布尔值 true/false，实际返回整数 "
                        + ret.resultInteger().getAsLong() + "。";
            }
            if (ret.resultBoolean().isEmpty()) {
                return "第 " + ret.lineNumber() + " 行：" + label
                        + " 是 cas 操作，返回值类型错误（应为 true/false）。";
            }
        }
        return null;
    }

    private Operation toOperation(int index, Record invoke, Record ret) {
        Long[] boxed = invoke.args();
        long[] args = new long[boxed.length];
        for (int i = 0; i < boxed.length; i++) {
            args[i] = boxed[i] == null ? 0L : boxed[i];
        }
        Operation.Builder b = Operation.builder(index)
                .client(invoke.client().orElseThrow())
                .opNo(invoke.opNo().getAsLong())
                .kind(invoke.kind().orElseThrow())
                .args(args)
                .invokeSeq(invoke.seq().getAsLong());
        if (ret != null) {
            b.returnSeq(ret.seq().getAsLong());
            ret.resultInteger().ifPresent(b::resultInteger);
            ret.resultBoolean().ifPresent(b::resultBoolean);
        }
        return b.build();
    }

    /**
     * 解析 64 位有符号整数；失败（含溢出）返回 {@code null}。
     * 接受可选的正负号，不接受其它前导格式。
     */
    static Long parseLong(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        int start = 0;
        char c0 = token.charAt(0);
        if (c0 == '+' || c0 == '-') {
            if (token.length() == 1) {
                return null;
            }
            start = 1;
        }
        for (int i = start; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
