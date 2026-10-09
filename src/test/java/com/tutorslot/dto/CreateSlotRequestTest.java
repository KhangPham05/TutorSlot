package com.tutorslot.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Slot creation's "rejects a past start" rule isn't inside ProviderDashboardService at all --
// it's the @Future constraint on this record, enforced by @Valid at the controller. So this is a
// plain Bean Validation test, no Mockito/service involved.
class CreateSlotRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void rejectsAPastStart() {
        CreateSlotRequest request = new CreateSlotRequest(1L, LocalDateTime.now().minusDays(1));

        Set<ConstraintViolation<CreateSlotRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("startDateTime"));
    }

    @Test
    void acceptsAFutureStartWithAService() {
        CreateSlotRequest request = new CreateSlotRequest(1L, LocalDateTime.now().plusDays(1));

        Set<ConstraintViolation<CreateSlotRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
