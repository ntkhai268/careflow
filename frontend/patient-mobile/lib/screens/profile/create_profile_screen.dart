import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../providers/patient_provider.dart';

/// Screen for creating a new patient profile.
/// Form with QR scan placeholder + manual input fields.
class CreateProfileScreen extends ConsumerStatefulWidget {
  const CreateProfileScreen({super.key});

  @override
  ConsumerState<CreateProfileScreen> createState() =>
      _CreateProfileScreenState();
}

class _CreateProfileScreenState extends ConsumerState<CreateProfileScreen> {
  final _formKey = GlobalKey<FormState>();
  final _fullNameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _idCardController = TextEditingController();
  final _insuranceController = TextEditingController();
  final _occupationController = TextEditingController();
  final _addressController = TextEditingController();

  DateTime? _selectedDate;
  String _selectedGender = 'MALE';
  String _idCardType = 'CCCD';

  @override
  void dispose() {
    _fullNameController.dispose();
    _phoneController.dispose();
    _idCardController.dispose();
    _insuranceController.dispose();
    _occupationController.dispose();
    _addressController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(patientProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Tạo hồ sơ bệnh nhân'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.pop(),
        ),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(AppSpacing.base),
        child: Form(
          key: _formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // QR Scan section
              _buildQrScanSection(),
              const SizedBox(height: AppSpacing.xl),
              // Divider with text
              _buildDividerWithText('Hoặc nhập thủ công'),
              const SizedBox(height: AppSpacing.xl),
              // Manual input section
              _buildSectionTitle('Thông tin chung'),
              const SizedBox(height: AppSpacing.base),
              _buildFullNameField(),
              const SizedBox(height: AppSpacing.base),
              _buildDateOfBirthField(),
              const SizedBox(height: AppSpacing.base),
              _buildGenderField(),
              const SizedBox(height: AppSpacing.xl),
              _buildSectionTitle('Giấy tờ tùy thân'),
              const SizedBox(height: AppSpacing.base),
              _buildIdCardField(),
              const SizedBox(height: AppSpacing.base),
              _buildInsuranceField(),
              const SizedBox(height: AppSpacing.xl),
              _buildSectionTitle('Thông tin bổ sung'),
              const SizedBox(height: AppSpacing.base),
              _buildOccupationField(),
              const SizedBox(height: AppSpacing.base),
              _buildPhoneField(),
              const SizedBox(height: AppSpacing.base),
              _buildAddressField(),
              const SizedBox(height: AppSpacing.xxl),
              // Error message
              if (state.errorMessage != null)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(AppSpacing.md),
                  margin: const EdgeInsets.only(bottom: AppSpacing.base),
                  decoration: BoxDecoration(
                    color: AppColors.errorLight,
                    borderRadius: BorderRadius.circular(AppRadius.sm),
                  ),
                  child: Text(
                    state.errorMessage!,
                    style: const TextStyle(color: AppColors.error, fontSize: 13),
                  ),
                ),
              // Submit button
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: state.isLoading ? null : _handleSubmit,
                  child: state.isLoading
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('TẠO MỚI HỒ SƠ'),
                ),
              ),
              const SizedBox(height: AppSpacing.xxl),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildQrScanSection() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: AppColors.cardBorder),
      ),
      child: Column(
        children: [
          Icon(Icons.qr_code_scanner, size: 48, color: AppColors.primary),
          const SizedBox(height: AppSpacing.md),
          const Text(
            'Quét mã BHYT hoặc CCCD',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w600,
              color: AppColors.textPrimary,
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          const Text(
            'Tự động điền thông tin từ mã QR',
            style: TextStyle(
              fontSize: 13,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(height: AppSpacing.base),
          OutlinedButton.icon(
            onPressed: () {
              ScaffoldMessenger.of(context).showSnackBar(
                const SnackBar(
                  content:
                      Text('Tính năng quét QR sẽ được tích hợp sau'),
                ),
              );
            },
            icon: const Icon(Icons.camera_alt_outlined),
            label: const Text('MỞ CAMERA QUÉT'),
          ),
        ],
      ),
    );
  }

  Widget _buildDividerWithText(String text) {
    return Row(
      children: [
        const Expanded(child: Divider(color: AppColors.cardBorder)),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.base),
          child: Text(
            text,
            style: const TextStyle(
              color: AppColors.textSecondary,
              fontSize: 13,
            ),
          ),
        ),
        const Expanded(child: Divider(color: AppColors.cardBorder)),
      ],
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w600,
        color: AppColors.textPrimary,
      ),
    );
  }

  Widget _buildFullNameField() {
    return TextFormField(
      controller: _fullNameController,
      textCapitalization: TextCapitalization.words,
      decoration: const InputDecoration(
        labelText: 'Họ và tên *',
        hintText: 'Nhập họ và tên đầy đủ',
        prefixIcon: Icon(Icons.person_outline),
      ),
      validator: (value) {
        if (value == null || value.trim().isEmpty) {
          return 'Vui lòng nhập họ và tên';
        }
        return null;
      },
    );
  }

  Widget _buildDateOfBirthField() {
    final dateFormat = DateFormat('dd/MM/yyyy');
    return InkWell(
      onTap: _pickDate,
      child: InputDecorator(
        decoration: InputDecoration(
          labelText: 'Ngày sinh *',
          prefixIcon: const Icon(Icons.calendar_today_outlined),
          suffixIcon: const Icon(Icons.arrow_drop_down),
          errorText: _selectedDate == null && _formKey.currentState != null
              ? null
              : null,
        ),
        child: Text(
          _selectedDate != null
              ? dateFormat.format(_selectedDate!)
              : 'Chọn ngày sinh',
          style: TextStyle(
            fontSize: 14,
            color: _selectedDate != null
                ? AppColors.textPrimary
                : AppColors.textHint,
          ),
        ),
      ),
    );
  }

  Future<void> _pickDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate ?? DateTime(2000, 1, 1),
      firstDate: DateTime(1920),
      lastDate: DateTime.now(),
      locale: const Locale('vi', 'VN'),
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: const ColorScheme.light(
              primary: AppColors.primary,
              onPrimary: Colors.white,
              surface: Colors.white,
            ),
          ),
          child: child!,
        );
      },
    );
    if (picked != null) {
      setState(() => _selectedDate = picked);
    }
  }

  Widget _buildGenderField() {
    return DropdownButtonFormField<String>(
      initialValue: _selectedGender,
      decoration: const InputDecoration(
        labelText: 'Giới tính *',
        prefixIcon: Icon(Icons.wc_outlined),
      ),
      items: const [
        DropdownMenuItem(value: 'MALE', child: Text('Nam')),
        DropdownMenuItem(value: 'FEMALE', child: Text('Nữ')),
        DropdownMenuItem(value: 'OTHER', child: Text('Khác')),
      ],
      onChanged: (value) {
        if (value != null) setState(() => _selectedGender = value);
      },
    );
  }

  Widget _buildIdCardField() {
    return Row(
      children: [
        // ID type selector
        SizedBox(
          width: 140,
          child: DropdownButtonFormField<String>(
            initialValue: _idCardType,
            decoration: const InputDecoration(
              contentPadding:
                  EdgeInsets.symmetric(horizontal: 8, vertical: 16),
            ),
            isExpanded: true,
            items: const [
              DropdownMenuItem(value: 'CCCD', child: Text('CCCD')),
              DropdownMenuItem(value: 'PASSPORT', child: Text('Hộ chiếu')),
            ],
            onChanged: (value) {
              if (value != null) setState(() => _idCardType = value);
            },
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        // ID number input
        Expanded(
          child: TextFormField(
            controller: _idCardController,
            keyboardType: TextInputType.number,
            decoration: const InputDecoration(
              labelText: 'Số CCCD/Hộ chiếu *',
              hintText: 'Nhập số CCCD',
            ),
            validator: (value) {
              if (value == null || value.trim().isEmpty) {
                return 'Vui lòng nhập số CCCD';
              }
              return null;
            },
          ),
        ),
      ],
    );
  }

  Widget _buildInsuranceField() {
    return TextFormField(
      controller: _insuranceController,
      decoration: const InputDecoration(
        labelText: 'Mã BHYT',
        hintText: 'Nhập mã bảo hiểm y tế (nếu có)',
        prefixIcon: Icon(Icons.health_and_safety_outlined),
      ),
    );
  }

  Widget _buildOccupationField() {
    return TextFormField(
      controller: _occupationController,
      decoration: const InputDecoration(
        labelText: 'Nghề nghiệp *',
        hintText: 'Nhập nghề nghiệp',
        prefixIcon: Icon(Icons.work_outline),
      ),
      validator: (value) {
        if (value == null || value.trim().isEmpty) {
          return 'Vui lòng nhập nghề nghiệp';
        }
        return null;
      },
    );
  }

  Widget _buildPhoneField() {
    return TextFormField(
      controller: _phoneController,
      keyboardType: TextInputType.phone,
      decoration: const InputDecoration(
        labelText: 'Số điện thoại',
        hintText: 'Nhập số điện thoại',
        prefixIcon: Icon(Icons.phone_outlined),
      ),
    );
  }

  Widget _buildAddressField() {
    return TextFormField(
      controller: _addressController,
      maxLines: 2,
      decoration: const InputDecoration(
        labelText: 'Địa chỉ',
        hintText: 'Nhập địa chỉ',
        prefixIcon: Icon(Icons.location_on_outlined),
        alignLabelWithHint: true,
      ),
    );
  }

  Future<void> _handleSubmit() async {
    if (!_formKey.currentState!.validate()) return;

    if (_selectedDate == null) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Vui lòng chọn ngày sinh'),
          backgroundColor: AppColors.error,
        ),
      );
      return;
    }

    final data = {
      'fullName': _fullNameController.text.trim(),
      'dateOfBirth':
          '${_selectedDate!.year}-${_selectedDate!.month.toString().padLeft(2, '0')}-${_selectedDate!.day.toString().padLeft(2, '0')}',
      'gender': _selectedGender,
      'idCardNumber': _idCardController.text.trim(),
      'occupation': _occupationController.text.trim(),
    };

    // Optional fields
    if (_phoneController.text.trim().isNotEmpty) {
      data['phone'] = _phoneController.text.trim();
    }
    if (_insuranceController.text.trim().isNotEmpty) {
      data['insuranceNumber'] = _insuranceController.text.trim();
    }
    if (_addressController.text.trim().isNotEmpty) {
      data['address'] = _addressController.text.trim();
    }

    final success =
        await ref.read(patientProvider.notifier).createPatient(data);

    if (success && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Tạo hồ sơ thành công!'),
          backgroundColor: AppColors.success,
        ),
      );
      context.pop();
    }
  }
}
