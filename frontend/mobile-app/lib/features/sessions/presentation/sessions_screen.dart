import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../app/providers.dart';
import '../../../core/network/api_response.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/state_views.dart';
import '../../parking/data/parking_models.dart';

typedef SessionFilter = ({String? status, String? plate});

final sessionsProvider =
    FutureProvider.family<PageResult<SessionSummary>, SessionFilter>(
  (ref, filter) => ref.watch(parkingRepositoryProvider).getSessions(
        status: filter.status,
        plate: filter.plate,
      ),
);

const _statuses = ['ACTIVE', 'CLOSED', 'REQUIRES_ATTENTION'];

class SessionsScreen extends ConsumerStatefulWidget {
  const SessionsScreen({super.key, this.initialPlate});

  /// Pre-filled plate filter when deep-linked from an alert (/sessions?plate=...).
  final String? initialPlate;

  @override
  ConsumerState<SessionsScreen> createState() => _SessionsScreenState();
}

class _SessionsScreenState extends ConsumerState<SessionsScreen> {
  String? _status;
  String _plate = '';
  final _searchCtrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    final p = widget.initialPlate;
    if (p != null && p.isNotEmpty) {
      _plate = p;
      _searchCtrl.text = p;
    }
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final filter = (status: _status, plate: _plate.isEmpty ? null : _plate);
    final sessions = ref.watch(sessionsProvider(filter));

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
              for (final s in _statuses) _filterChip(Fmt.sessionStatus(s), s),
            ],
          ),
        ),
        const SizedBox(height: 4),
        Expanded(
          child: RefreshIndicator(
            onRefresh: () async => ref.invalidate(sessionsProvider(filter)),
            child: AsyncView<PageResult<SessionSummary>>(
              value: sessions,
              onRetry: () => ref.invalidate(sessionsProvider(filter)),
              data: (page) {
                if (page.content.isEmpty) {
                  return const EmptyView(message: 'Không có phiên nào');
                }
                return ListView.separated(
                  padding: const EdgeInsets.all(16),
                  itemCount: page.content.length,
                  separatorBuilder: (_, _) => const SizedBox(height: 8),
                  itemBuilder: (_, i) => _SessionTile(s: page.content[i]),
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

class _SessionTile extends StatelessWidget {
  const _SessionTile({required this.s});
  final SessionSummary s;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        onTap: () => context.go('/sessions/${s.id}'),
        leading: CircleAvatar(
          backgroundColor:
              statusColor(context, s.status).withValues(alpha: 0.15),
          child: Icon(Icons.directions_car,
              color: statusColor(context, s.status)),
        ),
        title: Text(s.plateNumber,
            style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text(
          'Vị trí ${s.slotCode ?? '—'} · Vào ${Fmt.dateTime(s.entryTime)}',
        ),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            StatusChip(
                label: Fmt.sessionStatus(s.status),
                color: statusColor(context, s.status)),
            const SizedBox(height: 4),
            Text(Fmt.duration(s.durationSeconds),
                style: Theme.of(context).textTheme.bodySmall),
          ],
        ),
      ),
    );
  }
}
