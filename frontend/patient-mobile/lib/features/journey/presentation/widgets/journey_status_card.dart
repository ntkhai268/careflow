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
  JourneyStatus.booked => const _StatusPresentation(
    Icons.event_available_rounded,
    AppColors.info,
    'Lịch khám đã được xác nhận',
    'Hệ thống sẽ cập nhật phiếu khám khi lịch của bạn được tiếp nhận.',
  ),
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
  JourneyStatus.labOrdered => const _StatusPresentation(
    Icons.biotech_rounded,
    AppColors.info,
    'Bác sĩ đã chỉ định xét nghiệm',
    'Mở hành trình để xem chỉ định và điểm thực hiện.',
  ),
  JourneyStatus.paymentPending => const _StatusPresentation(
    Icons.payments_rounded,
    AppColors.warning,
    'Đang chờ thanh toán xét nghiệm',
    'Hoàn tất thanh toán trực tuyến để tiếp tục.',
  ),
  JourneyStatus.waitingLab => const _StatusPresentation(
    Icons.hourglass_top_rounded,
    AppColors.warning,
    'Đang chờ thực hiện xét nghiệm',
    'Vui lòng đến điểm cận lâm sàng được hướng dẫn.',
  ),
  JourneyStatus.labInProgress => const _StatusPresentation(
    Icons.science_rounded,
    AppColors.success,
    'Đang thực hiện xét nghiệm',
    'Nhân viên xét nghiệm đang xử lý chỉ định của bạn.',
  ),
  JourneyStatus.labResultReady => const _StatusPresentation(
    Icons.assignment_turned_in_rounded,
    AppColors.success,
    'Đã có kết quả xét nghiệm',
    'Kết quả đang chờ bác sĩ xem và kết luận.',
  ),
  JourneyStatus.waitingResultReview => const _StatusPresentation(
    Icons.rate_review_rounded,
    AppColors.warning,
    'Đang chờ bác sĩ đọc kết quả',
    'Bác sĩ sẽ xem kết quả xét nghiệm của bạn.',
  ),
  JourneyStatus.resultReview => const _StatusPresentation(
    Icons.rate_review_rounded,
    AppColors.success,
    'Bác sĩ đang xem kết quả',
    'Vui lòng chờ bác sĩ hoàn tất kết luận.',
  ),
  JourneyStatus.prescribed => const _StatusPresentation(
    Icons.medication_rounded,
    AppColors.success,
    'Đã có toa thuốc',
    'Mở hành trình để xem kết quả khám và toa thuốc.',
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
  JourneyStatus.prescriptionPaymentPending => const _StatusPresentation(
    Icons.payment_rounded,
    AppColors.warning,
    'Chờ thanh toán tiền thuốc',
    'Chọn phương thức thanh toán để tiếp tục nhận thuốc.',
  ),
  JourneyStatus.prescriptionPaid => const _StatusPresentation(
    Icons.inventory_2_rounded,
    AppColors.info,
    'Nhà thuốc đang chuẩn bị đơn',
    'Bạn sẽ nhận thông báo khi thuốc sẵn sàng.',
  ),
  JourneyStatus.medicationReady => const _StatusPresentation(
    Icons.local_pharmacy_rounded,
    AppColors.success,
    'Thuốc đã sẵn sàng',
    'Đến quầy thuốc để nhận thuốc theo đơn.',
  ),
  JourneyStatus.completed => const _StatusPresentation(
    Icons.check_circle_rounded,
    AppColors.success,
    'Lượt khám đã hoàn tất',
    'Bạn có thể xem chẩn đoán, kết quả xét nghiệm và đơn thuốc.',
  ),
};

class _StatusPresentation {
  const _StatusPresentation(this.icon, this.color, this.title, this.detail);

  final IconData icon;
  final Color color;
  final String title;
  final String detail;
}
