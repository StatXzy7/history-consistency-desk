package com.regcheck.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 历史记录的输入合法性校验。
 *
 * <p>以下情况属于非法输入（而不是一致性失败）：
 * 编号重复、返回事件不晚于调用事件、返回值缺失或类型错误、
 * 调用数超过 {@link LinearizabilityChecker#MAX_CALLS}。
 * 不同调用的事件序号相同视为重叠（不引入先后约束），不算非法。
 */
public final class HistoryValidator {

    private HistoryValidator() {
    }

    public static ValidationResult validate(History history) {
        List<String> errors = new ArrayList<>();
        List<Call> calls = history.calls();

        if (calls.isEmpty()) {
            errors.add("历史为空：至少需要一条调用记录");
            return ValidationResult.invalid(errors);
        }
        if (calls.size() > LinearizabilityChecker.MAX_CALLS) {
            errors.add("调用数为 " + calls.size() + "，超过单次分析上限 "
                    + LinearizabilityChecker.MAX_CALLS);
        }

        Set<Integer> seenIds = new HashSet<>();
        for (Call c : calls) {
            if (!seenIds.add(c.id())) {
                errors.add("操作编号重复: #" + c.id());
            }
            if (c.client() == null || c.client().isBlank()) {
                errors.add("#" + c.id() + " 缺少客户端标识");
            }
            if (c.op() == null) {
                errors.add("#" + c.id() + " 缺少操作描述");
                continue;
            }
            if (c.responseSeq() == null) {
                if (c.result() != null) {
                    errors.add("#" + c.id() + " 没有返回事件却带有返回结果");
                }
            } else {
                if (c.responseSeq() <= c.invokeSeq()) {
                    errors.add("#" + c.id() + " 返回事件序号 " + c.responseSeq()
                            + " 不晚于调用事件序号 " + c.invokeSeq());
                }
                if (c.result() == null) {
                    errors.add("#" + c.id() + " 已返回但缺少结果");
                } else if (!resultTypeMatches(c)) {
                    errors.add("#" + c.id() + " 返回类型错误: " + c.op().kind()
                            + " 的结果应为 " + expectedType(c) + "，实际为 "
                            + c.result().getClass().getSimpleName());
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.valid()
                : ValidationResult.invalid(errors);
    }

    private static boolean resultTypeMatches(Call c) {
        return switch (c.op().kind()) {
            case READ, ADD -> c.result() instanceof Number;
            case CAS -> c.result() instanceof Boolean;
        };
    }

    private static String expectedType(Call c) {
        return switch (c.op().kind()) {
            case READ, ADD -> "整数";
            case CAS -> "布尔值";
        };
    }
}
