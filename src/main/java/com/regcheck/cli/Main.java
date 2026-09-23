package com.regcheck.cli;

import com.regcheck.core.CheckResult;
import com.regcheck.core.History;
import com.regcheck.core.HistoryText;
import com.regcheck.core.LinearizabilityChecker;
import com.regcheck.core.Samples;
import com.regcheck.core.WitnessStep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 程序入口。无参数时启动 Swing 界面；
 * 带参数时在命令行直接运行分析核心（不依赖界面）。
 *
 * <pre>
 * java -jar app.jar                        启动图形界面
 * java -jar app.jar --list                 列出内置样例
 * java -jar app.jar --sample 丢失更新       分析内置样例
 * java -jar app.jar --file history.txt     分析文本格式历史
 * java -jar app.jar --budget 1000 ...      设置搜索预算
 * </pre>
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            javax.swing.SwingUtilities.invokeLater(
                    () -> com.regcheck.ui.AppFrame.open());
            return;
        }
        int exit = runCli(args);
        if (exit != 0) {
            System.exit(exit);
        }
    }

    static int runCli(String[] args) {
        long budget = LinearizabilityChecker.DEFAULT_BUDGET;
        String sampleName = null;
        String file = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--list" -> {
                    Samples.all().keySet().forEach(System.out::println);
                    return 0;
                }
                case "--sample" -> sampleName = requireValue(args, ++i, "--sample");
                case "--file" -> file = requireValue(args, ++i, "--file");
                case "--budget" -> budget =
                        Long.parseLong(requireValue(args, ++i, "--budget"));
                default -> {
                    System.err.println("未知参数: " + args[i]);
                    return 2;
                }
            }
        }

        History history;
        try {
            if (sampleName != null) {
                history = Samples.all().get(sampleName);
                if (history == null) {
                    System.err.println("未知样例: " + sampleName + "（用 --list 查看）");
                    return 2;
                }
            } else if (file != null) {
                history = HistoryText.parse(Files.readString(Path.of(file)));
            } else {
                System.err.println("请指定 --sample <名称> 或 --file <路径>，或 --list");
                return 2;
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("读取历史失败: " + e.getMessage());
            return 2;
        }

        CheckResult result = new LinearizabilityChecker().check(history, budget);
        System.out.println(com.regcheck.core.ResultText.format(result));
        return 0;
    }

    private static String requireValue(String[] args, int i, String flag) {
        if (i >= args.length) {
            throw new IllegalArgumentException(flag + " 缺少参数值");
        }
        return args[i];
    }
}
