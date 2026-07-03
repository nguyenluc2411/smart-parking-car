import 'dart:convert';

import 'package:dio/dio.dart';

import '../../../core/network/api_response.dart';
import '../../../core/network/repo_helpers.dart';
import 'parking_models.dart';

/// Truy cập parking-service (:8081). Chỉ dùng endpoint trong api-contracts.md.
class ParkingRepository {
  ParkingRepository(this._dio);

  final Dio _dio;

  Future<SlotAvailability> getAvailability() => apiCall(
        () => _dio.get('/slots/availability'),
        (data) => SlotAvailability.fromJson(data as Map<String, dynamic>),
      );

  Future<PageResult<SessionSummary>> getSessions({
    String? status,
    String? date,
    String? plate,
    int page = 0,
    int size = 20,
  }) {
    final query = <String, dynamic>{'page': page, 'size': size};
    if (status != null && status.isNotEmpty) query['status'] = status;
    if (date != null && date.isNotEmpty) query['date'] = date;
    if (plate != null && plate.isNotEmpty) query['plate'] = plate;
    return apiCall(
      () => _dio.get('/sessions', queryParameters: query),
      (data) => PageResult.fromJson(
        data as Map<String, dynamic>,
        SessionSummary.fromJson,
      ),
    );
  }

  Future<SessionDetail> getSession(String id) => apiCall(
        () => _dio.get('/sessions/$id'),
        (data) => SessionDetail.fromJson(data as Map<String, dynamic>),
      );

  Future<List<Gate>> getGates() => apiCall(
        () => _dio.get('/gates'),
        (data) => (data as List<dynamic>)
            .map((e) => Gate.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );

  /// POST /gates/{id}/override (ADMIN). command ∈ OPEN | CLOSE.
  Future<void> overrideGate({
    required String id,
    required String command,
    required String reason,
  }) =>
      apiVoid(() => _dio.post(
            '/gates/$id/override',
            data: {'command': command, 'reason': reason},
          ));

  Future<List<Vehicle>> getWhitelist() => apiCall(
        () => _dio.get('/vehicles/whitelist'),
        (data) => (data as List<dynamic>)
            .map((e) => Vehicle.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );

  Future<Vehicle> addWhitelist({
    required String plateNumber,
    String? ownerName,
    String? note,
    bool force = false, // confirm moving a plate already in the blacklist
  }) =>
      apiCall(
        () => _dio.post('/vehicles/whitelist', data: {
          'plateNumber': plateNumber,
          'ownerName': ownerName,
          'note': note,
          'force': force,
        }),
        (data) => Vehicle.fromJson(data as Map<String, dynamic>),
      );

  Future<void> deleteWhitelist(String plate) =>
      apiVoid(() => _dio.delete('/vehicles/whitelist/$plate'));

  Future<List<Vehicle>> getBlacklist() => apiCall(
        () => _dio.get('/vehicles/blacklist'),
        (data) => (data as List<dynamic>)
            .map((e) => Vehicle.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );

  Future<Vehicle> addBlacklist({
    required String plateNumber,
    String? ownerName,
    String? note,
    bool force = false, // confirm moving a plate already in the whitelist
  }) =>
      apiCall(
        () => _dio.post('/vehicles/blacklist', data: {
          'plateNumber': plateNumber,
          'ownerName': ownerName,
          'note': note,
          'force': force,
        }),
        (data) => Vehicle.fromJson(data as Map<String, dynamic>),
      );

  Future<void> deleteBlacklist(String plate) =>
      apiVoid(() => _dio.delete('/vehicles/blacklist/$plate'));

  // ---- Alerts (BR-007) ----
  Future<List<Alert>> getAlerts({String? status}) => apiCall(
        () => _dio.get('/alerts', queryParameters: {'status': ?status}),
        (data) => ((data as Map<String, dynamic>)['content'] as List<dynamic>)
            .map((e) => Alert.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );

  Future<Alert> ackAlert(String id) => apiCall(
        () => _dio.post('/alerts/$id/ack'),
        (data) => Alert.fromJson(data as Map<String, dynamic>),
      );

  /// Live alert stream over Server-Sent Events. Reconnects with a short backoff; the caller
  /// (a StreamProvider) cancels the subscription on dispose. `receiveTimeout` is disabled because
  /// the connection is long-lived.
  Stream<Alert> streamAlerts() async* {
    while (true) {
      try {
        final response = await _dio.get<ResponseBody>(
          '/alerts/stream',
          options: Options(
            responseType: ResponseType.stream,
            receiveTimeout: Duration.zero,
            headers: {'Accept': 'text/event-stream'},
          ),
        );
        var buffer = '';
        await for (final chunk in response.data!.stream) {
          buffer += utf8.decode(chunk, allowMalformed: true);
          final frames = buffer.split('\n\n');
          buffer = frames.removeLast(); // keep trailing partial frame
          for (final frame in frames) {
            final line = frame
                .split('\n')
                .firstWhere((l) => l.startsWith('data:'), orElse: () => '');
            if (line.isEmpty) continue;
            try {
              yield Alert.fromJson(
                  jsonDecode(line.substring(5).trim()) as Map<String, dynamic>);
            } catch (_) {
              /* skip a malformed frame */
            }
          }
        }
      } catch (_) {
        /* network drop — fall through to reconnect */
      }
      await Future<void>.delayed(const Duration(seconds: 3));
    }
  }

  // ---- Slot setup (ADMIN) ----
  Future<List<Slot>> getSlots() => apiCall(
        () => _dio.get('/slots'),
        (data) => (data as List<dynamic>)
            .map((e) => Slot.fromJson(e as Map<String, dynamic>))
            .toList(growable: false),
      );

  Future<Slot> createSlot({required String slotCode, required String zone}) =>
      apiCall(
        () => _dio.post('/slots', data: {'slotCode': slotCode, 'zone': zone}),
        (data) => Slot.fromJson(data as Map<String, dynamic>),
      );

  Future<void> deleteSlot(String id) =>
      apiVoid(() => _dio.delete('/slots/$id'));

  Future<Slot> updateSlotStatus({required String id, required String status}) =>
      apiCall(
        () => _dio.patch('/slots/$id/status', data: {'status': status}),
        (data) => Slot.fromJson(data as Map<String, dynamic>),
      );

  /// Cấu hình nhanh: đặt khu [zone] có đúng [count] slot. Trả về `data` thô (created/removed/total).
  Future<Map<String, dynamic>> provisionZone({
    required String zone,
    required int count,
  }) =>
      apiCall(
        () => _dio.post('/slots/provision', data: {'zone': zone, 'count': count}),
        (data) => Map<String, dynamic>.from(data as Map),
      );
}
