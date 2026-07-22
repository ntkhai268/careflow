import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../screens/auth/splash_screen.dart';
import '../screens/auth/onboarding_screen.dart';
import '../screens/auth/login_screen.dart';
import '../screens/auth/register_screen.dart';
import '../screens/main_shell.dart';
import '../screens/profile/create_profile_screen.dart';
import '../screens/profile/profile_detail_screen.dart';
import '../screens/profile/edit_profile_screen.dart';
import '../screens/appointment/booking_step1_screen.dart';
import '../screens/appointment/booking_step2_screen.dart';
import '../screens/appointment/booking_step3_screen.dart';
import '../screens/appointment/booking_step4_screen.dart';
import '../screens/appointment/appointment_detail_screen.dart';
import '../models/patient.dart';
import '../models/appointment.dart';

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
    // Profile routes
    GoRoute(
      path: '/profile/create',
      builder: (context, state) => const CreateProfileScreen(),
    ),
    GoRoute(
      path: '/profile/:id',
      builder: (context, state) => const ProfileDetailScreen(),
    ),
    GoRoute(
      path: '/profile/:id/edit',
      builder: (context, state) => const EditProfileScreen(),
    ),
    // Booking flow routes
    GoRoute(
      path: '/booking/step1',
      builder: (context, state) => const BookingStep1Screen(),
    ),
    GoRoute(
      path: '/booking/step2',
      builder: (context, state) {
        final patient = state.extra as Patient;
        return BookingStep2Screen(patient: patient);
      },
    ),
    GoRoute(
      path: '/booking/step3',
      builder: (context, state) {
        final data = state.extra as Map<String, dynamic>;
        return BookingStep3Screen(
          patient: data['patient'] as Patient,
          department: data['department'] as Department,
        );
      },
    ),
    GoRoute(
      path: '/booking/step4',
      builder: (context, state) {
        final data = state.extra as Map<String, dynamic>;
        return BookingStep4Screen(
          patient: data['patient'] as Patient,
          department: data['department'] as Department,
          date: data['date'] as DateTime,
          timeSlot: data['timeSlot'] as String,
        );
      },
    ),
    // Appointment detail
    GoRoute(
      path: '/appointment/:id',
      builder: (context, state) {
        final id = state.pathParameters['id']!;
        return AppointmentDetailScreen(appointmentId: id);
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

