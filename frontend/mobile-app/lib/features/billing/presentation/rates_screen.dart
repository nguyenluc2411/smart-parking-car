import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../data/billing_models.dart';

final ratesProvider = FutureProvider<Rate>(
  (ref) => ref.watch(billingRepositoryProvider).getRates(),
);

class RatesScreen extends ConsumerWidget {
  const RatesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final rates = ref.watch(ratesProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(ratesProvider),
      child: AsyncView<Rate>(
        value: rates,
        onRetry: () => ref.invalidate(ratesProvider),
        data: (rate) => ListView(
          padding: const EdgeInsets.all(16),
          children: [
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Bảng giá hiện hành',
                        style: Theme.of(context).textTheme.titleMedium),
                    const Divider(height: 28),
                    _kv(context, 'Đơn giá/phút',
                        Fmt.currency(rate.ratePerMin.round())),
                    _kv(context, 'Hệ số cao điểm', '×${rate.peakMultiplier}'),
                    _kv(context, 'Phí qua đêm',
                        Fmt.currency(rate.overnightFlat)),
                    _kv(context, 'Phí tối thiểu', Fmt.currency(rate.minCharge)),
                    _kv(context, 'Hiệu lực từ', Fmt.date(rate.effectiveFrom)),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text('Khung giờ cao điểm',
                style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            for (final s in rate.schedules)
              Card(
                child: ListTile(
                  leading: Icon(
                    s.isPeak ? Icons.trending_up : Icons.trending_flat,
                    color: s.isPeak
                        ? Colors.orange.shade800
                        : Theme.of(context).colorScheme.outline,
                  ),
                  title: Text('${s.hourStart}:00 – ${s.hourEnd}:00'),
                  subtitle: Text('Ngày: ${Fmt.dayType(s.dayType)}'),
                  trailing: Text(s.isPeak ? 'Cao điểm' : 'Thường'),
                ),
              ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: () => _edit(context, ref, rate),
              icon: const Icon(Icons.edit),
              label: const Text('Cập nhật bảng giá'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _edit(BuildContext context, WidgetRef ref, Rate rate) async {
    final updated = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _EditRateSheet(rate: rate),
    );
    if (updated == true) ref.invalidate(ratesProvider);
  }

  Widget _kv(BuildContext context, String k, String v) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(k,
                style:
                    TextStyle(color: Theme.of(context).colorScheme.outline)),
            Text(v, style: const TextStyle(fontWeight: FontWeight.w600)),
          ],
        ),
      );
}

class _EditRateSheet extends ConsumerStatefulWidget {
  const _EditRateSheet({required this.rate});
  final Rate rate;

  @override
  ConsumerState<_EditRateSheet> createState() => _EditRateSheetState();
}

class _EditRateSheetState extends ConsumerState<_EditRateSheet> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _ratePerMin;
  late final TextEditingController _peakMultiplier;
  late final TextEditingController _overnightFlat;
  late final TextEditingController _minCharge;
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _ratePerMin =
        TextEditingController(text: widget.rate.ratePerMin.toString());
    _peakMultiplier =
        TextEditingController(text: widget.rate.peakMultiplier.toString());
    _overnightFlat =
        TextEditingController(text: widget.rate.overnightFlat.toString());
    _minCharge = TextEditingController(text: widget.rate.minCharge.toString());
  }

  @override
  void dispose() {
    _ratePerMin.dispose();
    _peakMultiplier.dispose();
    _overnightFlat.dispose();
    _minCharge.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(billingRepositoryProvider).updateRates(
            ratePerMin: double.parse(_ratePerMin.text.trim()),
            peakMultiplier: double.parse(_peakMultiplier.text.trim()),
            overnightFlat: num.parse(_overnightFlat.text.trim()),
            minCharge: num.parse(_minCharge.text.trim()),
          );
      if (mounted) Navigator.pop(context, true);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Giá trị không hợp lệ');
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
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Cập nhật bảng giá',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            _numField(_ratePerMin, 'Đơn giá/phút'),
            const SizedBox(height: 12),
            _numField(_peakMultiplier, 'Hệ số cao điểm'),
            const SizedBox(height: 12),
            _numField(_overnightFlat, 'Phí qua đêm'),
            const SizedBox(height: 12),
            _numField(_minCharge, 'Phí tối thiểu'),
            if (_error != null) ...[
              const SizedBox(height: 12),
              Text(_error!,
                  style:
                      TextStyle(color: Theme.of(context).colorScheme.error)),
            ],
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _loading ? null : _submit,
              child: _loading
                  ? const SizedBox(
                      height: 22,
                      width: 22,
                      child: CircularProgressIndicator(strokeWidth: 2.5))
                  : const Text('Lưu'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _numField(TextEditingController c, String label) => TextFormField(
        controller: c,
        keyboardType: const TextInputType.numberWithOptions(decimal: true),
        decoration: InputDecoration(labelText: label),
        validator: (v) {
          if (v == null || v.trim().isEmpty) return 'Bắt buộc';
          if (num.tryParse(v.trim()) == null) return 'Số không hợp lệ';
          return null;
        },
      );
}
