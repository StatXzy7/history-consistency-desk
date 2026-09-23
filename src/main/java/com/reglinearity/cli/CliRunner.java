package com.reglinearity.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.reglinearity.core.ConsistencyService;
import com.reglinearity.core.LinearizabilityAnalyzer;

/**
 * 无界面命令行入口：证明分析核心可以脱离 Swing 独立运行。
 *
 * <pre>
 *   java -jar target/app.jar --cli [--budget N] [文件路径]
 * </pre>
 * <p>不给定文件路径时从标准输入读取历史文本。退出码：0=可解释，
 * 2=不可解释（一致但无法线性化），3=输入非法，4=未判定（预算耗尽）。</p>
 */
public final class CliRunner {

    public static final int EXIT_CONSISTENT = 0;
    public static final int EXIT_INCONSISTENT = 2;
    public static final int EXIT_ILLEGAL = 3;
    public static final int EXIT_UNDECIDED = 4;

    private CliRunner() {
    }

    public static int run(String[] args) throws IOException {
        long budget = LinearizabilityAnalyzer.DEFAULT_BUDGET;
        String filePath = null;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--budget" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("--budget 需要一个正整数参数。");
                        return EXIT_ILLEGAL;
                    }
                    try {
                        budget = Long.parseLong(args[++i]);
                    } catch (NumberFormatException ex) {
                        System.err.println("预算不是合法整数：" + args[i]);
                        return EXIT_ILLEGAL;
                    }
                    if (budget < 1) {
                        System.err.println("预算必须为正整数。");
                        return EXIT_ILLEGAL;
                    }
                }
                case "--help", "-h" -> {
                    printUsage();
                    return EXIT_CONSISTENT;
                }
                default -> filePath = arg;
            }
        }

        String text;
        if (filePath != null) {
            text = Files.readString(Path.of(filePath), StandardCharsets.UTF_8);
        } else {
            text = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
        }

        ConsistencyService service = new ConsistencyService();
        ConsistencyService.ServiceResponse response = service.analyze(text, budget);
        if (response.isIllegalInput()) {
            System.out.println("结论：输入非法");
            System.out.println(response.illegalError());
            return EXIT_ILLEGAL;
        }
        String rendered = ConsistencyService.render(response.history(), response.result());
        System.out.println(rendered);
        return switch (response.result().verdict()) {
            case CONSISTENT -> EXIT_CONSISTENT;
            case INCONSISTENT -> EXIT_INCONSISTENT;
            case UNDECIDED -> EXIT_UNDECIDED;
        };
    }

    private static void printUsage() {
        System.out.println("""
                寄存器线性一致性分析器（命令行模式）

                用法：
                  java -jar target/app.jar --cli [--budget N] [历史文件]
                  不给定文件时从标准输入读取。

                记录格式（每行一条，# 起为注释）：
                  <事件序号> INVOKE <客户端> <操作编号> read
                  <事件序号> INVOKE <客户端> <操作编号> add <整数增量>
                  <事件序号> INVOKE <客户端> <操作编号> cas <期望值> <目标值>
                  <事件序号> RETURN <客户端> <操作编号> <整数 | true | false>

                退出码：0 可解释；2 不可解释；3 输入非法；4 预算耗尽未判定。
                """);
    }
}
