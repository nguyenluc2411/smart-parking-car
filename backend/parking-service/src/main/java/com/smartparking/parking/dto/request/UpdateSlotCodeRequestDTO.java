package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSlotCodeRequestDTO(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]+")
        @Size(max = 10)
        String slotCode
) {
}
