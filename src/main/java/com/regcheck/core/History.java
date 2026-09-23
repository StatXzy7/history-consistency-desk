package com.regcheck.core;

import java.util.List;

/** 一次真实调用历史：若干调用记录的不可变集合。 */
public record History(List<Call> calls) {

    public History {
        calls = List.copyOf(calls);
    }
}
