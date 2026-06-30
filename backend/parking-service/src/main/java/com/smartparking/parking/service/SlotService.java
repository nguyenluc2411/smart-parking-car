package com.smartparking.parking.service;

import com.smartparking.parking.dto.request.CreateSlotRequestDTO;
import com.smartparking.parking.dto.request.ProvisionZoneRequestDTO;
import com.smartparking.parking.dto.request.UpdateSlotStatusRequestDTO;
import com.smartparking.parking.dto.response.ProvisionResultDTO;
import com.smartparking.parking.dto.response.SlotAvailabilityResponseDTO;
import com.smartparking.parking.dto.response.SlotResponseDTO;
import com.smartparking.parking.dto.response.SlotResyncResultDTO;
import java.util.List;
import java.util.UUID;

/** Slot queries, reconciliation, and admin lot setup (BR-003). */
public interface SlotService {

    SlotAvailabilityResponseDTO getAvailability();

    List<SlotResponseDTO> listSlots();

    /** BR-003-4: reconcile slot occupancy against ACTIVE sessions (ground truth). ADMIN only. */
    SlotResyncResultDTO resync();

    /** Create one slot. ADMIN only. Conflict if the code already exists. */
    SlotResponseDTO createSlot(CreateSlotRequestDTO request);

    /** Delete a slot. ADMIN only. Conflict if the slot is OCCUPIED. */
    void deleteSlot(UUID id);

    /** Set a slot EMPTY or MAINTENANCE. ADMIN only. Conflict if it is OCCUPIED or target is OCCUPIED. */
    SlotResponseDTO updateStatus(UUID id, UpdateSlotStatusRequestDTO request);

    /** Quick lot setup: make a zone have exactly {@code count} slots (create/remove). ADMIN only. */
    ProvisionResultDTO provisionZone(ProvisionZoneRequestDTO request);
}
