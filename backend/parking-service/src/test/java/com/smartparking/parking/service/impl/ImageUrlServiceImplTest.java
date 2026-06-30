package com.smartparking.parking.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ImageUrlServiceImpl} — focused on the plate-crop key derivation, which must
 * stay in sync with edge-agent FrameStorage._plate_key (the sibling object key edge-agent stores).
 */
class ImageUrlServiceImplTest {

    @Test
    void plateCropKey_replacesJpgSuffix() {
        assertEquals("frames/2025/IN_x.plate.jpg",
                ImageUrlServiceImpl.plateCropKey("frames/2025/IN_x.jpg"));
    }

    @Test
    void plateCropKey_appendsWhenNoJpgSuffix() {
        assertEquals("frames/2025/IN_x.plate.jpg",
                ImageUrlServiceImpl.plateCropKey("frames/2025/IN_x"));
    }

    @Test
    void presignedPlateCrop_nullOrBlankKey_returnsNull() {
        // Storage disabled (blank credentials) -> service short-circuits without a MinioClient call.
        ImageUrlServiceImpl service = new ImageUrlServiceImpl(null, "parking-frames", 15, "", "");
        assertNull(service.presignedPlateCrop(null));
        assertNull(service.presignedPlateCrop("  "));
        assertNull(service.presignedPlateCrop("frames/2025/IN_x.jpg")); // disabled -> null
    }
}
