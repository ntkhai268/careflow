import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'config/theme.dart';
import 'config/router.dart';
import 'services/push_notification_service.dart';
import 'widgets/biometric_app_lock.dart';
import 'config/release_configuration.dart';
import 'services/medical_file_cache.dart';
import 'widgets/notification_coordinator.dart';

@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  if (Firebase.apps.isEmpty) await Firebase.initializeApp();
}

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  validateReleaseConfiguration();
  await MedicalFileCache.instance.initialize();

  await FirebaseBootstrap.initialize();
  if (FirebaseBootstrap.isReady) {
    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
  }

  // Set status bar style
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
    ),
  );

  runApp(const ProviderScope(child: CareFlowApp()));
}

class CareFlowApp extends ConsumerStatefulWidget {
  const CareFlowApp({super.key});

  @override
  ConsumerState<CareFlowApp> createState() => _CareFlowAppState();
}

class _CareFlowAppState extends ConsumerState<CareFlowApp> {
  @override
  void initState() {
    super.initState();
    if (FirebaseBootstrap.isReady) {
      ref.read(pushNotificationServiceProvider).initializeHandlers().catchError(
        (Object _) {
          /* Retry on authenticated bind; inbox remains available. */
        },
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'CareFlow',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      routerConfig: appRouter,
      builder: (context, child) => NotificationCoordinator(
        child: BiometricAppLock(child: child ?? const SizedBox.shrink()),
      ),
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('vi', 'VN'), Locale('en', 'US')],
      locale: const Locale('vi', 'VN'),
    );
  }
}
