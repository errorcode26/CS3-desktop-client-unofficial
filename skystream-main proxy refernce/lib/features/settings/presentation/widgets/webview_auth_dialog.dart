import 'dart:async';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../../shared/widgets/custom_widgets.dart';

class WebViewAuthDialog extends StatefulWidget {
  final String providerName;
  final String initialUrl;
  final String redirectUrlPrefix;

  const WebViewAuthDialog({
    super.key,
    required this.providerName,
    required this.initialUrl,
    required this.redirectUrlPrefix,
  });

  @override
  State<WebViewAuthDialog> createState() => _WebViewAuthDialogState();
}

class _WebViewAuthDialogState extends State<WebViewAuthDialog> {
  InAppWebViewController? webViewController;
  double progress = 0;
  final TextEditingController _urlController = TextEditingController();
  bool _isPopped = false;
  // Parsed once for the redirect comparison below.
  late final Uri? _expectedRedirect = Uri.tryParse(widget.redirectUrlPrefix);

  /// Compare a candidate redirect URL against [widget.redirectUrlPrefix] by
  /// scheme + host + optional port — never by raw string prefix.
  /// String prefix matching is unsafe because e.g. `http://localhost`
  /// would also match `http://localhost.attacker.com`, letting a
  /// malicious redirect inject a token from a different origin.
  bool _matchesRedirect(Uri url) {
    final expected = _expectedRedirect;
    if (expected == null) return false;
    if (url.scheme != expected.scheme) return false;
    if (url.host != expected.host) return false;
    if (expected.hasPort && url.port != expected.port) return false;
    return true;
  }

  void _handleRedirect(WebUri? url) {
    if (url == null || _isPopped) return;
    final parsed = Uri.tryParse(url.toString());
    if (parsed == null) return;

    final isExpected = _matchesRedirect(parsed);
    // AniList's implicit-grant flow puts the access token in the fragment of
    // its pin page (https://anilist.co/api/v2/oauth/pin#access_token=…)
    // before any redirect to localhost happens — accept that exact URL too.
    final isAnilistPin =
        widget.providerName == 'AniList' &&
        parsed.scheme == 'https' &&
        parsed.host == 'anilist.co' &&
        parsed.path == '/api/v2/oauth/pin';

    if (isExpected || isAnilistPin) {
      _isPopped = true;
      Navigator.of(context).pop(url.toString());
    }
  }

  /// Clear cookies, localStorage, and HTTP cache so a previous user's web
  /// session can't silently re-authenticate the device's current user.
  Future<void> _purgeWebViewState() async {
    try {
      await CookieManager.instance().deleteAllCookies();
    } catch (_) {}
    try {
      await InAppWebViewController.clearAllCache();
    } catch (_) {}
  }

  @override
  void initState() {
    super.initState();
    // Fire-and-forget — the webview launches the auth URL immediately, and
    // we want stale session state gone before the page even loads. If it
    // races, worst case the user sees themselves auto-logged-in once and
    // we still clear for next time.
    unawaited(_purgeWebViewState());
  }

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Avoid flutter_inappwebview on Windows/Linux as they are not fully supported
    if (!kIsWeb && (Platform.isWindows || Platform.isLinux)) {
      return _buildDesktopFallback(context);
    }
    return _buildWebView(context);
  }

  Widget _buildDesktopFallback(BuildContext context) {
    final theme = Theme.of(context);
    return AlertDialog(
      surfaceTintColor: Colors.transparent,
      title: Text('Login to ${widget.providerName}'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              '1. Click the button below to open the login page in your browser.',
            ),
            const SizedBox(height: 16),
            CustomButton(
              isPrimary: true,
              onPressed: () async {
                final uri = Uri.parse(widget.initialUrl);
                if (await canLaunchUrl(uri)) {
                  await launchUrl(uri, mode: LaunchMode.externalApplication);
                }
              },
              child: const Text('Open Browser'),
            ),
            const SizedBox(height: 24),
            Text(
              '2. After logging in, the browser will redirect you to a blank page starting with ${widget.redirectUrlPrefix}.',
            ),
            const SizedBox(height: 8),
            const Text(
              '3. Copy the ENTIRE URL from your browser\'s address bar and paste it below:',
            ),
            const SizedBox(height: 16),
            CustomTextField(
              controller: _urlController,
              decoration: const InputDecoration(
                labelText: 'Pasted Redirect URL',
                hintText: 'http://localhost/?code=...',
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(
            'Cancel',
            style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
          ),
        ),
        CustomButton(
          isPrimary: true,
          onPressed: () {
            final url = _urlController.text.trim();
            if (url.isNotEmpty) {
              Navigator.pop(context, url);
            }
          },
          child: const Text('Submit'),
        ),
      ],
    );
  }

  Widget _buildWebView(BuildContext context) {
    return Dialog(
      insetPadding: const EdgeInsets.all(16),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: SizedBox(
          width: 800,
          height: 600,
          child: Column(
            children: [
              AppBar(
                title: Text('Login to ${widget.providerName}'),
                leading: IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () => Navigator.pop(context),
                ),
                elevation: 0,
              ),
              if (progress < 1.0) LinearProgressIndicator(value: progress),
              Expanded(
                child: InAppWebView(
                  initialUrlRequest: URLRequest(url: WebUri(widget.initialUrl)),
                  initialSettings: InAppWebViewSettings(
                    useShouldOverrideUrlLoading: true,
                    javaScriptEnabled: true,
                  ),
                  onWebViewCreated: (controller) {
                    webViewController = controller;
                  },
                  onLoadStart: (controller, url) {
                    _handleRedirect(url);
                  },
                  onLoadStop: (controller, url) {
                    _handleRedirect(url);
                  },
                  onUpdateVisitedHistory: (controller, url, isReload) {
                    _handleRedirect(url);
                  },
                  onReceivedError: (controller, request, error) {
                    _handleRedirect(request.url);
                  },
                  shouldOverrideUrlLoading:
                      (controller, navigationAction) async {
                        final url = navigationAction.request.url;
                        if (url != null) {
                          final parsed = Uri.tryParse(url.toString());
                          if (parsed != null && _matchesRedirect(parsed)) {
                            _handleRedirect(url);
                            return NavigationActionPolicy.CANCEL;
                          }
                        }
                        return NavigationActionPolicy.ALLOW;
                      },
                  onProgressChanged: (controller, progress) {
                    setState(() {
                      this.progress = progress / 100;
                    });
                  },
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
