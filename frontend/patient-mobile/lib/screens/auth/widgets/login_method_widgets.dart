part of '../login_methods_screen.dart';

const loginQuestions = {
  'memory': 'Kỷ niệm riêng nào bạn luôn nhớ?',
  'phrase': 'Câu nói bí mật do bạn tự đặt là gì?',
  'place': 'Tên địa điểm đặc biệt chỉ bạn biết là gì?',
};

Future<String?> _showSecretQuestionBottomSheet(
  BuildContext context,
  String currentQuestion,
) {
  return showModalBottomSheet<String>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    backgroundColor: AppColors.surface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
    ),
    builder: (context) => SafeArea(
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Padding(
                padding: EdgeInsets.fromLTRB(8, 0, 8, 16),
                child: Text(
                  'Chọn câu hỏi bí mật',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                ),
              ),
              ...loginQuestions.entries.map(
                (q) => Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    tileColor: currentQuestion == q.key
                        ? AppColors.primarySurface
                        : AppColors.background,
                    title: Text(
                      q.value,
                      style: const TextStyle(fontSize: 16, height: 1.5),
                    ),
                    trailing: Icon(
                      currentQuestion == q.key
                          ? Icons.check_circle_rounded
                          : Icons.radio_button_unchecked,
                      color: AppColors.primary,
                    ),
                    onTap: () => Navigator.pop(context, q.key),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _MethodCard extends StatelessWidget {
  const _MethodCard({
    required this.title,
    required this.detail,
    required this.onTap,
    this.biometric = false,
  });
  final String title, detail;
  final VoidCallback? onTap;
  final bool biometric;
  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    child: Material(
      color: AppColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppColors.cardBorder),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: AppColors.primarySurface,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: biometric
                    ? const Icon(
                        Icons.fingerprint_rounded,
                        color: AppColors.primary,
                        size: 23,
                      )
                    : const Icon(
                        Icons.chat_bubble_outline_rounded,
                        color: AppColors.primary,
                        size: 23,
                      ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      detail,
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textSecondary,
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              const Icon(
                Icons.chevron_right_rounded,
                color: AppColors.textSecondary,
                size: 20,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _AuthNotice extends StatelessWidget {
  const _AuthNotice({required this.text, this.success = false});
  final String text;
  final bool success;
  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: success ? AppColors.successLight : AppColors.errorLight,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            success
                ? Icons.check_circle_outline_rounded
                : Icons.error_outline_rounded,
            size: 20,
            color: success ? AppColors.success : AppColors.error,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                fontSize: 14,
                height: 1.5,
                color: success ? AppColors.success : AppColors.error,
              ),
            ),
          ),
        ],
      ),
    ),
  );
}
