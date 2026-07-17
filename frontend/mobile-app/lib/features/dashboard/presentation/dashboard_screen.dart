import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/stat_card.dart';
import '../../../core/widgets/state_views.dart';
import '../../billing/data/billing_models.dart';
import '../../parking/data/parking_models.dart';

final _availabilityProvider = FutureProvider<SlotAvailability>(
  (ref) => ref.watch(parkingRepositoryProvider).getAvailability(),
);

/// Đếm phiên ACTIVE qua totalElements của trang đầu.
final _activeSessionsProvider = FutureProvider<int>(
  (ref) async {
    final page = await ref
        .watch(parkingRepositoryProvider)
        .getSessions(status: 'ACTIVE', size: 1);
    return page.totalElements;
  },
);

final _dailyReportProvider = FutureProvider<DailyReport>(
  (ref) => ref.watch(billingRepositoryProvider).getDailyReport(),
);

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final availability = ref.watch(_availabilityProvider);
    final activeSessions = ref.watch(_activeSessionsProvider);
    final daily = ref.watch(_dailyReportProvider);

    Future<void> refresh() async {
      ref.invalidate(_availabilityProvider);
      ref.invalidate(_activeSessionsProvider);
      ref.invalidate(_dailyReportProvider);
    }

    return RefreshIndicator(
      onRefresh: refresh,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          AsyncView<SlotAvailability>(
            value: availability,
            onRetry: () => ref.invalidate(_availabilityProvider),
            data: (a) => _OccupancyCard(availability: a),
          ),
          const SizedBox(height: 12),
          GridView.count(
            crossAxisCount: 2,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 12,
            crossAxisSpacing: 12,
            childAspectRatio: 1.25,
            children: [
              availability.maybeWhen(
                data: (a) => StatCard(
                  label: 'Chỗ trống',
                  value: '${a.emptySlots}',
                  icon: Icons.local_parking,
                  color: Theme.of(context).brightness == Brightness.dark
                      ? Colors.green.shade400
                      : Colors.green.shade700,
                ),
                orElse: () => const _LoadingCard(),
              ),
              activeSessions.maybeWhen(
                data: (n) => StatCard(
                  label: 'Phiên đang hoạt động',
                  value: '$n',
                  icon: Icons.directions_car,
                ),
                orElse: () => const _LoadingCard(),
              ),
              daily.maybeWhen(
                data: (d) => StatCard(
                  label: 'Doanh thu hôm nay',
                  value: Fmt.currency(d.totalRevenue),
                  icon: Icons.payments_outlined,
                  color: AppTheme.brandCyan,
                ),
                orElse: () => const _LoadingCard(),
              ),
              daily.maybeWhen(
                data: (d) => StatCard(
                  label: 'Lượt xe hôm nay',
                  value: '${d.totalSessions}',
                  icon: Icons.confirmation_number_outlined,
                  color: AppTheme.brandIndigo,
                  sub: '${d.peakSessions} giờ cao điểm',
                ),
                orElse: () => const _LoadingCard(),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _OccupancyCard extends StatelessWidget {
  const _OccupancyCard({required this.availability});
  final SlotAvailability availability;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final a = availability;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Tỉ lệ lấp đầy', style: theme.textTheme.titleMedium),
            const SizedBox(height: 16),
            Row(
              children: [
                SizedBox(
                  width: 90,
                  height: 90,
                  child: Stack(
                    alignment: Alignment.center,
                    children: [
                      SizedBox(
                        width: 90,
                        height: 90,
                        child: CircularProgressIndicator(
                          value: a.occupancyRate.clamp(0, 1).toDouble(),
                          strokeWidth: 10,
                          backgroundColor:
                              theme.colorScheme.surfaceContainerHighest,
                        ),
                      ),
                      Text(Fmt.percent(a.occupancyRate),
                          style: theme.textTheme.titleMedium
                              ?.copyWith(fontWeight: FontWeight.bold)),
                    ],
                  ),
                ),
                const SizedBox(width: 24),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      _row(context, 'Tổng chỗ', '${a.totalSlots}'),
                      _row(context, 'Đang dùng', '${a.occupiedSlots}'),
                      _row(context, 'Còn trống', '${a.emptySlots}'),
                      _row(context, 'Bảo trì', '${a.maintenanceSlots}'),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _row(BuildContext context, String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(k,
                style: TextStyle(color: Theme.of(context).colorScheme.outline)),
            Text(v, style: const TextStyle(fontWeight: FontWeight.w600)),
          ],
        ),
      );
}

class _LoadingCard extends StatelessWidget {
  const _LoadingCard();
  @override
  Widget build(BuildContext context) => const Card(
        child: Center(
          child: SizedBox(
            height: 22,
            width: 22,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
        ),
      );
}
