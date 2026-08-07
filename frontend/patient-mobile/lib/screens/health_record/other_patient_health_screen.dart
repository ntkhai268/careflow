import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../models/patient.dart';
import '../../providers/auth_provider.dart';
import '../../services/patient_service.dart';

class OtherPatientHealthScreen extends ConsumerWidget {
  const OtherPatientHealthScreen({super.key, this.excludePatientId});

  final String? excludePatientId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final auth = ref.watch(authProvider);
    final userId = auth.userId;

    if (auth.status != AuthStatus.authenticated || userId == null) {
      return const Scaffold(
        body: Center(child: Text('Vui lòng đăng nhập để xem hồ sơ bệnh nhân.')),
      );
    }

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(title: const Text('Sức khỏe bệnh nhân khác')),
      body: FutureBuilder<List<Patient>>(
        future: ref.read(patientServiceProvider).getPatientsByUserId(userId),
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.xl),
                child: Text(
                  'Không thể tải danh sách hồ sơ bệnh nhân khác.',
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
              ),
            );
          }

          final profiles = (snapshot.data ?? const <Patient>[])
              .where((patient) => patient.id != excludePatientId)
              .toList();
          if (profiles.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(AppSpacing.xl),
                child: Text(
                  'Tài khoản chưa có hồ sơ bệnh nhân khác.',
                  textAlign: TextAlign.center,
                ),
              ),
            );
          }

          return ListView.separated(
            padding: const EdgeInsets.all(AppSpacing.base),
            itemCount: profiles.length,
            separatorBuilder: (context, index) =>
                const SizedBox(height: AppSpacing.sm),
            itemBuilder: (context, index) {
              final patient = profiles[index];
              return Card(
                child: ListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.base,
                    vertical: AppSpacing.sm,
                  ),
                  leading: CircleAvatar(
                    backgroundColor: AppColors.primarySurface,
                    child: Text(
                      patient.fullName.isEmpty
                          ? '?'
                          : patient.fullName[0].toUpperCase(),
                      style: const TextStyle(color: AppColors.primary),
                    ),
                  ),
                  title: Text(
                    patient.fullName,
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  ),
                  subtitle: Text(
                    '${patient.genderDisplay} • ${patient.patientCode}',
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => context.push(
                    '/patient/${patient.id}/health-records',
                    extra: {
                      'patientName': patient.fullName,
                      'patientGender': patient.genderDisplay,
                      'patientBirthYear': patient.dateOfBirth?.year ?? 0,
                    },
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
