import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../config/theme.dart';
import '../../models/patient.dart';
import '../../providers/auth_provider.dart';
import '../../services/patient_service.dart';
import '../../utils/api_error_message.dart';

/// Booking Step 1: Choose patient profile
class BookingStep1Screen extends ConsumerStatefulWidget {
  const BookingStep1Screen({super.key});

  @override
  ConsumerState<BookingStep1Screen> createState() => _BookingStep1ScreenState();
}

class _BookingStep1ScreenState extends ConsumerState<BookingStep1Screen> {
  List<Patient> _patients = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadPatients();
  }

  Future<void> _loadPatients() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final service = ref.read(patientServiceProvider);
      final userId = ref.read(authProvider).userId;
      if (userId == null || userId.isEmpty) {
        setState(() {
          _patients = [];
          _isLoading = false;
        });
        return;
      }
      final patients = await service.getPatientsByUserId(userId);
      setState(() {
        _patients = patients;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _patients = [];
        _isLoading = false;
        _error = ApiErrorMessage.from(
          e,
          fallback: 'Không thể tải hồ sơ bệnh nhân. Vui lòng thử lại.',
        );
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Chọn hồ sơ bệnh nhân'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => context.pop(),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                // Step indicator
                _buildStepIndicator(),
                // Content
                Expanded(
                  child: _error != null
                      ? _buildErrorState()
                      : _patients.isEmpty
                      ? _buildEmptyState()
                      : _buildPatientList(),
                ),
              ],
            ),
    );
  }

  Widget _buildStepIndicator() {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.base,
        vertical: AppSpacing.md,
      ),
      color: AppColors.surface,
      child: Row(
        children: [
          _buildStep(1, 'Hồ sơ', true),
          _buildStepLine(false),
          _buildStep(2, 'Chuyên khoa', false),
          _buildStepLine(false),
          _buildStep(3, 'Ngày & Ca', false),
          _buildStepLine(false),
          _buildStep(4, 'Xác nhận', false),
        ],
      ),
    );
  }

  Widget _buildStep(int number, String label, bool active) {
    return Expanded(
      child: Column(
        children: [
          Container(
            width: 28,
            height: 28,
            decoration: BoxDecoration(
              color: active ? AppColors.primary : AppColors.cardBorder,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                '$number',
                style: TextStyle(
                  color: active ? Colors.white : AppColors.textHint,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              color: active ? AppColors.primary : AppColors.textHint,
              fontWeight: active ? FontWeight.w600 : FontWeight.w400,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildStepLine(bool completed) {
    return Container(
      width: 20,
      height: 2,
      color: completed ? AppColors.primary : AppColors.cardBorder,
      margin: const EdgeInsets.only(bottom: 16),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xxl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.person_add_rounded,
              size: 64,
              color: AppColors.primaryLight,
            ),
            const SizedBox(height: AppSpacing.base),
            Text(
              'Chưa có hồ sơ bệnh nhân',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Vui lòng tạo hồ sơ trước khi đặt khám',
              style: Theme.of(context).textTheme.bodyMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.xl),
            ElevatedButton.icon(
              onPressed: () => context.push('/profile/create'),
              icon: const Icon(Icons.add_rounded),
              label: const Text('Tạo hồ sơ mới'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xxl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.cloud_off_rounded,
              size: 64,
              color: AppColors.error,
            ),
            const SizedBox(height: AppSpacing.base),
            Text(_error!, textAlign: TextAlign.center),
            const SizedBox(height: AppSpacing.xl),
            OutlinedButton.icon(
              onPressed: _loadPatients,
              icon: const Icon(Icons.refresh_rounded),
              label: const Text('Thử lại'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPatientList() {
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.base),
      children: [
        Text(
          'Chọn người khám bệnh',
          style: Theme.of(
            context,
          ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: AppSpacing.sm),
        Text(
          'Bạn có thể đặt khám cho bản thân hoặc người thân',
          style: Theme.of(context).textTheme.bodyMedium,
        ),
        const SizedBox(height: AppSpacing.base),
        ..._patients.map(
          (patient) => _PatientSelectCard(
            patient: patient,
            onTap: () {
              // Navigate to step 2 with selected patient
              context.push('/booking/step2', extra: patient);
            },
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        OutlinedButton.icon(
          onPressed: () => context.push('/profile/create'),
          icon: const Icon(Icons.person_add_rounded),
          label: const Text('Thêm hồ sơ mới'),
        ),
      ],
    );
  }
}

class _PatientSelectCard extends StatelessWidget {
  final Patient patient;
  final VoidCallback onTap;

  const _PatientSelectCard({required this.patient, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        boxShadow: AppShadows.card,
        border: Border.all(color: AppColors.cardBorder, width: 0.5),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(AppRadius.lg),
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: AppColors.primarySurface,
                  child: Text(
                    patient.fullName.isNotEmpty
                        ? patient.fullName[0].toUpperCase()
                        : '?',
                    style: TextStyle(
                      fontSize: 22,
                      fontWeight: FontWeight.w600,
                      color: AppColors.primary,
                    ),
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        patient.fullName,
                        style: Theme.of(context).textTheme.titleMedium
                            ?.copyWith(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 2),
                      if (patient.dateOfBirth != null)
                        Text(
                          '${patient.dateOfBirth!.day}/${patient.dateOfBirth!.month}/${patient.dateOfBirth!.year} • ${patient.genderDisplay}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                      if (patient.idCardNumber != null)
                        Text(
                          'CCCD: ${patient.idCardNumber}',
                          style: Theme.of(context).textTheme.bodySmall,
                        ),
                    ],
                  ),
                ),
                Icon(Icons.chevron_right_rounded, color: AppColors.textHint),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
