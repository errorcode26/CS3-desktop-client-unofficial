package com.lagradost.common.platform

class WebViewNotInstalledException(message: String = "Playwright/Chromium is not installed.") : RuntimeException(message)
