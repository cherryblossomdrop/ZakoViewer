package moe.zako.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import moe.zako.viewer.core.icon.AppIconManager
import moe.zako.viewer.ui.components.ZakoToastHost
import moe.zako.viewer.ZakoApplication
import moe.zako.viewer.ui.navigation.ZakoNavHost
import moe.zako.viewer.ui.navigation.Screen
import moe.zako.viewer.ui.theme.ZakoViewerTheme
import moe.zako.viewer.ui.theme.ThemePreset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as ZakoApplication

        setContent {
            val dynamicColor by app.settingsRepository.dynamicColorFlow.collectAsState(initial = false)
            val customThemeKey by app.settingsRepository.customThemeFlow.collectAsState(initial = "#686BDD")
            val coverBlurEnabled by app.settingsRepository.coverBlurFlow.collectAsState(initial = true)

            val themePreset = ThemePreset.fromKey(customThemeKey) ?: ThemePreset.PURPLE

            ZakoViewerTheme(
                dynamicColor = dynamicColor,
                themePreset = themePreset,
                themeKey = customThemeKey,
                coverBlurEnabled = coverBlurEnabled
            ) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                val isTabletLandscape = configuration.smallestScreenWidthDp >= 600 &&
                        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

                val showBottomBar = currentRoute?.startsWith("home") == true || currentRoute in listOf(
                    Screen.Home.route,
                    Screen.Favorites.route,
                    Screen.Profile.route
                )

                val bottomPadding by animateDpAsState(
                    targetValue = if (showBottomBar && !isTabletLandscape) 92.dp else 40.dp,
                    label = "toastBottomPadding"
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    ZakoNavHost(
                        navController = navController,
                        app = app
                    )

                    ZakoToastHost(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(bottom = bottomPadding)
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        val app = application as ZakoApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppIconManager.syncIcon(applicationContext, app.settingsRepository)
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
