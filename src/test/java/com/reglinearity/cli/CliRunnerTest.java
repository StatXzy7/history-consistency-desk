package com.reglinearity.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 命令行入口测试：证明分析核心脱离 Swing 可用，退出码与三态结论对应。 */
class CliRunnerTest {

    private PrintStream originalOut;
    private ByteArrayOutputStream captured;

    @BeforeEach
    void redirectStdout() {
        originalOut = System.out;
        captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    private void feedStdin(String text) {
        System.setIn(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private String output() {
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("一致历史：退出码 0，输出逐步寄存器值")
    void consistentFromStdin() throws Exception {
        feedStdin("1 INVOKE C1 1 add 3\n2 RETURN C1 1 3\n");
        int code = CliRunner.run(new String[0]);
        assertEquals(CliRunner.EXIT_CONSISTENT, code);
        assertTrue(output().contains("可解释"));
        assertTrue(output().contains("0 → 3") || output().contains("0 -> 3"));
    }

    @Test
    @DisplayName("不一致历史：退出码 2，输出冲突原因")
    void inconsistentFromFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("h.txt");
        Files.writeString(file, """
                1 INVOKE C1 1 add 1
                2 INVOKE C2 1 add 1
                3 RETURN C1 1 1
                4 RETURN C2 1 1
                """, StandardCharsets.UTF_8);
        int code = CliRunner.run(new String[]{file.toString()});
        assertEquals(CliRunner.EXIT_INCONSISTENT, code);
        assertTrue(output().contains("不可解释"));
        assertTrue(output().contains("应得到 2"));
    }

    @Test
    @DisplayName("非法输入：退出码 3")
    void illegalInput() throws Exception {
        feedStdin("1 INVOKE C1 1 cas 0 1\n2 RETURN C1 1 1\n");
        int code = CliRunner.run(new String[0]);
        assertEquals(CliRunner.EXIT_ILLEGAL, code);
        assertTrue(output().contains("输入非法"));
    }

    @Test
    @DisplayName("预算耗尽：退出码 4 且明确写出未判定")
    void budgetExhausted() throws Exception {
        feedStdin("1 INVOKE C1 1 add 1\n2 INVOKE C2 1 add 1\n"
                + "3 RETURN C1 1 1\n4 RETURN C2 1 1\n");
        int code = CliRunner.run(new String[]{"--budget", "1"});
        assertEquals(CliRunner.EXIT_UNDECIDED, code);
        assertTrue(output().contains("未判定"));
    }
}
