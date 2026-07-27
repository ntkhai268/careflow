import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../screens/auth/splash_screen.dart';
import '../screens/auth/onboarding_screen.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/register_screen.dart';
import '../screens/main_shell.dart';
import '../screens/health_record/health_record_list_screen.dart';
import '../screens/health_record/health_record_form_screen.dart';
import '../screens/health_record/health_record_detail_screen.dart';

/// GoRouter configuration for CareFlow app.
final GoRouter appRouter = GoRouter(
  initialLocation: '/splash',
  debugLogDiagnostics: true,
  routes: [
    GoRoute(
      path: '/splash',
      builder: (context, state) => const SplashScreen(),
    ),
    GoRoute(
      path: '/onboarding',
      builder: (context, state) => const OnboardingScreen(),
    ),
    GoRoute(
      path: '/login',
      builder: (context, state) => const LoginScreen(),
    ),
    GoRoute(
      path: '/register',
      builder: (context, state) => const RegisterScreen(),
    ),
    GoRoute(
      path: '/',
      builder: (context, state) => const MainShell(),
    ),
    GoRoute(
      path: '/health-records/:patientId',
      builder: (context, state) {
        final patientId = state.pathParameters['patientId']!;
        final extra = state.extra as Map<String, dynamic>? ?? {};
        return HealthRecordListScreen(
          patientId: patientId,
          patientName: extra['patientName'] ?? '',
          patientGender: extra['patientGender'] ?? '',
          patientBirthYear: extra['patientBirthYear'] ?? '',
        );
      },
    ),
    GoRoute(
      path: '/health-records/:patientId/add',
      builder: (context, state) {
        final patientId = state.pathParameters['patientId']!;
        return HealthRecordFormScreen(patientId: patientId);
      },
    ),
    GoRoute(
      path: '/health-records/:patientId/detail/:recordId',
      builder: (context, state) {
        final patientId = state.pathParameters['patientId']!;
        final recordId = state.pathParameters['recordId']!;
        return HealthRecordDetailScreen(
          patientId: patientId,
          recordId: recordId,
        );
      },
    ),
  ],
  errorBuilder: (context, state) => Scaffold(
    body: Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.error_outline, size: 64, color: Colors.red),
          const SizedBox(height: 16),
          Text(
            'Trang không tìm thấy',
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 8),
          Text('Đường dẫn: ${state.uri}'),
          const SizedBox(height: 24),
          ElevatedButton(
            onPressed: () => context.go('/'),
            child: const Text('Về trang chủ'),
          ),
        ],
      ),
    ),
  ),
);
