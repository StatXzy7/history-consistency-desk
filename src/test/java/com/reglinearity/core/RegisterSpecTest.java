package com.reglinearity.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 顺序规范转移的单元测试。 */
class RegisterSpecTest {

    private Operation op(OpKind kind, Long intResult, Boolean boolResult, long... args) {
        Operation.Builder b = Operation.builder(0)
                .client("C").opNo(1).kind(kind).args(args).invokeSeq(1).returnSeq(2L);
        if (intResult != null) {
            b.resultInteger(intResult);
        }
        if (boolResult != null) {
            b.resultBoolean(boolResult);
        }
        return b.build();
    }

    private Operation pending(OpKind kind, long... args) {
        return Operation.builder(0).client("C").opNo(1).kind(kind).args(args).invokeSeq(1).build();
    }

    @Test
    @DisplayName("read 返回当前值且不改变寄存器")
    void readReturnsCurrent() {
        RegisterSpec.Transition ok = RegisterSpec.apply(op(OpKind.READ, 7L, null), 7);
        assertTrue(ok.legal());
        assertEquals(7L, ok.nextValue());

        assertFalse(RegisterSpec.apply(op(OpKind.READ, 8L, null), 7).legal());
    }

    @Test
    @DisplayName("add 返回相加后的新值")
    void addReturnsNewValue() {
        RegisterSpec.Transition t = RegisterSpec.apply(op(OpKind.ADD, 3L, null, 5), -2);
        assertTrue(t.legal());
        assertEquals(3L, t.nextValue());

        assertFalse(RegisterSpec.apply(op(OpKind.ADD, 4L, null, 5), -2).legal());
    }

    @Test
    @DisplayName("add 在 long 边界溢出时该步骤不可解释")
    void addOverflowRejected() {
        assertFalse(RegisterSpec.apply(op(OpKind.ADD, 0L, null, 1), Long.MAX_VALUE).legal());
        assertFalse(RegisterSpec.apply(pending(OpKind.ADD, -1), Long.MIN_VALUE).legal());
    }

    @Test
    @DisplayName("cas 仅在当前值等于期望值时成功并改值")
    void casSemantics() {
        RegisterSpec.Transition success = RegisterSpec.apply(op(OpKind.CAS, null, true, 0, 1), 0);
        assertTrue(success.legal());
        assertEquals(1L, success.nextValue());

        RegisterSpec.Transition fail = RegisterSpec.apply(op(OpKind.CAS, null, false, 0, 1), 5);
        assertTrue(fail.legal());
        assertEquals(5L, fail.nextValue());

        // 当前值等于期望值却返回 false：非法
        assertFalse(RegisterSpec.apply(op(OpKind.CAS, null, false, 0, 1), 0).legal());
        // 当前值不等于期望值却返回 true：非法
        assertFalse(RegisterSpec.apply(op(OpKind.CAS, null, true, 0, 1), 9).legal());
    }

    @Test
    @DisplayName("未返回调用在任何当前值上都可以补全为规范允许的结果")
    void pendingCanAlwaysComplete() {
        assertTrue(RegisterSpec.apply(pending(OpKind.READ), 42).legal());
        assertTrue(RegisterSpec.apply(pending(OpKind.ADD, 8), 42).legal());
        assertTrue(RegisterSpec.apply(pending(OpKind.CAS, 0, 1), 0).legal());
        assertTrue(RegisterSpec.apply(pending(OpKind.CAS, 0, 1), 1).legal());
    }
}
