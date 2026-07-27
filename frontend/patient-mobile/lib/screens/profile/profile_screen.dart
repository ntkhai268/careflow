import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../config/theme.dart';

/// Profile screen with navigation to Health Records
class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Hồ sơ bệnh nhân'),
        actions: [
          IconButton(
            icon: const Icon(Icons.person_add_outlined),
            onPressed: () {},
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _buildMenuCard(
            context,
            icon: Icons.health_and_safety_outlined,
            title: 'Hồ sơ sức khỏe',
            subtitle: 'Quản lý thông tin sức khỏe, tiền sử bệnh',
            onTap: () {
              // TODO: Replace with actual patient ID from auth state
              context.push('/health-records/placeholder-patient-id', extra: {
                'patientName': 'Bệnh nhân',
                'patientGender': 'Nam',
                'patientBirthYear': '2000',
              });
            },
          ),
          const SizedBox(height: 12),
          _buildMenuCard(
            context,
            icon: Icons.person_outline,
            title: 'Thông tin cá nhân',
            subtitle: 'Cập nhật thông tin hồ sơ',
            onTap: () {},
          ),
          const SizedBox(height: 12),
          _buildMenuCard(
            context,
            icon: Icons.settings_outlined,
            title: 'Cài đặt',
            subtitle: 'Ngôn ngữ, thông báo, bảo mật',
            onTap: () {},
          ),
        ],
      ),
    );
  }

  Widget _buildMenuCard(BuildContext context,
      {required IconData icon,
      required String title,
      required String subtitle,
      required VoidCallback onTap}) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: AppColors.cardBorder),
      ),
      child: ListTile(
        leading: Container(
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: AppColors.primarySurface,
            borderRadius: BorderRadius.circular(10),
          ),
          child: Icon(icon, color: AppColors.primary, size: 24),
        ),
        title: Text(title,
            style: const TextStyle(
                fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
        subtitle: Text(subtitle,
            style: const TextStyle(
                fontSize: 13, color: AppColors.textSecondary)),
        trailing:
            const Icon(Icons.chevron_right, color: AppColors.textSecondary),
        onTap: onTap,
      ),
    );
  }
}
