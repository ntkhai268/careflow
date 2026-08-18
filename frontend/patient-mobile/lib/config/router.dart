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
import '../screens/health_record/health_record_list_screen.dart';
import '../screens/health_record/health_record_form_screen.dart';
import '../screens/health_record/health_record_detail_screen.dart';
import '../screens/health_record/other_patient_health_screen.dart';
import '../screens/appointment/booking_step1_screen.dart';
import '../screens/appointment/booking_step2_screen.dart';
import '../screens/appointment/booking_step3_screen.dart';
import '../screens/appointment/booking_step4_screen.dart';
import '../screens/appointment/appointment_detail_screen.dart';
import '../features/journey/presentation/clinic_queue_screen.dart';
import '../features/journey/presentation/consultation_screen.dart';
import '../features/journey/presentation/journey_hub_screen.dart';
import '../features/journey/presentation/journey_timeline_screen.dart';
import '../features/journey/presentation/laboratory_screen.dart';
import '../features/journey/presentation/result_review_screen.dart';
import '../features/journey/presentation/visit_outcome_screen.dart';
import '../features/journey/presentation/visit_results_screen.dart';
import '../features/journey/presentation/visit_ticket_screen.dart';
import '../features/journey/presentation/hospital_qr_check_in_screen.dart';
import '../features/journey/domain/journey_models.dart';
import '../models/patient.dart';
import '../models/appointment.dart';

/// GoRouter configuration for CareFlow app.
final GoRouter appRouter = GoRouter(
  initialLocation: '/splash',
  debugLogDiagnostics: true,
  routes: [
    GoRoute(path: '/splash', builder: (context, state) => const SplashScreen()),
    GoRoute(
      path: '/onboarding',
      builder: (context, state) => const OnboardingScreen(),
    ),
    GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),
    GoRoute(
      path: '/register',
      builder: (context, state) => const RegisterScreen(),
    ),
    GoRoute(path: '/', builder: (context, state) => const MainShell()),
    GoRoute(
      path: '/appointments',
      builder: (context, state) => const MainShell(initialIndex: 1),
    ),
    GoRoute(
      path: '/visit-results',
      builder: (context, state) => const VisitResultsScreen(),
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
    // Health record routes
    GoRoute(
      path: '/patient/:patientId/health-records',
      builder: (context, state) {
        final patientId = state.pathParameters['patientId']!;
        final extra = state.extra as Map<String, dynamic>? ?? {};
        final birthYearRaw = extra['patientBirthYear'];
        final birthYear = birthYearRaw is int
            ? birthYearRaw
            : int.tryParse(birthYearRaw?.toString() ?? '') ?? 0;
        return HealthRecordListScreen(
          patientId: patientId,
          patientName: extra['patientName']?.toString() ?? '',
          patientGender: extra['patientGender']?.toString() ?? '',
          patientBirthYear: birthYear,
        );
      },
    ),
    GoRoute(
      path: '/health-records/patients',
      builder: (context, state) {
        final extra = state.extra as Map<String, dynamic>? ?? {};
        return OtherPatientHealthScreen(
          excludePatientId: extra['excludePatientId']?.toString(),
        );
      },
    ),
    GoRoute(
      path: '/patient/:patientId/health-records/new',
      builder: (context, state) {
        final patientId = state.pathParameters['patientId']!;
        return HealthRecordFormScreen(patientId: patientId);
      },
    ),
    GoRoute(
      path: '/patient/:patientId/health-records/:recordId',
      builder: (context, state) {
        final patientId = state.pathParameters['patientId']!;
        final recordId = state.pathParameters['recordId']!;
        return HealthRecordDetailScreen(
          patientId: patientId,
          recordId: recordId,
        );
      },
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
    GoRoute(
      path: '/journey/:appointmentId',
      builder: (context, state) => JourneyHubScreen(
        appointmentId: state.pathParameters['appointmentId']!,
      ),
      routes: [
        GoRoute(
          path: 'ticket',
          builder: (context, state) => VisitTicketScreen(
            appointmentId: state.pathParameters['appointmentId']!,
          ),
        ),
        GoRoute(
          path: 'check-in',
          builder: (context, state) => HospitalQrCheckInScreen(
            appointmentId: state.pathParameters['appointmentId']!,
          ),
        ),
        GoRoute(
          path: 'queue',
          builder: (context, state) => ClinicQueueScreen(
            appointmentId: state.pathParameters['appointmentId']!,
          ),
        ),
        GoRoute(
          path: 'consultation',
          builder: (context, state) => ConsultationScreen(
            appointmentId: state.pathParameters['appointmentId']!,
          ),
        ),
        GoRoute(
          path: 'laboratory',
          builder: (context, state) => LaboratoryScreen(
            appointmentId: state.pathParameters['appointmentId']!,
          ),
        ),
        GoRoute(
          path: 'result-review',
          builder: (context, state) => ResultReviewScreen(
            appointmentId: state.pathParameters['appointmentId']!,
          ),
        ),
        GoRoute(
          path: 'outcome',
          builder: (context, state) => VisitOutcomeScreen(
            appointmentId: state.pathParameters['appointmentId']!,
            initialJourney: state.extra is PatientJourney
                ? state.extra as PatientJourney
                : null,
          ),
        ),
        GoRoute(
          path: 'timeline',
          builder: (context, state) => JourneyTimelineScreen(
            appointmentId: state.pathParameters['appointmentId']!,
          ),
        ),
      ],
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
