import 'package:flutter/material.dart';
import 'custom_widgets.dart';
import 'package:skystream/l10n/generated/app_localizations.dart';
import 'loading_indicator.dart';

class LoadingDialog extends StatelessWidget {
  final String message;
  final VoidCallback onCancel;

  const LoadingDialog({
    super.key,
    required this.message,
    required this.onCancel,
  });

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false, // Prevent dismissing by back button without using Cancel
      onPopInvokedWithResult: (didPop, result) {
        if (didPop) return;
        // Optionally handle back button here if you want it to trigger onCancel
      },
      child: AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 24),
            const AppLoadingIndicator(),
            const SizedBox(height: 24),
            Text(
              message,
              textAlign: TextAlign.center,
              style: Theme.of(
                context,
              ).textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.w500),
            ),
          ],
        ),
        actions: [
          CustomButton(
            isPrimary: false,
            onPressed: () {
              onCancel();
              Navigator.of(context).pop();
            },
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Text(AppLocalizations.of(context)!.cancel),
            ),
          ),
        ],
      ),
    );
  }

  static Future<void> show(
    BuildContext context, {
    required String message,
    required VoidCallback onCancel,
  }) {
    return showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) => LoadingDialog(message: message, onCancel: onCancel),
    );
  }
}
