import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

class DocumentFields {
  const DocumentFields({this.number = '', this.name = '', this.birth = ''});
  final String number, name, birth;
  static String? isoBirth(String value) {
    final match = RegExp(r'^(\d{2})/(\d{2})/(\d{4})$').firstMatch(value.trim());
    if (match == null) return null;
    final day = int.parse(match[1]!),
        month = int.parse(match[2]!),
        year = int.parse(match[3]!);
    final date = DateTime(year, month, day), now = DateTime.now();
    if (date.day != day ||
        date.month != month ||
        year < 1900 ||
        date.isAfter(now)) {
      return null;
    }
    return '${match[3]}-${match[2]}-${match[1]}';
  }

  factory DocumentFields.parse(String text, {String? qr}) {
    final parts = qr?.split('|');
    if (parts != null &&
        parts.length >= 4 &&
        RegExp(r'^\d{12}$').hasMatch(parts[0]) &&
        RegExp(r'^\d{8}$').hasMatch(parts[3])) {
      final raw = parts[3];
      return DocumentFields(
        number: parts[0],
        name: parts[2],
        birth:
            '${raw.substring(0, 2)}/${raw.substring(2, 4)}/${raw.substring(4)}',
      );
    }
    final lines = text
        .split('\n')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    final number =
        RegExp(
          r'(?<![0-9])(?:[0-9][ \u00a0]*){11}[0-9](?![ \u00a0]*[0-9])',
        ).firstMatch(text)?.group(0)?.replaceAll(RegExp(r'[^0-9]'), '') ??
        '';
    String name = '', birth = '';
    for (var i = 0; i < lines.length; i++) {
      final line = lines[i];
      if (RegExp(
        r'^h[oọ][,\s].*t[eê]n|full\s*name',
        caseSensitive: false,
      ).hasMatch(line)) {
        final colon = line.lastIndexOf(':');
        final suffix = colon >= 0 ? line.substring(colon + 1).trim() : '';
        final candidate = suffix.isNotEmpty
            ? suffix
            : (i + 1 < lines.length ? lines[i + 1] : '');
        if (!RegExp(
          r'full\s*name|birth|ng[aà]y sinh',
          caseSensitive: false,
        ).hasMatch(candidate)) {
          name = candidate;
        }
      }
      if (RegExp(
        r'ng[aà]y sinh|date of birth|birth',
        caseSensitive: false,
      ).hasMatch(line)) {
        final next = '$line ${i + 1 < lines.length ? lines[i + 1] : ''}';
        final date = RegExp(
          r'(\d{2})\s*[/.-]\s*(\d{2})\s*[/.-]\s*(\d{4})',
        ).firstMatch(next);
        if (date != null) birth = '${date[1]}/${date[2]}/${date[3]}';
      }
    }
    return DocumentFields(number: number, name: name, birth: birth);
  }
}

/// Images are sent only through the local platform channel, never an HTTP API.
class LocalDocumentReader {
  static const channel = MethodChannel('careflow/document-reader');
  Future<DocumentFields?> read() async {
    final photo = await ImagePicker().pickImage(
      source: ImageSource.camera,
      preferredCameraDevice: CameraDevice.rear,
      maxWidth: 1800,
      maxHeight: 1800,
      imageQuality: 90,
    );
    if (photo == null) return null;
    Uint8List? bytes;
    try {
      if (await photo.length() > 5 * 1024 * 1024) {
        throw const FormatException('Ảnh quá lớn');
      }
      bytes = await photo.readAsBytes();
      final response = await channel.invokeMapMethod<String, dynamic>('read', {
        'bytes': bytes,
      });
      return DocumentFields.parse(
        response?['text'] as String? ?? '',
        qr: response?['qr'] as String?,
      );
    } finally {
      bytes?.fillRange(0, bytes.length, 0);
      final temporary = File(photo.path);
      if (await temporary.exists()) await temporary.delete();
    }
  }
}

final localDocumentReaderProvider = Provider<LocalDocumentReader>(
  (ref) => LocalDocumentReader(),
);
