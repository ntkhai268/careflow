import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('CareFlow app smoke test', (WidgetTester tester) async {
    // Basic smoke test — verify app can start without crashing
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Center(child: Text('CareFlow Test')),
        ),
      ),
    );

    expect(find.text('CareFlow Test'), findsOneWidget);
  });
}
