package com.reglinearity.core;

/**
 * 合法顺序（线性化）中的一步：某个调用在某个寄存器值上原子执行，得到新值。
 *
 * <p>不可变值对象。</p>
 */
public final class LinearizationStep {

    private final Operation operation;
    private final long valueBefore;
    private final long valueAfter;
    /** 未返回调用是否在解释中被补全（已返回调用恒为 false）。 */
    private final boolean completedPending;

    public LinearizationStep(Operation operation, long valueBefore, long valueAfter,
                             boolean completedPending) {
        this.operation = operation;
        this.valueBefore = valueBefore;
        this.valueAfter = valueAfter;
        this.completedPending = completedPending;
    }

    public Operation operation() {
        return operation;
    }

    public long valueBefore() {
        return valueBefore;
    }

    public long valueAfter() {
        return valueAfter;
    }

    /** 该步对应的调用是否为“未返回但在本解释中补全”的调用。 */
    public boolean completedPending() {
        return completedPending;
    }
}
