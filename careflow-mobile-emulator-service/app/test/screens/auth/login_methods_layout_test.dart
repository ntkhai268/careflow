import 'package:careflow_patient/config/theme.dart';
import 'package:careflow_patient/screens/auth/login_methods_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

void main() {
  for (final scale in [1.0, 2.0]) {
    testWidgets('recovery remains usable at 360px and text scale $scale', (tester) async {
      tester.view.physicalSize = const Size(360, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(ProviderScope(child: MaterialApp(
        theme: AppTheme.lightTheme,
        builder: (context, child) => MediaQuery(data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)), child: child!),
        home: const LoginMethodsScreen(recovery: true),
      )));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.text('Câu hỏi bí mật'));
      await tester.tap(find.text('Câu hỏi bí mật'));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.text('Đặt lại mật khẩu'));
      await tester.tap(find.text('Đặt lại mật khẩu'));
      await tester.pumpAndSettle();
      expect(find.text('Vui lòng nhập tên đăng nhập hoặc email'), findsOneWidget);
      expect(find.text('Vui lòng nhập câu trả lời bí mật'), findsOneWidget);
      expect(tester.takeException(), isNull);
      await tester.ensureVisible(find.text('Kỷ niệm riêng nào bạn luôn nhớ?'));
      await tester.tap(find.text('Kỷ niệm riêng nào bạn luôn nhớ?'));
      await tester.pumpAndSettle();
      await tester.ensureVisible(find.text('Tên địa điểm đặc biệt chỉ bạn biết là gì?'));
      await tester.tap(find.text('Tên địa điểm đặc biệt chỉ bạn biết là gì?'));
      await tester.pumpAndSettle();
      expect(find.text('Chọn câu hỏi bí mật'), findsNothing);
      expect(tester.takeException(), isNull);
    });
  }
  testWidgets('back returns from a method to chooser before leaving auth', (tester) async {
    final router = GoRouter(initialLocation: '/login/methods', routes: [
      GoRoute(path: '/login/methods', builder: (_, _) => const LoginMethodsScreen()),
      GoRoute(path: '/login', builder: (_, _) => const Scaffold(body: Text('Password login route'))),
    ]);
    addTearDown(router.dispose);
    await tester.pumpWidget(ProviderScope(child: MaterialApp.router(theme: AppTheme.lightTheme, routerConfig: router)));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Vân tay / Khuôn mặt'));
    await tester.pumpAndSettle();
    expect(find.text('Đăng nhập bằng passkey'), findsOneWidget);
    await tester.tap(find.byTooltip('Quay lại'));
    await tester.pumpAndSettle();
    expect(find.text('Cách đăng nhập'), findsOneWidget);
    await tester.tap(find.byTooltip('Quay lại'));
    await tester.pumpAndSettle();
    expect(find.text('Password login route'), findsOneWidget);
  });
}
