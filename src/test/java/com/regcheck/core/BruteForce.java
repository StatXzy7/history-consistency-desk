package com.regcheck.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 独立的暴力参考实现：状态 = （尚未生效的调用集合，当前寄存器值）。
 * 每一步枚举每一个“前置调用都已生效”的调用并按语义生效；
 * 已返回调用必须得到记录的结果；未返回调用结果任意，也可以一直被忽略
 * （当剩余集合中没有已返回调用时立即成功）。
 *
 * <p>该实现穷举所有合法排列（含未返回调用的所有补全方式），
 * 不做记忆化、不设预算，与被测检查器不共享代码，仅用于小规模测试核对。
 */
final class BruteForce {

    private BruteForce() {
    }

    static boolean isLinearizable(History history) {
        return dfs(new ArrayList<>(history.calls()), 0L);
    }

    private static boolean dfs(List<Call> remaining, long value) {
        boolean anyCompleted = remaining.stream().anyMatch(Call::isCompleted);
        if (!anyCompleted) {
            return true; // 剩余未返回调用全部可忽略
        }
        for (int i = 0; i < remaining.size(); i++) {
            Call c = remaining.get(i);
            if (hasUnlinearizedPredecessor(c, remaining)) {
                continue;
            }
            Long next = apply(c, value);
            if (next == null) {
                continue; // 已返回调用的结果与语义矛盾
            }
            List<Call> rest = new ArrayList<>(remaining);
            rest.remove(i);
            if (dfs(rest, next)) {
                return true;
            }
        }
        return false;
    }

    /** a 的返回序号 &lt; c 的调用序号且 a 尚未生效 ⇒ c 还不能生效。 */
    private static boolean hasUnlinearizedPredecessor(Call c, List<Call> remaining) {
        for (Call a : remaining) {
            if (a != c && a.responseSeq() != null
                    && a.responseSeq() < c.invokeSeq()) {
                return true;
            }
        }
        return false;
    }

    /** 返回生效后的寄存器值；结果矛盾时返回 null。 */
    private static Long apply(Call c, long value) {
        return switch (c.op().kind()) {
            case READ -> {
                if (c.isCompleted() && ((Number) c.result()).longValue() != value) {
                    yield null;
                }
                yield value;
            }
            case ADD -> {
                long nv = value + c.op().arg1();
                if (c.isCompleted() && ((Number) c.result()).longValue() != nv) {
                    yield null;
                }
                yield nv;
            }
            case CAS -> {
                boolean success = value == c.op().arg1();
                if (c.isCompleted() && !c.result().equals(Boolean.valueOf(success))) {
                    yield null;
                }
                yield success ? c.op().arg2() : value;
            }
        };
    }
}
