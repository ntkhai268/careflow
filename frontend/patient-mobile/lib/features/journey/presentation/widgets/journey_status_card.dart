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
    'Vui lòng đưa mã QR cho nhân viên để xác nhận đến khám.',
  ),
  JourneyStatus.checkedIn => const _StatusPresentation(
    Icons.verified_user_rounded,
    AppColors.info,
    'Đã xác nhận đến khám',
    'Nhân viên đang đưa bạn vào hàng đợi phòng khám.',
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
  JourneyStatus.prescribed => const _StatusPresentation(
    Icons.medication_rounded,
    AppColors.info,
    'Đã kê toa thuốc',
    'Hệ thống đang tổng hợp quyết toán cuối lượt khám.',
  ),
  JourneyStatus.settlementPending => const _StatusPresentation(
    Icons.receipt_long_rounded,
    AppColors.info,
    'Đã lập quyết toán lượt khám',
    'Tổng hợp phí khám, xét nghiệm và thuốc đã sẵn sàng.',
  ),
  JourneyStatus.paymentDue => const _StatusPresentation(
    Icons.payment_rounded,
    AppColors.warning,
    'Cần thanh toán phần còn lại',
    'Thanh toán trước khi nhận thuốc tại bệnh viện.',
  ),
  JourneyStatus.settled => const _StatusPresentation(
    Icons.verified_rounded,
    AppColors.success,
    'Đã quyết toán lượt khám',
    'Nhà thuốc có thể tiếp nhận và phát thuốc theo đơn.',
  ),
  JourneyStatus.refundPending => const _StatusPresentation(
    Icons.currency_exchange_rounded,
    AppColors.warning,
    'Đang chờ hoàn khoản dư',
    'Khoản hoàn không chặn việc nhận thuốc.',
  ),
  JourneyStatus.refunded => const _StatusPresentation(
    Icons.check_circle_outline_rounded,
    AppColors.success,
    'Đã hoàn khoản dư',
    'Nhà thuốc có thể tiếp nhận và phát thuốc theo đơn.',
  ),
  JourneyStatus.medicationReady => const _StatusPresentation(
    Icons.local_pharmacy_rounded,
    AppColors.success,
    'Thuốc đã sẵn sàng',
    'Đến quầy thuốc để nhận thuốc theo đơn.',
  ),
  JourneyStatus.completed => const _StatusPresentation(
    Icons.task_alt_rounded,
    AppColors.success,
    'Lượt khám đã hoàn tất',
    'Bạn có thể xem lại kết quả và toa thuốc trong hồ sơ.',
  ),
  JourneyStatus.paymentPending ||
  JourneyStatus.prescriptionPaymentPending ||
  JourneyStatus.prescriptionPaid => const _StatusPresentation(
    Icons.sync_rounded,
    AppColors.info,
    'Đang đồng bộ hành trình',
    'Dữ liệu cũ sẽ được chuyển sang quyết toán cuối lượt.',
  ),
  JourneyStatus.booked => const _StatusPresentation(
    Icons.event_note_rounded,
    AppColors.info,
    'Đã đặt lịch',
    'Phiếu khám sẽ được phát hành sau khi lịch hẹn được xác nhận.',
  ),
  JourneyStatus.labOrdered ||
  JourneyStatus.waitingLab ||
  JourneyStatus.labInProgress ||
  JourneyStatus.labResultReady ||
  JourneyStatus.waitingResultReview ||
  JourneyStatus.resultReview => const _StatusPresentation(
    Icons.science_rounded,
    AppColors.info,
    'Đang xử lý xét nghiệm',
    'Theo dõi hàng đợi và kết quả xét nghiệm tại đây.',
  ),
};

class _StatusPresentation {
  const _StatusPresentation(this.icon, this.color, this.title, this.detail);

  final IconData icon;
  final Color color;
  final String title;
  final String detail;
}
