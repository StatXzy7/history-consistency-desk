package com.regcheck.core;

/**
 * 一次调用记录。
 *
 * @param id          操作编号（在同一历史内必须唯一）
 * @param client      发起调用的客户端标识
 * @param op          操作类型与参数
 * @param invokeSeq   调用事件序号
 * @param responseSeq 返回事件序号；null 表示该调用没有返回（pending）
 * @param result      返回结果（READ/ADD 为 Long，CAS 为 Boolean）；pending 调用必须为 null
 */
public record Call(int id, String client, Operation op, long invokeSeq,
                   Long responseSeq, Object result) {

    public boolean isCompleted() {
        return responseSeq != null;
    }

    /** 展示用短标签，例如 "#3 A: ADD 1 → 2"。 */
    public String label() {
        return "#" + id + " " + client + ": " + op
                + (isCompleted() ? " → " + result : " (未返回)");
    }
}
