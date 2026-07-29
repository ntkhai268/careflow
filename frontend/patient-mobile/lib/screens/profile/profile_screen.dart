import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../providers/patient_provider.dart';

/// Profile screen showing patient records list.
/// Empty state if no records, or patient card with summary info.
class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key});

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  @override
  void initState() {
    super.initState();
    // Load patient data when screen initializes
    Future.microtask(() => ref.read(patientProvider.notifier).loadPatient());
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(patientProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Hồ sơ bệnh nhân'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/'),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.person_add_outlined),
            tooltip: 'Tạo mới',
            onPressed: () => context.push('/profile/create'),
          ),
        ],
      ),
      body: _buildBody(state),
    );
  }

  Widget _buildBody(PatientState state) {
    if (state.isLoading) {
      return const Center(
        child: CircularProgressIndicator(color: AppColors.primary),
      );
    }

    if (state.errorMessage != null) {
      return _buildErrorState(state.errorMessage!);
    }

    if (state.patient == null) {
      return _buildEmptyState();
    }

    return _buildPatientList(state);
  }

  Widget _buildErrorState(String message) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 64, color: AppColors.error),
            const SizedBox(height: AppSpacing.base),
            Text(
              message,
              textAlign: TextAlign.center,
              style: const TextStyle(color: AppColors.textSecondary),
            ),
            const SizedBox(height: AppSpacing.xl),
            ElevatedButton.icon(
              onPressed: () =>
                  ref.read(patientProvider.notifier).loadPatient(),
              icon: const Icon(Icons.refresh),
              label: const Text('Thử lại'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState() {
    return SingleChildScrollView(
      child: Column(
        children: [
          // Info banner
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(AppSpacing.base),
            color: AppColors.primarySurface,
            child: Row(
              children: [
                Icon(Icons.info_outline,
                    color: AppColors.primary, size: 20),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    'Bạn chưa có hồ sơ bệnh nhân. Vui lòng tạo mới hồ sơ để được đặt khám.',
                    style: TextStyle(
                      color: AppColors.primary,
                      fontSize: 13,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.xxxl),
          // Title
          const Text(
            'Tạo hồ sơ bệnh nhân',
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: AppSpacing.xxl),
            child: Text(
              'Bạn được phép tạo tối đa 10 hồ sơ\n(cá nhân và người thân trong gia đình)',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: AppColors.textSecondary,
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.xxl),
          // Primary button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
            child: SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () => context.push('/profile/create'),
                child: const Text('CHƯA TỪNG KHÁM ĐĂNG KÝ MỚI'),
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.base),
          // Scan QR button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
            child: SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text('Tính năng quét QR sẽ được cập nhật sau'),
                    ),
                  );
                },
                icon: const Icon(Icons.qr_code_scanner),
                label: const Text('QUÉT MÃ BHYT/CCCD'),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPatientList(PatientState state) {
    final patient = state.patient!;
    final dateFormat = DateFormat('dd/MM/yyyy');

    return SingleChildScrollView(
      child: Column(
        children: [
          // Info banner
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(AppSpacing.base),
            color: AppColors.primarySurface,
            child: Row(
              children: [
                Icon(Icons.info_outline,
                    color: AppColors.primary, size: 20),
                const SizedBox(width: AppSpacing.sm),
                Expanded(
                  child: Text(
                    'Vui lòng chọn 1 trong các hồ sơ bên dưới, hoặc bấm vào biểu tượng ở trên để thêm hồ sơ người bệnh.',
                    style: TextStyle(
                      color: AppColors.primary,
                      fontSize: 13,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.base),
          // Patient card
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.base),
            child: Container(
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(AppRadius.lg),
                boxShadow: AppShadows.card,
              ),
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Name
                  Row(
                    children: [
                      Icon(Icons.person_outline,
                          color: AppColors.primary, size: 22),
                      const SizedBox(width: AppSpacing.sm),
                      Expanded(
                        child: Text(
                          patient.fullName.toUpperCase(),
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                            color: AppColors.primary,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: AppSpacing.md),
                  // Phone
                  if (patient.phone != null)
                    _buildInfoRow(
                        Icons.phone_outlined, patient.phone!),
                  // Date of birth
                  if (patient.dateOfBirth != null)
                    _buildInfoRow(Icons.cake_outlined,
                        dateFormat.format(patient.dateOfBirth!)),
                  // Address
                  if (patient.address != null &&
                      patient.address!.isNotEmpty)
                    _buildInfoRow(
                        Icons.location_on_outlined, patient.address!),
                  const SizedBox(height: AppSpacing.base),
                  // Action buttons
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton(
                          onPressed: () =>
                              context.push('/profile/${patient.id}'),
                          child: const Text('Chi tiết'),
                        ),
                      ),
                      const SizedBox(width: AppSpacing.md),
                      Expanded(
                        child: ElevatedButton(
                          onPressed: () {
                            context.push(
                              '/patient/${patient.id}/health-records',
                              extra: {
                                'patientName': patient.fullName,
                                'patientGender': patient.genderDisplay,
                                'patientBirthYear': patient.dateOfBirth?.year ?? 0,
                              },
                            );
                          },
                          child: const Text('Thông tin sức khỏe'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.base),
          // Search more button
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.base),
            child: SizedBox(
              width: double.infinity,
              child: ElevatedButton.icon(
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text(
                          'Tính năng tìm kiếm thông tin sức khỏe sẽ được cập nhật sau'),
                    ),
                  );
                },
                icon: const Icon(Icons.search),
                label: const Text('Xem thêm thông tin sức khỏe khác'),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(IconData icon, String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
      child: Row(
        children: [
          Icon(icon, color: AppColors.primary, size: 18),
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              text,
              style: const TextStyle(
                fontSize: 14,
                color: AppColors.textPrimary,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
