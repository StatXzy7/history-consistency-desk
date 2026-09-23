package com.reglinearity;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.reglinearity.cli.CliRunner;
import com.reglinearity.ui.AnalyzerFrame;

/**
 * 程序入口：默认启动 Swing 桌面界面；传入 {@code --cli} 时运行无界面命令行模式。
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            if ("--cli".equals(arg)) {
                System.exit(CliRunner.run(withoutFlag(args, "--cli")));
                return;
            }
        }
        setSystemLookAndFeel();
        SwingUtilities.invokeLater(() -> {
            AnalyzerFrame frame = new AnalyzerFrame();
            frame.setVisible(true);
        });
    }

    private static String[] withoutFlag(String[] args, String flag) {
        java.util.List<String> kept = new java.util.ArrayList<>();
        for (String arg : args) {
            if (!flag.equals(arg)) {
                kept.add(arg);
            }
        }
        return kept.toArray(String[]::new);
    }

    private static void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 退回跨平台外观即可，无需打扰用户。
        }
    }
}
