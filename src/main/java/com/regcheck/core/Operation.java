package com.regcheck.core;

/**
 * 一次寄存器操作的类型与参数。
 * READ：无参数；ADD：arg1 为加数；CAS：arg1 为期望值，arg2 为新值。
 */
public record Operation(OpKind kind, long arg1, long arg2) {

    public static Operation read() {
        return new Operation(OpKind.READ, 0, 0);
    }

    public static Operation add(long delta) {
        return new Operation(OpKind.ADD, delta, 0);
    }

    public static Operation cas(long expected, long newValue) {
        return new Operation(OpKind.CAS, expected, newValue);
    }

    /** 解析文本形式：READ / ADD 3 / CAS 0 1。 */
    public static Operation parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("操作不能为空");
        }
        String[] parts = text.trim().split("\\s+");
        return switch (parts[0].toUpperCase()) {
            case "READ" -> {
                requireArity(parts, 1);
                yield read();
            }
            case "ADD" -> {
                requireArity(parts, 2);
                yield add(Long.parseLong(parts[1]));
            }
            case "CAS" -> {
                requireArity(parts, 3);
                yield cas(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
            }
            default -> throw new IllegalArgumentException("未知操作类型: " + parts[0]);
        };
    }

    private static void requireArity(String[] parts, int expected) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("参数个数错误，应为 " + (expected - 1) + " 个");
        }
    }

    @Override
    public String toString() {
        return switch (kind) {
            case READ -> "READ";
            case ADD -> "ADD " + arg1;
            case CAS -> "CAS " + arg1 + " " + arg2;
        };
    }
}
