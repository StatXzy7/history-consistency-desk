package com.reglinearity.core;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * 输入文本中的一条原始事件记录。
 *
 * <p>两种形态：</p>
 * <ul>
 *   <li>调用：{@code seq INVOKE client opNo kind [args...]}</li>
 *   <li>返回：{@code seq RETURN client opNo result}</li>
 * </ul>
 *
 * <p>该类只承载词法层面的解析结果（token 是否齐全、整数能否解析等），
 * 跨记录的语义校验（配对、类型、重复编号等）在 {@code HistoryParser} 中完成。
 * 字段在解析失败时可能为 {@code null}，因此访问器统一使用 {@link Optional}。
 */
public final class Record {

    /** 记录形态。 */
    public enum Form {
        INVOKE,
        RETURN
    }

    private final int lineNumber;
    private final Form form;
    private final Long seq;
    private final String client;
    private final Long opNo;
    private final OpKind kind;
    /** ADD 参数 d；CAS 参数 expected；READ 无参数。 */
    private final Long[] args;
    private final Long resultInteger;
    private final Boolean resultBoolean;

    private Record(Builder builder) {
        this.lineNumber = builder.lineNumber;
        this.form = builder.form;
        this.seq = builder.seq;
        this.client = builder.client;
        this.opNo = builder.opNo;
        this.kind = builder.kind;
        this.args = builder.args;
        this.resultInteger = builder.resultInteger;
        this.resultBoolean = builder.resultBoolean;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public Form form() {
        return form;
    }

    public OptionalLong seq() {
        return seq == null ? OptionalLong.empty() : OptionalLong.of(seq);
    }

    public Optional<String> client() {
        return Optional.ofNullable(client);
    }

    public OptionalLong opNo() {
        return opNo == null ? OptionalLong.empty() : OptionalLong.of(opNo);
    }

    public Optional<OpKind> kind() {
        return Optional.ofNullable(kind);
    }

    /** 返回不可变长数组副本；未解析出参数时返回空数组。 */
    public Long[] args() {
        return args == null ? new Long[0] : args.clone();
    }

    public OptionalLong resultInteger() {
        return resultInteger == null ? OptionalLong.empty() : OptionalLong.of(resultInteger);
    }

    public Optional<Boolean> resultBoolean() {
        return Optional.ofNullable(resultBoolean);
    }

    /** 词法解析器使用的构造器。 */
    public static Builder builder(int lineNumber, Form form) {
        return new Builder(lineNumber, form);
    }

    /** 仅供测试构造“已解析”记录使用。 */
    public static Record invoke(int lineNumber, long seq, String client, long opNo,
                                OpKind kind, long... args) {
        Builder b = builder(lineNumber, Form.INVOKE)
                .seq(seq).client(client).opNo(opNo).kind(kind);
        Long[] boxed = new Long[args.length];
        for (int i = 0; i < args.length; i++) {
            boxed[i] = args[i];
        }
        return b.args(boxed).build();
    }

    /** 仅供测试构造整数返回值的返回记录使用。 */
    public static Record returnInteger(int lineNumber, long seq, String client, long opNo,
                                       long value) {
        return builder(lineNumber, Form.RETURN)
                .seq(seq).client(client).opNo(opNo).resultInteger(value).build();
    }

    /** 仅供测试构造布尔返回值的返回记录使用。 */
    public static Record returnBoolean(int lineNumber, long seq, String client, long opNo,
                                       boolean value) {
        return builder(lineNumber, Form.RETURN)
                .seq(seq).client(client).opNo(opNo).resultBoolean(value).build();
    }

    /** 仅供测试构造返回值词法非法（类型错误）的返回记录使用。 */
    public static Record returnBadToken(int lineNumber, long seq, String client, long opNo) {
        return builder(lineNumber, Form.RETURN)
                .seq(seq).client(client).opNo(opNo).build();
    }

    /** 可变构造器，词法解析阶段逐字段填充，缺失即保持 {@code null}。 */
    public static final class Builder {
        private final int lineNumber;
        private final Form form;
        private Long seq;
        private String client;
        private Long opNo;
        private OpKind kind;
        private Long[] args;
        private Long resultInteger;
        private Boolean resultBoolean;

        private Builder(int lineNumber, Form form) {
            this.lineNumber = lineNumber;
            this.form = form;
        }

        public Builder seq(long value) {
            this.seq = value;
            return this;
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

        public Builder args(Long[] value) {
            this.args = value == null ? null : value.clone();
            return this;
        }

        public Builder resultInteger(long value) {
            this.resultInteger = value;
            return this;
        }

        public Builder resultBoolean(boolean value) {
            this.resultBoolean = value;
            return this;
        }

        public Record build() {
            return new Record(this);
        }
    }
}
