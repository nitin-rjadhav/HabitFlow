package com.ofalvai.habittracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ofalvai.habittracker.core.ui.theme.AppTheme
import com.ofalvai.habittracker.feature.misc.R

/** Android Health Connect may launch this activity from its permission dialog. */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme(isDynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            text = getString(R.string.health_connect_rationale_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = getString(R.string.health_connect_rationale_body),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(onClick = { finish() }) {
                            Text(getString(R.string.health_connect_rationale_close))
                        }
                    }
                }
            }
        }
    }
}
