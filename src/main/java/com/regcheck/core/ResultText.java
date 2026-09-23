package com.regcheck.core;

import java.util.List;

/** 把分析结论格式化为人类可读文本（CLI 与界面共用）。 */
public final class ResultText {

    private ResultText() {
    }

    public static String format(CheckResult result) {
        StringBuilder sb = new StringBuilder();
        switch (result) {
            case CheckResult.Invalid invalid -> {
                sb.append("【输入非法】\n");
                invalid.errors().forEach(e -> sb.append("  - ").append(e).append('\n'));
            }
            case CheckResult.Linearizable lin -> {
                sb.append("【可线性化】找到一个合法顺序（寄存器初值 0）：\n");
                List<WitnessStep> steps = lin.witness();
                for (int i = 0; i < steps.size(); i++) {
                    sb.append(String.format("  %2d. %s%n", i + 1,
                            steps.get(i).describe()));
                }
            }
            case CheckResult.NotLinearizable not ->
                    sb.append("【不可线性化】\n").append(not.explanation()).append('\n');
            case CheckResult.Undecided und -> sb.append("【未判定】搜索预算耗尽：已探索 ")
                    .append(und.exploredStates()).append(" 个状态（预算 ")
                    .append(und.budget()).append("）。增大预算后重试。\n");
        }
        return sb.toString();
    }
}
