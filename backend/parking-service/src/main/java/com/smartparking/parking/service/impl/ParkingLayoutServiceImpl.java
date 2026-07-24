package com.smartparking.parking.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.parking.dto.request.SaveParkingLayoutRequestDTO;
import com.smartparking.parking.dto.request.CreateFloorRequestDTO;
import com.smartparking.parking.dto.request.CreateParkingLotRequestDTO;
import com.smartparking.parking.dto.request.CreateZoneRequestDTO;
import com.smartparking.parking.dto.request.CreateZoneSlotRequestDTO;
import com.smartparking.parking.dto.request.UpdateNameRequestDTO;
import com.smartparking.parking.dto.request.UpdateParkingLotRequestDTO;
import com.smartparking.parking.dto.request.UpdateSlotCodeRequestDTO;
import com.smartparking.parking.dto.response.ParkingLayoutResponseDTO;
import com.smartparking.parking.dto.response.ParkingLotResponseDTO;
import com.smartparking.parking.dto.response.ParkingStructureResponseDTO;
import com.smartparking.parking.dto.response.SlotResponseDTO;
import com.smartparking.parking.entity.Gate;
import com.smartparking.parking.entity.ParkingLayout;
import com.smartparking.parking.entity.ParkingLot;
import com.smartparking.parking.entity.ParkingFloor;
import com.smartparking.parking.entity.ParkingZone;
import com.smartparking.parking.entity.Slot;
import com.smartparking.parking.exception.ConflictException;
import com.smartparking.parking.exception.ResourceNotFoundException;
import com.smartparking.parking.repository.GateRepository;
import com.smartparking.parking.repository.GateLogRepository;
import com.smartparking.parking.repository.ParkingLayoutRepository;
import com.smartparking.parking.repository.ParkingLotRepository;
import com.smartparking.parking.repository.ParkingFloorRepository;
import com.smartparking.parking.repository.ParkingZoneRepository;
import com.smartparking.parking.repository.SlotRepository;
import com.smartparking.parking.repository.SessionRepository;
import com.smartparking.parking.repository.ReservationRepository;
import com.smartparking.parking.service.ParkingLayoutService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;
import com.smartparking.parking.entity.enums.GateDirection;
import com.smartparking.parking.entity.enums.GateStatus;
import com.smartparking.parking.entity.enums.SlotStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParkingLayoutServiceImpl implements ParkingLayoutService {
    private final ParkingLotRepository lotRepository;
    private final ParkingFloorRepository floorRepository;
    private final ParkingZoneRepository zoneRepository;
    private final ParkingLayoutRepository layoutRepository;
    private final SlotRepository slotRepository;
    private final GateRepository gateRepository;
    private final GateLogRepository gateLogRepository;
    private final SessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ParkingLotResponseDTO> listLots() {
        return lotRepository.findAll().stream()
                .sorted(Comparator.comparing(ParkingLot::getName))
                .map(lot -> new ParkingLotResponseDTO(lot.getId(), lot.getLotCode(), lot.getName(),
                        lot.getAddress(), lot.isActive()))
                .toList();
    }

    @Override
    @Transactional
    public ParkingStructureResponseDTO createLot(CreateParkingLotRequestDTO request) {
        String code = request.lotCode().trim().toUpperCase(Locale.ROOT);
        if (lotRepository.findAll().stream().anyMatch(lot -> lot.getLotCode().equalsIgnoreCase(code))) {
            throw new ConflictException("Mã bãi đã tồn tại: " + code);
        }
        ParkingLot lot = lotRepository.save(ParkingLot.builder()
                .lotCode(code).name(request.name().trim()).address(request.address()).active(true).build());
        ParkingFloor floor = floorRepository.save(ParkingFloor.builder()
                .parkingLotId(lot.getId()).floorCode("GROUND").name("Mặt đất").sortOrder(0).build());
        if (request.createTemplate()) {
            createZones(floor, valueOrDefault(request.groundZoneCount(), 1),
                    valueOrDefault(request.slotsPerZone(), 10));
            createTemplateGate(lot, floor, code + "_IN", GateDirection.IN, true);
            createTemplateGate(lot, floor, code + "_OUT", GateDirection.OUT, true);
            createTemplateGate(lot, floor, code + "_AUXIN", GateDirection.IN, false);
            createTemplateGate(lot, floor, code + "_AUXOUT", GateDirection.OUT, false);
        } else {
            createZones(floor, 1, 0);
        }
        createDefaultLayout(lot.getId(), floor.getId());
        return getStructure(lot.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ParkingStructureResponseDTO getStructure(UUID lotId) {
        ParkingLot lot = requireLot(lotId);
        List<ParkingStructureResponseDTO.Floor> floors = floorRepository
                .findByParkingLotIdOrderBySortOrderAsc(lotId).stream()
                .map(floor -> new ParkingStructureResponseDTO.Floor(floor.getId(),
                        floor.getFloorCode(), floor.getName(), floor.getSortOrder(),
                        zoneRepository.findByFloorIdOrderByZoneCodeAsc(floor.getId()).stream()
                                .map(zone -> new ParkingStructureResponseDTO.Zone(zone.getId(),
                                        zone.getZoneCode(), zone.getName(),
                                        slotRepository.countByZoneId(zone.getId())))
                                .toList()))
                .toList();
        return new ParkingStructureResponseDTO(toLot(lot), floors);
    }

    @Override
    @Transactional
    public ParkingStructureResponseDTO addFloor(UUID lotId, CreateFloorRequestDTO request) {
        requireLot(lotId);
        String code = request.floorCode().trim().toUpperCase(Locale.ROOT);
        if (floorRepository.findByParkingLotIdOrderBySortOrderAsc(lotId).stream()
                .anyMatch(floor -> floor.getFloorCode().equalsIgnoreCase(code))) {
            throw new ConflictException("Mã tầng đã tồn tại: " + code);
        }
        int order = floorRepository.findByParkingLotIdOrderBySortOrderAsc(lotId).size();
        ParkingFloor floor = floorRepository.save(ParkingFloor.builder().parkingLotId(lotId)
                .floorCode(code).name(request.name().trim()).sortOrder(order).build());
        createZones(floor, valueOrDefault(request.zoneCount(), 1),
                valueOrDefault(request.slotsPerZone(), 0));
        createDefaultLayout(lotId, floor.getId());
        return getStructure(lotId);
    }

    @Override
    @Transactional
    public ParkingStructureResponseDTO addZone(UUID floorId, CreateZoneRequestDTO request) {
        ParkingFloor floor = requireFloor(floorId);
        String code = request.zoneCode().trim().toUpperCase(Locale.ROOT);
        if (zoneRepository.findByFloorIdAndZoneCode(floorId, code).isPresent()) {
            throw new ConflictException("Mã khu đã tồn tại trong tầng: " + code);
        }
        ParkingZone zone = zoneRepository.save(ParkingZone.builder().floorId(floorId)
                .zoneCode(code).name(request.name().trim()).build());
        for (int i = 1; i <= request.initialSlots(); i++) {
            slotRepository.save(Slot.builder().slotCode(code + "%02d".formatted(i)).zone(code)
                    .zoneId(zone.getId()).status(SlotStatus.EMPTY)
                    .gridRow((i - 1) / 5).gridCol((i - 1) % 5).build());
        }
        refreshDraftWithMissingReferences(floorId);
        return getStructure(floor.getParkingLotId());
    }

    @Override
    @Transactional
    public ParkingStructureResponseDTO updateLot(UUID lotId, UpdateParkingLotRequestDTO request) {
        ParkingLot lot = requireLot(lotId);
        lot.setName(request.name().trim());
        lot.setAddress(request.address() == null ? null : request.address().trim());
        lotRepository.save(lot);
        return getStructure(lotId);
    }

    @Override
    @Transactional
    public void deleteLot(UUID lotId) {
        ParkingLot lot = requireLot(lotId);
        if ("DEFAULT".equalsIgnoreCase(lot.getLotCode())) {
            throw new ConflictException("Không thể xóa bãi mặc định của hệ thống");
        }
        List<ParkingFloor> floors = floorRepository.findByParkingLotIdOrderBySortOrderAsc(lotId);
        for (ParkingFloor floor : floors) {
            ensureFloorCanBeDeleted(floor);
        }
        for (ParkingFloor floor : floors) {
            deleteFloorData(floor);
        }
        lotRepository.delete(lot);
    }

    @Override
    @Transactional
    public ParkingStructureResponseDTO updateFloor(UUID floorId, UpdateNameRequestDTO request) {
        ParkingFloor floor = requireFloor(floorId);
        floor.setName(request.name().trim());
        floorRepository.save(floor);
        return getStructure(floor.getParkingLotId());
    }

    @Override
    @Transactional
    public void deleteFloor(UUID floorId) {
        ParkingFloor floor = requireFloor(floorId);
        if (floor.getSortOrder() == 0) {
            throw new ConflictException(
                    "Không thể xóa riêng mặt đất. Nếu không còn sử dụng bãi, hãy xóa toàn bộ bãi xe");
        }
        ensureFloorCanBeDeleted(floor);
        deleteFloorData(floor);
    }

    @Override
    @Transactional
    public ParkingStructureResponseDTO updateZone(UUID zoneId, UpdateNameRequestDTO request) {
        ParkingZone zone = requireZone(zoneId);
        ParkingFloor floor = requireFloor(zone.getFloorId());
        zone.setName(request.name().trim());
        zoneRepository.save(zone);
        renameZoneInDraft(floor.getId(), zone.getZoneCode(), zone.getName());
        return getStructure(floor.getParkingLotId());
    }

    @Override
    @Transactional
    public void deleteZone(UUID zoneId) {
        ParkingZone zone = requireZone(zoneId);
        ParkingFloor floor = requireFloor(zone.getFloorId());
        if (zoneRepository.findByFloorIdOrderByZoneCodeAsc(floor.getId()).size() <= 1) {
            throw new ConflictException(
                    "Không thể xóa khu cuối cùng của tầng. Hãy thêm khu khác hoặc xóa tầng");
        }
        List<Slot> slots = slotRepository.findByZoneIdOrderBySlotCodeAsc(zoneId);
        ensureSlotsCanBeDeleted(slots);
        removeLayoutReferences(floor.getId(),
                slots.stream().map(Slot::getId).collect(java.util.stream.Collectors.toSet()),
                zone.getZoneCode());
        slotRepository.deleteAll(slots);
        slotRepository.flush();
        zoneRepository.delete(zone);
    }

    @Override
    @Transactional
    public SlotResponseDTO addSlot(UUID zoneId, CreateZoneSlotRequestDTO request) {
        ParkingZone zone = requireZone(zoneId);
        String code = request.slotCode().trim().toUpperCase(Locale.ROOT);
        if (slotRepository.existsByZoneIdAndSlotCode(zoneId, code)) {
            throw new ConflictException("Mã ô đã tồn tại trong " + zone.getName() + ": " + code);
        }
        Slot slot = slotRepository.save(Slot.builder()
                .slotCode(code)
                .zone(zone.getZoneCode())
                .zoneId(zoneId)
                .status(SlotStatus.EMPTY)
                .build());
        reflowZoneSlots(zoneId);
        refreshDraftWithMissingReferences(zone.getFloorId());
        return toSlotResponse(slot);
    }

    @Override
    @Transactional
    public SlotResponseDTO updateSlotCode(UUID slotId, UpdateSlotCodeRequestDTO request) {
        Slot slot = requireSlot(slotId);
        String code = request.slotCode().trim().toUpperCase(Locale.ROOT);
        if (!code.equalsIgnoreCase(slot.getSlotCode())
                && slotRepository.existsByZoneIdAndSlotCode(slot.getZoneId(), code)) {
            throw new ConflictException("Mã ô đã tồn tại trong khu: " + code);
        }
        slot.setSlotCode(code);
        slotRepository.save(slot);
        reflowZoneSlots(slot.getZoneId());
        return toSlotResponse(slot);
    }

    @Override
    @Transactional
    public void deleteSlot(UUID slotId) {
        Slot slot = requireSlot(slotId);
        ensureSlotsCanBeDeleted(List.of(slot));
        ParkingZone zone = requireZone(slot.getZoneId());
        removeLayoutReferences(zone.getFloorId(), Set.of(slotId), null);
        slotRepository.delete(slot);
        slotRepository.flush();
        reflowZoneSlots(zone.getId());
    }

    @Override
    @Transactional
    public ParkingLayoutResponseDTO getDraft(UUID parkingLotId) {
        ParkingFloor floor = firstFloor(parkingLotId);
        return getFloorDraft(floor.getId());
    }

    @Override
    @Transactional
    public ParkingLayoutResponseDTO getFloorDraft(UUID floorId) {
        ParkingFloor floor = requireFloor(floorId);
        ParkingLayout layout = layoutRepository.findByFloorId(floorId)
                .orElseGet(() -> createDefaultLayout(floor.getParkingLotId(), floorId));
        if (readElements(layout.getDraftElements()).isEmpty()) {
            layout.setDraftElements(writeElements(buildDefaultElements(floorId)));
            layout = layoutRepository.save(layout);
        }
        return toResponse(layout);
    }

    @Override
    @Transactional
    public ParkingLayoutResponseDTO saveDraft(UUID parkingLotId, SaveParkingLayoutRequestDTO request) {
        return saveFloorDraft(firstFloor(parkingLotId).getId(), request);
    }

    @Override
    @Transactional
    public ParkingLayoutResponseDTO saveFloorDraft(UUID floorId, SaveParkingLayoutRequestDTO request) {
        ParkingFloor floor = requireFloor(floorId);
        ParkingLayout layout = layoutRepository.findByFloorId(floorId)
                .orElseGet(() -> createDefaultLayout(floor.getParkingLotId(), floorId));
        if (layout.getDraftVersion() != request.expectedVersion()) {
            throw new ConflictException("Sơ đồ đã được người khác cập nhật; hãy tải lại trước khi lưu");
        }
        validate(request, floorId);
        layout.setCanvasWidth(request.canvasWidth());
        layout.setCanvasHeight(request.canvasHeight());
        layout.setDraftElements(writeElements(request.elements()));
        layout.setDraftVersion(layout.getDraftVersion() + 1);
        return toResponse(layoutRepository.save(layout));
    }

    @Override
    @Transactional
    public ParkingLayoutResponseDTO publish(UUID parkingLotId) {
        return publishFloor(firstFloor(parkingLotId).getId());
    }

    @Override
    @Transactional
    public ParkingLayoutResponseDTO publishFloor(UUID floorId) {
        requireFloor(floorId);
        ParkingLayout layout = layoutRepository.findByFloorId(floorId)
                .orElseThrow(() -> new ResourceNotFoundException("Parking layout", floorId.toString()));
        List<SaveParkingLayoutRequestDTO.Element> elements = readElements(layout.getDraftElements());
        validate(new SaveParkingLayoutRequestDTO(layout.getDraftVersion(), layout.getCanvasWidth(),
                layout.getCanvasHeight(), elements), floorId);
        layout.setPublishedElements(layout.getDraftElements());
        layout.setPublishedVersion(layout.getDraftVersion());
        layout.setPublishedAt(OffsetDateTime.now());
        return toResponse(layoutRepository.save(layout));
    }

    private void validate(SaveParkingLayoutRequestDTO request, UUID floorId) {
        Set<UUID> ids = new HashSet<>();
        Set<UUID> slotRefs = new HashSet<>();
        Set<UUID> gateRefs = new HashSet<>();
        Set<UUID> zoneIds = zoneRepository.findByFloorIdOrderByZoneCodeAsc(floorId).stream()
                .map(ParkingZone::getId).collect(java.util.stream.Collectors.toSet());
        Set<UUID> existingSlots = slotRepository.findAll().stream()
                .filter(slot -> zoneIds.contains(slot.getZoneId())).map(Slot::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<UUID> existingGates = gateRepository.findByFloorId(floorId).stream()
                .map(Gate::getId).collect(java.util.stream.Collectors.toSet());
        for (var element : request.elements()) {
            if (!ids.add(element.id())) throw new IllegalArgumentException("Trùng mã phần tử trên sơ đồ");
            if (element.width() < 20 || element.height() < 20
                    || element.x() < 0 || element.y() < 0
                    || element.x() + element.width() > request.canvasWidth()
                    || element.y() + element.height() > request.canvasHeight()) {
                throw new IllegalArgumentException("Phần tử nằm ngoài phạm vi sơ đồ");
            }
            if (element.type() == SaveParkingLayoutRequestDTO.ElementType.SLOT) {
                if (element.referenceId() == null || !existingSlots.contains(element.referenceId())) {
                    throw new IllegalArgumentException("Slot trên sơ đồ không tồn tại");
                }
                if (!slotRefs.add(element.referenceId())) throw new IllegalArgumentException("Một slot xuất hiện hai lần");
            }
            if (element.type() == SaveParkingLayoutRequestDTO.ElementType.GATE) {
                if (element.referenceId() == null || !existingGates.contains(element.referenceId())) {
                    throw new IllegalArgumentException("Cổng trên sơ đồ không tồn tại");
                }
                if (!gateRefs.add(element.referenceId())) throw new IllegalArgumentException("Một cổng xuất hiện hai lần");
            }
        }
    }

    private List<SaveParkingLayoutRequestDTO.Element> buildDefaultElements(UUID floorId) {
        List<SaveParkingLayoutRequestDTO.Element> elements = new ArrayList<>();
        ParkingFloor floor = requireFloor(floorId);
        List<ParkingZone> zones = zoneRepository.findByFloorIdOrderByZoneCodeAsc(floorId);

        elements.add(layoutElement(SaveParkingLayoutRequestDTO.ElementType.ROAD, "Luồng xe chính",
                80, 320, 1120, 140, Map.of("kind", "MAIN_ROAD")));
        elements.add(layoutElement(SaveParkingLayoutRequestDTO.ElementType.LABEL,
                floor.getSortOrder() == 0 ? "NHÀ ĐIỀU HÀNH" : "SẢNH THANG MÁY",
                535, 345, 210, 90, Map.of("kind", "BOOTH")));
        elements.add(layoutElement(SaveParkingLayoutRequestDTO.ElementType.LABEL,
                "Hướng di chuyển  →", 490, 280, 300, 38, Map.of("kind", "DIRECTION")));

        if (floor.getSortOrder() > 0) {
            elements.add(layoutElement(SaveParkingLayoutRequestDTO.ElementType.ROAD, "DỐC LÊN ↑",
                    95, 332, 235, 115, Map.of("kind", "RAMP_UP")));
            elements.add(layoutElement(SaveParkingLayoutRequestDTO.ElementType.ROAD, "DỐC XUỐNG ↓",
                    950, 332, 235, 115, Map.of("kind", "RAMP_DOWN")));
        }

        int upperZoneCount = (zones.size() + 1) / 2;
        int lowerZoneCount = zones.size() / 2;
        for (int zoneIndex = 0; zoneIndex < zones.size(); zoneIndex++) {
            ParkingZone zone = zones.get(zoneIndex);
            boolean upper = zoneIndex % 2 == 0;
            int position = zoneIndex / 2;
            int rowZoneCount = Math.max(1, upper ? upperZoneCount : lowerZoneCount);
            int zoneWidth = Math.max(240, 1040 / rowZoneCount);
            int zoneX = 120 + position * zoneWidth;
            int zoneY = upper ? 25 : 490;
            int actualWidth = Math.min(zoneWidth - 20, 1020);

            elements.add(layoutElement(SaveParkingLayoutRequestDTO.ElementType.ZONE,
                    zone.getName(), zoneX, zoneY, actualWidth, 265,
                    Map.of("zoneCode", zone.getZoneCode())));

            List<Slot> zoneSlots = slotRepository.findAll().stream()
                    .filter(slot -> zone.getId().equals(slot.getZoneId()))
                    .sorted(Comparator.comparing(Slot::getSlotCode))
                    .toList();
            int columns = Math.max(1, (actualWidth - 30) / 52);
            for (int slotIndex = 0; slotIndex < zoneSlots.size(); slotIndex++) {
                Slot slot = zoneSlots.get(slotIndex);
                int col = slotIndex % columns;
                int row = slotIndex / columns;
                if (row > 2) break;
                elements.add(new SaveParkingLayoutRequestDTO.Element(UUID.randomUUID(),
                        SaveParkingLayoutRequestDTO.ElementType.SLOT, slot.getId(), slot.getSlotCode(),
                        zoneX + 18 + col * 52, zoneY + 46 + row * 72,
                        42, 62, upper ? 0 : 180, Map.of("zoneCode", zone.getZoneCode())));
            }
        }

        List<Gate> gates = gateRepository.findByFloorId(floorId).stream()
                .sorted(Comparator.comparing(Gate::getGateCode)).toList();
        for (int i = 0; i < gates.size(); i++) {
            Gate gate = gates.get(i);
            int y = gate.isHasBarrier() ? 338 : 405;
            int x = gate.getDirection() == GateDirection.IN ? 15 : 1155;
            elements.add(new SaveParkingLayoutRequestDTO.Element(UUID.randomUUID(),
                    SaveParkingLayoutRequestDTO.ElementType.GATE, gate.getId(), gate.getGateCode(),
                    x, y, 110, 56, 0,
                    Map.of("hasBarrier", Boolean.toString(gate.isHasBarrier()))));
            if (gate.isHasBarrier()) {
                elements.add(layoutElement(SaveParkingLayoutRequestDTO.ElementType.BARRIER,
                        "BARIE", gate.getDirection() == GateDirection.IN ? 75 : 1110,
                        y + 50, 115, 24, Map.of("direction", gate.getDirection().name())));
            }
        }
        return elements;
    }

    private ParkingLayout createDefaultLayout(UUID parkingLotId, UUID floorId) {
        return layoutRepository.save(ParkingLayout.builder()
                .parkingLotId(parkingLotId).floorId(floorId).canvasWidth(1280).canvasHeight(780)
                .draftElements(writeElements(buildDefaultElements(floorId)))
                .publishedElements(objectMapper.createArrayNode())
                .draftVersion(1).publishedVersion(0).build());
    }

    private SaveParkingLayoutRequestDTO.Element layoutElement(
            SaveParkingLayoutRequestDTO.ElementType type, String label,
            int x, int y, int width, int height, Map<String, String> properties) {
        return new SaveParkingLayoutRequestDTO.Element(UUID.randomUUID(), type, null, label,
                x, y, width, height, 0, properties);
    }

    private ParkingLot requireLot(UUID id) {
        return lotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking lot", id.toString()));
    }

    private ParkingFloor requireFloor(UUID id) {
        return floorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking floor", id.toString()));
    }

    private ParkingZone requireZone(UUID id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Parking zone", id.toString()));
    }

    private Slot requireSlot(UUID id) {
        return slotRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Slot", id.toString()));
    }

    private ParkingFloor firstFloor(UUID lotId) {
        requireLot(lotId);
        return floorRepository.findByParkingLotIdOrderBySortOrderAsc(lotId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Parking floor", lotId.toString()));
    }

    private ParkingLotResponseDTO toLot(ParkingLot lot) {
        return new ParkingLotResponseDTO(lot.getId(), lot.getLotCode(), lot.getName(),
                lot.getAddress(), lot.isActive());
    }

    private void createTemplateGate(ParkingLot lot, ParkingFloor floor, String code,
                                    GateDirection direction, boolean hasBarrier) {
        gateRepository.save(Gate.builder().gateCode(code).direction(direction)
                .status(hasBarrier ? GateStatus.CLOSED : GateStatus.OPEN)
                .hasBarrier(hasBarrier).parkingLotId(lot.getId()).floorId(floor.getId()).build());
    }

    private void createZones(ParkingFloor floor, int zoneCount, int slotsPerZone) {
        for (int zoneIndex = 0; zoneIndex < zoneCount; zoneIndex++) {
            String zoneCode = Character.toString((char) ('A' + zoneIndex));
            ParkingZone zone = zoneRepository.save(ParkingZone.builder()
                    .floorId(floor.getId()).zoneCode(zoneCode).name("Khu " + zoneCode).build());
            for (int slotIndex = 1; slotIndex <= slotsPerZone; slotIndex++) {
                slotRepository.save(Slot.builder()
                        .slotCode(zoneCode + "%02d".formatted(slotIndex))
                        .zone(zoneCode)
                        .zoneId(zone.getId())
                        .status(SlotStatus.EMPTY)
                        .gridRow((slotIndex - 1) / 10)
                        .gridCol((slotIndex - 1) % 10)
                        .build());
            }
        }
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private void ensureFloorCanBeDeleted(ParkingFloor floor) {
        List<ParkingZone> zones = zoneRepository.findByFloorIdOrderByZoneCodeAsc(floor.getId());
        List<Slot> slots = zones.stream()
                .flatMap(zone -> slotRepository.findByZoneIdOrderBySlotCodeAsc(zone.getId()).stream())
                .toList();
        ensureSlotsCanBeDeleted(slots);
        for (Gate gate : gateRepository.findByFloorId(floor.getId())) {
            if (sessionRepository.existsByEntryGateIdOrExitGateId(gate.getId(), gate.getId())
                    || gateLogRepository.existsByGateId(gate.getId())) {
                throw new ConflictException("Không thể xóa " + floor.getName()
                        + " vì cổng " + gate.getGateCode()
                        + " đã có lịch sử vận hành");
            }
        }
    }

    private void ensureSlotsCanBeDeleted(List<Slot> slots) {
        for (Slot slot : slots) {
            if (slot.getStatus() == SlotStatus.OCCUPIED) {
                throw new ConflictException("Không thể xóa ô " + slot.getSlotCode()
                        + " vì đang có xe đậu");
            }
            if (slot.getStatus() == SlotStatus.RESERVED) {
                throw new ConflictException("Không thể xóa ô " + slot.getSlotCode()
                        + " vì đã được khách đặt trước");
            }
            if (sessionRepository.existsBySlotId(slot.getId())) {
                throw new ConflictException("Không thể xóa ô " + slot.getSlotCode()
                        + " vì đã có lịch sử gửi xe. Hãy chuyển ô sang trạng thái tạm ngưng");
            }
            if (reservationRepository.existsBySlotId(slot.getId())) {
                throw new ConflictException("Không thể xóa ô " + slot.getSlotCode()
                        + " vì đã có lịch sử đặt chỗ. Hãy chuyển ô sang trạng thái tạm ngưng");
            }
        }
    }

    private void deleteFloorData(ParkingFloor floor) {
        List<ParkingZone> zones = zoneRepository.findByFloorIdOrderByZoneCodeAsc(floor.getId());
        List<Slot> slots = zones.stream()
                .flatMap(zone -> slotRepository.findByZoneIdOrderBySlotCodeAsc(zone.getId()).stream())
                .toList();
        layoutRepository.findByFloorId(floor.getId()).ifPresent(layoutRepository::delete);
        slotRepository.deleteAll(slots);
        slotRepository.flush();
        gateRepository.deleteAll(gateRepository.findByFloorId(floor.getId()));
        zoneRepository.deleteAll(zones);
        floorRepository.delete(floor);
    }

    private void renameZoneInDraft(UUID floorId, String zoneCode, String name) {
        ParkingLayout layout = layoutRepository.findByFloorId(floorId).orElse(null);
        if (layout == null) return;
        List<SaveParkingLayoutRequestDTO.Element> updated = readElements(layout.getDraftElements()).stream()
                .map(element -> element.type() == SaveParkingLayoutRequestDTO.ElementType.ZONE
                        && zoneCode.equals(element.properties().get("zoneCode"))
                        ? copyElement(element, element.referenceId(), name)
                        : element)
                .toList();
        layout.setDraftElements(writeElements(updated));
        layout.setDraftVersion(layout.getDraftVersion() + 1);
        layoutRepository.save(layout);
    }

    private void removeLayoutReferences(UUID floorId, Set<UUID> referenceIds, String zoneCode) {
        ParkingLayout layout = layoutRepository.findByFloorId(floorId).orElse(null);
        if (layout == null) return;
        java.util.function.Predicate<SaveParkingLayoutRequestDTO.Element> keep = element ->
                (element.referenceId() == null || !referenceIds.contains(element.referenceId()))
                        && !(zoneCode != null
                        && element.type() == SaveParkingLayoutRequestDTO.ElementType.ZONE
                        && zoneCode.equals(element.properties().get("zoneCode")));
        layout.setDraftElements(writeElements(readElements(layout.getDraftElements()).stream()
                .filter(keep).toList()));
        layout.setPublishedElements(writeElements(readElements(layout.getPublishedElements()).stream()
                .filter(keep).toList()));
        layout.setDraftVersion(layout.getDraftVersion() + 1);
        layoutRepository.save(layout);
    }

    private SaveParkingLayoutRequestDTO.Element copyElement(
            SaveParkingLayoutRequestDTO.Element element, UUID referenceId, String label) {
        return new SaveParkingLayoutRequestDTO.Element(element.id(), element.type(), referenceId,
                label, element.x(), element.y(), element.width(), element.height(),
                element.rotation(), element.properties());
    }

    private void reflowZoneSlots(UUID zoneId) {
        List<Slot> slots = slotRepository.findByZoneIdOrderBySlotCodeAsc(zoneId);
        for (Slot slot : slots) {
            slot.setGridRow(null);
            slot.setGridCol(null);
        }
        slotRepository.saveAll(slots);
        slotRepository.flush();
        for (int index = 0; index < slots.size(); index++) {
            slots.get(index).setGridRow(index / 10);
            slots.get(index).setGridCol(index % 10);
        }
        slotRepository.saveAll(slots);
    }

    private SlotResponseDTO toSlotResponse(Slot slot) {
        return new SlotResponseDTO(slot.getId(), slot.getSlotCode(), slot.getZone(),
                slot.getZoneId(), slot.getStatus(), slot.getCurrentSessionId(),
                slot.getGridRow(), slot.getGridCol());
    }

    private void refreshDraftWithMissingReferences(UUID floorId) {
        ParkingFloor floor = requireFloor(floorId);
        ParkingLayout layout = layoutRepository.findByFloorId(floorId)
                .orElseGet(() -> createDefaultLayout(floor.getParkingLotId(), floorId));
        List<SaveParkingLayoutRequestDTO.Element> current = new ArrayList<>(
                readElements(layout.getDraftElements()));
        Set<UUID> used = current.stream().map(SaveParkingLayoutRequestDTO.Element::referenceId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        buildDefaultElements(floorId).stream()
                .filter(element -> element.referenceId() != null && !used.contains(element.referenceId()))
                .forEach(current::add);
        layout.setDraftElements(writeElements(current));
        layout.setDraftVersion(layout.getDraftVersion() + 1);
        layoutRepository.save(layout);
    }

    private ParkingLayoutResponseDTO toResponse(ParkingLayout layout) {
        return new ParkingLayoutResponseDTO(layout.getParkingLotId(), layout.getFloorId(), layout.getCanvasWidth(),
                layout.getCanvasHeight(), layout.getDraftVersion(), layout.getPublishedVersion(),
                layout.getPublishedAt(), readElements(layout.getDraftElements()));
    }

    private JsonNode writeElements(List<SaveParkingLayoutRequestDTO.Element> elements) {
        return objectMapper.valueToTree(elements);
    }

    private List<SaveParkingLayoutRequestDTO.Element> readElements(JsonNode json) {
        return objectMapper.convertValue(json, new TypeReference<>() {});
    }
}
