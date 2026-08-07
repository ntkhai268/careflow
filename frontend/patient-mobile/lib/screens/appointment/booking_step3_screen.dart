import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../models/appointment.dart';
import '../../models/patient.dart';
import '../../services/appointment_service.dart';
import '../../utils/appointment_slot.dart';

/// Booking Step 3: Choose date and time slot
class BookingStep3Screen extends ConsumerStatefulWidget {
  final Patient patient;
  final Department department;
  final DateTime? currentTimeOverride;

  const BookingStep3Screen({
    super.key,
    required this.patient,
    required this.department,
    this.currentTimeOverride,
  });

  @override
  ConsumerState<BookingStep3Screen> createState() => _BookingStep3ScreenState();
}

class _BookingStep3ScreenState extends ConsumerState<BookingStep3Screen> {
  late DateTime _selectedDate;
  String? _selectedSlot;
  List<AppointmentTimeSlot> _timeSlots = [];
  bool _isLoading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _selectedDate = _now;
    // If selected date is today but past working hours, default to tomorrow
    if (_selectedDate.hour >= 17) {
      _selectedDate = _selectedDate.add(const Duration(days: 1));
    }
    _loadTimeSlots();
  }

  Future<void> _loadTimeSlots() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final service = ref.read(appointmentServiceProvider);
      final slots = await service.getTimeSlotAvailability(
        department: widget.department.code,
        date: _selectedDate,
      );
      if (!mounted) return;
      setState(() {
        _timeSlots = slots;
        _isLoading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _timeSlots = [];
        _error = 'Không thể tải danh sách ca khám. Vui lòng thử lại.';
        _isLoading = false;
      });
    }
  }

  DateTime get _now => widget.currentTimeOverride ?? DateTime.now();

  List<AppointmentTimeSlot> _slotsFor() => _timeSlots;

  bool _isPast(AppointmentTimeSlot slot) => !isAppointmentSlotAvailable(
    selectedDate: _selectedDate,
    slot: slot.timeSlot,
    now: _now,
  );

  bool _isSelectable(AppointmentTimeSlot slot) => !_isPast(slot) && slot.available;

  List<AppointmentTimeSlot> get _morningSlots => _slotsFor()
      .where((s) => s.timeSlot.compareTo('12:00') < 0)
      .toList();

  List<AppointmentTimeSlot> get _afternoonSlots => _slotsFor()
      .where((s) => s.timeSlot.compareTo('12:00') >= 0)
      .toList();

  Future<void> _pickDate() async {
    final now = _now;
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate,
      firstDate: now,
      lastDate: now.add(const Duration(days: 30)),
      locale: const Locale('vi'),
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: Theme.of(
              context,
            ).colorScheme.copyWith(primary: AppColors.primary),
          ),
          child: child!,
        );
      },
    );
    if (picked != null) {
      setState(() {
        _selectedDate = picked;
        _selectedSlot = null;
      });
      await _loadTimeSlots();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Chọn ngày & ca khám'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => context.pop(),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? _TimeSlotLoadError(message: _error!, onRetry: _loadTimeSlots)
          : Column(
              children: [
                _buildStepIndicator(),
                Expanded(
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.all(AppSpacing.base),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _buildInfoBar(),
                        const SizedBox(height: AppSpacing.lg),
                        _buildDatePicker(),
                        const SizedBox(height: AppSpacing.xl),
                        _buildTimeSlotsSection(
                          'Buổi sáng',
                          _morningSlots,
                          Icons.wb_sunny_rounded,
                        ),
                        const SizedBox(height: AppSpacing.lg),
                        _buildTimeSlotsSection(
                          'Buổi chiều',
                          _afternoonSlots,
                          Icons.wb_twilight_rounded,
                        ),
                      ],
                    ),
                  ),
                ),
                _buildBottomBar(),
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
          _buildStepLine(true),
          _buildStep(2, 'Chuyên khoa', true),
          _buildStepLine(true),
          _buildStepActive(3, 'Ngày & Ca'),
          _buildStepLine(false),
          _buildStep(4, 'Xác nhận', false),
        ],
      ),
    );
  }

  Widget _buildStep(int number, String label, bool completed) {
    return Expanded(
      child: Column(
        children: [
          Container(
            width: 28,
            height: 28,
            decoration: BoxDecoration(
              color: completed ? AppColors.success : AppColors.cardBorder,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: completed
                  ? const Icon(Icons.check, size: 16, color: Colors.white)
                  : Text(
                      '$number',
                      style: TextStyle(
                        color: AppColors.textHint,
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
              color: completed ? AppColors.success : AppColors.textHint,
              fontWeight: FontWeight.w600,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildStepActive(int number, String label) {
    return Expanded(
      child: Column(
        children: [
          Container(
            width: 28,
            height: 28,
            decoration: BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                '$number',
                style: const TextStyle(
                  color: Colors.white,
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
              color: AppColors.primary,
              fontWeight: FontWeight.w600,
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

  Widget _buildInfoBar() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.primarySurface,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: Row(
        children: [
          Icon(
            Appointment.departmentIcon(widget.department.code),
            color: AppColors.primary,
            size: 20,
          ),
          const SizedBox(width: AppSpacing.sm),
          Text(
            widget.department.name,
            style: TextStyle(
              color: AppColors.primary,
              fontWeight: FontWeight.w600,
              fontSize: 13,
            ),
          ),
          const SizedBox(width: AppSpacing.sm),
          Text('•', style: TextStyle(color: AppColors.textHint)),
          const SizedBox(width: AppSpacing.sm),
          Icon(Icons.person_rounded, color: AppColors.primary, size: 16),
          const SizedBox(width: 4),
          Expanded(
            child: Text(
              widget.patient.fullName,
              style: TextStyle(color: AppColors.primary, fontSize: 13),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildDatePicker() {
    final dateStr = DateFormat('EEEE, dd/MM/yyyy', 'vi').format(_selectedDate);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Ngày khám',
          style: Theme.of(
            context,
          ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: AppSpacing.sm),
        InkWell(
          onTap: _pickDate,
          borderRadius: BorderRadius.circular(AppRadius.md),
          child: Container(
            padding: const EdgeInsets.all(AppSpacing.base),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(AppRadius.md),
              border: Border.all(color: AppColors.primary, width: 1.5),
            ),
            child: Row(
              children: [
                Icon(
                  Icons.calendar_today_rounded,
                  color: AppColors.primary,
                  size: 22,
                ),
                const SizedBox(width: AppSpacing.md),
                Expanded(
                  child: Text(
                    dateStr,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
                Icon(
                  Icons.edit_calendar_rounded,
                  color: AppColors.primary,
                  size: 20,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildTimeSlotsSection(
    String title,
    List<AppointmentTimeSlot> slots,
    IconData icon,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, color: AppColors.warning, size: 20),
            const SizedBox(width: AppSpacing.sm),
            Text(
              title,
              style: Theme.of(
                context,
              ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        if (slots.any((slot) => !_isSelectable(slot)))
          Padding(
            padding: const EdgeInsets.only(bottom: AppSpacing.sm),
            child: Text(
              'Màu xám: đã qua giờ • màu đỏ: đã đủ người',
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                color: AppColors.textHint,
              ),
            ),
          ),
        if (slots.isEmpty)
          Text(
            'Không còn ca phù hợp trong buổi này.',
            style: Theme.of(
              context,
            ).textTheme.bodySmall?.copyWith(color: AppColors.textHint),
          )
        else
          Wrap(
            spacing: AppSpacing.sm,
            runSpacing: AppSpacing.sm,
            children: slots.map((slot) {
              final isSelected = _selectedSlot == slot.timeSlot;
              final isPast = _isPast(slot);
              final isSelectable = _isSelectable(slot);
              final status = isPast
                  ? 'Đã qua giờ'
                  : slot.isFull
                  ? 'Đã đầy'
                  : '${slot.remaining} chỗ trống';
              return Semantics(
                button: true,
                enabled: isSelectable,
                label: '${slot.timeSlot}, $status',
                child: ChoiceChip(
                  label: Text(slot.timeSlot),
                  selected: isSelected,
                  onSelected: isSelectable
                      ? (selected) {
                          setState(() {
                            _selectedSlot = selected ? slot.timeSlot : null;
                          });
                        }
                      : null,
                  selectedColor: AppColors.primary,
                  backgroundColor: AppColors.surface,
                  disabledColor: isPast
                      ? AppColors.background
                      : AppColors.errorLight,
                  materialTapTargetSize: MaterialTapTargetSize.padded,
                  labelStyle: TextStyle(
                    color: isSelected
                        ? Colors.white
                        : isSelectable
                        ? AppColors.textPrimary
                        : AppColors.textHint,
                    fontWeight: isSelected ? FontWeight.w600 : FontWeight.w400,
                    fontSize: 13,
                  ),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppRadius.sm),
                    side: BorderSide(
                      color: isSelected
                          ? AppColors.primary
                          : isSelectable
                          ? AppColors.cardBorder
                          : AppColors.textHint,
                    ),
                  ),
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                ),
              );
            }).toList(),
          ),
      ],
    );
  }

  Widget _buildBottomBar() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.base),
      decoration: BoxDecoration(
        color: AppColors.surface,
        boxShadow: AppShadows.bottomNav,
      ),
      child: SafeArea(
        child: SizedBox(
          width: double.infinity,
          child: ElevatedButton(
            onPressed: _selectedSlot != null
                ? () {
                    final selectedSlot = _timeSlots
                        .where((slot) => slot.timeSlot == _selectedSlot)
                        .firstOrNull;
                    if (selectedSlot == null || !_isSelectable(selectedSlot)) {
                      setState(() => _selectedSlot = null);
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            selectedSlot?.isFull == true
                                ? 'Ca khám này đã đủ người. Vui lòng chọn ca khác.'
                                : 'Ca khám này đã qua. Vui lòng chọn ca khác.',
                          ),
                        ),
                      );
                      return;
                    }
                    context.push(
                      '/booking/step4',
                      extra: {
                        'patient': widget.patient,
                        'department': widget.department,
                        'date': _selectedDate,
                        'timeSlot': _selectedSlot,
                      },
                    );
                  }
                : null,
            child: const Text('Tiếp tục'),
          ),
        ),
      ),
    );
  }
}

class _TimeSlotLoadError extends StatelessWidget {
  const _TimeSlotLoadError({required this.message, required this.onRetry});

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
