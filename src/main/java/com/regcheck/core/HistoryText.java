package com.regcheck.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 历史的文本格式解析与打印，供 CLI 与界面使用。
 *
 * <p>每行一条调用，字段以竖线分隔：
 * <pre>
 * 编号 | 客户端 | 操作 | 调用序号 | 返回序号 | 结果
 * 1 | A | ADD 1 | 1 | 6 | 1
 * 2 | B | READ | 2 | 3 | 0
 * 3 | C | CAS 0 5 | 4 |     |        ← 未返回的调用
 * </pre>
 * 返回序号与结果留空表示该调用没有返回。空行与 # 开头的行被忽略。
 */
public final class HistoryText {

    private HistoryText() {
    }

    public static History parse(String text) {
        List<Call> calls = new ArrayList<>();
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\\|", -1);
            if (f.length != 6) {
                throw new IllegalArgumentException(
                        "第 " + (i + 1) + " 行应有 6 个以 | 分隔的字段: " + line);
            }
            try {
                int id = Integer.parseInt(f[0].trim());
                String client = f[1].trim();
                Operation op = Operation.parse(f[2].trim());
                long invoke = Long.parseLong(f[3].trim());
                Long response = f[4].trim().isEmpty()
                        ? null : Long.parseLong(f[4].trim());
                Object result = f[5].trim().isEmpty()
                        ? null : parseResult(f[5].trim());
                calls.add(new Call(id, client, op, invoke, response, result));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "第 " + (i + 1) + " 行数字格式错误: " + e.getMessage());
            }
        }
        return new History(calls);
    }

    private static Object parseResult(String s) {
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("false")) {
            return Boolean.valueOf(s);
        }
        return Long.parseLong(s);
    }

    public static String print(History history) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 编号 | 客户端 | 操作 | 调用序号 | 返回序号 | 结果\n");
        for (Call c : history.calls()) {
            sb.append(c.id()).append(" | ")
                    .append(c.client()).append(" | ")
                    .append(c.op()).append(" | ")
                    .append(c.invokeSeq()).append(" | ")
                    .append(c.responseSeq() == null ? "" : c.responseSeq()).append(" | ")
                    .append(c.result() == null ? "" : c.result()).append('\n');
        }
        return sb.toString();
    }
}
