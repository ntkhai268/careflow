import 'package:flutter/material.dart';

import '../../../../../config/theme.dart';
import '../../domain/journey_models.dart';

class JourneyStatusCard extends StatelessWidget {
  const JourneyStatusCard({super.key, required this.journey});

  final PatientJourney journey;

  @override
  Widget build(BuildContext context) {
    final presentation = _presentationFor(journey.status);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.base),
        child: Row(
          children: [
            CircleAvatar(
              backgroundColor: presentation.color.withValues(alpha: 0.12),
              foregroundColor: presentation.color,
              child: Icon(presentation.icon),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    presentation.title,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: AppSpacing.xs),
                  Text(presentation.detail),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

_StatusPresentation _presentationFor(JourneyStatus status) => switch (status) {
  JourneyStatus.ticketIssued => const _StatusPresentation(
    Icons.qr_code_scanner_rounded,
    AppColors.info,
    'Phiếu khám đã sẵn sàng',
    'Đến bệnh viện và quét mã QR đang được hiển thị tại phòng khám.',
  ),
  JourneyStatus.checkedIn => const _StatusPresentation(
    Icons.verified_user_rounded,
    AppColors.info,
    'Đã xác nhận đến khám',
    'Hệ thống đã xác nhận vị trí và đưa bạn vào hàng đợi phòng khám.',
  ),
  JourneyStatus.waiting => const _StatusPresentation(
    Icons.people_alt_rounded,
    AppColors.warning,
    'Đang chờ khám',
    'Vui lòng theo dõi thứ tự hàng đợi.',
  ),
  JourneyStatus.called => const _StatusPresentation(
    Icons.campaign_rounded,
    AppColors.success,
    'Đã đến lượt bạn',
    'Vui lòng đến phòng khám ngay.',
  ),
  JourneyStatus.inConsultation => const _StatusPresentation(
    Icons.medical_services_rounded,
    AppColors.success,
    'Đang khám bệnh',
    'Bác sĩ đang khám cho bạn.',
  ),
  JourneyStatus.completed => const _StatusPresentation(
    Icons.check_circle_rounded,
    AppColors.success,
    'Lượt khám đã hoàn tất',
    'Bạn có thể xem chẩn đoán, kết quả xét nghiệm và đơn thuốc.',
  ),
  _ => const _StatusPresentation(
    Icons.info_outline_rounded,
    AppColors.info,
    'Hành trình khám',
    'Thông tin hành trình sẽ được cập nhật tại đây.',
  ),
};

class _StatusPresentation {
  const _StatusPresentation(this.icon, this.color, this.title, this.detail);

  final IconData icon;
  final Color color;
  final String title;
  final String detail;
}
