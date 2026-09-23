package com.reglinearity.core;

/**
 * 分析结论的三态分类。
 *
 * <ul>
 *   <li>{@link #CONSISTENT}：存在一个保持非重叠先后关系、且每步符合寄存器规范的原子顺序；</li>
 *   <li>{@link #INCONSISTENT}：穷举了预算内全部候选顺序，均不合法（输入本身合法）；</li>
 *   <li>{@link #UNDECIDED}：搜索在预算内未能完成，不能据此断言不存在解释。</li>
 * </ul>
 */
public enum Verdict {
    CONSISTENT("可解释（一致）"),
    INCONSISTENT("不可解释（不一致）"),
    UNDECIDED("未判定（预算耗尽）");

    private final String displayName;

    Verdict(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
