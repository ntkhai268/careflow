import 'dart:io';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:uuid/uuid.dart';

/// Only temporary medical files live here; never use user-provided paths.
class MedicalFileCache {
  MedicalFileCache({Future<Directory> Function()? temporaryDirectory})
    : _temporaryDirectory = temporaryDirectory ?? getTemporaryDirectory;
  final Future<Directory> Function() _temporaryDirectory;
  static final instance = MedicalFileCache();
  Future<Directory>? _root;
  Future<Directory>? _base;
  final _requests = <CancelToken>{};
  int epoch = 0;

  Future<Directory> initialize() => _base ??= () async {
    final root = Directory(
      '${(await _temporaryDirectory()).path}/careflow-medical-files',
    );
    if (await root.exists()) await root.delete(recursive: true);
    return root.create(recursive: true);
  }();

  Future<Directory> directory() => _root ??= () async {
    final base = await initialize();
    return Directory(
      '${base.path}/${const Uuid().v4()}',
    ).create(recursive: true);
  }();

  void register(CancelToken request) => _requests.add(request);
  void unregister(CancelToken request) => _requests.remove(request);

  Future<void> clear() async {
    epoch++;
    for (final request in _requests.toList()) {
      request.cancel('Session changed');
    }
    _requests.clear();
    final pending = _root;
    _root = null;
    if (pending != null) {
      final root = await pending;
      if (await root.exists()) await root.delete(recursive: true);
    }
  }
}
