package com.reglinearity.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 单行文本的词法解析结果：空白分隔的 token，或以 {@code #} 开头的注释/空行。
 */
final class LineTokens {

    private final int lineNumber;
    private final List<String> tokens;
    private final boolean blank;

    private LineTokens(int lineNumber, List<String> tokens, boolean blank) {
        this.lineNumber = lineNumber;
        this.tokens = tokens;
        this.blank = blank;
    }

    /**
     * 切分一行文本。规则：按空白拆分 token；{@code #} 之后的内容为注释。
     */
    static LineTokens split(int lineNumber, String rawLine) {
        String line = rawLine;
        int hash = line.indexOf('#');
        if (hash >= 0) {
            line = line.substring(0, hash);
        }
        List<String> tokens = new ArrayList<>();
        for (String token : line.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return new LineTokens(lineNumber, List.copyOf(tokens), tokens.isEmpty());
    }

    int lineNumber() {
        return lineNumber;
    }

    List<String> tokens() {
        return tokens;
    }

    boolean isBlank() {
        return blank;
    }
}
