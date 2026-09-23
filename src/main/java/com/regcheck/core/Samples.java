package com.regcheck.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 内置样例历史。 */
public final class Samples {

    private Samples() {
    }

    /** 正确交错：重叠的 READ 在 ADD 之前生效，CAS 因值不符而失败。 */
    public static History correctInterleaving() {
        return new History(List.of(
                new Call(1, "A", Operation.add(1), 1, 6L, 1L),
                new Call(2, "B", Operation.read(), 2, 3L, 0L),
                new Call(3, "C", Operation.cas(1, 5), 4, 5L, false)));
    }

    /** 丢失更新：两个 ADD(1) 都返回 1，不可能满足原子语义。 */
    public static History lostUpdate() {
        return new History(List.of(
                new Call(1, "A", Operation.read(), 1, 2L, 0L),
                new Call(2, "B", Operation.add(1), 3, 4L, 1L),
                new Call(3, "C", Operation.add(1), 5, 6L, 1L)));
    }

    /** 比较交换竞争：两个重叠的 CAS(0,·) 都声称成功，矛盾。 */
    public static History casRace() {
        return new History(List.of(
                new Call(1, "A", Operation.cas(0, 1), 1, 6L, true),
                new Call(2, "B", Operation.cas(0, 2), 2, 5L, true),
                new Call(3, "C", Operation.read(), 7, 8L, 1L)));
    }

    /** 含未返回调用：pending 的 ADD 被补全后，后续 READ 的值才说得通。 */
    public static History pendingCall() {
        return new History(List.of(
                new Call(1, "A", Operation.add(3), 1, null, null),
                new Call(2, "B", Operation.read(), 2, 3L, 3L)));
    }

    /** 所有内置样例（保持插入顺序）。 */
    public static Map<String, History> all() {
        Map<String, History> map = new LinkedHashMap<>();
        map.put("正确交错", correctInterleaving());
        map.put("丢失更新", lostUpdate());
        map.put("比较交换竞争", casRace());
        map.put("未返回调用补全", pendingCall());
        return map;
    }
}
