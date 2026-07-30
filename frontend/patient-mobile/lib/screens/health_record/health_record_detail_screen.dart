import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../services/health_record_service.dart';
import '../../providers/health_record_provider.dart';
import '../../models/health_record.dart';
import '../../config/theme.dart';

class HealthRecordDetailScreen extends ConsumerStatefulWidget {
  final String patientId;
  final String recordId;

  const HealthRecordDetailScreen({
    super.key,
    required this.patientId,
    required this.recordId,
  });

  @override
  ConsumerState<HealthRecordDetailScreen> createState() =>
      _HealthRecordDetailScreenState();
}

class _HealthRecordDetailScreenState
    extends ConsumerState<HealthRecordDetailScreen> {
  late Future<HealthRecord> _recordFuture;
  bool _isEditing = false;
  bool _isSaving = false;

  // Controllers for edit mode
  final _titleController = TextEditingController();
  final _facilityController = TextEditingController();
  final _notesController = TextEditingController();
  final _bloodSugarController = TextEditingController();
  final _bloodPressureController = TextEditingController();
  final _heightController = TextEditingController();
  final _weightController = TextEditingController();
  final _waistController = TextEditingController();
  final _pulseController = TextEditingController();
  final _temperatureController = TextEditingController();
  final _respiratoryRateController = TextEditingController();

  String? _bloodType;
  String? _drugAllergy;
  String? _chemicalAllergy;
  String? _foodAllergy;
  String? _heartDisease;
  String? _hypertension;
  String? _mentalIllness;
  String? _cancer;
  String? _asthma;
  String? _epilepsy;
  String? _tuberculosis;

  HealthRecord? _record;

  @override
  void initState() {
    super.initState();
    _loadRecord();
  }

  void _loadRecord() {
    _recordFuture = ref
        .read(healthRecordServiceProvider)
        .getById(widget.patientId, widget.recordId);
    _recordFuture.then((record) {
      _record = record;
      _populateControllers(record);
    });
  }

  void _populateControllers(HealthRecord record) {
    _titleController.text = record.title;
    _facilityController.text = record.facilityName;
    _notesController.text = record.notes ?? '';
    _bloodSugarController.text = record.bloodSugar?.toString() ?? '';
    _bloodPressureController.text = record.bloodPressure ?? '';
    _heightController.text = record.heightCm?.toString() ?? '';
    _weightController.text = record.weightKg?.toString() ?? '';
    _waistController.text = record.waistCm?.toString() ?? '';
    _pulseController.text = record.pulse?.toString() ?? '';
    _temperatureController.text = record.temperature?.toString() ?? '';
    _respiratoryRateController.text = record.respiratoryRate?.toString() ?? '';
    _bloodType = record.bloodType;
    _drugAllergy = record.drugAllergy;
    _chemicalAllergy = record.chemicalAllergy;
    _foodAllergy = record.foodAllergy;
    _heartDisease = record.heartDisease;
    _hypertension = record.hypertension;
    _mentalIllness = record.mentalIllness;
    _cancer = record.cancer;
    _asthma = record.asthma;
    _epilepsy = record.epilepsy;
    _tuberculosis = record.tuberculosis;
  }

  String _getAllergyDisplay(String? value) {
    switch (value) {
      case 'YES':
        return 'Có';
      case 'NO':
        return 'Không';
      case 'UNKNOWN':
        return 'Không biết';
      default:
        return 'Chưa chọn';
    }
  }

  String _getBmiText() {
    final h = double.tryParse(_heightController.text);
    final w = double.tryParse(_weightController.text);
    if (h != null && w != null && h > 0) {
      final bmi = w / ((h / 100) * (h / 100));
      return bmi.toStringAsFixed(1);
    }
    return 'Chưa có';
  }

  Future<void> _deleteRecord() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xóa hồ sơ?'),
        content: const Text('Bạn có chắc chắn muốn xóa hồ sơ này?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Xóa', style: TextStyle(color: Colors.red)),
          ),
        ],
      ),
    );

    if (confirm == true) {
      try {
        await ref
            .read(healthRecordServiceProvider)
            .delete(widget.patientId, widget.recordId);
        ref.invalidate(healthRecordsProvider(widget.patientId));
        if (mounted) {
          context.pop();
        }
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(
            context,
          ).showSnackBar(SnackBar(content: Text('Lỗi: $e')));
        }
      }
    }
  }

  Future<void> _saveRecord() async {
    setState(() => _isSaving = true);
    try {
      final data = {
        'title': _titleController.text,
        'facilityName': _facilityController.text,
        'notes': _notesController.text,
        'bloodSugar': double.tryParse(_bloodSugarController.text),
        'bloodPressure': _bloodPressureController.text.isEmpty
            ? null
            : _bloodPressureController.text,
        'heightCm': double.tryParse(_heightController.text),
        'weightKg': double.tryParse(_weightController.text),
        'waistCm': double.tryParse(_waistController.text),
        'bloodType': _bloodType,
        'pulse': int.tryParse(_pulseController.text),
        'temperature': double.tryParse(_temperatureController.text),
        'respiratoryRate': int.tryParse(_respiratoryRateController.text),
        'drugAllergy': _drugAllergy,
        'chemicalAllergy': _chemicalAllergy,
        'foodAllergy': _foodAllergy,
        'heartDisease': _heartDisease,
        'hypertension': _hypertension,
        'mentalIllness': _mentalIllness,
        'cancer': _cancer,
        'asthma': _asthma,
        'epilepsy': _epilepsy,
        'tuberculosis': _tuberculosis,
      };

      final service = ref.read(healthRecordServiceProvider);
      final updated = await service.update(
        widget.patientId,
        widget.recordId,
        data,
      );
      _record = updated;
      _populateControllers(updated);
      ref.invalidate(healthRecordsProvider(widget.patientId));

      if (mounted) {
        setState(() {
          _isEditing = false;
          _isSaving = false;
          _recordFuture = Future.value(updated);
        });
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Cập nhật thành công!')));
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSaving = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Lỗi: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FA),
      appBar: AppBar(
        title: Text(
          _isEditing ? 'Chỉnh sửa hồ sơ' : 'Chi tiết hồ sơ',
          style: const TextStyle(color: Colors.white),
        ),
        backgroundColor: const Color(0xFF29B6F6),
        iconTheme: const IconThemeData(color: Colors.white),
        actions: [
          if (!_isEditing)
            IconButton(
              icon: const Icon(Icons.edit, color: Colors.white),
              tooltip: 'Chỉnh sửa',
              onPressed: () => setState(() => _isEditing = true),
            ),
          IconButton(
            icon: const Icon(Icons.delete_outline, color: Colors.white),
            tooltip: 'Xóa',
            onPressed: _deleteRecord,
          ),
        ],
      ),
      body: FutureBuilder<HealthRecord>(
        future: _recordFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(child: Text('Lỗi: ${snapshot.error}'));
          }
          if (!snapshot.hasData) {
            return const Center(child: Text('Không tìm thấy dữ liệu'));
          }

          final record = snapshot.data!;
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // Thông tin chung
              ExpansionTile(
                title: const Text(
                  'Thông tin chung',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                initiallyExpanded: true,
                childrenPadding: const EdgeInsets.all(16),
                children: [
                  _buildTextField('Tiêu đề', _titleController),
                  const SizedBox(height: 16),
                  _buildReadOnlyField(
                    'Ngày',
                    '${record.recordDate.day.toString().padLeft(2, '0')}/${record.recordDate.month.toString().padLeft(2, '0')}/${record.recordDate.year}',
                  ),
                  const SizedBox(height: 16),
                  _buildTextField('Tên cơ sở y tế', _facilityController),
                  const SizedBox(height: 16),
                  _buildTextField('Ghi chú', _notesController, maxLines: 3),
                  if (record.files != null && record.files!.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    const Text(
                      'Hình ảnh/Tệp',
                      style: TextStyle(fontWeight: FontWeight.w500),
                    ),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: record.files!
                          .map(
                            (file) => ActionChip(
                              label: Text(file.fileName),
                              avatar: const Icon(Icons.attach_file, size: 18),
                              onPressed: () {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text('Tải file: ${file.fileName}'),
                                  ),
                                );
                                // TODO: Use url_launcher to open URL
                              },
                            ),
                          )
                          .toList(),
                    ),
                  ],
                ],
              ),

              // Chỉ số sức khỏe
              ExpansionTile(
                title: const Text(
                  'Chỉ số sức khỏe',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                childrenPadding: const EdgeInsets.all(16),
                children: [
                  _buildTextField(
                    'Đường huyết (mmol/L)',
                    _bloodSugarController,
                    keyboardType: TextInputType.number,
                  ),
                  const SizedBox(height: 16),
                  _buildTextField('Huyết áp (mmHg)', _bloodPressureController),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: _buildTextField(
                          'Chiều cao (cm)',
                          _heightController,
                          keyboardType: TextInputType.number,
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: _buildTextField(
                          'Cân nặng (kg)',
                          _weightController,
                          keyboardType: TextInputType.number,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  _buildReadOnlyField('BMI', _getBmiText()),
                  const SizedBox(height: 16),
                  _buildTextField(
                    'Vòng bụng (cm)',
                    _waistController,
                    keyboardType: TextInputType.number,
                  ),
                  const SizedBox(height: 16),
                  _isEditing
                      ? DropdownButtonFormField<String>(
                          decoration: const InputDecoration(
                            labelText: 'Nhóm máu',
                            border: OutlineInputBorder(),
                          ),
                          initialValue: _bloodType,
                          items: ['A', 'B', 'AB', 'O']
                              .map(
                                (e) =>
                                    DropdownMenuItem(value: e, child: Text(e)),
                              )
                              .toList(),
                          onChanged: (v) => setState(() => _bloodType = v),
                        )
                      : _buildReadOnlyField(
                          'Nhóm máu',
                          _bloodType ?? 'Chưa chọn',
                        ),
                  const SizedBox(height: 16),
                  _buildTextField(
                    'Mạch (lần/phút)',
                    _pulseController,
                    keyboardType: TextInputType.number,
                  ),
                  const SizedBox(height: 16),
                  _buildTextField(
                    'Nhiệt độ (°C)',
                    _temperatureController,
                    keyboardType: TextInputType.number,
                  ),
                  const SizedBox(height: 16),
                  _buildTextField(
                    'Nhịp thở (lần/phút)',
                    _respiratoryRateController,
                    keyboardType: TextInputType.number,
                  ),
                ],
              ),

              // Tiền sử gia đình
              ExpansionTile(
                title: const Text(
                  'Tiền sử gia đình',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                childrenPadding: const EdgeInsets.all(16),
                children: [
                  _buildAllergyRow(
                    'Dị ứng thuốc',
                    _drugAllergy,
                    (v) => setState(() => _drugAllergy = v),
                  ),
                  _buildAllergyRow(
                    'Dị ứng hóa chất',
                    _chemicalAllergy,
                    (v) => setState(() => _chemicalAllergy = v),
                  ),
                  _buildAllergyRow(
                    'Dị ứng thực phẩm',
                    _foodAllergy,
                    (v) => setState(() => _foodAllergy = v),
                  ),
                ],
              ),

              // Tiền sử bệnh tật gia đình
              ExpansionTile(
                title: const Text(
                  'Tiền sử Bệnh tật gia đình',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                ),
                childrenPadding: const EdgeInsets.all(16),
                children: [
                  _buildAllergyRow(
                    'Tim mạch',
                    _heartDisease,
                    (v) => setState(() => _heartDisease = v),
                  ),
                  _buildAllergyRow(
                    'Tăng huyết áp',
                    _hypertension,
                    (v) => setState(() => _hypertension = v),
                  ),
                  _buildAllergyRow(
                    'Tâm thần',
                    _mentalIllness,
                    (v) => setState(() => _mentalIllness = v),
                  ),
                  _buildAllergyRow(
                    'Ung thư',
                    _cancer,
                    (v) => setState(() => _cancer = v),
                  ),
                  _buildAllergyRow(
                    'Hen suyễn',
                    _asthma,
                    (v) => setState(() => _asthma = v),
                  ),
                  _buildAllergyRow(
                    'Động kinh',
                    _epilepsy,
                    (v) => setState(() => _epilepsy = v),
                  ),
                  _buildAllergyRow(
                    'Lao',
                    _tuberculosis,
                    (v) => setState(() => _tuberculosis = v),
                  ),
                ],
              ),
            ],
          );
        },
      ),
      bottomNavigationBar: _isEditing
          ? SafeArea(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: _isSaving
                            ? null
                            : () {
                                setState(() => _isEditing = false);
                                if (_record != null) {
                                  _populateControllers(_record!);
                                }
                              },
                        style: OutlinedButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          side: const BorderSide(color: Colors.grey),
                        ),
                        child: const Text(
                          'Hủy',
                          style: TextStyle(color: Colors.grey),
                        ),
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: _isSaving ? null : _saveRecord,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.orange,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                        ),
                        child: _isSaving
                            ? const SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  color: Colors.white,
                                  strokeWidth: 2,
                                ),
                              )
                            : const Text(
                                'Lưu thay đổi',
                                style: TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                      ),
                    ),
                  ],
                ),
              ),
            )
          : null,
    );
  }

  Widget _buildTextField(
    String label,
    TextEditingController controller, {
    int maxLines = 1,
    TextInputType? keyboardType,
  }) {
    return TextFormField(
      controller: controller,
      readOnly: !_isEditing,
      maxLines: maxLines,
      keyboardType: keyboardType,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
        filled: !_isEditing,
        fillColor: _isEditing ? null : const Color(0xFFF5F5F5),
      ),
    );
  }

  Widget _buildReadOnlyField(String label, String value) {
    return TextFormField(
      initialValue: value,
      readOnly: true,
      decoration: InputDecoration(
        labelText: label,
        border: const OutlineInputBorder(),
        filled: true,
        fillColor: const Color(0xFFF5F5F5),
      ),
    );
  }

  Widget _buildAllergyRow(
    String title,
    String? groupValue,
    ValueChanged<String?> onChanged,
  ) {
    if (!_isEditing) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Row(
          children: [
            Expanded(
              flex: 2,
              child: Text(
                title,
                style: const TextStyle(fontWeight: FontWeight.w500),
              ),
            ),
            Expanded(
              flex: 1,
              child: Text(
                _getAllergyDisplay(groupValue),
                style: TextStyle(
                  color: groupValue == 'YES'
                      ? Colors.red
                      : AppColors.textPrimary,
                  fontWeight: groupValue == 'YES'
                      ? FontWeight.bold
                      : FontWeight.normal,
                ),
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'YES', label: Text('Có')),
            ButtonSegment(value: 'NO', label: Text('Không')),
            ButtonSegment(value: 'UNKNOWN', label: Text('Không biết')),
          ],
          selected: groupValue != null ? {groupValue} : {},
          emptySelectionAllowed: true,
          onSelectionChanged: (values) =>
              onChanged(values.isEmpty ? null : values.first),
          style: SegmentedButton.styleFrom(
            selectedBackgroundColor: AppColors.primarySurface,
            selectedForegroundColor: AppColors.primaryDark,
          ),
        ),
        const Divider(),
      ],
    );
  }
}
