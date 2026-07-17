import 'package:flutter/material.dart';
import '../config/theme.dart';
import 'home/home_screen.dart';
import 'profile/profile_screen.dart';
import 'appointment/appointment_screen.dart';
import 'notification/notification_screen.dart';
import 'profile/account_screen.dart';

/// Main shell with 5-tab Bottom Navigation Bar.
/// Tabs: Trang chủ, Hồ sơ, Phiếu khám, Thông báo, Tài khoản
class MainShell extends StatefulWidget {
  const MainShell({super.key});

  @override
  State<MainShell> createState() => _MainShellState();
}

class _MainShellState extends State<MainShell> {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    HomeScreen(),
    ProfileScreen(),
    AppointmentScreen(),
    NotificationScreen(),
    AccountScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: Container(
        decoration: BoxDecoration(
          color: AppColors.surface,
          boxShadow: AppShadows.bottomNav,
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: BottomNavigationBar(
              currentIndex: _currentIndex,
              onTap: (index) {
                setState(() => _currentIndex = index);
              },
              items: [
                _buildNavItem(
                  icon: Icons.home_outlined,
                  activeIcon: Icons.home_rounded,
                  label: 'Trang chủ',
                ),
                _buildNavItem(
                  icon: Icons.folder_shared_outlined,
                  activeIcon: Icons.folder_shared_rounded,
                  label: 'Hồ sơ',
                ),
                _buildNavItem(
                  icon: Icons.receipt_long_outlined,
                  activeIcon: Icons.receipt_long_rounded,
                  label: 'Phiếu khám',
                ),
                _buildNavItemWithBadge(
                  icon: Icons.notifications_outlined,
                  activeIcon: Icons.notifications_rounded,
                  label: 'Thông báo',
                  badgeCount: 330,
                ),
                _buildNavItem(
                  icon: Icons.person_outline_rounded,
                  activeIcon: Icons.person_rounded,
                  label: 'Tài khoản',
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  BottomNavigationBarItem _buildNavItem({
    required IconData icon,
    required IconData activeIcon,
    required String label,
  }) {
    return BottomNavigationBarItem(
      icon: Icon(icon),
      activeIcon: Icon(activeIcon),
      label: label,
    );
  }

  BottomNavigationBarItem _buildNavItemWithBadge({
    required IconData icon,
    required IconData activeIcon,
    required String label,
    required int badgeCount,
  }) {
    return BottomNavigationBarItem(
      icon: Badge(
        label: Text(
          badgeCount > 99 ? '99+' : '$badgeCount',
          style: const TextStyle(
            fontSize: 9,
            fontWeight: FontWeight.w600,
            color: Colors.white,
          ),
        ),
        backgroundColor: AppColors.error,
        child: Icon(icon),
      ),
      activeIcon: Badge(
        label: Text(
          badgeCount > 99 ? '99+' : '$badgeCount',
          style: const TextStyle(
            fontSize: 9,
            fontWeight: FontWeight.w600,
            color: Colors.white,
          ),
        ),
        backgroundColor: AppColors.error,
        child: Icon(activeIcon),
      ),
      label: label,
    );
  }
}
