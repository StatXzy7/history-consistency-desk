package com.regcheck.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryTextTest {

    @Test
    void parsesPendingCallsWithBlankFields() {
        History h = HistoryText.parse("""
                # 注释行应被忽略
                1 | A | ADD 3 | 1 |   |
                2 | B | READ | 2 | 3 | 3
                """);
        assertEquals(2, h.calls().size());
        assertEquals(null, h.calls().get(0).responseSeq());
        assertEquals(null, h.calls().get(0).result());
        assertEquals(3L, h.calls().get(1).responseSeq());
        assertEquals(3L, h.calls().get(1).result());
    }

    @Test
    void rejectsWrongFieldCount() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> HistoryText.parse("1 | A | READ | 1 | 2"));
        assertTrue(e.getMessage().contains("6 个"));
    }

    @Test
    void rejectsUnknownOperation() {
        assertThrows(IllegalArgumentException.class,
                () -> HistoryText.parse("1 | A | FROB | 1 | 2 | 0"));
    }

    @Test
    void parsePrintRoundTripForAllSamples() {
        for (History h : Samples.all().values()) {
            assertEquals(h, HistoryText.parse(HistoryText.print(h)));
        }
    }

    @Test
    void fileRoundTrip(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("h.txt");
        Files.writeString(f, HistoryText.print(Samples.pendingCall()));
        History parsed = HistoryText.parse(Files.readString(f));
        assertEquals(Samples.pendingCall(), parsed);
    }

    @Test
    void parsedIllegalDuplicateIsReportedAsInvalidNotFailure() {
        History h = HistoryText.parse("""
                1 | A | READ | 1 | 2 | 0
                1 | B | READ | 3 | 4 | 0
                """);
        CheckResult r = new LinearizabilityChecker().check(h);
        assertInstanceOf(CheckResult.Invalid.class, r);
    }
}
