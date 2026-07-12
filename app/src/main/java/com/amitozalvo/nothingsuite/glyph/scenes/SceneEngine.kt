package com.amitozalvo.nothingsuite.glyph.scenes

import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import com.amitozalvo.nothingsuite.state.ContextSnapshot

/**
 * Decides what the matrix shows: the current toast if any, otherwise the
 * first active scene in the user's configured order (ambient is the
 * always-active fallback). Pure logic — unit-testable.
 */
class SceneEngine(scenes: List<Scene>) {

    private val scenesById = scenes.associateBy { it.id }

    var currentToast: MatrixToast? = null
        private set

    fun postToast(toast: MatrixToast) {
        val current = currentToast
        // An OTP toast is only replaced by another OTP toast
        if (current is OtpToast && toast !is OtpToast) return
        currentToast = toast
    }

    /** Returns true if a toast was dismissed by the button press. */
    fun dismissToastByButton(): Boolean {
        val toast = currentToast ?: return false
        currentToast = null
        return toast.dismissableByButton
    }

    fun clearOtpToast(notificationKey: String? = null) {
        val toast = currentToast
        if (toast is OtpToast &&
            (notificationKey == null || toast.notificationKey == notificationKey)
        ) {
            currentToast = null
        }
    }

    fun selectScene(snapshot: ContextSnapshot, settings: GlyphSettings): Scene {
        val order = settings.activeSceneOrder()
        return order.asSequence()
            .mapNotNull { scenesById[it] }
            .firstOrNull { it.isActive(snapshot, settings) }
            ?: scenesById.getValue(com.amitozalvo.nothingsuite.config.SceneIds.AMBIENT)
    }

    /**
     * Render one frame. Expired toasts are cleaned up here so callers just
     * render on every tick/event.
     */
    fun renderFrame(snapshot: ContextSnapshot, settings: GlyphSettings, tick: Long): IntArray {
        val buffer = MatrixBuffer()
        currentToast?.let { toast ->
            if (toast.isExpired(snapshot.now)) {
                currentToast = null
            } else {
                toast.render(buffer, tick)
                return buffer.snapshot()
            }
        }
        selectScene(snapshot, settings).render(buffer, snapshot, settings, tick)
        return buffer.snapshot()
    }

    /** The active scene after the currently shown one, for button "peek". */
    fun peekNextScene(snapshot: ContextSnapshot, settings: GlyphSettings): Scene? {
        val active = settings.activeSceneOrder()
            .mapNotNull { scenesById[it] }
            .filter { it.isActive(snapshot, settings) }
        if (active.size < 2) return null
        val current = selectScene(snapshot, settings)
        val idx = active.indexOfFirst { it.id == current.id }
        return active[(idx + 1).mod(active.size)]
    }

    companion object {
        fun createDefault(): SceneEngine = SceneEngine(
            listOf(NextEventScene(), AlarmScene(), MediaScene(), AmbientScene())
        )
    }
}
