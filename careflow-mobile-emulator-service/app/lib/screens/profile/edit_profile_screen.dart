import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../providers/patient_provider.dart';

/// Screen for editing an existing patient profile.
/// Pre-fills form with current data, allows saving changes.
class EditProfileScreen extends ConsumerStatefulWidget {
  const EditProfileScreen({super.key});

  @override
  ConsumerState<EditProfileScreen> createState() => _EditProfileScreenState();
}

class _EditProfileScreenState extends ConsumerState<EditProfileScreen> {
  final _formKey = GlobalKey<FormState>();
  final _fullNameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _idCardController = TextEditingController();
  final _insuranceController = TextEditingController();
  final _occupationController = TextEditingController();
  final _addressController = TextEditingController();

  DateTime? _selectedDate;
  String _selectedGender = 'MALE';
  bool _initialized = false;

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

  void _initFromPatient() {
    if (_initialized) return;
    final patient = ref.read(patientProvider).patient;
    if (patient == null) return;

    _fullNameController.text = patient.fullName;
    _phoneController.text = patient.phone ?? '';
    _idCardController.text = patient.idCardNumber ?? '';
    _insuranceController.text = patient.insuranceNumber ?? '';
    _occupationController.text = patient.occupation ?? '';
    _addressController.text = patient.address ?? '';
    _selectedDate = patient.dateOfBirth;
    _selectedGender = patient.gender ?? 'MALE';
    _initialized = true;
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(patientProvider);

    // Initialize form fields from patient data
    _initFromPatient();

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Chỉnh sửa hồ sơ'),
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
                    style:
                        const TextStyle(color: AppColors.error, fontSize: 13),
                  ),
                ),
              // Save button
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: state.isLoading ? null : _handleSave,
                  child: state.isLoading
                      ? const SizedBox(
                          height: 20,
                          width: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Text('LƯU THAY ĐỔI'),
                ),
              ),
              const SizedBox(height: AppSpacing.xxl),
            ],
          ),
        ),
      ),
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
        decoration: const InputDecoration(
          labelText: 'Ngày sinh *',
          prefixIcon: Icon(Icons.calendar_today_outlined),
          suffixIcon: Icon(Icons.arrow_drop_down),
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
    return TextFormField(
      controller: _idCardController,
      keyboardType: TextInputType.number,
      decoration: const InputDecoration(
        labelText: 'Số CCCD *',
        prefixIcon: Icon(Icons.credit_card_outlined),
      ),
      validator: (value) {
        if (value == null || value.trim().isEmpty) {
          return 'Vui lòng nhập số CCCD';
        }
        return null;
      },
    );
  }

  Widget _buildInsuranceField() {
    return TextFormField(
      controller: _insuranceController,
      decoration: const InputDecoration(
        labelText: 'Mã BHYT',
        prefixIcon: Icon(Icons.health_and_safety_outlined),
      ),
    );
  }

  Widget _buildOccupationField() {
    return TextFormField(
      controller: _occupationController,
      decoration: const InputDecoration(
        labelText: 'Nghề nghiệp *',
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
        prefixIcon: Icon(Icons.location_on_outlined),
        alignLabelWithHint: true,
      ),
    );
  }

  Future<void> _handleSave() async {
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

    final patient = ref.read(patientProvider).patient;
    if (patient == null) return;

    final data = {
      'userId': patient.userId,
      'fullName': _fullNameController.text.trim(),
      'dateOfBirth':
          '${_selectedDate!.year}-${_selectedDate!.month.toString().padLeft(2, '0')}-${_selectedDate!.day.toString().padLeft(2, '0')}',
      'gender': _selectedGender,
      'idCardNumber': _idCardController.text.trim(),
      'occupation': _occupationController.text.trim(),
      'phone': _phoneController.text.trim(),
      'insuranceNumber': _insuranceController.text.trim(),
      'address': _addressController.text.trim(),
    };

    final success = await ref
        .read(patientProvider.notifier)
        .updatePatient(patient.id, data);

    if (success && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Cập nhật hồ sơ thành công!'),
          backgroundColor: AppColors.success,
        ),
      );
      context.pop();
    }
  }
}
