package com.regcheck.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryValidatorTest {

    private final LinearizabilityChecker checker = new LinearizabilityChecker();

    @Test
    void duplicateIdIsInvalid() {
        History h = new History(List.of(
                new Call(1, "A", Operation.read(), 1, 2L, 0L),
                new Call(1, "B", Operation.read(), 3, 4L, 0L)));
        assertInvalid(h, "编号重复");
    }

    @Test
    void responseBeforeInvokeIsInvalid() {
        History h = new History(List.of(
                new Call(1, "A", Operation.read(), 4, 3L, 0L)));
        assertInvalid(h, "返回事件序号");
    }

    @Test
    void sameResponseAndInvokeSeqIsInvalid() {
        History h = new History(List.of(
                new Call(1, "A", Operation.read(), 5, 5L, 0L)));
        assertInvalid(h, "不晚于");
    }

    @Test
    void wrongResultTypeIsInvalid() {
        // READ 返回了布尔值
        History h = new History(List.of(
                new Call(1, "A", Operation.read(), 1, 2L, Boolean.TRUE)));
        assertInvalid(h, "返回类型错误");
    }

    @Test
    void casIntegerResultIsInvalid() {
        History h = new History(List.of(
                new Call(1, "A", Operation.cas(0, 1), 1, 2L, 1L)));
        assertInvalid(h, "返回类型错误");
    }

    @Test
    void resultWithoutResponseIsInvalid() {
        History h = new History(List.of(
                new Call(1, "A", Operation.read(), 1, null, 0L)));
        assertInvalid(h, "没有返回事件却带有返回结果");
    }

    @Test
    void responseWithoutResultIsInvalid() {
        History h = new History(List.of(
                new Call(1, "A", Operation.read(), 1, 2L, null)));
        assertInvalid(h, "缺少结果");
    }

    @Test
    void emptyHistoryIsInvalid() {
        CheckResult.Invalid r = (CheckResult.Invalid)
                checker.check(new History(new ArrayList<>()));
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("历史为空")));
    }

    @Test
    void blankClientIsInvalid() {
        History h = new History(List.of(
                new Call(1, "  ", Operation.read(), 1, 2L, 0L)));
        assertInvalid(h, "客户端");
    }

    private void assertInvalid(History h, String expectedFragment) {
        CheckResult r = checker.check(h);
        CheckResult.Invalid invalid =
                assertInstanceOf(CheckResult.Invalid.class, r,
                        "非法输入不应被当作一致性失败");
        assertTrue(invalid.errors().stream().anyMatch(e -> e.contains(expectedFragment)),
                "期望错误信息包含「" + expectedFragment + "」，实际: " + invalid.errors());
    }
}
