import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../providers/health_record_provider.dart';

class HealthRecordListScreen extends ConsumerWidget {
  final String patientId;
  final String patientName;
  final String patientGender;
  final int patientBirthYear;

  const HealthRecordListScreen({
    Key? key,
    required this.patientId,
    required this.patientName,
    required this.patientGender,
    required this.patientBirthYear,
  }) : super(key: key);

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final healthRecordsAsync = ref.watch(healthRecordsProvider(patientId));

    return Scaffold(
      backgroundColor: const Color(0xFFF5F7FA),
      appBar: AppBar(
        title: const Text(
          'Hồ sơ sức khỏe',
          style: TextStyle(color: Colors.white),
        ),
        backgroundColor: const Color(0xFF29B6F6),
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Patient info card
          Container(
            color: Colors.white,
            padding: const EdgeInsets.all(16.0),
            child: Row(
              children: [
                CircleAvatar(
                  backgroundColor: const Color(0xFFE1F5FE),
                  child: const Icon(Icons.person, color: Color(0xFF29B6F6)),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        patientName,
                        style: const TextStyle(
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '$patientGender - $patientBirthYear',
                        style: const TextStyle(
                          color: Colors.grey,
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right, color: Colors.grey),
              ],
            ),
          ),
          const Padding(
            padding: EdgeInsets.all(16.0),
            child: Text(
              'Quản lý thông tin sức khỏe',
              style: TextStyle(
                fontWeight: FontWeight.bold,
                fontSize: 16,
                color: Colors.black87,
              ),
            ),
          ),
          Expanded(
            child: healthRecordsAsync.when(
              data: (records) {
                if (records.isEmpty) {
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: const [
                        Icon(Icons.monitor_heart, size: 80, color: Colors.grey),
                        SizedBox(height: 16),
                        Text(
                          'Chưa có thông tin sức khỏe',
                          style: TextStyle(color: Colors.grey, fontSize: 16),
                        ),
                      ],
                    ),
                  );
                }
                return RefreshIndicator(
                  onRefresh: () async {
                    ref.invalidate(healthRecordsProvider(patientId));
                  },
                  child: ListView.builder(
                    itemCount: records.length,
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    itemBuilder: (context, index) {
                      final record = records[index];
                      return Card(
                        margin: const EdgeInsets.only(bottom: 16),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(16.0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment:
                                    MainAxisAlignment.spaceBetween,
                                children: [
                                  Expanded(
                                    child: Text(
                                      record.title,
                                      style: const TextStyle(
                                        color: Color(0xFF29B6F6),
                                        fontWeight: FontWeight.bold,
                                        fontSize: 16,
                                      ),
                                    ),
                                  ),
                                  TextButton(
                                    onPressed: () {
                                      context.push(
                                        '/patient/$patientId/health-records/${record.id}',
                                      );
                                    },
                                    child: const Text(
                                      'Chi tiết',
                                      style: TextStyle(
                                        color: Color(0xFF29B6F6),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                'Ngày: ${record.recordDate.day}/${record.recordDate.month}/${record.recordDate.year}',
                              ),
                              const SizedBox(height: 4),
                              Text('Tên cơ sở y tế: ${record.facilityName}'),
                              if (record.files != null &&
                                  record.files!.isNotEmpty) ...[
                                const SizedBox(height: 12),
                                Row(
                                  children: [
                                    const Icon(
                                      Icons.attach_file,
                                      size: 16,
                                      color: Colors.grey,
                                    ),
                                    const SizedBox(width: 4),
                                    Expanded(
                                      child: Text(
                                        record.files!.first.fileName,
                                        style: const TextStyle(
                                          color: Colors.blue,
                                          decoration: TextDecoration.underline,
                                        ),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ),
                                  ],
                                ),
                              ],
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, st) => Center(child: Text('Lỗi: $e')),
            ),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Container(
            decoration: BoxDecoration(
              gradient: const LinearGradient(
                colors: [Color(0xFF26C6DA), Color(0xFF00BFA5)],
              ),
              borderRadius: BorderRadius.circular(8),
            ),
            child: ElevatedButton(
              onPressed: () {
                context.push('/health-records/$patientId/add');
              },
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.transparent,
                shadowColor: Colors.transparent,
                padding: const EdgeInsets.symmetric(vertical: 16),
              ),
              child: const Text(
                'Tải lên',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
            ),
          ),
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () {
          context.push('/health-records/$patientId/add');
        },
        backgroundColor: const Color(0xFF26C6DA),
        child: const Icon(Icons.medical_information, color: Colors.white),
      ),
    );
  }
}
