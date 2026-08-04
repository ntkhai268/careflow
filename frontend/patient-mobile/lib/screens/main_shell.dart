import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../config/theme.dart';
import '../features/journey/application/journey_providers.dart';
import '../providers/patient_provider.dart';
import 'appointment/appointment_screen.dart';
import 'home/home_screen.dart';
import 'notification/notification_screen.dart';
import 'profile/account_screen.dart';
import 'profile/profile_screen.dart';

/// Main shell with five task-oriented destinations.
class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key, this.initialIndex = 0});

  final int initialIndex;

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> {
  late int _currentIndex = widget.initialIndex.clamp(0, 4).toInt();

  @override
  void didUpdateWidget(covariant MainShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.initialIndex != widget.initialIndex) {
      setState(() => _currentIndex = widget.initialIndex.clamp(0, 4).toInt());
    }
  }

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex.clamp(0, 4);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final patientState = ref.read(patientProvider);
      if (!patientState.isLoading && patientState.patient == null) {
        ref.read(patientProvider.notifier).loadPatient();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final unreadCount = ref.watch(unreadJourneyNotificationCountProvider);
    final screens = [
      HomeScreen(
        onAppointmentsTap: () => _selectTab(1),
        onRecordsTap: () => _selectTab(2),
        onNotificationTap: () => _selectTab(3),
        onResultsTap: () => context.push('/visit-results'),
      ),
      const AppointmentScreen(),
      const ProfileScreen(),
      const NotificationScreen(),
      const AccountScreen(),
    ];

    return Scaffold(
      body: IndexedStack(index: _currentIndex, children: screens),
      bottomNavigationBar: DecoratedBox(
        decoration: BoxDecoration(
          color: AppColors.surface,
          border: const Border(top: BorderSide(color: AppColors.cardBorder)),
          boxShadow: AppShadows.bottomNav,
        ),
        child: SafeArea(
          top: false,
          child: NavigationBar(
            selectedIndex: _currentIndex,
            onDestinationSelected: _selectTab,
            destinations: [
              const NavigationDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home_rounded),
                label: 'Trang chủ',
              ),
              const NavigationDestination(
                icon: Icon(Icons.event_note_outlined),
                selectedIcon: Icon(Icons.event_note_rounded),
                label: 'Lịch khám',
              ),
              const NavigationDestination(
                icon: Icon(Icons.folder_shared_outlined),
                selectedIcon: Icon(Icons.folder_shared_rounded),
                label: 'Hồ sơ',
              ),
              NavigationDestination(
                icon: _notificationIcon(
                  Icons.notifications_outlined,
                  unreadCount,
                ),
                selectedIcon: _notificationIcon(
                  Icons.notifications_rounded,
                  unreadCount,
                ),
                label: 'Thông báo',
              ),
              const NavigationDestination(
                icon: Icon(Icons.person_outline_rounded),
                selectedIcon: Icon(Icons.person_rounded),
                label: 'Tài khoản',
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _selectTab(int index) {
    if (index == _currentIndex) return;
    setState(() => _currentIndex = index);
  }

  Widget _notificationIcon(IconData icon, int badgeCount) => badgeCount == 0
      ? Icon(icon)
      : Badge(
          label: Text(badgeCount > 99 ? '99+' : '$badgeCount'),
          backgroundColor: AppColors.error,
          child: Icon(icon),
        );
}
