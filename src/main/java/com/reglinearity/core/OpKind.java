package com.reglinearity.core;

/**
 * 寄存器支持的三类原子操作。
 *
 * <ul>
 *   <li>{@link #READ} 读：无参数，返回寄存器当前值；</li>
 *   <li>{@link #ADD} 加：参数 d，原子执行 x := x + d，返回新值；</li>
 *   <li>{@link #CAS} 比较并交换：参数 expected / desired，
 *       当且仅当 x == expected 时 x := desired，返回是否成功。</li>
 * </ul>
 */
public enum OpKind {
    READ("read", "读取", 0, ResultType.INTEGER),
    ADD("add", "加上整数并返回新值", 1, ResultType.INTEGER),
    CAS("cas", "比较并交换", 2, ResultType.BOOLEAN);

    /** 输入文本中使用的关键字（大小写不敏感）。 */
    private final String keyword;
    private final String description;
    /** 调用参数个数。 */
    private final int argCount;
    private final ResultType resultType;

    OpKind(String keyword, String description, int argCount, ResultType resultType) {
        this.keyword = keyword;
        this.description = description;
        this.argCount = argCount;
        this.resultType = resultType;
    }

    public String keyword() {
        return keyword;
    }

    public String description() {
        return description;
    }

    public int argCount() {
        return argCount;
    }

    public ResultType resultType() {
        return resultType;
    }

    /**
     * 按关键字解析操作类型，大小写不敏感。
     *
     * @return 对应的操作类型；无法识别时返回 {@code null}
     */
    public static OpKind fromKeyword(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase();
        for (OpKind kind : values()) {
            if (kind.keyword.equals(normalized)) {
                return kind;
            }
        }
        return null;
    }

    /** 调用返回值应当具有的类型。 */
    public enum ResultType {
        INTEGER,
        BOOLEAN
    }
}
