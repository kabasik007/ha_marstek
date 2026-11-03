package ua.zilla.energy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import ua.zilla.energy.ui.EnergyApp
import ua.zilla.energy.ui.theme.ZillaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZillaTheme {
                val model: EnergyViewModel = viewModel()
                EnergyApp(model)
            }
        }
    }
}
