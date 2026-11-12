package de.subnetcalc.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.subnetcalc.app.ui.SubnetScreen
import de.subnetcalc.app.ui.theme.SubnetCalcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubnetCalcTheme {
                SubnetScreen()
            }
        }
    }
}
