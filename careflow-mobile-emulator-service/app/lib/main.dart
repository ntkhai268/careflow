import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'config/device_config.dart';
import 'platform/browser_bridge.dart';
import 'providers/auth_provider.dart';
import 'config/theme.dart';
import 'config/router.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final namespace = DeviceConfig.namespace;
  if (namespace != null) {
    SharedPreferences.setPrefix('$namespace.preferences.');
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
    listenBrowserControls(_control);
  }

  Future<void> _control(String command) async {
    if (command == 'logout') {
      await ref.read(authProvider.notifier).logout();
      appRouter.go('/login');
      return;
    }
    if (ref.read(authProvider).status != AuthStatus.authenticated) {
      appRouter.go('/login');
      return;
    }
    switch (command) {
      case 'home':
        appRouter.go('/');
      case 'appointments':
        appRouter.go('/?tab=1');
      case 'notifications':
        appRouter.go('/?tab=3');
      case 'back':
        if (appRouter.canPop()) appRouter.pop();
    }
  }

  @override
  void dispose() {
    listenBrowserControls((_) {});
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'CareFlow',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      routerConfig: appRouter,
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
