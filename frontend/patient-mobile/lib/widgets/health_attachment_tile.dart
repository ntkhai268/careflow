import 'dart:io';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:open_filex/open_filex.dart';
import 'package:share_plus/share_plus.dart';
import '../models/health_record.dart';
import '../services/health_attachment_service.dart';
import '../utils/api_error_message.dart';

class HealthAttachmentTile extends ConsumerStatefulWidget {
  const HealthAttachmentTile({
    super.key,
    required this.patientId,
    required this.attachment,
  });
  final String patientId;
  final HealthRecordFileInfo attachment;
  @override
  ConsumerState<HealthAttachmentTile> createState() =>
      _HealthAttachmentTileState();
}

class _HealthAttachmentTileState extends ConsumerState<HealthAttachmentTile> {
  CancelToken? _cancel;
  double? _progress;
  String? _error;
  @override
  void dispose() {
    _cancel?.cancel();
    super.dispose();
  }

  Future<void> _open({bool share = false}) async {
    if (_cancel != null) return;
    final cancel = CancelToken();
    setState(() {
      _cancel = cancel;
      _error = null;
      _progress = null;
    });
    File? file;
    try {
      file = await ref
          .read(healthAttachmentServiceProvider)
          .download(
            widget.patientId,
            widget.attachment,
            cancel: cancel,
            progress: (done, total) {
              if (mounted) {
                setState(() => _progress = total > 0 ? done / total : null);
              }
            },
          );
      if (!mounted || cancel.isCancelled) return;
      if (share) {
        final box = context.findRenderObject() as RenderBox?;
        await SharePlus.instance.share(
          ShareParams(
            files: [XFile(file.path)],
            fileNameOverrides: [
              widget.attachment.fileName.split(RegExp(r'[/\\]')).last,
            ],
            sharePositionOrigin: box == null
                ? null
                : box.localToGlobal(Offset.zero) & box.size,
          ),
        );
      } else {
        final result = await OpenFilex.open(file.path);
        if (result.type != ResultType.done) {
          throw const FormatException(
            'Điện thoại chưa mở được tệp này. Bạn có thể chọn Chia sẻ/Lưu tệp.',
          );
        }
      }
    } catch (error) {
      if (mounted && !cancel.isCancelled) {
        setState(
          () => _error = error is FormatException
              ? error.message.toString()
              : ApiErrorMessage.from(
                  error,
                  fallback: 'Không thể tải hoặc mở tệp. Vui lòng thử lại.',
                ),
        );
      }
    } finally {
      // Native viewers can read asynchronously; clear on logout or next startup.
      if (mounted) {
        setState(() {
          _cancel = null;
          _progress = null;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) => SizedBox(
    width: double.infinity,
    child: Card(
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                const Icon(Icons.attach_file),
                const SizedBox(width: 8),
                Expanded(child: Text(widget.attachment.fileName)),
                if (_cancel != null)
                  IconButton(
                    tooltip: 'Hủy tải',
                    onPressed: () => _cancel?.cancel(),
                    icon: const Icon(Icons.close),
                  )
                else ...[
                  TextButton(onPressed: _open, child: const Text('Mở')),
                  IconButton(
                    tooltip: 'Chia sẻ/Lưu tệp',
                    onPressed: () => _open(share: true),
                    icon: const Icon(Icons.ios_share),
                  ),
                ],
              ],
            ),
            if (_cancel != null) LinearProgressIndicator(value: _progress),
            if (_error != null)
              Text(
                _error!,
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
          ],
        ),
      ),
    ),
  );
}
