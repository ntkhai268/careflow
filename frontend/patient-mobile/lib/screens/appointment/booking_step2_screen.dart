import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../config/theme.dart';
import '../../features/journey/application/journey_providers.dart';
import '../../models/appointment.dart';
import '../../models/patient.dart';
import '../../services/appointment_service.dart';

/// Booking Step 2: Choose department
class BookingStep2Screen extends ConsumerStatefulWidget {
  final Patient patient;

  const BookingStep2Screen({super.key, required this.patient});

  @override
  ConsumerState<BookingStep2Screen> createState() => _BookingStep2ScreenState();
}

class _BookingStep2ScreenState extends ConsumerState<BookingStep2Screen> {
  List<Department> _departments = [];
  bool _isLoading = true;
  bool _usingDemoData = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadDepartments();
  }

  Future<void> _loadDepartments() async {
    setState(() {
      _isLoading = true;
      _usingDemoData = false;
      _error = null;
    });
    try {
      final service = ref.read(appointmentServiceProvider);
      final departments = await service.getDepartments();
      if (!mounted) return;
      setState(() {
        _departments = departments;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      if (!ref.read(demoModeProvider)) {
        setState(() {
          _departments = [];
          _error = 'Không thể tải danh sách chuyên khoa. Vui lòng thử lại.';
          _isLoading = false;
        });
        return;
      }
      setState(() {
        _departments = _demoDepartments;
        _usingDemoData = true;
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Chọn chuyên khoa'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => context.pop(),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? _ReferenceDataError(message: _error!, onRetry: _loadDepartments)
          : Column(
              children: [
                _buildStepIndicator(),
                if (_usingDemoData)
                  const Padding(
                    padding: EdgeInsets.only(top: AppSpacing.md),
                    child: Text('Dữ liệu chuyên khoa mô phỏng'),
                  ),
                // Selected patient info
                _buildPatientInfo(),
                // Department grid
                Expanded(child: _buildDepartmentGrid()),
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
          _buildStep(1, 'Hồ sơ', true, true),
          _buildStepLine(true),
          _buildStep(2, 'Chuyên khoa', true, false),
          _buildStepLine(false),
          _buildStep(3, 'Ngày & Ca', false, false),
          _buildStepLine(false),
          _buildStep(4, 'Xác nhận', false, false),
        ],
      ),
    );
  }

  Widget _buildStep(int number, String label, bool active, bool completed) {
    return Expanded(
      child: Column(
        children: [
          Container(
            width: 28,
            height: 28,
            decoration: BoxDecoration(
              color: completed
                  ? AppColors.success
                  : active
                  ? AppColors.primary
                  : AppColors.cardBorder,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: completed
                  ? const Icon(Icons.check, size: 16, color: Colors.white)
                  : Text(
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
      color: completed ? AppColors.success : AppColors.cardBorder,
      margin: const EdgeInsets.only(bottom: 16),
    );
  }

  Widget _buildPatientInfo() {
    return Container(
      margin: const EdgeInsets.fromLTRB(
        AppSpacing.base,
        AppSpacing.md,
        AppSpacing.base,
        0,
      ),
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.primarySurface,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: Row(
        children: [
          Icon(Icons.person_rounded, color: AppColors.primary, size: 20),
          const SizedBox(width: AppSpacing.sm),
          Text(
            'Đặt cho: ',
            style: TextStyle(color: AppColors.textSecondary, fontSize: 13),
          ),
          Text(
            widget.patient.fullName,
            style: TextStyle(
              color: AppColors.primary,
              fontWeight: FontWeight.w600,
              fontSize: 13,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDepartmentGrid() {
    return GridView.builder(
      padding: const EdgeInsets.all(AppSpacing.base),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 2,
        crossAxisSpacing: AppSpacing.md,
        mainAxisSpacing: AppSpacing.md,
        childAspectRatio: 1.3,
      ),
      itemCount: _departments.length,
      itemBuilder: (context, index) {
        final dept = _departments[index];
        return _DepartmentCard(
          department: dept,
          onTap: () {
            context.push(
              '/booking/step3',
              extra: {'patient': widget.patient, 'department': dept},
            );
          },
        );
      },
    );
  }
}

final _demoDepartments = [
  Department(code: 'NOI_TONG_QUAT', name: 'Nội tổng quát'),
  Department(code: 'NHI', name: 'Nhi'),
  Department(code: 'NGOAI', name: 'Ngoại'),
  Department(code: 'SAN', name: 'Sản'),
  Department(code: 'MAT', name: 'Mắt'),
  Department(code: 'TAI_MUI_HONG', name: 'Tai mũi họng'),
  Department(code: 'RANG_HAM_MAT', name: 'Răng hàm mặt'),
  Department(code: 'DA_LIEU', name: 'Da liễu'),
  Department(code: 'THAN_KINH', name: 'Thần kinh'),
  Department(code: 'TIM_MACH', name: 'Tim mạch'),
  Department(code: 'CO_XUONG_KHOP', name: 'Cơ xương khớp'),
];

class _ReferenceDataError extends StatelessWidget {
  const _ReferenceDataError({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.xl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.cloud_off_rounded, color: AppColors.error, size: 48),
          const SizedBox(height: AppSpacing.md),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: AppSpacing.md),
          OutlinedButton(onPressed: onRetry, child: const Text('Thử lại')),
        ],
      ),
    ),
  );
}

class _DepartmentCard extends StatelessWidget {
  final Department department;
  final VoidCallback onTap;

  const _DepartmentCard({required this.department, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Container(
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
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.primarySurface,
                  shape: BoxShape.circle,
                ),
                child: Icon(
                  Appointment.departmentIcon(department.code),
                  color: AppColors.primary,
                  size: 28,
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Text(
                department.name,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  fontWeight: FontWeight.w500,
                  color: AppColors.textPrimary,
                ),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
