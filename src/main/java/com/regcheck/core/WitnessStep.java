package com.regcheck.core;

/**
 * 见证序列中的一步：某次调用在该位置生效，寄存器值由 valueBefore 变为 valueAfter。
 *
 * @param completedInWitness 该调用原本没有返回，是在此解释中被补全的
 * @param observedResult     该步的返回结果（补全的调用为解释给出的结果）
 */
public record WitnessStep(Call call, long valueBefore, long valueAfter,
                          boolean completedInWitness, Object observedResult) {

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(call.label());
        sb.append("  | ").append(valueBefore).append(" → ").append(valueAfter);
        if (completedInWitness) {
            sb.append("  (未返回，补全为 ").append(observedResult).append(")");
        }
        return sb.toString();
    }
}
