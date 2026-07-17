import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/api_response.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../data/billing_models.dart';

typedef InvoiceFilter = ({String? status, String? plate});

final invoicesProvider =
    FutureProvider.family<PageResult<Invoice>, InvoiceFilter>(
  (ref, filter) => ref.watch(billingRepositoryProvider).listInvoices(
        status: filter.status,
        plate: filter.plate,
      ),
);

const _statuses = ['PENDING', 'PAID', 'WAIVED'];
const _payMethods = ['CASH', 'TRANSFER', 'EWALLET'];

class BillingScreen extends ConsumerStatefulWidget {
  const BillingScreen({super.key, this.initialSessionId});
  final String? initialSessionId;

  @override
  ConsumerState<BillingScreen> createState() => _BillingScreenState();
}

class _BillingScreenState extends ConsumerState<BillingScreen> {
  String? _status;
  String _plate = '';
  final _searchCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    // Deep-link cũ: nếu mở kèm sessionId, nạp đúng hóa đơn đó rồi mở chi tiết.
    final sid = widget.initialSessionId;
    if (sid != null && sid.isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _openBySession(sid));
    }
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  InvoiceFilter get _filter =>
      (status: _status, plate: _plate.isEmpty ? null : _plate);

  Future<void> _openBySession(String sessionId) async {
    try {
      final inv = await ref
          .read(billingRepositoryProvider)
          .getInvoiceBySession(sessionId);
      if (mounted) _openDetail(inv);
    } on ApiException catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(e.message)));
      }
    }
  }

  Future<void> _openDetail(Invoice invoice) async {
    final paid = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _InvoiceDetailSheet(invoice: invoice),
    );
    if (paid == true) ref.invalidate(invoicesProvider(_filter));
  }

  @override
  Widget build(BuildContext context) {
    final invoices = ref.watch(invoicesProvider(_filter));

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: TextField(
            controller: _searchCtrl,
            decoration: InputDecoration(
              hintText: 'Tìm theo biển số…',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: _plate.isEmpty
                  ? null
                  : IconButton(
                      icon: const Icon(Icons.clear),
                      onPressed: () {
                        _searchCtrl.clear();
                        setState(() => _plate = '');
                      },
                    ),
            ),
            onSubmitted: (v) => setState(() => _plate = v.trim()),
          ),
        ),
        SizedBox(
          height: 44,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            children: [
              _filterChip('Tất cả', null),
              for (final s in _statuses) _filterChip(Fmt.invoiceStatus(s), s),
            ],
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          child: RefreshIndicator(
            onRefresh: () async => ref.invalidate(invoicesProvider(_filter)),
            child: AsyncView<PageResult<Invoice>>(
              value: invoices,
              onRetry: () => ref.invalidate(invoicesProvider(_filter)),
              data: (page) {
                if (page.content.isEmpty) {
                  return const EmptyView(
                    message: 'Không có hóa đơn nào',
                    icon: Icons.receipt_long_outlined,
                  );
                }
                return ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: page.content.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (_, i) => _InvoiceTile(
                    invoice: page.content[i],
                    onTap: () => _openDetail(page.content[i]),
                  ),
                );
              },
            ),
          ),
        ),
      ],
    );
  }

  Widget _filterChip(String label, String? value) {
    final selected = _status == value;
    return Padding(
      padding: const EdgeInsets.only(right: 8),
      child: FilterChip(
        label: Text(label),
        selected: selected,
        onSelected: (_) => setState(() => _status = value),
      ),
    );
  }
}

class _InvoiceTile extends StatelessWidget {
  const _InvoiceTile({required this.invoice, required this.onTap});
  final Invoice invoice;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = statusColor(context, invoice.status);
    return Card(
      child: ListTile(
        onTap: onTap,
        leading: CircleAvatar(
          backgroundColor: color.withValues(alpha: 0.15),
          child: Icon(Icons.receipt_long, color: color),
        ),
        title: Text(invoice.plateNumber,
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(
          'Ra ${Fmt.dateTime(invoice.exitTime)} · ${Fmt.minutes(invoice.durationMinutes)}',
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(Fmt.currency(invoice.amount),
                style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            StatusChip(
                label: Fmt.invoiceStatus(invoice.status), color: color),
          ],
        ),
      ),
    );
  }
}

class _InvoiceDetailSheet extends ConsumerWidget {
  const _InvoiceDetailSheet({required this.invoice});
  final Invoice invoice;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final status = invoice.status.toUpperCase();
    final isPayable = status == 'PENDING';
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 4, 20, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(invoice.plateNumber,
                  style: theme.textTheme.titleLarge
                      ?.copyWith(fontWeight: FontWeight.bold)),
              StatusChip(
                  label: Fmt.invoiceStatus(invoice.status),
                  color: statusColor(context, invoice.status)),
            ],
          ),
          const Divider(height: 28),
          _kv(context, 'Mã hóa đơn', invoice.invoiceId),
          _kv(context, 'Giờ vào', Fmt.dateTime(invoice.entryTime)),
          _kv(context, 'Giờ ra', Fmt.dateTime(invoice.exitTime)),
          _kv(context, 'Thời lượng', Fmt.minutes(invoice.durationMinutes)),
          _kv(context, 'Đơn giá/phút', Fmt.currency(invoice.ratePerMin.round())),
          _kv(context, 'Giờ cao điểm', invoice.peakApplied ? 'Có' : 'Không'),
          _kv(context, 'Qua đêm', invoice.overnightApplied ? 'Có' : 'Không'),
          const Divider(height: 28),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Tổng tiền', style: theme.textTheme.titleMedium),
              Text(Fmt.currency(invoice.amount),
                  style: theme.textTheme.headlineSmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: theme.colorScheme.primary)),
            ],
          ),
          const SizedBox(height: 16),
          FilledButton.icon(
            onPressed: isPayable ? () => _pay(context, ref) : null,
            icon: Icon(isPayable ? Icons.payments : Icons.check_circle),
            label: Text(isPayable
                ? 'Thanh toán'
                : (status == 'WAIVED' ? 'Miễn phí' : 'Đã thanh toán')),
          ),
        ],
      ),
    );
  }

  Future<void> _pay(BuildContext context, WidgetRef ref) async {
    final ok = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _PaymentSheet(invoice: invoice),
    );
    if (ok == true && context.mounted) Navigator.pop(context, true);
  }

  Widget _kv(BuildContext context, String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(k,
                style:
                    TextStyle(color: Theme.of(context).colorScheme.outline)),
            Flexible(
              child: Text(v,
                  textAlign: TextAlign.right,
                  style: const TextStyle(fontWeight: FontWeight.w600)),
            ),
          ],
        ),
      );
}

class _PaymentSheet extends ConsumerStatefulWidget {
  const _PaymentSheet({required this.invoice});
  final Invoice invoice;

  @override
  ConsumerState<_PaymentSheet> createState() => _PaymentSheetState();
}

class _PaymentSheetState extends ConsumerState<_PaymentSheet> {
  String _method = _payMethods.first;
  late final TextEditingController _amount;
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _amount =
        TextEditingController(text: widget.invoice.amount.round().toString());
  }

  @override
  void dispose() {
    _amount.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final amount = num.tryParse(_amount.text.trim());
    if (amount == null || amount <= 0) {
      setState(() => _error = 'Số tiền không hợp lệ');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    final messenger = ScaffoldMessenger.of(context);
    try {
      await ref.read(billingRepositoryProvider).pay(
            sessionId: widget.invoice.sessionId,
            method: _method,
            amountPaid: amount,
          );
      if (mounted) Navigator.pop(context, true);
      messenger.showSnackBar(
          const SnackBar(content: Text('Thanh toán thành công')));
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 20,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Thanh toán', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          DropdownButtonFormField<String>(
            initialValue: _method,
            decoration: const InputDecoration(labelText: 'Phương thức'),
            items: [
              for (final m in _payMethods)
                DropdownMenuItem(value: m, child: Text(Fmt.paymentMethod(m))),
            ],
            onChanged: (v) => setState(() => _method = v ?? _method),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _amount,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(labelText: 'Số tiền nhận'),
          ),
          if (_error != null) ...[
            const SizedBox(height: 12),
            Text(_error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ],
          const SizedBox(height: 20),
          FilledButton(
            onPressed: _loading ? null : _submit,
            child: _loading
                ? const SizedBox(
                    height: 22,
                    width: 22,
                    child: CircularProgressIndicator(strokeWidth: 2.5))
                : const Text('Xác nhận thanh toán'),
          ),
        ],
      ),
    );
  }
}
