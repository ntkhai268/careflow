import 'package:careflow_patient/config/router.dart';
import 'package:careflow_patient/features/journey/presentation/clinic_queue_screen.dart';
import 'package:careflow_patient/features/journey/presentation/consultation_screen.dart';
import 'package:careflow_patient/features/journey/presentation/journey_hub_screen.dart';
import 'package:careflow_patient/features/journey/presentation/journey_timeline_screen.dart';
import 'package:careflow_patient/features/journey/presentation/laboratory_screen.dart';
import 'package:careflow_patient/features/journey/presentation/result_review_screen.dart';
import 'package:careflow_patient/features/journey/presentation/visit_outcome_screen.dart';
import 'package:careflow_patient/features/journey/presentation/visit_ticket_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final routes = <String, Type>{
    '/journey/apt-router': JourneyHubScreen,
    '/journey/apt-router/ticket': VisitTicketScreen,
    '/journey/apt-router/queue': ClinicQueueScreen,
    '/journey/apt-router/consultation': ConsultationScreen,
    '/journey/apt-router/laboratory': LaboratoryScreen,
    '/journey/apt-router/result-review': ResultReviewScreen,
    '/journey/apt-router/outcome': VisitOutcomeScreen,
    '/journey/apt-router/timeline': JourneyTimelineScreen,
  };

  for (final entry in routes.entries) {
    testWidgets('${entry.key} constructs its journey screen', (tester) async {
      appRouter.go(entry.key);
      await tester.pumpWidget(
        ProviderScope(child: MaterialApp.router(routerConfig: appRouter)),
      );
      await tester.pump();

      expect(find.byType(entry.value), findsOneWidget);
      expect(find.textContaining('Đường dẫn:'), findsNothing);
    });
  }
}
