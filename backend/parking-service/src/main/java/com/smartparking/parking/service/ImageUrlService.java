package com.smartparking.parking.service;

/**
 * Resolves a stored snapshot object key into a short-lived presigned URL the client can load directly
 * from object storage. Access control is upstream: only a caller authorised to view the session
 * detail receives these URLs (operator via getById, driver via the ownership check in getByIdForDriver).
 */
public interface ImageUrlService {

    /** A presigned GET URL for {@code objectKey}, or {@code null} when key is blank or storage is off. */
    String presignedGet(String objectKey);

    /**
     * A presigned GET URL for the plate close-up saved alongside a full frame. edge-agent stores the
     * crop at the sibling key {@code <objectKey without .jpg>.plate.jpg} (see edge-agent
     * FrameStorage.put_detection). Returns {@code null} when {@code objectKey} is blank or storage is off.
     */
    String presignedPlateCrop(String objectKey);
}
