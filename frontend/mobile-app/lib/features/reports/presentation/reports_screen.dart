import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/stat_card.dart';
import '../../../core/widgets/state_views.dart';
import '../../billing/data/billing_models.dart';

final _dailyProvider = FutureProvider<DailyReport>(
  (ref) => ref.watch(billingRepositoryProvider).getDailyReport(),
);

final _monthlyProvider = FutureProvider<MonthlyReport>(
  (ref) => ref.watch(billingRepositoryProvider).getMonthlyReport(),
);

class ReportsScreen extends ConsumerWidget {
  const ReportsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final daily = ref.watch(_dailyProvider);
    final monthly = ref.watch(_monthlyProvider);

    return RefreshIndicator(
      onRefresh: () async {
        ref.invalidate(_dailyProvider);
        ref.invalidate(_monthlyProvider);
      },
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Text('Báo cáo ngày',
              style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          AsyncView<DailyReport>(
            value: daily,
            onRetry: () => ref.invalidate(_dailyProvider),
            data: (d) => _DailySection(report: d),
          ),
          const SizedBox(height: 28),
          Text('Báo cáo tháng',
              style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 12),
          AsyncView<MonthlyReport>(
            value: monthly,
            onRetry: () => ref.invalidate(_monthlyProvider),
            data: (m) => _MonthlySection(report: m),
          ),
        ],
      ),
    );
  }
}

class _DailySection extends StatelessWidget {
  const _DailySection({required this.report});
  final DailyReport report;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 1.35,
          children: [
            StatCard(
                label: 'Doanh thu',
                value: Fmt.currency(report.totalRevenue),
                icon: Icons.payments_outlined,
                color: AppTheme.brandCyan),
            StatCard(
                label: 'Lượt xe',
                value: '${report.totalSessions}',
                icon: Icons.directions_car),
            StatCard(
                label: 'Cao điểm',
                value: '${report.peakSessions}',
                icon: Icons.trending_up,
                color: Theme.of(context).brightness == Brightness.dark
                    ? Colors.orange.shade400
                    : Colors.orange.shade800),
            StatCard(
                label: 'TG trung bình',
                value: Fmt.minutes(report.avgDurationMinutes),
                icon: Icons.timer_outlined,
                color: AppTheme.brandIndigo),
          ],
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(8, 20, 16, 12),
            child: SizedBox(
              height: 220,
              child: _RevenueBarChart(data: report.revenueByHour),
            ),
          ),
        ),
      ],
    );
  }
}

class _RevenueBarChart extends StatelessWidget {
  const _RevenueBarChart({required this.data});
  final List<HourRevenue> data;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (data.isEmpty) {
      return const EmptyView(message: 'Chưa có dữ liệu theo giờ');
    }
    final maxY = data
        .map((e) => e.revenue.toDouble())
        .reduce((a, b) => a > b ? a : b);
    return BarChart(
      BarChartData(
        maxY: maxY * 1.2,
        gridData: const FlGridData(show: false),
        borderData: FlBorderData(show: false),
        titlesData: FlTitlesData(
          leftTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          topTitles:
              const AxisTitles(sideTitles: SideTitles(showTitles: false)),
          bottomTitles: AxisTitles(
            sideTitles: SideTitles(
              showTitles: true,
              interval: 1,
              getTitlesWidget: (value, meta) {
                final idx = value.toInt();
                if (idx < 0 || idx >= data.length) {
                  return const SizedBox.shrink();
                }
                // Chỉ hiển thị mỗi 3 giờ cho đỡ rối.
                if (data[idx].hour % 3 != 0) return const SizedBox.shrink();
                return Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text('${data[idx].hour}h',
                      style: theme.textTheme.bodySmall),
                );
              },
            ),
          ),
        ),
        barGroups: [
          for (var i = 0; i < data.length; i++)
            BarChartGroupData(
              x: i,
              barRods: [
                BarChartRodData(
                  toY: data[i].revenue.toDouble(),
                  gradient: const LinearGradient(
                    begin: Alignment.bottomCenter,
                    end: Alignment.topCenter,
                    colors: [AppTheme.brandIndigo, AppTheme.brandCyan],
                  ),
                  width: 8,
                  borderRadius: BorderRadius.circular(3),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

class _MonthlySection extends StatelessWidget {
  const _MonthlySection({required this.report});
  final MonthlyReport report;

  @override
  Widget build(BuildContext context) {
    final growthPositive = report.growthRate >= 0;
    return Column(
      children: [
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 1.35,
          children: [
            StatCard(
                label: 'Doanh thu tháng',
                value: Fmt.currency(report.totalRevenue),
                icon: Icons.account_balance_wallet_outlined,
                color: AppTheme.brandCyan),
            StatCard(
              label: 'Tăng trưởng',
              value: '${growthPositive ? '+' : ''}'
                  '${(report.growthRate * 100).toStringAsFixed(1)}%',
              icon: growthPositive
                  ? Icons.arrow_upward
                  : Icons.arrow_downward,
              color: growthPositive
                  ? (Theme.of(context).brightness == Brightness.dark
                      ? Colors.green.shade400
                      : Colors.green.shade700)
                  : Theme.of(context).colorScheme.error,
            ),
            StatCard(
                label: 'Lượt xe',
                value: '${report.totalSessions}',
                icon: Icons.directions_car),
            StatCard(
                label: 'TB ngày',
                value: Fmt.currency(report.avgDailyRevenue),
                icon: Icons.calendar_today_outlined,
                color: AppTheme.brandIndigo),
          ],
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(8, 20, 16, 12),
            child: SizedBox(
              height: 220,
              child: _RevenueLineChart(data: report.revenueByDay),
            ),
          ),
        ),
      ],
    );
  }
}

class _RevenueLineChart extends StatelessWidget {
  const _RevenueLineChart({required this.data});
  final List<DayRevenue> data;

  @override
  Widget build(BuildContext context) {
    if (data.isEmpty) {
      return const EmptyView(message: 'Chưa có dữ liệu theo ngày');
    }
    return LineChart(
      LineChartData(
        gridData: const FlGridData(show: true, drawVerticalLine: false),
        borderData: FlBorderData(show: false),
        titlesData: const FlTitlesData(
          leftTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
          rightTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
          topTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
          bottomTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
        ),
        lineBarsData: [
          LineChartBarData(
            isCurved: true,
            gradient: const LinearGradient(
              colors: [AppTheme.brandIndigo, AppTheme.brandCyan],
            ),
            barWidth: 3,
            dotData: const FlDotData(show: false),
            belowBarData: BarAreaData(
              show: true,
              gradient: LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  AppTheme.brandIndigo.withValues(alpha: 0.18),
                  AppTheme.brandCyan.withValues(alpha: 0.02),
                ],
              ),
            ),
            spots: [
              for (var i = 0; i < data.length; i++)
                FlSpot(i.toDouble(), data[i].revenue.toDouble()),
            ],
          ),
        ],
      ),
    );
  }
}
