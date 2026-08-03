import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../providers/patient_provider.dart';

/// Screen showing detailed patient profile information.
/// Displays all fields, with edit and delete actions.
class ProfileDetailScreen extends ConsumerWidget {
  const ProfileDetailScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(patientProvider);
    final patient = state.patient;

    if (state.isLoading) {
      return Scaffold(
        appBar: AppBar(title: const Text('Chi tiết hồ sơ')),
        body: const Center(
          child: CircularProgressIndicator(color: AppColors.primary),
        ),
      );
    }

    if (patient == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Chi tiết hồ sơ')),
        body: const Center(child: Text('Không tìm thấy hồ sơ')),
      );
    }

    final dateFormat = DateFormat('dd/MM/yyyy');

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Xác nhận thông tin'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.delete_outline),
            tooltip: 'Xóa hồ sơ',
            onPressed: () => _confirmDelete(context, ref, patient.id),
          ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(AppSpacing.base),
              child: Container(
                width: double.infinity,
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(AppRadius.lg),
                  boxShadow: AppShadows.card,
                ),
                child: Column(
                  children: [
                    // Avatar header
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(AppSpacing.xl),
                      decoration: const BoxDecoration(
                        gradient: AppColors.headerGradient,
                        borderRadius: BorderRadius.only(
                          topLeft: Radius.circular(AppRadius.lg),
                          topRight: Radius.circular(AppRadius.lg),
                        ),
                      ),
                      child: Column(
                        children: [
                          CircleAvatar(
                            radius: 40,
                            backgroundColor: Colors.white.withValues(alpha: 0.3),
                            child: const Icon(
                              Icons.person,
                              size: 50,
                              color: Colors.white,
                            ),
                          ),
                          const SizedBox(height: AppSpacing.md),
                          Text(
                            patient.fullName,
                            style: const TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.w700,
                              color: Colors.white,
                            ),
                          ),
                          const SizedBox(height: AppSpacing.xs),
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 12, vertical: 4),
                            decoration: BoxDecoration(
                              color: Colors.white.withValues(alpha: 0.2),
                              borderRadius:
                                  BorderRadius.circular(AppRadius.full),
                            ),
                            child: Text(
                              patient.patientCode,
                              style: const TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: Colors.white,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                    // Detail fields
                    Padding(
                      padding: const EdgeInsets.all(AppSpacing.lg),
                      child: Column(
                        children: [
                          _buildDetailRow(
                              'Họ và tên', patient.fullName, Icons.person_outline),
                          _buildDetailRow(
                            'Mã bệnh nhân',
                            patient.patientCode,
                            Icons.badge_outlined,
                          ),
                          if (patient.dateOfBirth != null)
                            _buildDetailRow(
                              'Ngày sinh',
                              dateFormat.format(patient.dateOfBirth!),
                              Icons.cake_outlined,
                            ),
                          _buildDetailRow(
                            'Giới tính',
                            patient.genderDisplay,
                            Icons.wc_outlined,
                          ),
                          if (patient.idCardNumber != null)
                            _buildDetailRow(
                              'Số CCCD',
                              patient.idCardNumber!,
                              Icons.credit_card_outlined,
                            ),
                          if (patient.insuranceNumber != null &&
                              patient.insuranceNumber!.isNotEmpty)
                            _buildDetailRow(
                              'Mã BHYT',
                              patient.insuranceNumber!,
                              Icons.health_and_safety_outlined,
                            ),
                          if (patient.occupation != null)
                            _buildDetailRow(
                              'Nghề nghiệp',
                              patient.occupation!,
                              Icons.work_outline,
                            ),
                          if (patient.phone != null &&
                              patient.phone!.isNotEmpty)
                            _buildDetailRow(
                              'Số điện thoại',
                              patient.phone!,
                              Icons.phone_outlined,
                            ),
                          if (patient.address != null &&
                              patient.address!.isNotEmpty)
                            _buildDetailRow(
                              'Địa chỉ',
                              patient.address!,
                              Icons.location_on_outlined,
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
          // Bottom action buttons
          Container(
            padding: const EdgeInsets.all(AppSpacing.base),
            decoration: BoxDecoration(
              color: AppColors.surface,
              boxShadow: AppShadows.bottomNav,
            ),
            child: SafeArea(
              child: Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () => context.go('/'),
                      child: const Text('Trang chủ'),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () =>
                          context.push('/profile/${patient.id}/edit'),
                      child: const Text('Chỉnh sửa'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDetailRow(String label, String value, IconData icon) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.base),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, size: 20, color: AppColors.primary),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w500,
                    color: AppColors.textPrimary,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _confirmDelete(BuildContext context, WidgetRef ref, String id) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xóa hồ sơ'),
        content:
            const Text('Bạn có chắc chắn muốn xóa hồ sơ bệnh nhân này?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: const Text('Hủy'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.of(ctx).pop();
              final success =
                  await ref.read(patientProvider.notifier).deletePatient(id);
              if (success && context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Đã xóa hồ sơ'),
                    backgroundColor: AppColors.success,
                  ),
                );
                context.go('/');
              }
            },
            style: TextButton.styleFrom(foregroundColor: AppColors.error),
            child: const Text('Xóa'),
          ),
        ],
      ),
    );
  }
}
