import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../services/health_record_service.dart';
import '../../models/health_record.dart';

class HealthRecordDetailScreen extends ConsumerStatefulWidget {
  final String patientId;
  final String recordId;

  const HealthRecordDetailScreen({
    Key? key,
    required this.patientId,
    required this.recordId,
  }) : super(key: key);

  @override
  ConsumerState<HealthRecordDetailScreen> createState() => _HealthRecordDetailScreenState();
}

class _HealthRecordDetailScreenState extends ConsumerState<HealthRecordDetailScreen> {
  late Future<HealthRecord> _recordFuture;

  @override
  void initState() {
    super.initState();
    _loadRecord();
  }

  void _loadRecord() {
    _recordFuture = ref.read(healthRecordServiceProvider).getById(widget.patientId, widget.recordId);
  }

  Future<void> _deleteRecord() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Xóa hồ sơ?'),
        content: const Text('Bạn có chắc chắn muốn xóa hồ sơ này?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Hủy')),
          TextButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Xóa', style: TextStyle(color: Colors.red))),
        ],
      ),
    );

    if (confirm == true) {
      try {
        await ref.read(healthRecordServiceProvider).delete(widget.patientId, widget.recordId);
        if (mounted) context.pop();
      } catch (e) {
        if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Lỗi: $e')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FA),
      appBar: AppBar(
        title: const Text('Chi tiết hồ sơ', style: TextStyle(color: Colors.white)),
        backgroundColor: const Color(0xFF29B6F6),
        iconTheme: const IconThemeData(color: Colors.white),
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
              Card(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(record.title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: Color(0xFF29B6F6))),
                      const SizedBox(height: 8),
                      Text('Ngày: ${record.recordDate.day}/${record.recordDate.month}/${record.recordDate.year}'),
                      const SizedBox(height: 4),
                      Text('Cơ sở y tế: ${record.facilityName}'),
                      if (record.notes != null && record.notes!.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        Text('Ghi chú: ${record.notes}', style: const TextStyle(color: Colors.grey)),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              
              if (record.bloodSugar != null || record.bloodPressure != null || record.heightCm != null || record.weightKg != null)
                Card(
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Chỉ số sức khỏe', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                        const Divider(),
                        if (record.bloodSugar != null) Text('Đường huyết: ${record.bloodSugar} mmol/L'),
                        if (record.bloodPressure != null) Text('Huyết áp: ${record.bloodPressure} mmHg'),
                        if (record.heightCm != null) Text('Chiều cao: ${record.heightCm} cm'),
                        if (record.weightKg != null) Text('Cân nặng: ${record.weightKg} kg'),
                        if (record.bmi != null) Text('BMI: ${record.bmi}'),
                        if (record.bloodType != null) Text('Nhóm máu: ${record.bloodType}'),
                      ],
                    ),
                  ),
                ),
              
              const SizedBox(height: 16),
              
              if (record.files != null && record.files!.isNotEmpty)
                Card(
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Tệp đính kèm', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                        const Divider(),
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: record.files!.map((file) {
                            return Chip(
                              label: Text(file.fileName),
                              avatar: const Icon(Icons.attach_file),
                            );
                          }).toList(),
                        ),
                      ],
                    ),
                  ),
                ),
              
              const SizedBox(height: 24),
              ElevatedButton.icon(
                onPressed: _deleteRecord,
                icon: const Icon(Icons.delete, color: Colors.white),
                label: const Text('Xóa hồ sơ này', style: TextStyle(color: Colors.white)),
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.red,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
