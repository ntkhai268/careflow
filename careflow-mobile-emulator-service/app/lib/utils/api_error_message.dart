import 'package:dio/dio.dart';

/// Converts transport/API errors into messages that are safe and understandable
/// for a patient. Technical exception strings must never be rendered directly
/// by the UI.
class ApiErrorMessage {
  const ApiErrorMessage._();

  static String from(
    Object error, {
    String fallback = 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.',
    String unauthorized = 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
  }) {
    if (error is DioException) {
      return _fromDio(error, fallback: fallback, unauthorized: unauthorized);
    }

    if (error is FormatException) {
      return 'Dữ liệu từ máy chủ không đúng định dạng. Vui lòng thử lại sau.';
    }

    final text = error.toString().replaceFirst(
      RegExp(r'^(Exception|StateError):\s*'),
      '',
    );
    if (text.isNotEmpty && !text.startsWith('Instance of ')) {
      return text;
    }
    return fallback;
  }

  static String _fromDio(
    DioException error, {
    required String fallback,
    required String unauthorized,
  }) {
    final status = error.response?.statusCode;
    final serverMessage = _serverMessage(error.response?.data);
    if (serverMessage != null) {
      final normalized = serverMessage.toLowerCase();
      if (normalized.contains('internal server error') ||
          normalized.contains('whitelabel error')) {
        return fallback;
      }
      if (normalized.contains('patient not found with userid')) {
        return 'Bạn chưa có hồ sơ bệnh nhân. Vui lòng tạo hồ sơ mới để tiếp tục.';
      }
      if (normalized.contains('not allowed') ||
          normalized.contains('không có quyền')) {
        return 'Bạn không có quyền thực hiện thao tác này.';
      }
      return serverMessage;
    }

    if (status == 409) return fallback;

    return switch (error.type) {
      DioExceptionType.connectionError =>
        'Không thể kết nối đến máy chủ. Vui lòng kiểm tra Internet và thử lại.',
      DioExceptionType.connectionTimeout =>
        'Kết nối đến máy chủ quá lâu. Vui lòng thử lại sau.',
      DioExceptionType.receiveTimeout =>
        'Máy chủ phản hồi quá lâu. Vui lòng thử lại sau.',
      DioExceptionType.sendTimeout =>
        'Không thể gửi yêu cầu. Vui lòng thử lại.',
      DioExceptionType.badCertificate =>
        'Kết nối bảo mật của máy chủ không hợp lệ. Vui lòng liên hệ hỗ trợ.',
      DioExceptionType.cancel => 'Yêu cầu đã bị hủy. Vui lòng thử lại.',
      DioExceptionType.badResponse => switch (status) {
        400 => 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.',
        401 => unauthorized,
        403 => 'Bạn không có quyền thực hiện thao tác này.',
        404 => 'Không tìm thấy dữ liệu yêu cầu.',
        409 =>
          'Dữ liệu bị trùng hoặc thao tác đang xung đột. Vui lòng kiểm tra lại.',
        422 => 'Thông tin chưa hợp lệ. Vui lòng kiểm tra lại.',
        429 => 'Bạn thao tác quá nhanh. Vui lòng chờ một chút rồi thử lại.',
        500 || 502 || 503 || 504 => fallback,
        _ => fallback,
      },
      _ => fallback,
    };
  }

  static String? _serverMessage(Object? data) {
    if (data is! Map) return null;

    final direct = data['message'] ?? data['error'];
    if (direct is String && direct.trim().isNotEmpty) {
      return direct.trim();
    }

    final nested = data['data'];
    if (nested is Map && nested['message'] is String) {
      final message = (nested['message'] as String).trim();
      if (message.isNotEmpty) return message;
    }
    return null;
  }
}
