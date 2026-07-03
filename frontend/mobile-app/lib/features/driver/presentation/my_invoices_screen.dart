import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/api_response.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../../billing/data/billing_models.dart';

/// Hoá đơn của các biển đã duyệt của tài xế (billing GET /driver/invoices).
final driverInvoicesProvider =
    FutureProvider.autoDispose<PageResult<Invoice>>((ref) {
  return ref.watch(driverRepositoryProvider).myInvoices();
});

class MyInvoicesScreen extends ConsumerWidget {
  const MyInvoicesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(driverInvoicesProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(driverInvoicesProvider),
      child: AsyncView<PageResult<Invoice>>(
        value: async,
        onRetry: () => ref.invalidate(driverInvoicesProvider),
        data: (page) {
          if (page.content.isEmpty) {
            return ListView(
              children: const [
                SizedBox(height: 120),
                EmptyView(
                    message: 'Chưa có hoá đơn',
                    icon: Icons.receipt_long_outlined),
              ],
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(12),
            itemCount: page.content.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (_, i) => _InvoiceCard(invoice: page.content[i]),
          );
        },
      ),
    );
  }
}

class _InvoiceCard extends ConsumerWidget {
  const _InvoiceCard({required this.invoice});

  final Invoice invoice;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isPending = invoice.status.toUpperCase() == 'PENDING';
    return Card(
      child: ListTile(
        leading: const CircleAvatar(child: Icon(Icons.receipt_long)),
        title: Text(Fmt.currency(invoice.amount),
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${invoice.plateNumber} · ${Fmt.minutes(invoice.durationMinutes)}'),
            Text('Ra: ${Fmt.dateTime(invoice.exitTime)}'),
          ],
        ),
        trailing: StatusChip(
          label: Fmt.invoiceStatus(invoice.status),
          color: statusColor(context, invoice.status),
        ),
        isThreeLine: true,
        onTap: isPending ? () => _confirmPay(context, ref) : null,
      ),
    );
  }

  Future<void> _confirmPay(BuildContext context, WidgetRef ref) async {
    final messenger = ScaffoldMessenger.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Thanh toán online'),
        content: Text(
            'Thanh toán hoá đơn ${invoice.plateNumber}\nSố tiền: ${Fmt.currency(invoice.amount)}?'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: const Text('Huỷ')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, true),
              child: const Text('Thanh toán')),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      final result =
          await ref.read(driverRepositoryProvider).payInvoice(invoice.invoiceId);
      ref.invalidate(driverInvoicesProvider);
      messenger.showSnackBar(SnackBar(
          content: Text(
              'Thanh toán thành công (${Fmt.invoiceStatus(result.status)})')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text('Lỗi: ${e.message}')));
    } catch (_) {
      messenger.showSnackBar(
          const SnackBar(content: Text('Thanh toán thất bại, thử lại sau.')));
    }
  }
}
