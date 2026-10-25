import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../models/health_record.dart';
import 'api_service.dart';
import 'medical_file_cache.dart';

class HealthAttachmentService {
  HealthAttachmentService(this.api, {MedicalFileCache? cache})
    : cache = cache ?? MedicalFileCache.instance;
  final ApiService api;
  final MedicalFileCache cache;
  static const maxBytes = 10 * 1024 * 1024;

  Future<File> download(
    String patientId,
    HealthRecordFileInfo attachment, {
    required CancelToken cancel,
    void Function(int, int)? progress,
  }) async {
    if (!RegExp(r'^[0-9a-fA-F-]{36}$').hasMatch(patientId) ||
        !RegExp(r'^[0-9a-fA-F-]{36}$').hasMatch(attachment.id)) {
      throw const FormatException('Thông tin tệp không hợp lệ.');
    }
    final extension = attachment.fileName.split('.').last.toLowerCase();
    if (!const {'pdf', 'png', 'jpg', 'jpeg'}.contains(extension)) {
      throw const FormatException('Chỉ hỗ trợ mở ảnh JPEG, PNG và PDF.');
    }
    final epoch = cache.epoch;
    File? file;
    IOSink? sink;
    ResponseBody? body;
    var consumed = false;
    cache.register(cancel);
    try {
      final response = await api.stream(
        '/patients/$patientId/health-records/files/${attachment.id}',
        cancelToken: cancel,
      );
      body = response.data as ResponseBody;
      final total =
          int.tryParse(
            body.headers[Headers.contentLengthHeader]?.firstOrNull ?? '',
          ) ??
          -1;
      if (total > maxBytes) {
        throw const FormatException('Tệp vượt quá giới hạn 10MB.');
      }
      final root = await cache.directory();
      if (epoch != cache.epoch || cancel.isCancelled) {
        throw const FormatException('Phiên đã thay đổi. Vui lòng thử lại.');
      }
      final folder = await Directory(
        '${root.path}/${const Uuid().v4()}',
      ).create(recursive: true);
      file = File('${folder.path}/ho-so.$extension');
      sink = file.openWrite();
      var received = 0;
      final signature = <int>[];
      consumed = true;
      await for (final chunk in body.stream) {
        if (cancel.isCancelled || epoch != cache.epoch) {
          throw const FormatException('Đã hủy tải tệp.');
        }
        received += chunk.length;
        if (received > maxBytes) {
          throw const FormatException('Tệp vượt quá giới hạn 10MB.');
        }
        if (signature.length < 8) {
          signature.addAll(chunk.take(8 - signature.length));
        }
        sink.add(chunk);
        progress?.call(received, total);
      }
      await sink.flush();
      await sink.close();
      sink = null;
      if (total >= 0 && received != total ||
          !validSignature(extension, signature)) {
        throw const FormatException(
          'Tệp bị thiếu dữ liệu hoặc không đúng định dạng.',
        );
      }
      if (epoch != cache.epoch || cancel.isCancelled) {
        throw const FormatException('Phiên đã thay đổi.');
      }
      return file;
    } catch (_) {
      if (sink != null) await sink.close();
      if (file != null && await file.parent.exists()) {
        await file.parent.delete(recursive: true);
      }
      rethrow;
    } finally {
      if (body != null && !consumed) {
        await body.stream.listen((_) {}, onError: (_) {}).cancel();
      }
      cache.unregister(cancel);
    }
  }

  static bool validSignature(String extension, List<int> bytes) {
    final expected = switch (extension) {
      'pdf' => [37, 80, 68, 70, 45],
      'png' => [137, 80, 78, 71, 13, 10, 26, 10],
      _ => [255, 216, 255],
    };
    return bytes.length >= expected.length &&
        List.generate(
          expected.length,
          (i) => bytes[i] == expected[i],
        ).every((v) => v);
  }
}

final healthAttachmentServiceProvider = Provider(
  (ref) => HealthAttachmentService(ref.watch(apiServiceProvider)),
);
