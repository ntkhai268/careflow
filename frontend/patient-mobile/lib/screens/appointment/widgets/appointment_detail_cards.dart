part of '../appointment_detail_screen.dart';

String _formatAppointmentCurrency(int amount) =>
    '${NumberFormat('#,###', 'vi_VN').format(amount)} ₫';

bool _canOpenVisitTicket(Appointment appointment) =>
    appointment.status == 'CONFIRMED' ||
    appointment.status == 'CHECKED_IN' ||
    appointment.status == 'IN_PROGRESS';

class _AppointmentDetailRow extends StatelessWidget {
  const _AppointmentDetailRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: AppColors.primary, size: 20),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w500,
                    color: AppColors.textPrimary,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AppointmentStatusHeader extends StatelessWidget {
  const _AppointmentStatusHeader({required this.appointment});

  final Appointment appointment;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.xl),
      decoration: BoxDecoration(
        color: appointment.statusBgColor,
        borderRadius: BorderRadius.circular(AppRadius.lg),
      ),
      child: Column(
        children: [
          Icon(
            appointment.statusIcon,
            size: 48,
            color: appointment.statusColor,
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            appointment.statusDisplayName,
            style: TextStyle(
              fontSize: 20,
              fontWeight: FontWeight.w700,
              color: appointment.statusColor,
            ),
          ),
        ],
      ),
    );
  }
}

class _AppointmentInfoCard extends StatelessWidget {
  const _AppointmentInfoCard({
    required this.appointment,
    required this.dateStr,
  });

  final Appointment appointment;
  final String dateStr;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        boxShadow: AppShadows.card,
        border: Border.all(color: AppColors.cardBorder, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Thông tin lịch khám',
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: AppSpacing.base),
          if (appointment.patientName != null)
            _AppointmentDetailRow(
              icon: Icons.person_rounded,
              label: 'Bệnh nhân',
              value: appointment.patientName!,
            ),
          _AppointmentDetailRow(
            icon: Appointment.departmentIcon(appointment.department),
            label: 'Chuyên khoa',
            value: appointment.departmentDisplayName,
          ),
          if (appointment.roomDisplayName != null)
            _AppointmentDetailRow(
              icon: Icons.meeting_room_rounded,
              label: 'Phòng khám',
              value: appointment.roomDisplayName!,
            ),
          _AppointmentDetailRow(
            icon: Icons.calendar_today_rounded,
            label: 'Ngày khám',
            value: dateStr,
          ),
          _AppointmentDetailRow(
            icon: Icons.access_time_rounded,
            label: 'Ca khám',
            value: appointment.timeSlot,
          ),
          if (appointment.queueNumber != null)
            _AppointmentDetailRow(
              icon: Icons.confirmation_number_rounded,
              label: 'Số thứ tự',
              value: appointment.queueNumber!,
            ),
          if (appointment.reason != null && appointment.reason!.isNotEmpty)
            _AppointmentDetailRow(
              icon: Icons.note_rounded,
              label: 'Lý do khám',
              value: appointment.reason!,
            ),
        ],
      ),
    );
  }
}

class _AppointmentPaymentCard extends StatelessWidget {
  const _AppointmentPaymentCard({required this.receipt});

  final AppointmentPaymentReceipt receipt;

  @override
  Widget build(BuildContext context) {
    final color = receipt.isPaid ? AppColors.success : AppColors.warning;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        boxShadow: AppShadows.card,
        border: Border.all(color: AppColors.cardBorder, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Thanh toán',
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: AppSpacing.base),
          _AppointmentDetailRow(
            icon: Icons.receipt_long_rounded,
            label: 'Dịch vụ',
            value: receipt.serviceName,
          ),
          _AppointmentDetailRow(
            icon: Icons.payments_rounded,
            label: 'Số tiền',
            value: _formatAppointmentCurrency(receipt.amount),
          ),
          _AppointmentDetailRow(
            icon: Icons.account_balance_wallet_rounded,
            label: 'Phương thức',
            value: receipt.method.displayName,
          ),
          Row(
            children: [
              Icon(Icons.verified_rounded, color: color, size: 20),
              const SizedBox(width: AppSpacing.md),
              Text(
                receipt.status.displayName,
                style: TextStyle(
                  color: color,
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _AppointmentVisitTicketActionCard extends StatelessWidget {
  const _AppointmentVisitTicketActionCard({required this.appointment});

  final Appointment appointment;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.primaryLight,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: AppColors.primary.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.confirmation_number_rounded, color: AppColors.primary),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Text(
                  'Phiếu khám của bạn',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          const Text(
            'Mở để xem phiếu khám và quét QR chung khi bạn đến bệnh viện.',
          ),
          const SizedBox(height: AppSpacing.md),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              key: const Key('open-journey-detail'),
              onPressed: () =>
                  context.push('/journey/${appointment.id}/ticket'),
              icon: const Icon(Icons.open_in_new_rounded),
              label: const Text('Xem phiếu khám'),
            ),
          ),
        ],
      ),
    );
  }
}

class _AppointmentTimelineCard extends StatelessWidget {
  const _AppointmentTimelineCard({required this.appointment});

  final Appointment appointment;

  @override
  Widget build(BuildContext context) {
    final statuses = [
      {'key': 'PENDING', 'label': 'Đặt khám', 'icon': Icons.schedule_rounded},
      {
        'key': 'CONFIRMED',
        'label': 'Xác nhận',
        'icon': Icons.check_circle_outline_rounded,
      },
      {'key': 'CHECKED_IN', 'label': 'Check-in', 'icon': Icons.login_rounded},
      {
        'key': 'IN_PROGRESS',
        'label': 'Đang khám',
        'icon': Icons.medical_services_rounded,
      },
      {'key': 'COMPLETED', 'label': 'Hoàn tất', 'icon': Icons.task_alt_rounded},
    ];

    if (appointment.status == 'CANCELLED') {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: BoxDecoration(
          color: AppColors.errorLight,
          borderRadius: BorderRadius.circular(AppRadius.lg),
        ),
        child: Row(
          children: [
            Icon(Icons.cancel_outlined, color: AppColors.error, size: 24),
            const SizedBox(width: AppSpacing.md),
            Text(
              'Lịch khám đã bị hủy',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppColors.error,
              ),
            ),
          ],
        ),
      );
    }

    final statusOrder = [
      'PENDING',
      'CONFIRMED',
      'CHECKED_IN',
      'IN_PROGRESS',
      'COMPLETED',
    ];
    final currentIndex = statusOrder.indexOf(appointment.status);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        boxShadow: AppShadows.card,
        border: Border.all(color: AppColors.cardBorder, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Tiến trình',
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: AppSpacing.base),
          ...statuses.asMap().entries.map((entry) {
            final i = entry.key;
            final s = entry.value;
            final isDone = i <= currentIndex;
            final isCurrent = i == currentIndex;
            final isLast = i == statuses.length - 1;

            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Column(
                  children: [
                    Container(
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        color: isDone
                            ? AppColors.success
                            : AppColors.cardBorder,
                        shape: BoxShape.circle,
                        border: isCurrent
                            ? Border.all(color: AppColors.primary, width: 2)
                            : null,
                      ),
                      child: Center(
                        child: isDone
                            ? const Icon(
                                Icons.check,
                                size: 16,
                                color: Colors.white,
                              )
                            : Icon(
                                s['icon'] as IconData,
                                size: 14,
                                color: AppColors.textHint,
                              ),
                      ),
                    ),
                    if (!isLast)
                      Container(
                        width: 2,
                        height: 28,
                        color: isDone
                            ? AppColors.success
                            : AppColors.cardBorder,
                      ),
                  ],
                ),
                const SizedBox(width: AppSpacing.md),
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    s['label'] as String,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: isCurrent ? FontWeight.w600 : FontWeight.w400,
                      color: isDone
                          ? AppColors.textPrimary
                          : AppColors.textHint,
                    ),
                  ),
                ),
              ],
            );
          }),
        ],
      ),
    );
  }
}

class _AppointmentCancelActionBar extends StatelessWidget {
  const _AppointmentCancelActionBar({
    required this.isCancelling,
    required this.onCancel,
  });

  final bool isCancelling;
  final VoidCallback onCancel;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.base),
      decoration: BoxDecoration(
        color: AppColors.surface,
        boxShadow: AppShadows.bottomNav,
      ),
      child: SafeArea(
        child: Row(
          children: [
            Expanded(
              child: SizedBox(
                height: 52,
                child: OutlinedButton(
                  onPressed: isCancelling ? null : onCancel,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.error,
                    side: const BorderSide(color: AppColors.error, width: 1.5),
                  ),
                  child: isCancelling
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Hủy lịch khám'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
