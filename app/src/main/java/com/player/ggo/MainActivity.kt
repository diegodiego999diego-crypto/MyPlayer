package com.player.ggo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.player.ggo.ui.FolderListScreen
import com.player.ggo.ui.PlayerScreen
import com.player.ggo.ui.SongListScreen
import com.player.ggo.ui.theme.MyPlayerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppRoot()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-escaneo completo al abrir la app: cubre cambios con la app
        // cerrada. Se hace en onStart para que tambien cubra el regreso
        // desde segundo plano. Los observers se inician desde el
        // ViewModel al conceder permiso (init-flow), asi que aqui no
        // duplicamos arranque.
        appViewModel()?.let { vm ->
            if (vm.uiState.value.permissionGranted) {
                vm.refreshFromMediaStore()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Si la activity se esta terminando (no es solo rotacion),
        // libera observers para no filtrar FileObserver/ContentObserver.
        if (isFinishing) {
            appViewModel()?.stopObservers()
        }
    }

    /**
     * Devuelve el MainViewModel actual si ya fue creado por Compose.
     * Si la actividad acaba de crearse y Compose aun no corrio, retorna
     * null y el re-escaneo de onStart se pospone al primer recoleccion
     * de UiState (que dispara onPermissionResult).
     */
    private fun appViewModel(): MainViewModel? = AppRootVmHolder.vm
}

/** Holder estatico para que MainActivity pueda acceder al ViewModel
 * desde onStart()/onStop() antes de que Compose lo reconfigure. */
internal object AppRootVmHolder { @Volatile var vm: MainViewModel? = null }

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    AppRootVmHolder.vm = vm
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()

    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.onPermissionResult(true) else launcher.launch(permission)
    }

    val navTarget = when {
        !state.permissionGranted -> "permission"
        state.playerVisible -> "player"
        state.selectedFolder != null -> "songs"
        else -> "folders"
    }

    // El reproductor completo entra deslizandose desde abajo (como las
    // apps de musica nativas); el resto de pantallas usan fundido.
    AnimatedContent(
        targetState = navTarget,
        transitionSpec = {
            when {
                targetState == "player" ->
                    (slideInVertically { it } + fadeIn()) togetherWith fadeOut()
                initialState == "player" ->
                    fadeIn() togetherWith (slideOutVertically { it } + fadeOut())
                else -> fadeIn() togetherWith fadeOut()
            }
        },
        label = "nav"
    ) { target ->
        when (target) {
            "permission" -> PermissionRequestScreen { launcher.launch(permission) }
            "player" -> PlayerScreen(vm)
            "songs" -> SongListScreen(vm)
            else -> FolderListScreen(vm)
        }
    }
}

@Composable
private fun PermissionRequestScreen(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MyPlayer necesita permiso para leer tu música.",
            style = MaterialTheme.typography.titleMedium
        )
        Button(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("Conceder permiso")
        }
    }
}
