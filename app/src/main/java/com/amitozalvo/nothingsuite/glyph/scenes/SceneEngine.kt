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

    @Suppress("UNCHECKED_CAST")
    fun <T : Scene> scene(id: String): T = scenesById.getValue(id) as T

    var currentToast: MatrixToast? = null
        private set

    fun postToast(toast: MatrixToast) {
        val current = currentToast
        if (current != null && toast.priority < current.priority) return
        currentToast = toast
    }

    /** Returns true if a toast was dismissed by the button press. */
    fun dismissToastByButton(): Boolean {
        val toast = currentToast ?: return false
        if (!toast.dismissableByButton) return false
        currentToast = null
        return true
    }

    fun clearRingingAlarmToast() {
        if (currentToast is AlarmRingingToast) currentToast = null
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
        val toast = currentToast
        if (toast != null && toast.isExpired(snapshot.now)) currentToast = null
        currentToast.let { active ->
            if (active != null) {
                active.render(buffer, tick)
            } else {
                selectScene(snapshot, settings).render(buffer, snapshot, settings, tick)
            }
        }
        // The physical matrix is round — corner cells don't exist
        buffer.maskCircle()
        return buffer.snapshot()
    }

    companion object {
        fun createDefault(): SceneEngine = SceneEngine(
            listOf(NextEventScene(), AlarmScene(), MediaScene(), AmbientScene())
        )
    }
}
