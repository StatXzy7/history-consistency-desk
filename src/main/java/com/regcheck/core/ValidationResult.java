package com.regcheck.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 输入合法性校验结果。非法记录与一致性失败严格区分。 */
public final class ValidationResult {

    private final List<String> errors;

    private ValidationResult(List<String> errors) {
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    public static ValidationResult invalid(List<String> errors) {
        return new ValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return errors;
    }
}
