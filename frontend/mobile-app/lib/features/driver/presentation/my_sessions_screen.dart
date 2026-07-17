import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_response.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../../parking/data/parking_models.dart';

/// Phiên gửi xe của các biển đã duyệt của tài xế (parking GET /driver/sessions).
final driverSessionsProvider =
    FutureProvider.autoDispose<PageResult<SessionSummary>>((ref) {
  return ref.watch(driverRepositoryProvider).mySessions();
});

class MySessionsScreen extends ConsumerWidget {
  const MySessionsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(driverSessionsProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(driverSessionsProvider),
      child: AsyncView<PageResult<SessionSummary>>(
        value: async,
        onRetry: () => ref.invalidate(driverSessionsProvider),
        data: (page) {
          if (page.content.isEmpty) {
            return ListView(
              children: const [
                SizedBox(height: 120),
                EmptyView(
                    message: 'Chưa có phiên gửi xe',
                    icon: Icons.local_parking_outlined),
              ],
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(12),
            itemCount: page.content.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (_, i) => _SessionCard(session: page.content[i]),
          );
        },
      ),
    );
  }
}

class _SessionCard extends StatelessWidget {
  const _SessionCard({required this.session});

  final SessionSummary session;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: const CircleAvatar(child: Icon(Icons.directions_car)),
        title: Text(session.plateNumber,
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Vào: ${Fmt.dateTime(session.entryTime)}'),
            Text(session.exitTime == null
                ? 'Đang gửi${session.slotCode == null ? '' : ' · ô ${session.slotCode}'}'
                : 'Ra: ${Fmt.dateTime(session.exitTime)} · ${Fmt.duration(session.durationSeconds)}'),
          ],
        ),
        trailing: StatusChip(
          label: Fmt.sessionStatus(session.status),
          color: statusColor(context, session.status),
        ),
        isThreeLine: true,
      ),
    );
  }
}
