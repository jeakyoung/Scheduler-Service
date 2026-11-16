package com.scheduler.web.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingTest {

    @Test
    void nullOrEmptyBecomesEmptyString() {
        assertThat(Masking.lastFour(null)).isEmpty();
        assertThat(Masking.lastFour("")).isEmpty();
    }

    @Test
    void valuesAtOrBelowVisibleLengthAreFullyMasked() {
        // REVIEW.md M2: previously "4002".length()==4 meant visible==length, so 0 stars
        // were prepended and the whole plaintext value leaked straight through.
        assertThat(Masking.lastFour("4002")).isEqualTo("****");
        assertThat(Masking.lastFour("1")).isEqualTo("*");
        assertThat(Masking.lastFour("abc")).isEqualTo("***");
        assertThat(Masking.lastFour("4002")).doesNotContain("4002");
    }

    @Test
    void valuesLongerThanVisibleLengthKeepOnlyLastFourVisible() {
        assertThat(Masking.lastFour("super-secret-pw")).isEqualTo("***********t-pw");
        assertThat(Masking.lastFour("12345")).isEqualTo("*2345");
    }
}
