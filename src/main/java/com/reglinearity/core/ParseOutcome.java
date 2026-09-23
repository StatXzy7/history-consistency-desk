package com.reglinearity.core;

import java.util.Optional;

/**
 * 输入解析与校验的结果。
 *
 * <p>两种互斥结局：</p>
 * <ul>
 *   <li>{@link #illegal(String)}：输入记录非法（重复编号、返回早于调用、返回类型错误等），
 *       不能进入一致性分析，也绝不能混为“不一致”；</li>
 *   <li>{@link #valid(History)}：得到可分析的 {@link History}（允许为空历史）。</li>
 * </ul>
 *
 * <p>不可变值对象。</p>
 */
public final class ParseOutcome {

    private final History history;
    private final String error;

    private ParseOutcome(History history, String error) {
        this.history = history;
        this.error = error;
    }

    static ParseOutcome valid(History history) {
        return new ParseOutcome(history, null);
    }

    static ParseOutcome illegal(String error) {
        return new ParseOutcome(null, error);
    }

    public boolean isValid() {
        return error == null;
    }

    public Optional<History> history() {
        return Optional.ofNullable(history);
    }

    /** 非法原因（仅 {@link #isValid()} 为 false 时存在）。 */
    public Optional<String> error() {
        return Optional.ofNullable(error);
    }
}
