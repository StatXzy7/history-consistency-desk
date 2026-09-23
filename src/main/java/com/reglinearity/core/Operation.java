package com.reglinearity.core;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 一次完整（或尚未返回）的寄存器调用：由同客户端、同操作编号的调用记录与返回记录配对而成。
 *
 * <p>{@code index} 是分析期使用的稳定下标：先按调用事件序号排序，
 * 调用序号相同（非法）时不进入分析，因此不影响确定性。</p>
 *
 * <p>不可变值对象。</p>
 */
public final class Operation {

    private final int index;
    private final String client;
    private final long opNo;
    private final OpKind kind;
    private final long[] args;
    private final long invokeSeq;
    /** 未返回调用的返回序号为空。 */
    private final Long returnSeq;
    private final Long resultInteger;
    private final Boolean resultBoolean;

    private Operation(Builder builder) {
        this.index = builder.index;
        this.client = builder.client;
        this.opNo = builder.opNo;
        this.kind = builder.kind;
        this.args = builder.args == null ? new long[0] : builder.args.clone();
        this.invokeSeq = builder.invokeSeq;
        this.returnSeq = builder.returnSeq;
        this.resultInteger = builder.resultInteger;
        this.resultBoolean = builder.resultBoolean;
    }

    public int index() {
        return index;
    }

    public String client() {
        return client;
    }

    public long opNo() {
        return opNo;
    }

    public OpKind kind() {
        return kind;
    }

    public long[] args() {
        return args.clone();
    }

    public long arg(int i) {
        return args[i];
    }

    public long invokeSeq() {
        return invokeSeq;
    }

    public OptionalLong returnSeq() {
        return returnSeq == null ? OptionalLong.empty() : OptionalLong.of(returnSeq);
    }

    public boolean completed() {
        return returnSeq != null;
    }

    public OptionalLong resultInteger() {
        return resultInteger == null ? OptionalLong.empty() : OptionalLong.of(resultInteger);
    }

    public Optional<Boolean> resultBoolean() {
        return Optional.ofNullable(resultBoolean);
    }

    /** 形如 {@code C1#3} 的显示标签。 */
    public String label() {
        return client + "#" + opNo;
    }

    /** 形如 {@code add(5) -> 7} 或 {@code cas(0,1) -> true} 的规范文本。 */
    public String signature() {
        StringBuilder sb = new StringBuilder(kind.keyword()).append('(');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(args[i]);
        }
        sb.append(')');
        if (completed()) {
            sb.append(" -> ");
            if (kind.resultType() == OpKind.ResultType.BOOLEAN) {
                sb.append(Boolean.TRUE.equals(resultBoolean));
            } else {
                sb.append(resultInteger);
            }
        } else {
            sb.append(" -> <未返回>");
        }
        return sb.toString();
    }

    public static Builder builder(int index) {
        return new Builder(index);
    }

    /** 配对构造器。 */
    public static final class Builder {
        private final int index;
        private String client;
        private long opNo;
        private OpKind kind;
        private long[] args;
        private long invokeSeq;
        private Long returnSeq;
        private Long resultInteger;
        private Boolean resultBoolean;

        private Builder(int index) {
            this.index = index;
        }

        public Builder client(String value) {
            this.client = value;
            return this;
        }

        public Builder opNo(long value) {
            this.opNo = value;
            return this;
        }

        public Builder kind(OpKind value) {
            this.kind = value;
            return this;
        }

        public Builder args(long[] value) {
            this.args = value == null ? null : value.clone();
            return this;
        }

        public Builder invokeSeq(long value) {
            this.invokeSeq = value;
            return this;
        }

        public Builder returnSeq(Long value) {
            this.returnSeq = value;
            return this;
        }

        public Builder resultInteger(Long value) {
            this.resultInteger = value;
            return this;
        }

        public Builder resultBoolean(Boolean value) {
            this.resultBoolean = value;
            return this;
        }

        public Operation build() {
            return new Operation(this);
        }
    }
}
