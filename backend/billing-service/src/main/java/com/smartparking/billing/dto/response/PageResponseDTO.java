package com.smartparking.billing.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** Paged list envelope matching the list shape in docs/api-contracts.md. */
public record PageResponseDTO<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
    public static <T> PageResponseDTO<T> of(Page<?> source, List<T> content) {
        return new PageResponseDTO<>(content, source.getTotalElements(), source.getTotalPages(),
                source.getNumber(), source.getSize());
    }
}
