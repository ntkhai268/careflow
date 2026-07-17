import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../config/theme.dart';
import '../../providers/auth_provider.dart';

/// Account (Tài khoản) tab — matching Medpro design
class AccountScreen extends ConsumerWidget {
  const AccountScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: Column(
        children: [
          // Profile header with gradient
          Container(
            width: double.infinity,
            decoration: const BoxDecoration(
              gradient: AppColors.primaryGradient,
              borderRadius: BorderRadius.only(
                bottomLeft: Radius.circular(24),
                bottomRight: Radius.circular(24),
              ),
            ),
            child: SafeArea(
              bottom: false,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
                child: Column(
                  children: [
                    // Back button + title
                    Row(
                      children: [
                        const SizedBox(width: 40),
                        const Expanded(
                          child: Text(
                            'Tài khoản',
                            textAlign: TextAlign.center,
                            style: TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.w600,
                              color: Colors.white,
                            ),
                          ),
                        ),
                        const SizedBox(width: 40),
                      ],
                    ),
                    const SizedBox(height: 24),
                    // Avatar
                    Container(
                      width: 80,
                      height: 80,
                      decoration: BoxDecoration(
                        color: Colors.white,
                        shape: BoxShape.circle,
                        border: Border.all(
                            color: Colors.white.withValues(alpha: 0.5),
                            width: 3),
                      ),
                      child: Icon(Icons.person,
                          size: 44, color: AppColors.textHint),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      authState.email ?? 'user@careflow.vn',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: Colors.white,
                      ),
                    ),
                    const SizedBox(height: 12),
                    // Logout button
                    OutlinedButton(
                      onPressed: () async {
                        await ref.read(authProvider.notifier).logout();
                        if (context.mounted) {
                          context.go('/login');
                        }
                      },
                      style: OutlinedButton.styleFrom(
                        foregroundColor: Colors.white,
                        side: const BorderSide(color: Colors.white, width: 1),
                        padding: const EdgeInsets.symmetric(
                            horizontal: 20, vertical: 8),
                        shape: RoundedRectangleBorder(
                          borderRadius:
                              BorderRadius.circular(AppRadius.full),
                        ),
                      ),
                      child: const Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text('Đăng xuất',
                              style: TextStyle(fontSize: 13)),
                          SizedBox(width: 4),
                          Icon(Icons.chevron_right, size: 18),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),

          // Menu items
          Expanded(
            child: ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const SizedBox(height: 8),
                _buildSectionTitle('Điều khoản và quy định'),
                const SizedBox(height: 8),
                _buildMenuItem(
                  icon: Icons.shield_outlined,
                  iconColor: AppColors.accent,
                  title: 'Quy định sử dụng',
                  onTap: () {},
                ),
                _buildMenuItem(
                  icon: Icons.lock_outline,
                  iconColor: AppColors.warning,
                  title: 'Chính sách bảo mật',
                  onTap: () {},
                ),
                _buildMenuItem(
                  icon: Icons.description_outlined,
                  iconColor: AppColors.error,
                  title: 'Điều khoản dịch vụ',
                  onTap: () {},
                ),

                const SizedBox(height: 16),
                const Divider(),
                const SizedBox(height: 8),

                _buildMenuItem(
                  icon: Icons.medical_information_outlined,
                  iconColor: AppColors.primary,
                  title: 'Xem/Lưu thông tin sức khỏe',
                  onTap: () {},
                ),
                _buildMenuItem(
                  icon: Icons.phone_outlined,
                  iconColor: AppColors.accent,
                  title: 'Hỗ trợ tư vấn/đặt khám',
                  onTap: () {},
                ),
                _buildMenuItem(
                  icon: Icons.thumb_up_outlined,
                  iconColor: AppColors.warning,
                  title: 'Đánh giá ứng dụng',
                  onTap: () {},
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w700,
        color: AppColors.textPrimary,
      ),
    );
  }

  Widget _buildMenuItem({
    required IconData icon,
    required Color iconColor,
    required String title,
    required VoidCallback onTap,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      child: ListTile(
        onTap: onTap,
        contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
        leading: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: iconColor.withValues(alpha: 0.1),
            borderRadius: BorderRadius.circular(AppRadius.sm),
          ),
          child: Icon(icon, color: iconColor, size: 20),
        ),
        title: Text(
          title,
          style: const TextStyle(
            fontSize: 15,
            fontWeight: FontWeight.w500,
            color: AppColors.textPrimary,
          ),
        ),
        trailing: const Icon(Icons.chevron_right,
            color: AppColors.textHint, size: 20),
      ),
    );
  }
}
