package com.tutorslot.dto;

import java.util.List;

public record ProviderDashboardDto(
        List<ProviderSlotDto> slots,
        List<ProviderSubjectOptionDto> subjects
) {
}
