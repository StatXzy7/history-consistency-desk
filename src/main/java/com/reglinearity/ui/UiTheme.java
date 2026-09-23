package com.reglinearity.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * 界面统一的配色与字体常量：有意选择深色技术风，避免模板化灰白外观。
 */
final class UiTheme {

    private UiTheme() {
    }

    static final Color BACKGROUND = new Color(0x1E, 0x21, 0x27);
    static final Color SURFACE = new Color(0x26, 0x2B, 0x33);
    static final Color SURFACE_HIGHLIGHT = new Color(0x32, 0x39, 0x44);
    static final Color BORDER = new Color(0x44, 0x4C, 0x58);
    static final Color TEXT = new Color(0xE6, 0xEA, 0xEF);
    static final Color TEXT_MUTED = new Color(0x9A, 0xA4, 0xB1);

    static final Color ACCENT = new Color(0x5A, 0x9B, 0xF2);
    static final Color OK = new Color(0x4C, 0xCA, 0x8A);
    static final Color BAD = new Color(0xF0, 0x6A, 0x6A);
    static final Color WARN = new Color(0xE8, 0xB2, 0x4A);

    /** 各操作类型的主色。 */
    static final Color READ_COLOR = new Color(0x6F, 0xB7, 0xFF);
    static final Color ADD_COLOR = new Color(0x8E, 0xD8, 0x7E);
    static final Color CAS_COLOR = new Color(0xC8, 0x9B, 0xF0);

    /** 重叠区间底色。 */
    static final Color OVERLAP_FILL = new Color(0xF0, 0x6A, 0x6A, 28);

    static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 13);
    static final Font MONO_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 13);
    static final Font UI = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    static final Font UI_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 13);
    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 15);

    static Color operationColor(com.reglinearity.core.OpKind kind) {
        return switch (kind) {
            case READ -> READ_COLOR;
            case ADD -> ADD_COLOR;
            case CAS -> CAS_COLOR;
        };
    }
}
