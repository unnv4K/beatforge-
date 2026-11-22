package com.beatforge.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.beatforge.app.ui.BF
import com.beatforge.app.ui.BeatTheme
import com.beatforge.app.ui.ChannelScreen
import com.beatforge.app.ui.FxScreen
import com.beatforge.app.ui.HelpScreen
import com.beatforge.app.ui.SliceScreen
import com.beatforge.app.ui.StudioScreen

class MainActivity : ComponentActivity() {
    private val vm: BeatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { BeatTheme { App(vm) } }
    }

    override fun onStart() { super.onStart(); vm.engine.setPaused(false) }

    override fun onStop() {
        super.onStop()
        if (!vm.recording) vm.engine.setPaused(true)
    }
}

private val TABS = listOf("Студия" to "◼◼", "Канал" to "🎚", "FX" to "✦", "Нарезка" to "✂", "Справка" to "?")

@Composable
fun App(vm: BeatViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = BF.bg,
        bottomBar = {
            NavigationBar(containerColor = BF.panel) {
                TABS.forEachIndexed { i, (name, icon) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Text(icon, fontSize = 18.sp) },
                        label = { Text(name, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BF.orange, selectedTextColor = BF.orange,
                            unselectedIconColor = BF.dim, unselectedTextColor = BF.dim,
                            indicatorColor = BF.panel2
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().background(BF.bg).padding(pad)) {
            when (tab) {
                0 -> StudioScreen(vm)
                1 -> ChannelScreen(vm)
                2 -> FxScreen(vm)
                3 -> SliceScreen(vm)
                else -> HelpScreen(vm) { tab = 0 }
            }
        }
    }
}
