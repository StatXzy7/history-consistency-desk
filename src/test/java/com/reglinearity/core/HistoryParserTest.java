package com.reglinearity.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析器测试：合法历史的配对，以及各类“非法输入”——
 * 非法输入必须走 {@link ParseOutcome#isValid()} == false 通道，绝不能混入一致性失败。
 */
class HistoryParserTest {

    private final HistoryParser parser = new HistoryParser();

    @Nested
    @DisplayName("合法输入")
    class Valid {

        @Test
        @DisplayName("顺序的 add + read 正确配对并按调用序号排序")
        void parsesSequentialHistory() {
            String text = """
                    1 INVOKE C1 1 add 5
                    2 RETURN C1 1 5
                    3 INVOKE C1 2 read
                    4 RETURN C1 2 5
                    """;
            ParseOutcome outcome = parser.parse(text);
            assertTrue(outcome.isValid(), outcome.error().orElse(""));
            History h = outcome.history().orElseThrow();
            assertEquals(2, h.size());
            assertEquals(OpKind.ADD, h.operation(0).kind());
            assertEquals(5L, h.operation(0).resultInteger().orElseThrow());
            assertEquals(OpKind.READ, h.operation(1).kind());
            // 非重叠：add 的返回序号 2 <= read 的调用序号 3
            assertEquals(java.util.List.of(0), h.predecessors(1));
            assertEquals(java.util.List.of(), h.overlaps(0));
        }

        @Test
        @DisplayName("文本顺序打乱仍按事件序号建立先后；关键字大小写与注释容忍")
        void ordersByEventSequenceNumber() {
            String text = """
                    # 注释行
                    4 RETURN C1 1 10
                    2 INVOKE C2 1 READ
                    1 INVOKE C1 1 ADD 10
                    3 RETURN C2 1 10
                    """;
            ParseOutcome outcome = parser.parse(text);
            assertTrue(outcome.isValid(), outcome.error().orElse(""));
            History h = outcome.history().orElseThrow();
            assertEquals(2, h.size());
            // 两个调用区间 [1,4] 与 [2,3] 重叠，互无前序
            assertTrue(h.predecessors(0).isEmpty());
            assertTrue(h.predecessors(1).isEmpty());
            assertEquals(java.util.List.of(1), h.overlaps(0));
        }

        @Test
        @DisplayName("客户端标识大小写敏感：c1 与 C1 是不同客户端")
        void clientIdentifiersAreCaseSensitive() {
            String text = """
                    1 INVOKE C1 1 add 1
                    2 RETURN c1 1 1
                    """;
            ParseOutcome outcome = parser.parse(text);
            assertFalse(outcome.isValid());
            assertTrue(outcome.error().orElseThrow().contains("找不到对应的调用"));
        }

        @Test
        @DisplayName("未返回调用解析为未完成调用，仍可进入分析")
        void parsesPendingInvocation() {
            String text = """
                    1 INVOKE C1 1 add 5
                    2 INVOKE C2 1 read
                    3 RETURN C1 1 5
                    """;
            ParseOutcome outcome = parser.parse(text);
            assertTrue(outcome.isValid(), outcome.error().orElse(""));
            History h = outcome.history().orElseThrow();
            Operation pending = h.operation(1);
            assertFalse(pending.completed());
            assertTrue(pending.returnSeq().isEmpty());
            assertTrue(pending.resultInteger().isEmpty());
        }

        @Test
        @DisplayName("空文本/仅注释得到空历史（平凡合法）")
        void emptyInputIsValidEmptyHistory() {
            assertTrue(parser.parse("   # only comment\n").isValid());
            ParseOutcome o = parser.parse("");
            assertTrue(o.isValid());
            assertEquals(0, o.history().orElseThrow().size());
        }

        @Test
        @DisplayName("cas 的 true/false 返回值合法")
        void parsesCasResults() {
            String text = """
                    1 INVOKE C1 1 cas 0 1
                    2 RETURN C1 1 true
                    3 INVOKE C2 1 cas 5 6
                    4 RETURN C2 1 FALSE
                    """;
            ParseOutcome outcome = parser.parse(text);
            assertTrue(outcome.isValid(), outcome.error().orElse(""));
            History h = outcome.history().orElseThrow();
            assertEquals(Boolean.TRUE, h.operation(0).resultBoolean().orElseThrow());
            assertEquals(Boolean.FALSE, h.operation(1).resultBoolean().orElseThrow());
        }
    }

    @Nested
    @DisplayName("非法输入（区别于一致性失败）")
    class Illegal {

        @Test
        @DisplayName("重复事件序号")
        void duplicateSequenceNumber() {
            String text = """
                    1 INVOKE C1 1 add 1
                    1 RETURN C1 1 1
                    """;
            ParseOutcome o = parser.parse(text);
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("事件序号 1 重复"));
        }

        @Test
        @DisplayName("同客户端重复操作编号")
        void duplicateOperationNumber() {
            String text = """
                    1 INVOKE C1 1 add 1
                    2 RETURN C1 1 1
                    3 INVOKE C1 1 read
                    4 RETURN C1 1 1
                    """;
            ParseOutcome o = parser.parse(text);
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("重复调用"));
        }

        @Test
        @DisplayName("返回找不到调用")
        void orphanReturn() {
            String text = """
                    1 INVOKE C1 1 add 1
                    2 RETURN C2 9 1
                    """;
            ParseOutcome o = parser.parse(text);
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("找不到对应的调用"));
        }

        @Test
        @DisplayName("返回事件序号早于调用事件序号")
        void returnBeforeInvokeNumbering() {
            // 按序号排序后 RETURN(2) 仍晚于 INVOKE(5)？这里返回序号严格更小
            String text = """
                    5 INVOKE C1 1 add 1
                    2 RETURN C1 1 1
                    """;
            ParseOutcome o = parser.parse(text);
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("早于其调用事件序号"));
        }

        @Test
        @DisplayName("cas 返回整数：返回类型错误")
        void casReturnsInteger() {
            String text = """
                    1 INVOKE C1 1 cas 0 1
                    2 RETURN C1 1 1
                    """;
            ParseOutcome o = parser.parse(text);
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("应返回布尔值"));
        }

        @Test
        @DisplayName("add 返回布尔：返回类型错误")
        void addReturnsBoolean() {
            String text = """
                    1 INVOKE C1 1 add 1
                    2 RETURN C1 1 true
                    """;
            ParseOutcome o = parser.parse(text);
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("应返回整数"));
        }

        @Test
        @DisplayName("read 返回不可解析的字面量：词法/类型错误")
        void readReturnsGarbage() {
            String text = """
                    1 INVOKE C1 1 read
                    2 RETURN C1 1 maybe
                    """;
            ParseOutcome o = parser.parse(text);
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("既不是整数也不是布尔值"));
        }

        @Test
        @DisplayName("未知操作类型")
        void unknownOperationKind() {
            String text = """
                    1 INVOKE C1 1 multiply 2
                    """;
            ParseOutcome o = parser.parse(text);
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("未知操作类型"));
        }

        @Test
        @DisplayName("参数个数不符")
        void wrongArgumentCount() {
            assertEquals(false, parser.parse("1 INVOKE C1 1 add 1 2\n").isValid());
            assertEquals(false, parser.parse("1 INVOKE C1 1 cas 0\n").isValid());
            assertEquals(false, parser.parse("1 INVOKE C1 1 read 7\n").isValid());
        }

        @Test
        @DisplayName("参数不是整数 / 序号不是整数 / 字段不足")
        void lexicalErrors() {
            assertFalse(parser.parse("1 INVOKE C1 1 add x\n").isValid());
            assertFalse(parser.parse("x INVOKE C1 1 add 1\n").isValid());
            assertFalse(parser.parse("1 INVOKE C1\n").isValid());
            assertFalse(parser.parse("1 FROBNICATE C1 1\n").isValid());
        }

        @Test
        @DisplayName("RETURN 携带多余字段")
        void returnWithExtraField() {
            ParseOutcome o = parser.parse("1 INVOKE C1 1 read\n2 RETURN C1 1 5 6\n");
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("多余字段"));
        }

        @Test
        @DisplayName("超过十六个调用直接判非法")
        void moreThanSixteenOperationsIsIllegal() {
            StringBuilder sb = new StringBuilder();
            int seq = 1;
            for (int op = 1; op <= 17; op++) {
                sb.append(seq++).append(" INVOKE C1 ").append(op).append(" add 1\n");
                sb.append(seq++).append(" RETURN C1 ").append(op).append(' ').append(op).append('\n');
            }
            ParseOutcome o = parser.parse(sb.toString());
            assertFalse(o.isValid());
            assertTrue(o.error().orElseThrow().contains("16"));
        }

        @Test
        @DisplayName("非法输入不产生 History，路由方可区分于不一致")
        void illegalHasNoHistory() {
            ParseOutcome o = parser.parse("1 INVOKE C1 1 add 1\n2 RETURN C1 1 true\n");
            assertFalse(o.isValid());
            assertTrue(o.history().isEmpty());
            assertEquals(Optional.empty(), o.history());
        }
    }
}
