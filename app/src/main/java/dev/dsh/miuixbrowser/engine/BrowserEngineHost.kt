package dev.dsh.miuixbrowser.engine

import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView

/** All downloadable engines expose an Android View; engine-specific classes stay in their plugin APK. */
@Composable
fun BrowserEngineHost(adapter: BrowserEngineAdapter, modifier: Modifier = Modifier) {
    val engineView = adapter.view
    AndroidView(
        factory = { context ->
            (engineView.parent as? ViewGroup)?.removeView(engineView)
            engineView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            androidx.core.view.ViewCompat.requestApplyInsets(engineView)
            engineView
        },
        update = { view ->
            view.post {
                if (view.isAttachedToWindow) {
                    androidx.core.view.ViewCompat.requestApplyInsets(view)
                    view.requestLayout()
                }
            }
        },
        modifier = modifier.fillMaxSize().onSizeChanged { size ->
            if (size.width > 0 && size.height > 0) {
                engineView.post {
                    if (engineView.isAttachedToWindow) {
                        engineView.requestLayout(); engineView.invalidate()
                        if (adapter is WebViewEngineAdapter) engineView.evaluateJavascript(
                            "window.dispatchEvent(new Event('resize')); if(window.visualViewport)window.visualViewport.dispatchEvent(new Event('resize'));", null
                        )
                    }
                }
            }
        },
    )
}
