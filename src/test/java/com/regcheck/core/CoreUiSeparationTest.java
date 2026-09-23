package com.regcheck.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分析核心必须能脱离界面运行：core 包的源码不得引用 AWT/Swing。
 */
class CoreUiSeparationTest {

    @Test
    void corePackageDoesNotReferenceUiToolkits() throws Exception {
        Path coreDir = Path.of("src", "main", "java", "com", "regcheck", "core");
        assertTrue(Files.isDirectory(coreDir), "core 目录应存在");
        try (var files = Files.walk(coreDir)) {
            for (Path f : (Iterable<Path>) files.filter(p ->
                    p.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(f);
                assertTrue(!src.contains("java.awt") && !src.contains("javax.swing"),
                        f + " 引用了界面工具包，核心无法脱离界面运行");
            }
        }
    }

    @Test
    void checkerRunsWithoutUiClasspath() {
        // 直接在测试中以纯逻辑方式调用核心，不触碰 com.regcheck.ui
        CheckResult r = new LinearizabilityChecker().check(Samples.lostUpdate());
        assertInstanceOf(CheckResult.NotLinearizable.class, r);
    }
}
