import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_exception.dart';
import '../../../core/network/api_response.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../../admin/data/admin_models.dart';

final usersProvider = FutureProvider<PageResult<AppUser>>(
  (ref) => ref.watch(adminRepositoryProvider).getUsers(),
);

class UsersScreen extends ConsumerWidget {
  const UsersScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final users = ref.watch(usersProvider);
    return RefreshIndicator(
      onRefresh: () async => ref.invalidate(usersProvider),
      child: AsyncView<PageResult<AppUser>>(
        value: users,
        onRetry: () => ref.invalidate(usersProvider),
        data: (page) {
          if (page.content.isEmpty) {
            return const EmptyView(message: 'Không có người dùng');
          }
          return ListView.separated(
            padding: const EdgeInsets.all(16),
            itemCount: page.content.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (_, i) => _UserTile(user: page.content[i]),
          );
        },
      ),
    );
  }
}

class _UserTile extends ConsumerWidget {
  const _UserTile({required this.user});
  final AppUser user;

  Future<void> _toggle(BuildContext context, WidgetRef ref) async {
    final activating = !user.isActive;
    final ok = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title:
            Text(activating ? 'Kích hoạt tài khoản' : 'Vô hiệu hóa tài khoản'),
        content: Text(activating
            ? 'Bật lại "${user.username}" để đăng nhập được?'
            : 'Vô hiệu hóa "${user.username}"? Người này sẽ không đăng nhập được.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Hủy')),
          FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: Text(activating ? 'Kích hoạt' : 'Vô hiệu hóa')),
        ],
      ),
    );
    if (ok != true || !context.mounted) return;
    final messenger = ScaffoldMessenger.of(context);
    try {
      final repo = ref.read(adminRepositoryProvider);
      if (activating) {
        await repo.activateUser(user.id);
      } else {
        await repo.deactivateUser(user.id);
      }
      ref.invalidate(usersProvider);
      messenger.showSnackBar(SnackBar(
          content: Text(activating ? 'Đã kích hoạt' : 'Đã vô hiệu hóa')));
    } on ApiException catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(e.message)));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isAdmin = user.role == 'ADMIN';
    final activeColor =
        user.isActive ? Colors.green.shade700 : Theme.of(context).colorScheme.error;
    return Card(
      child: ListTile(
        onTap: () => _toggle(context, ref),
        leading: CircleAvatar(
          child: Text(user.username.characters.first.toUpperCase()),
        ),
        title: Text(user.username,
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text('${user.email}\nTạo ${Fmt.date(user.createdAt)}'),
        isThreeLine: true,
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            StatusChip(
              label: Fmt.role(user.role),
              color: isAdmin
                  ? Theme.of(context).colorScheme.primary
                  : Theme.of(context).colorScheme.outline,
            ),
            const SizedBox(height: 4),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(user.isActive ? Icons.check_circle : Icons.block,
                    size: 14, color: activeColor),
                const SizedBox(width: 4),
                Text(user.isActive ? 'Hoạt động' : 'Vô hiệu',
                    style: TextStyle(fontSize: 12, color: activeColor)),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
