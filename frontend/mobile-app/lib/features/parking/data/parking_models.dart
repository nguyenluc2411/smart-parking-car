// Models khớp 100% docs/api-contracts.md (parking-service :8081).
// Không tự thêm field ngoài contract.

class SlotAvailability {
  final int totalSlots;
  final int occupiedSlots;
  final int emptySlots;
  final int maintenanceSlots;
  final double occupancyRate;

  const SlotAvailability({
    required this.totalSlots,
    required this.occupiedSlots,
    required this.emptySlots,
    required this.maintenanceSlots,
    required this.occupancyRate,
  });

  factory SlotAvailability.fromJson(Map<String, dynamic> j) => SlotAvailability(
        totalSlots: j['totalSlots'] as int,
        occupiedSlots: j['occupiedSlots'] as int,
        emptySlots: j['emptySlots'] as int,
        maintenanceSlots: j['maintenanceSlots'] as int,
        occupancyRate: (j['occupancyRate'] as num).toDouble(),
      );
}

class SessionSummary {
  final String id;
  final String plateNumber;
  final String? slotCode;
  final String entryTime;
  final String? exitTime;
  final int? durationSeconds;
  final String status;

  const SessionSummary({
    required this.id,
    required this.plateNumber,
    required this.slotCode,
    required this.entryTime,
    required this.exitTime,
    required this.durationSeconds,
    required this.status,
  });

  factory SessionSummary.fromJson(Map<String, dynamic> j) => SessionSummary(
        id: j['id'] as String,
        plateNumber: j['plateNumber'] as String,
        slotCode: j['slotCode'] as String?,
        entryTime: j['entryTime'] as String,
        exitTime: j['exitTime'] as String?,
        durationSeconds: j['durationSeconds'] as int?,
        status: j['status'] as String,
      );
}

class SlotRef {
  final String id;
  final String slotCode;
  final String zone;
  const SlotRef({required this.id, required this.slotCode, required this.zone});

  factory SlotRef.fromJson(Map<String, dynamic> j) => SlotRef(
        id: j['id'] as String,
        slotCode: j['slotCode'] as String,
        zone: j['zone'] as String,
      );
}

class Slot {
  final String id;
  final String slotCode;
  final String zone;
  final String status; // EMPTY | OCCUPIED | MAINTENANCE
  final String? currentSessionId;

  const Slot({
    required this.id,
    required this.slotCode,
    required this.zone,
    required this.status,
    required this.currentSessionId,
  });

  factory Slot.fromJson(Map<String, dynamic> j) => Slot(
        id: j['id'] as String,
        slotCode: j['slotCode'] as String,
        zone: j['zone'] as String,
        status: j['status'] as String,
        currentSessionId: j['currentSessionId'] as String?,
      );
}

class GateRef {
  final String id;
  final String gateCode;
  const GateRef({required this.id, required this.gateCode});

  factory GateRef.fromJson(Map<String, dynamic> j) => GateRef(
        id: j['id'] as String,
        gateCode: j['gateCode'] as String,
      );
}

class SessionDetail {
  final String id;
  final String plateNumber;
  final SlotRef? slot;
  final GateRef? entryGate;
  final GateRef? exitGate;
  final String entryTime;
  final String? exitTime;
  final int? durationSeconds;
  final String status;
  final String? entryImageUrl;
  final String? exitImageUrl;

  const SessionDetail({
    required this.id,
    required this.plateNumber,
    required this.slot,
    required this.entryGate,
    required this.exitGate,
    required this.entryTime,
    required this.exitTime,
    required this.durationSeconds,
    required this.status,
    required this.entryImageUrl,
    required this.exitImageUrl,
  });

  factory SessionDetail.fromJson(Map<String, dynamic> j) => SessionDetail(
        id: j['id'] as String,
        plateNumber: j['plateNumber'] as String,
        slot: j['slot'] == null
            ? null
            : SlotRef.fromJson(j['slot'] as Map<String, dynamic>),
        entryGate: j['entryGate'] == null
            ? null
            : GateRef.fromJson(j['entryGate'] as Map<String, dynamic>),
        exitGate: j['exitGate'] == null
            ? null
            : GateRef.fromJson(j['exitGate'] as Map<String, dynamic>),
        entryTime: j['entryTime'] as String,
        exitTime: j['exitTime'] as String?,
        durationSeconds: j['durationSeconds'] as int?,
        status: j['status'] as String,
        entryImageUrl: j['entryImageUrl'] as String?,
        exitImageUrl: j['exitImageUrl'] as String?,
      );
}

class Gate {
  final String id;
  final String gateCode;
  final String direction;
  final String status;
  final String? lastCommand;
  final String? lastCommandAt;

  const Gate({
    required this.id,
    required this.gateCode,
    required this.direction,
    required this.status,
    required this.lastCommand,
    required this.lastCommandAt,
  });

  factory Gate.fromJson(Map<String, dynamic> j) => Gate(
        id: j['id'] as String,
        gateCode: j['gateCode'] as String,
        direction: j['direction'] as String,
        status: j['status'] as String,
        lastCommand: j['lastCommand'] as String?,
        lastCommandAt: j['lastCommandAt'] as String?,
      );
}

class Vehicle {
  final String id;
  final String plateNumber;
  final String vehicleType; // WHITELIST | BLACKLIST
  final String? ownerName;
  final String? note;
  final String createdAt;
  // Set only on a POST that moved a plate between lists (e.g. 'WHITELIST'); null otherwise.
  final String? reclassifiedFrom;

  const Vehicle({
    required this.id,
    required this.plateNumber,
    required this.vehicleType,
    required this.ownerName,
    required this.note,
    required this.createdAt,
    this.reclassifiedFrom,
  });

  factory Vehicle.fromJson(Map<String, dynamic> j) => Vehicle(
        id: j['id'] as String,
        plateNumber: j['plateNumber'] as String,
        vehicleType: j['vehicleType'] as String,
        ownerName: j['ownerName'] as String?,
        note: j['note'] as String?,
        createdAt: j['createdAt'] as String,
        reclassifiedFrom: j['reclassifiedFrom'] as String?,
      );
}

class Alert {
  final String id;
  final String alertType; // DUPLICATE_ACTIVE_ENTRY | BLACKLIST_HIT | UNMATCHED_EXIT | LOW_CONFIDENCE
  final String severity; // CRITICAL | WARNING
  final String? plateNumber;
  final String? gateId;
  final String? sessionId;
  final String? imageUrl; // presigned URL of the captured frame
  final String message;
  final String status; // NEW | ACKNOWLEDGED
  final String createdAt;

  const Alert({
    required this.id,
    required this.alertType,
    required this.severity,
    required this.plateNumber,
    required this.gateId,
    required this.sessionId,
    required this.imageUrl,
    required this.message,
    required this.status,
    required this.createdAt,
  });

  bool get isCritical => severity == 'CRITICAL';

  factory Alert.fromJson(Map<String, dynamic> j) => Alert(
        id: j['id'] as String,
        alertType: j['alertType'] as String,
        severity: j['severity'] as String,
        plateNumber: j['plateNumber'] as String?,
        gateId: j['gateId'] as String?,
        sessionId: j['sessionId'] as String?,
        imageUrl: j['imageUrl'] as String?,
        message: j['message'] as String,
        status: j['status'] as String,
        createdAt: j['createdAt'] as String,
      );
}
