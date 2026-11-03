package dev.mauve.fit.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.mauve.fit.ui.Accent

/**
 * The launcher icon follows the accent colour: the manifest has one launcher alias per accent
 * (".IconCopper", ".IconSage", ...) and exactly one of them is enabled. Switched only when the app
 * goes to the background, since some launchers redraw or briefly drop the icon on a change.
 */
object LauncherIcon {
    private fun component(context: Context, accent: Accent) =
        ComponentName(context, "dev.mauve.fit.Icon" + accent.key.replaceFirstChar { it.uppercase() })

    fun apply(context: Context, accent: Accent) {
        val pm = context.packageManager
        val wanted = component(context, accent)
        if (pm.getComponentEnabledSetting(wanted) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return
        // Enable the new entry first, so there is never a moment without any launcher icon.
        pm.setComponentEnabledSetting(wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        for (a in Accent.entries) {
            if (a == accent) continue
            val c = component(context, a)
            if (pm.getComponentEnabledSetting(c) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) continue
            pm.setComponentEnabledSetting(c, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
