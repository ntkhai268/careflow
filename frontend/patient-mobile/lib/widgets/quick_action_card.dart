import 'package:flutter/material.dart';
import '../config/theme.dart';

/// Quick action card for Home screen grid (matches Medpro style)
class QuickActionCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  final Color? iconColor;
  final Color? backgroundColor;
  final bool showBadge;
  final String? badgeText;

  const QuickActionCard({
    super.key,
    required this.icon,
    required this.label,
    this.onTap,
    this.iconColor,
    this.backgroundColor,
    this.showBadge = false,
    this.badgeText,
  });

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 200),
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // Icon container
            Stack(
              clipBehavior: Clip.none,
              children: [
                Container(
                  width: 56,
                  height: 56,
                  decoration: BoxDecoration(
                    color: backgroundColor ?? AppColors.primarySurface,
                    borderRadius: BorderRadius.circular(AppRadius.lg),
                  ),
                  child: Icon(
                    icon,
                    size: 28,
                    color: iconColor ?? AppColors.primary,
                  ),
                ),
                if (showBadge && badgeText != null)
                  Positioned(
                    right: -4,
                    top: -4,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppColors.error,
                        borderRadius: BorderRadius.circular(AppRadius.full),
                      ),
                      child: Text(
                        badgeText!,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 8),
            // Label
            Text(
              label,
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: AppColors.textPrimary,
                    fontWeight: FontWeight.w500,
                    fontSize: 12,
                  ),
            ),
          ],
        ),
      ),
    );
  }
}
