package com.smartparking.parking.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Plate-number normalization + format validation (BR-001-3/4). */
public final class PlateNumbers {

    /**
     * BR-001-3: valid VN plate. 2 digits + 1–2 series letters (incl. Đ; covers 51F, 51LD, 51MK, …)
     * + dash + 3–5 digits + optional .NN, e.g. 51F-12345, 51F-123.45, 51LD-123.45.
     */
    private static final Pattern PATTERN =
            Pattern.compile("^[0-9]{2}[A-ZĐ]{1,2}-[0-9]{3,5}(\\.[0-9]{2})?$");

    private PlateNumbers() {
    }

    /**
     * BR-001-4: chuẩn hóa về MỘT dạng duy nhất — viết hoa, bỏ khoảng trắng VÀ bỏ dấu chấm trang trí
     * ("30A-123.45" -> "30A-12345"). Dấu chấm chỉ để nhìn; ALPR luôn ra dạng không chấm, nên bỏ chấm
     * giúp biển gõ tay (whitelist/blacklist/nhập thủ công) khớp với biển camera đọc.
     */
    public static String normalize(String raw) {
        return raw == null ? null : raw.replaceAll("[\\s.]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String normalized) {
        return normalized != null && PATTERN.matcher(normalized).matches();
    }
}
