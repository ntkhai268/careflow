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
  final MobileScannerController _scannerController = MobileScannerController();
  bool _isProcessing = false;
  String? _errorMessage;

  @override
  void dispose() {
    _scannerController.dispose();
    super.dispose();
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
      await ref.read(queueServiceProvider).checkInAtHospital(
            appointmentId: widget.appointmentId,
            checkInQrToken: token,
            latitude: position.latitude,
            longitude: position.longitude,
            accuracyMeters: position.accuracy,
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Đã xác nhận bạn đang có mặt tại bệnh viện.')),
      );
      context.pop(true);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _isProcessing = false;
        _errorMessage = error is QueueServiceException
            ? error.message
            : 'Không thể lấy vị trí hoặc xác nhận check-in. Vui lòng thử lại.';
      });
      await _scannerController.start();
    }
  }

  Future<Position> _currentPosition() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      throw const QueueServiceException('Vui lòng bật dịch vụ định vị để check-in.');
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
                  ),
                  Center(
                    child: Container(
                      width: 230,
                      height: 230,
                      decoration: BoxDecoration(
                        border: Border.all(color: Colors.white, width: 3),
                        borderRadius: BorderRadius.circular(AppSpacing.md),
                      ),
                    ),
                  ),
                  if (_isProcessing)
                    Container(
                      color: Colors.black54,
                      alignment: Alignment.center,
                      child: const CircularProgressIndicator(color: Colors.white),
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
            'Sau khi quét, CareFlow sẽ lấy vị trí một lần để kiểm tra bạn đang ở trong khu vực bệnh viện. Ứng dụng không theo dõi vị trí liên tục.',
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
          ],
        ],
      ),
    );
  }
}
