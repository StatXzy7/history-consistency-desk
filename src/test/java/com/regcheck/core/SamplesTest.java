package com.regcheck.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SamplesTest {

    private final LinearizabilityChecker checker = new LinearizabilityChecker();

    @Test
    void correctInterleavingSample() {
        assertInstanceOf(CheckResult.Linearizable.class,
                checker.check(Samples.correctInterleaving()));
    }

    @Test
    void lostUpdateSample() {
        assertInstanceOf(CheckResult.NotLinearizable.class,
                checker.check(Samples.lostUpdate()));
    }

    @Test
    void casRaceSample() {
        assertInstanceOf(CheckResult.NotLinearizable.class,
                checker.check(Samples.casRace()));
    }

    @Test
    void pendingCallSample() {
        assertInstanceOf(CheckResult.Linearizable.class,
                checker.check(Samples.pendingCall()));
    }
}
