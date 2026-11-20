package moe.zako.viewer.core.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.first
import moe.zako.viewer.core.logger.ZakoLog
import moe.zako.viewer.data.repository.SettingsRepository

object AppIconManager {
    private const val TAG = "AppIconManager"

    const val ALIAS_DEFAULT = "moe.zako.viewer.MainActivityAliasDefault"
    const val ALIAS_MONET = "moe.zako.viewer.MainActivityAliasMonet"
    const val ALIAS_PURPLE = "moe.zako.viewer.MainActivityAliasPurple"
    const val ALIAS_GREEN = "moe.zako.viewer.MainActivityAliasGreen"
    const val ALIAS_YELLOW = "moe.zako.viewer.MainActivityAliasYellow"
    const val ALIAS_BLUE = "moe.zako.viewer.MainActivityAliasBlue"

    val ALL_ALIASES = listOf(
        ALIAS_DEFAULT,
        ALIAS_MONET,
        ALIAS_PURPLE,
        ALIAS_GREEN,
        ALIAS_YELLOW,
        ALIAS_BLUE
    )

    fun getTargetAlias(followTheme: Boolean, dynamicColor: Boolean, customTheme: String): String {
        if (!followTheme) {
            return ALIAS_DEFAULT
        }
        if (dynamicColor) {
            return ALIAS_MONET
        }
        return when (customTheme.trim().uppercase()) {
            "#686BDD" -> ALIAS_PURPLE
            "#5BB974" -> ALIAS_GREEN
            "#E5C138" -> ALIAS_YELLOW
            "#008BF3" -> ALIAS_BLUE
            else -> ALIAS_DEFAULT
        }
    }

    suspend fun syncIcon(context: Context, settingsRepository: SettingsRepository) {
        val followTheme = settingsRepository.appIconFollowThemeFlow.first()
        val dynamicColor = settingsRepository.dynamicColorFlow.first()
        val customTheme = settingsRepository.customThemeFlow.first()

        val targetAlias = getTargetAlias(followTheme, dynamicColor, customTheme)
        applyIconAlias(context, targetAlias)
    }

    fun applyIconAlias(context: Context, targetAlias: String) {
        val pm = context.packageManager
        val pkg = context.packageName

        fun isAliasEnabled(alias: String): Boolean {
            val comp = ComponentName(pkg, alias)
            val state = pm.getComponentEnabledSetting(comp)
            return when (state) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                else -> (alias == ALIAS_DEFAULT)
            }
        }

        val currentActiveAlias = ALL_ALIASES.firstOrNull { isAliasEnabled(it) } ?: ALIAS_DEFAULT

        if (currentActiveAlias == targetAlias) {
            ZakoLog.d(TAG, "Current app icon alias is already $targetAlias, skipping update")
            return
        }

        ZakoLog.i(TAG, "Switching app icon alias from $currentActiveAlias to $targetAlias")

        // 1. Enable target alias first
        pm.setComponentEnabledSetting(
            ComponentName(pkg, targetAlias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // 2. Disable all other aliases
        ALL_ALIASES.filter { it != targetAlias }.forEach { alias ->
            pm.setComponentEnabledSetting(
                ComponentName(pkg, alias),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
