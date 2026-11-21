package com.ofalvai.habittracker.feature.misc.healthconnect

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ofalvai.habittracker.core.ui.component.AppDefaultAppBar
import com.ofalvai.habittracker.feature.misc.R
import com.ofalvai.habittracker.core.ui.R as coreR

/**
 * Status-only setup screen. Read-only permission requests require an explicit
 * user selection and button click; this screen never reads health records.
 */
@Composable
fun HealthConnectScreen(
    viewModel: HealthConnectViewModel,
    navigateBack: () -> Unit
) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val status by viewModel.status.collectAsState()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        SupportedHealthConnectScreen(viewModel, status, navigateBack)
    } else {
        HealthConnectContent(
            status = HealthConnectStatus.Unavailable,
            navigateBack = navigateBack,
            retry = viewModel::refresh,
            requestReadAccess = null,
            permissionDenied = false
        )
    }
}

@Composable
private fun SupportedHealthConnectScreen(
    viewModel: HealthConnectViewModel,
    status: HealthConnectStatus?,
    navigateBack: () -> Unit
) {
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        permissionDenied = !granted.contains(viewModel.exerciseReadPermission)
        viewModel.refresh()
    }
    HealthConnectContent(
        status = status,
        navigateBack = navigateBack,
        retry = {
            permissionDenied = false
            viewModel.refresh()
        },
        requestReadAccess = {
            try {
                permissionDenied = false
                permissionLauncher.launch(setOf(viewModel.exerciseReadPermission))
            } catch (error: Exception) {
                viewModel.showError(error.message ?: "Unable to request permission")
            }
        },
        permissionDenied = permissionDenied
    )
}

@Composable
private fun HealthConnectContent(
    status: HealthConnectStatus?,
    navigateBack: () -> Unit,
    retry: () -> Unit,
    requestReadAccess: (() -> Unit)?,
    permissionDenied: Boolean
) {
    var selectedExerciseAccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        AppDefaultAppBar(
            title = { Text(stringResource(R.string.health_connect_title)) },
            navigationIcon = {
                IconButton(onClick = navigateBack) {
                    Icon(Icons.Rounded.ArrowBack, stringResource(coreR.string.common_back))
                }
            }
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.health_connect_description),
                style = MaterialTheme.typography.bodyLarge
            )
            when (status) {
                null -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.health_connect_checking))
                }
                HealthConnectStatus.Unavailable -> {
                    Text(stringResource(R.string.health_connect_unavailable))
                    Button(onClick = retry) { Text(stringResource(R.string.health_connect_retry)) }
                }
                HealthConnectStatus.SetupRequired -> {
                    Text(stringResource(R.string.health_connect_setup_required))
                    Button(onClick = retry) { Text(stringResource(R.string.health_connect_retry)) }
                }
                HealthConnectStatus.PermissionRequired -> {
                    Text(stringResource(R.string.health_connect_permission_required))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = selectedExerciseAccess,
                            onCheckedChange = { selectedExerciseAccess = it }
                        )
                        Text(
                            text = stringResource(R.string.health_connect_consent),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { requestReadAccess?.invoke() },
                        enabled = selectedExerciseAccess && requestReadAccess != null
                    ) {
                        Text(stringResource(R.string.health_connect_request_access))
                    }
                    if (permissionDenied) {
                        Text(stringResource(R.string.health_connect_permission_denied))
                    }
                }
                HealthConnectStatus.Ready -> {
                    Text(stringResource(R.string.health_connect_ready))
                    Button(onClick = retry) { Text(stringResource(R.string.health_connect_retry)) }
                }
                is HealthConnectStatus.Error -> {
                    Text(stringResource(R.string.health_connect_error, status.message))
                    Button(onClick = retry) { Text(stringResource(R.string.health_connect_retry)) }
                }
            }
            Text(
                text = stringResource(R.string.health_connect_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
