package com.tutorslot.dto;

import java.util.List;

public record MyAppointmentsDto(
        List<AppointmentSummaryDto> upcoming,
        List<AppointmentSummaryDto> history
) {
}
