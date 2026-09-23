package com.regcheck.core;

import java.util.List;

/**
 * 一次分析的结论，四种互斥结果：
 * <ul>
 *   <li>{@link Invalid} —— 输入记录非法（不是一致性失败）</li>
 *   <li>{@link Linearizable} —— 可解释，附带一个具体合法顺序及逐步寄存器值</li>
 *   <li>{@link NotLinearizable} —— 不可解释，附带冲突的已返回调用与原因</li>
 *   <li>{@link Undecided} —— 搜索预算耗尽，未判定</li>
 * </ul>
 */
public sealed interface CheckResult {

    /** 输入非法。 */
    record Invalid(List<String> errors) implements CheckResult {
    }

    /** 可线性化，witness 为一个具体的合法顺序（含被补全的未返回调用）。 */
    record Linearizable(List<WitnessStep> witness) implements CheckResult {
    }

    /**
     * 不可线性化。
     *
     * @param conflictCalls 一组相互冲突的已返回调用（经过缩减，但不保证最小）
     * @param explanation   人类可读的冲突原因
     */
    record NotLinearizable(List<Call> conflictCalls,
                           String explanation) implements CheckResult {
    }

    /**
     * 预算耗尽，未判定。绝不能把它当作“不存在解释”。
     *
     * @param exploredStates 已探索的状态数
     * @param budget         本次预算上限
     */
    record Undecided(long exploredStates, long budget) implements CheckResult {
    }
}
