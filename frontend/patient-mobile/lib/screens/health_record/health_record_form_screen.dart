import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:file_picker/file_picker.dart';
import '../../services/health_record_service.dart';
import '../../providers/health_record_provider.dart';
import '../../config/theme.dart';

class HealthRecordFormScreen extends ConsumerStatefulWidget {
  final String patientId;

  const HealthRecordFormScreen({Key? key, required this.patientId}) : super(key: key);

  @override
  ConsumerState<HealthRecordFormScreen> createState() => _HealthRecordFormScreenState();
}

class _HealthRecordFormScreenState extends ConsumerState<HealthRecordFormScreen> {
  final _formKey = GlobalKey<FormState>();
  
  final _titleController = TextEditingController();
  final _dateController = TextEditingController();
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

  DateTime? _selectedDate;
  final List<File> _selectedFiles = [];
  bool _isLoading = false;

  void _calculateBmi() {
    setState(() {});
  }
  
  String _getBmiText() {
    final h = double.tryParse(_heightController.text);
    final w = double.tryParse(_weightController.text);
    if (h != null && w != null && h > 0) {
      final bmi = w / ((h / 100) * (h / 100));
      return bmi.toStringAsFixed(1);
    }
    return "Chưa có";
  }

  Future<void> _pickFiles() async {
    final result = await FilePicker.platform.pickFiles(
      allowMultiple: true,
      type: FileType.custom,
      allowedExtensions: ['png', 'jpg', 'jpeg', 'doc', 'docx', 'pdf'],
    );
    if (result != null && result.files.isNotEmpty) {
      setState(() {
        _selectedFiles.addAll(
          result.paths.where((p) => p != null).map((p) => File(p!)),
        );
      });
    }
  }

  Future<void> _selectDate() async {
    final date = await showDatePicker(
      context: context,
      initialDate: DateTime.now(),
      firstDate: DateTime(1900),
      lastDate: DateTime.now(),
    );
    if (date != null) {
      setState(() {
        _selectedDate = date;
        _dateController.text = '${date.day.toString().padLeft(2, '0')}/${date.month.toString().padLeft(2, '0')}/${date.year}';
      });
    }
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    if (_selectedDate == null) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Vui lòng chọn ngày')));
      return;
    }

    setState(() => _isLoading = true);
    
    try {
      final data = {
        'title': _titleController.text,
        'recordDate': _selectedDate!.toIso8601String(),
        'facilityName': _facilityController.text,
        'notes': _notesController.text,
        'bloodSugar': double.tryParse(_bloodSugarController.text),
        'bloodPressure': _bloodPressureController.text,
        'heightCm': double.tryParse(_heightController.text),
        'weightKg': double.tryParse(_weightController.text),
        'bmi': double.tryParse(_getBmiText()),
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
      await service.create(widget.patientId, data, _selectedFiles.isEmpty ? null : _selectedFiles);
      
      // Invalidate provider so list screen refreshes
      ref.invalidate(healthRecordsProvider(widget.patientId));
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Tạo hồ sơ sức khỏe thành công!')),
        );
        context.pop();
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Lỗi: $e')));
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FA),
      appBar: AppBar(
        title: const Text('Thêm thông tin sức khỏe', style: TextStyle(color: Colors.white)),
        backgroundColor: const Color(0xFF29B6F6),
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            ExpansionTile(
              title: const Text('Thông tin chung', style: TextStyle(fontWeight: FontWeight.bold)),
              initiallyExpanded: true,
              childrenPadding: const EdgeInsets.all(16),
              children: [
                TextFormField(
                  decoration: const InputDecoration(labelText: 'Hồ sơ', border: OutlineInputBorder()),
                  initialValue: 'Tên bệnh nhân',
                  readOnly: true,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _titleController,
                  decoration: const InputDecoration(labelText: 'Tiêu đề*', hintText: 'Ví dụ: Khám mắt, tầm soát ung thư', border: OutlineInputBorder()),
                  validator: (val) => val == null || val.isEmpty ? 'Vui lòng nhập tiêu đề' : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _dateController,
                  decoration: const InputDecoration(labelText: 'Ngày*', suffixIcon: Icon(Icons.calendar_today), border: OutlineInputBorder()),
                  readOnly: true,
                  onTap: _selectDate,
                  validator: (val) => val == null || val.isEmpty ? 'Vui lòng chọn ngày' : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _facilityController,
                  decoration: const InputDecoration(labelText: 'Tên cơ sở y tế*', hintText: 'Nhập cơ sở y tế', border: OutlineInputBorder()),
                  validator: (val) => val == null || val.isEmpty ? 'Vui lòng nhập tên cơ sở' : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _notesController,
                  decoration: const InputDecoration(labelText: 'Ghi chú', hintText: 'Ghi chú chung cho đợt khám', border: OutlineInputBorder()),
                  maxLines: 3,
                ),
                const SizedBox(height: 16),
                InkWell(
                  onTap: _pickFiles,
                  child: Container(
                    padding: const EdgeInsets.all(24),
                    decoration: BoxDecoration(
                      border: Border.all(color: Colors.grey, style: BorderStyle.solid),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Column(
                      children: const [
                        Icon(Icons.add_photo_alternate, size: 40, color: Colors.grey),
                        SizedBox(height: 8),
                        Text('Tải lên Hình ảnh/Tệp', style: TextStyle(color: Colors.grey)),
                      ],
                    ),
                  ),
                ),
                if (_selectedFiles.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: _selectedFiles.map((f) => Chip(
                      label: Text(f.path.split('/').last),
                      onDeleted: () {
                        setState(() => _selectedFiles.remove(f));
                      },
                    )).toList(),
                  )
                ],
                const SizedBox(height: 8),
                const Text('*Ghi chú: Định dạng .png, .jpg, .doc, .docx, .pdf', style: TextStyle(fontSize: 12, color: Colors.grey)),
              ],
            ),
            
            ExpansionTile(
              title: const Text('Chỉ số sức khỏe', style: TextStyle(fontWeight: FontWeight.bold)),
              childrenPadding: const EdgeInsets.all(16),
              children: [
                TextFormField(
                  controller: _bloodSugarController,
                  decoration: const InputDecoration(labelText: 'Đường huyết (mmol/L)', hintText: '8.0', border: OutlineInputBorder()),
                  keyboardType: TextInputType.number,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _bloodPressureController,
                  decoration: const InputDecoration(labelText: 'Huyết áp (mmHg)', hintText: '120/80', border: OutlineInputBorder()),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: TextFormField(
                        controller: _heightController,
                        decoration: const InputDecoration(labelText: 'Chiều cao (cm)', border: OutlineInputBorder()),
                        keyboardType: TextInputType.number,
                        onChanged: (v) => _calculateBmi(),
                      ),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: TextFormField(
                        controller: _weightController,
                        decoration: const InputDecoration(labelText: 'Cân nặng (kg)', border: OutlineInputBorder()),
                        keyboardType: TextInputType.number,
                        onChanged: (v) => _calculateBmi(),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                TextFormField(
                  decoration: InputDecoration(labelText: 'BMI', hintText: _getBmiText(), border: const OutlineInputBorder()),
                  readOnly: true,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _waistController,
                  decoration: const InputDecoration(labelText: 'Vòng bụng (cm)', border: OutlineInputBorder()),
                  keyboardType: TextInputType.number,
                ),
                const SizedBox(height: 16),
                DropdownButtonFormField<String>(
                  decoration: const InputDecoration(labelText: 'Nhóm máu', border: OutlineInputBorder()),
                  initialValue: _bloodType,
                  items: ['A', 'B', 'AB', 'O'].map((e) => DropdownMenuItem(value: e, child: Text(e))).toList(),
                  onChanged: (v) => setState(() => _bloodType = v),
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _pulseController,
                  decoration: const InputDecoration(labelText: 'Mạch (lần/phút)', border: OutlineInputBorder()),
                  keyboardType: TextInputType.number,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _temperatureController,
                  decoration: const InputDecoration(labelText: 'Nhiệt độ (°C)', border: OutlineInputBorder()),
                  keyboardType: TextInputType.number,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _respiratoryRateController,
                  decoration: const InputDecoration(labelText: 'Nhịp thở (lần/phút)', border: OutlineInputBorder()),
                  keyboardType: TextInputType.number,
                ),
              ],
            ),
            
            ExpansionTile(
              title: const Text('Tiền sử gia đình', style: TextStyle(fontWeight: FontWeight.bold)),
              childrenPadding: const EdgeInsets.all(16),
              children: [
                _buildRadioGroup('Dị ứng thuốc', _drugAllergy, (v) => setState(() => _drugAllergy = v)),
                _buildRadioGroup('Dị ứng hóa chất', _chemicalAllergy, (v) => setState(() => _chemicalAllergy = v)),
                _buildRadioGroup('Dị ứng thực phẩm', _foodAllergy, (v) => setState(() => _foodAllergy = v)),
              ],
            ),
            
            ExpansionTile(
              title: const Text('Tiền sử Bệnh tật gia đình', style: TextStyle(fontWeight: FontWeight.bold)),
              childrenPadding: const EdgeInsets.all(16),
              children: [
                _buildRadioGroup('Tim mạch', _heartDisease, (v) => setState(() => _heartDisease = v)),
                _buildRadioGroup('Tăng huyết áp', _hypertension, (v) => setState(() => _hypertension = v)),
                _buildRadioGroup('Tâm thần', _mentalIllness, (v) => setState(() => _mentalIllness = v)),
                _buildRadioGroup('Ung thư', _cancer, (v) => setState(() => _cancer = v)),
                _buildRadioGroup('Hen suyễn', _asthma, (v) => setState(() => _asthma = v)),
                _buildRadioGroup('Động kinh', _epilepsy, (v) => setState(() => _epilepsy = v)),
                _buildRadioGroup('Lao', _tuberculosis, (v) => setState(() => _tuberculosis = v)),
              ],
            ),
            
            const SizedBox(height: 24),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () => context.pop(),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      side: const BorderSide(color: Colors.grey),
                    ),
                    child: const Text('Hủy', style: TextStyle(color: Colors.grey)),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isLoading ? null : _submit,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.orange,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                    ),
                    child: _isLoading 
                        ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white))
                        : const Text('Hoàn tất', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  Widget _buildRadioGroup(String title, String? groupValue, ValueChanged<String?> onChanged) {
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
          onSelectionChanged: (values) => onChanged(values.isEmpty ? null : values.first),
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
