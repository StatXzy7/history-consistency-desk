package com.regcheck.core;

/** 寄存器支持的三种原子操作。 */
public enum OpKind {
    /** 读取当前值，返回 long。 */
    READ,
    /** 加上一个整数并返回新值，返回 long。 */
    ADD,
    /** 比较并交换，返回 boolean。 */
    CAS
}
