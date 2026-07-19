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
        val view = ComposeView(this)
        imeLifecycleOwner.attach(view)
        view.setContent {
            KeyboardView()
        }
        return view
    }

    override fun onDestroy() {
        imeLifecycleOwner.onDestroy()
        super.onDestroy()
    }
}
