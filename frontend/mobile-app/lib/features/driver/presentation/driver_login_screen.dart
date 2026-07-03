import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/api_exception.dart';
import '../../../core/widgets/brand_logo.dart';
import 'driver_auth_controller.dart';

/// Đăng nhập tài xế qua OTP (ADR-010): bước 1 nhập SĐT → bước 2 nhập OTP
/// (+ họ tên nếu là lần đăng ký đầu).
class DriverLoginScreen extends ConsumerStatefulWidget {
  const DriverLoginScreen({super.key});

  @override
  ConsumerState<DriverLoginScreen> createState() => _DriverLoginScreenState();
}

enum _Step { phone, otp }

class _DriverLoginScreenState extends ConsumerState<DriverLoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _phone = TextEditingController(text: '0901234567');
  final _code = TextEditingController();
  final _fullName = TextEditingController();

  _Step _step = _Step.phone;
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    _phone.dispose();
    _code.dispose();
    _fullName.dispose();
    super.dispose();
  }

  Future<void> _requestOtp() async {
    if (_phone.text.trim().isEmpty) {
      setState(() => _error = 'Nhập số điện thoại');
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref
          .read(driverAuthControllerProvider.notifier)
          .requestOtp(_phone.text.trim());
      if (mounted) setState(() => _step = _Step.otp);
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Không gửi được OTP, vui lòng thử lại.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _verifyOtp() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(driverAuthControllerProvider.notifier).verifyOtp(
            phone: _phone.text.trim(),
            code: _code.text.trim(),
            fullName: _fullName.text.trim(),
          );
      // Điều hướng do DriverApp xử lý theo trạng thái auth.
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'Xác thực OTP thất bại, vui lòng thử lại.');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Form(
              key: _formKey,
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Center(child: BrandLogo(size: 64)),
                  const SizedBox(height: 16),
                  Text('Smart Parking',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.headlineSmall
                          ?.copyWith(fontWeight: FontWeight.bold)),
                  Text('Tài xế',
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium
                          ?.copyWith(color: theme.colorScheme.outline)),
                  const SizedBox(height: 32),
                  TextFormField(
                    controller: _phone,
                    enabled: _step == _Step.phone,
                    keyboardType: TextInputType.phone,
                    inputFormatters: [
                      FilteringTextInputFormatter.allow(RegExp(r'[0-9+]')),
                    ],
                    decoration: const InputDecoration(
                      labelText: 'Số điện thoại',
                      prefixIcon: Icon(Icons.phone_outlined),
                    ),
                  ),
                  if (_step == _Step.otp) ...[
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _code,
                      keyboardType: TextInputType.number,
                      inputFormatters: [
                        FilteringTextInputFormatter.digitsOnly,
                        LengthLimitingTextInputFormatter(8),
                      ],
                      decoration: const InputDecoration(
                        labelText: 'Mã OTP',
                        prefixIcon: Icon(Icons.password_outlined),
                      ),
                      validator: (v) => (v == null || v.trim().length < 4)
                          ? 'Nhập mã OTP'
                          : null,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _fullName,
                      decoration: const InputDecoration(
                        labelText: 'Họ tên (nếu đăng ký lần đầu)',
                        prefixIcon: Icon(Icons.person_outline),
                      ),
                    ),
                  ],
                  if (_error != null) ...[
                    const SizedBox(height: 16),
                    Text(_error!,
                        style: TextStyle(color: theme.colorScheme.error)),
                  ],
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: _loading
                        ? null
                        : (_step == _Step.phone ? _requestOtp : _verifyOtp),
                    child: _loading
                        ? const SizedBox(
                            height: 22,
                            width: 22,
                            child: CircularProgressIndicator(strokeWidth: 2.5),
                          )
                        : Text(_step == _Step.phone
                            ? 'Gửi OTP'
                            : 'Xác thực & đăng nhập'),
                  ),
                  if (_step == _Step.otp) ...[
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: _loading
                          ? null
                          : () => setState(() {
                                _step = _Step.phone;
                                _error = null;
                                _code.clear();
                              }),
                      child: const Text('Đổi số điện thoại'),
                    ),
                  ],
                  const SizedBox(height: 12),
                  Text(
                    'Nhập số điện thoại đã đăng ký để nhận OTP',
                    textAlign: TextAlign.center,
                    style: theme.textTheme.bodySmall
                        ?.copyWith(color: theme.colorScheme.outline),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
