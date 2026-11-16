package com.scheduler.engine.sp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlIdentifierValidatorTest {

    @Test
    void acceptsPlainIdentifier() {
        assertThatCode(() -> SqlIdentifierValidator.validate("SP_SAMPLE_LIST")).doesNotThrowAnyException();
    }

    @Test
    void acceptsSchemaQualifiedAndBracketedIdentifiers() {
        assertThatCode(() -> SqlIdentifierValidator.validate("dbo.SP_SAMPLE")).doesNotThrowAnyException();
        assertThatCode(() -> SqlIdentifierValidator.validate("[dbo].[SP_SAMPLE]")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrBlank() {
        assertThatThrownBy(() -> SqlIdentifierValidator.validate(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifierValidator.validate("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifierValidator.validate("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInjectionAttempts() {
        assertThatThrownBy(() -> SqlIdentifierValidator.validate("sp_test; DROP TABLE some_table; --"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifierValidator.validate("sp_test' OR '1'='1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifierValidator.validate("sp_test -- comment"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SqlIdentifierValidator.validate("sp test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
