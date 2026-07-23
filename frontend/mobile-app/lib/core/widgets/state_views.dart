import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Hiển thị thống nhất loading / error / data cho một [AsyncValue].
class AsyncView<T> extends StatelessWidget {
  const AsyncView({
    super.key,
    required this.value,
    required this.data,
    this.onRetry,
  });

  final AsyncValue<T> value;
  final Widget Function(T data) data;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    return value.when(
      data: data,
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (err, _) => ErrorView(message: '$err', onRetry: onRetry),
    );
  }
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.message, this.onRetry});

  final String message;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.error_outline,
                size: 48, color: theme.colorScheme.error),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            if (onRetry != null) ...[
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: onRetry,
                icon: const Icon(Icons.refresh),
                label: const Text('Thử lại'),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class EmptyView extends StatelessWidget {
  const EmptyView({super.key, required this.message, this.icon});

  final String message;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon ?? Icons.inbox_outlined,
              size: 48, color: theme.colorScheme.outline),
          const SizedBox(height: 12),
          Text(message, style: TextStyle(color: theme.colorScheme.outline)),
        ],
      ),
    );
  }
}

/// Badge trạng thái dùng chung (session/gate/invoice status).
class StatusChip extends StatelessWidget {
  const StatusChip({super.key, required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Text(
        label,
        style: TextStyle(
            color: color, fontWeight: FontWeight.w600, fontSize: 12),
      ),
    );
  }
}

/// Map status -> màu, dùng chung cho session/gate/invoice.
/// Chọn sắc độ theo brightness để chữ/biểu tượng luôn dễ đọc ở cả nền sáng và tối.
Color statusColor(BuildContext context, String status) {
  final scheme = Theme.of(context).colorScheme;
  final dark = Theme.of(context).brightness == Brightness.dark;
  final green = dark ? Colors.green.shade400 : Colors.green.shade700;
  final orange = dark ? Colors.orange.shade400 : Colors.orange.shade800;
  switch (status.toUpperCase()) {
    case 'ACTIVE':
    case 'OPEN':
    case 'PAID':
    case 'EMPTY':
    case 'FULFILLED':
      return green;
    case 'CLOSED':
    case 'OCCUPIED':
    case 'RESERVED':
      return scheme.primary;
    case 'PENDING':
    case 'MAINTENANCE':
    case 'HELD':
      return orange;
    case 'REQUIRES_ATTENTION':
    case 'CANCELLED':
    case 'BLACKLIST':
    case 'EXPIRED':
      return scheme.error;
    default:
      return scheme.outline;
  }
}
