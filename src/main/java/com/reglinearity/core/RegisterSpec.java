package com.reglinearity.core;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 顺序规范：初值为零的整数寄存器的三类原子操作的合法转移。
 *
 * <p>每个方法在给定当前寄存器值时，判断该操作（及其已记录的返回值）能否作为
 * 一个原子步骤发生，并给出步骤后的新值。纯函数，不依赖界面。</p>
 */
public final class RegisterSpec {

    /** 寄存器初始值。 */
    public static final long INITIAL_VALUE = 0L;

    private RegisterSpec() {
    }

    /** 原子步骤执行后的结果：是否合法，以及步骤后的寄存器值。 */
    public static final class Transition {
        private final boolean legal;
        private final long nextValue;

        private Transition(boolean legal, long nextValue) {
            this.legal = legal;
            this.nextValue = nextValue;
        }

        static Transition ok(long nextValue) {
            return new Transition(true, nextValue);
        }

        static Transition reject() {
            return new Transition(false, 0L);
        }

        public boolean legal() {
            return legal;
        }

        /** 步骤后的寄存器值；仅当 {@link #legal()} 为 true 时可调用。 */
        public long nextValue() {
            if (!legal) {
                throw new IllegalStateException("非法转移不存在步骤后的值。");
            }
            return nextValue;
        }
    }

    /** 两个 long 相加是否溢出（变号判定）。 */
    public static boolean addsOverflow(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0;
    }

    /**
     * 判断一次操作能否在寄存器值为 {@code current} 时原子执行。
     *
     * @param op 调用；已返回调用的返回值必须与规范相符，未返回调用允许补全为任意合法结果
     */
    public static Transition apply(Operation op, long current) {
        switch (op.kind()) {
            case READ:
                return applyRead(op, current);
            case ADD:
                return applyAdd(op, current);
            case CAS:
                return applyCas(op, current);
            default:
                return Transition.reject();
        }
    }

    private static Transition applyRead(Operation op, long current) {
        OptionalLong result = op.resultInteger();
        // 读不改变状态；已返回时返回值必须等于当前值。
        if (result.isPresent() && result.getAsLong() != current) {
            return Transition.reject();
        }
        return Transition.ok(current);
    }

    private static Transition applyAdd(Operation op, long current) {
        long delta = op.arg(0);
        if (addsOverflow(current, delta)) {
            // 规范中寄存器为整数；本工具以 long 表示，溢出视为该步骤无法解释。
            return Transition.reject();
        }
        long next = current + delta;
        OptionalLong result = op.resultInteger();
        if (result.isPresent() && result.getAsLong() != next) {
            return Transition.reject();
        }
        return Transition.ok(next);
    }

    private static Transition applyCas(Operation op, long current) {
        long expected = op.arg(0);
        long desired = op.arg(1);
        boolean success = current == expected;
        long next = success ? desired : current;
        Optional<Boolean> result = op.resultBoolean();
        if (result.isPresent() && result.get() != success) {
            return Transition.reject();
        }
        return Transition.ok(next);
    }
}
