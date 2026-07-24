package com.smartparking.parking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNameRequestDTO(
        @NotBlank @Size(max = 80) String name
) {
}
