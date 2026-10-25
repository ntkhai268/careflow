import 'dart:convert';
import 'dart:js_interop';

@JS('careflowBrowser')
external JSPromise<JSString> _call(JSString method, JSString payload);
@JS('careflowListenNotifications')
external void _listen(JSFunction callback);
@JS('careflowListenControls')
external void _controls(JSFunction callback);

Future<dynamic> call(String method, Map<String, dynamic> data) async {
  final result =
      jsonDecode(
            (await _call(method.toJS, jsonEncode(data).toJS).toDart).toDart,
          )
          as Map;
  if (result['error'] != null) throw Exception(result['error']);
  return result['data'];
}

void listenNotifications(void Function() callback) => _listen(callback.toJS);

void listenControls(void Function(String) callback) =>
    _controls(((JSString command) => callback(command.toDart)).toJS);
