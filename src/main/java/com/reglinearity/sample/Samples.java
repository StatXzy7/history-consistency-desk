package com.reglinearity.sample;

import java.util.List;

/**
 * 内置样例历史。样例文本即标准输入格式，可直接编辑后重新分析。
 */
public final class Samples {

    private Samples() {
    }

    /**
     * 样例 1：正确交错。
     *
     * <p>读操作（C2）先返回却返回了 10：它与 C1 的 add 区间重叠，
     * 可把读的线性化点放在 add 之后——合法顺序不是按返回时间排出来的。</p>
     */
    public static final Sample CORRECT_INTERLEAVING = new Sample(
            "正确交错（读后返回却读到新值）",
            "C1 的 add(10) 与 C2 的 read 区间重叠；read 先返回但返回 10，"
                    + "允许解释为 add 先生效、read 后生效。",
            """
            # 正确交错：事件 2 的读在事件 3 才返回 add 的结果，
            # 两个调用区间重叠，顺序由规范而非返回时间决定。
            1 INVOKE C1 1 add 10
            2 INVOKE C2 1 read
            3 RETURN C2 1 10
            4 RETURN C1 1 10
            """);

    /**
     * 样例 2：丢失更新。
     *
     * <p>两个重叠的 add(1) 都声称返回新值 1。任何原子顺序下第二个 add 必从 1 加到 2，
     * 不可能也返回 1——典型的丢失更新，不可解释。</p>
     */
    public static final Sample LOST_UPDATE = new Sample(
            "丢失更新（两个 add(1) 都返回 1）",
            "两个并发 add(1) 都返回 1；无论哪个先生效，后一个都应得到 2，冲突。",
            """
            # 丢失更新：C1、C2 的 add(1) 区间重叠，
            # 双方都返回新值 1，缺少一次自增。
            1 INVOKE C1 1 add 1
            2 INVOKE C2 1 add 1
            3 RETURN C1 1 1
            4 RETURN C2 1 1
            """);

    /**
     * 样例 3：比较并交换竞争。
     *
     * <p>两个重叠的 cas(0,1) 都返回 true。寄存器初值为 0，至多一个 cas 能看到 0 并成功，
     * 另一个看到的必然是 1，必须返回 false——双成功不可解释。</p>
     */
    public static final Sample CAS_RACE = new Sample(
            "比较并交换竞争（两个 cas 都成功）",
            "两个并发 cas(0,1) 都返回 true；初值 0 只能被交换一次，冲突。",
            """
            # CAS 竞争：两个 cas(0,1) 区间重叠且都声称成功。
            1 INVOKE C1 1 cas 0 1
            2 INVOKE C2 1 cas 0 1
            3 RETURN C1 1 true
            4 RETURN C2 1 true
            """);

    /**
     * 样例 4（附加）：存在未返回调用。
     *
     * <p>C2 的 read 一直没有返回，可以忽略；C1 的 add(5) 返回 5 完全可解释。</p>
     */
    public static final Sample PENDING_READ = new Sample(
            "未返回调用（一个始终未返回的 read）",
            "C2 的 read 没有返回记录：可忽略，也可在解释中补全，不影响 C1。",
            """
            # 未返回调用：C2 的 read 没有 RETURN，分析时允许丢弃或补全。
            1 INVOKE C1 1 add 5
            2 INVOKE C2 1 read
            3 RETURN C1 1 5
            """);

    public static List<Sample> all() {
        return List.of(CORRECT_INTERLEAVING, LOST_UPDATE, CAS_RACE, PENDING_READ);
    }

    /** 样例定义：名称、说明、标准格式的历史文本。 */
    public record Sample(String name, String description, String text) {
        @Override
        public String toString() {
            return name;
        }
    }
}
