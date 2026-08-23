import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:go_router/go_router.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../../config/theme.dart';
import '../../../services/queue_service.dart';

class HospitalQrCheckInScreen extends ConsumerStatefulWidget {
  const HospitalQrCheckInScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  ConsumerState<HospitalQrCheckInScreen> createState() =>
      _HospitalQrCheckInScreenState();
}

class _HospitalQrCheckInScreenState
    extends ConsumerState<HospitalQrCheckInScreen> {
  final MobileScannerController _scannerController = MobileScannerController(
    autoStart: false,
  );
  bool _isProcessing = false;
  bool _isScannerStarted = false;
  String? _errorMessage;
  bool _locationRequiresSettings = false;

  @override
  void dispose() {
    _scannerController.dispose();
    super.dispose();
  }

  Future<void> _startScanner() async {
    if (_isProcessing) return;
    setState(() {
      _isScannerStarted = true;
      _errorMessage = null;
    });
    await _scannerController.start();
  }

  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_isProcessing) return;
    String? token;
    for (final barcode in capture.barcodes) {
      final value = barcode.rawValue?.trim();
      if (value != null && value.isNotEmpty) {
        token = value;
        break;
      }
    }
    if (token == null) return;

    setState(() {
      _isProcessing = true;
      _errorMessage = null;
    });
    await _scannerController.stop();

    try {
      final position = await _currentPosition();
      await ref
          .read(queueServiceProvider)
          .checkInAtHospital(
            appointmentId: widget.appointmentId,
            checkInQrToken: token,
            latitude: position.latitude,
            longitude: position.longitude,
            accuracyMeters: position.accuracy,
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Đã xác nhận bạn đang có mặt tại bệnh viện.'),
        ),
      );
      context.pop(true);
    } catch (error) {
      if (!mounted) return;
      final locationPermission = await Geolocator.checkPermission();
      setState(() {
        _isProcessing = false;
        _locationRequiresSettings =
            locationPermission == LocationPermission.deniedForever;
        _errorMessage = error is QueueServiceException
            ? error.message
            : 'Không thể lấy vị trí hoặc xác nhận check-in. Vui lòng thử lại.';
      });
      await _scannerController.start();
    }
  }

  Future<Position> _currentPosition() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      throw const QueueServiceException(
        'Vui lòng bật dịch vụ định vị để check-in.',
      );
    }

    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    if (permission == LocationPermission.denied ||
        permission == LocationPermission.deniedForever) {
      throw const QueueServiceException(
        'CareFlow cần quyền vị trí để xác nhận bạn đang ở bệnh viện.',
      );
    }

    return Geolocator.getCurrentPosition(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Check-in tại bệnh viện'),
        leading: IconButton(
          tooltip: 'Quay lại',
          onPressed: () => context.pop(),
          icon: const Icon(Icons.arrow_back_rounded),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(AppSpacing.base),
        children: [
          Card(
            clipBehavior: Clip.antiAlias,
            child: SizedBox(
              height: 330,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  MobileScanner(
                    controller: _scannerController,
                    onDetect: _onDetect,
                    errorBuilder: (context, error) =>
                        _ScannerError(error: error, onRetry: _startScanner),
                  ),
                  if (!_isScannerStarted)
                    Container(
                      color: AppColors.surface,
                      padding: const EdgeInsets.all(AppSpacing.xl),
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          const Icon(
                            Icons.camera_alt_outlined,
                            color: AppColors.primary,
                            size: 48,
                          ),
                          const SizedBox(height: AppSpacing.md),
                          const Text(
                            'CareFlow cần camera để quét QR check-in.',
                            textAlign: TextAlign.center,
                          ),
                          const SizedBox(height: AppSpacing.md),
                          ElevatedButton.icon(
                            onPressed: _startScanner,
                            icon: const Icon(Icons.camera_alt_rounded),
                            label: const Text('Bắt đầu quét QR'),
                          ),
                        ],
                      ),
                    ),
                  IgnorePointer(
                    child: Center(
                      child: Container(
                        width: 230,
                        height: 230,
                        decoration: BoxDecoration(
                          border: Border.all(color: Colors.white, width: 3),
                          borderRadius: BorderRadius.circular(AppSpacing.md),
                        ),
                      ),
                    ),
                  ),
                  if (_isProcessing)
                    Container(
                      color: Colors.black54,
                      alignment: Alignment.center,
                      child: const CircularProgressIndicator(
                        color: Colors.white,
                      ),
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.base),
          Text(
            'Đưa camera vào mã QR đang hiển thị tại bệnh viện',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: AppSpacing.sm),
          const Text(
            'Sau khi quét, CareFlow sẽ xin quyền vị trí một lần để kiểm tra bạn đang ở bệnh viện. Ứng dụng không theo dõi vị trí liên tục.',
            textAlign: TextAlign.center,
          ),
          if (_errorMessage != null) ...[
            const SizedBox(height: AppSpacing.base),
            Card(
              color: AppColors.errorLight,
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.md),
                child: Text(
                  _errorMessage!,
                  textAlign: TextAlign.center,
                  style: const TextStyle(color: AppColors.error),
                ),
              ),
            ),
            if (_locationRequiresSettings) ...[
              const SizedBox(height: AppSpacing.sm),
              OutlinedButton.icon(
                onPressed: Geolocator.openAppSettings,
                icon: const Icon(Icons.settings_outlined),
                label: const Text('Mở Cài đặt quyền vị trí'),
              ),
            ],
          ],
        ],
      ),
    );
  }
}

class _ScannerError extends StatelessWidget {
  const _ScannerError({required this.error, required this.onRetry});

  final MobileScannerException error;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) => Container(
    color: AppColors.surface,
    padding: const EdgeInsets.all(AppSpacing.xl),
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(
          error.errorCode == MobileScannerErrorCode.permissionDenied
              ? Icons.no_photography_outlined
              : Icons.camera_alt_outlined,
          color: AppColors.error,
          size: 48,
        ),
        const SizedBox(height: AppSpacing.md),
        Text(
          error.errorCode == MobileScannerErrorCode.permissionDenied
              ? 'Chưa có quyền camera. Hãy cho phép camera để quét QR.'
              : 'Không thể mở camera. Vui lòng thử lại.',
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: AppSpacing.md),
        OutlinedButton.icon(
          onPressed: error.errorCode == MobileScannerErrorCode.permissionDenied
              ? () => Geolocator.openAppSettings()
              : onRetry,
          icon: const Icon(Icons.refresh_rounded),
          label: Text(
            error.errorCode == MobileScannerErrorCode.permissionDenied
                ? 'Mở Cài đặt quyền camera'
                : 'Thử mở camera lại',
          ),
        ),
      ],
    ),
  );
}
