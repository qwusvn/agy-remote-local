package com.example.agyremote.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AgyWebView(
    url: String,
    modifier: Modifier = Modifier,
    onPageStarted: () -> Unit = {},
    onPageFinished: () -> Unit = {},
    onErrorReceived: (String) -> Unit = {},
    onRequestFileChooser: (ValueCallback<Array<Uri>>) -> Unit = {},
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    onPageStarted()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onPageFinished()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        onErrorReceived(error?.description?.toString() ?: "Không thể kết nối tới máy chủ AGY")
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback != null) {
                        onRequestFileChooser(filePathCallback)
                        return true
                    }
                    return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
                }
            }
        }
    }

    // Xử lý nút Back của Android để quay lại trang trước trong WebView nếu có thể
    BackHandler(enabled = webView.canGoBack()) {
        webView.goBack()
    }

    DisposableEffect(url) {
        onWebViewCreated(webView)
        webView.loadUrl(url)
        onDispose {
            // cleanup if needed
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}
