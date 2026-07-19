package com.lekitech.lezgikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.ui.platform.ComposeView
import com.lekitech.lezgikeyboard.ui.KeyboardView

/**
 * IME entry point: hosts the Compose keyboard and owns the fixed-height
 * contract (the keyboard determines its own height — never the host).
 * Text operations, model wiring, and the event pipeline arrive with the
 * later stages; Stage 1 proves the geometry on device.
 */
class LezgiInputMethodService : InputMethodService() {

    private lateinit var imeLifecycleOwner: ImeLifecycleOwner

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner = ImeLifecycleOwner()
        imeLifecycleOwner.onCreate()
    }

    override fun onCreateInputView(): View {
        // Compose resolves the window Recomposer from the window root
        // (the decor view), not from the ComposeView, so the lifecycle
        // owners must be reachable from there — otherwise the first
        // window traversal aborts and the keyboard never appears.
        window?.window?.decorView?.let(imeLifecycleOwner::attach)
        val view = ComposeView(this)
        imeLifecycleOwner.attach(view)
        view.setContent {
            KeyboardView()
        }
        return view
    }

    // Never use fullscreen extract mode: the platform default turns it on
    // in landscape, replacing the host field with a fullscreen editor and
    // breaking the fixed-height contract. The keyboard is always just the
    // keyboard (DECISIONS.md D-018).
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        imeLifecycleOwner.onDestroy()
        super.onDestroy()
    }
}
