package com.example.racingpower

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember // Mantén esta importación
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.racingpower.ui.theme.RacingPowerTheme
import com.example.racingpower.utils.LocaleHelper
import com.example.racingpower.viewmodels.AuthState
import com.example.racingpower.viewmodels.AuthViewModel
import com.example.racingpower.viewmodels.InfiniteGameViewModel
// Importaciones de vistas explícitas para claridad
import com.example.racingpower.views.GameSelectionScreen
import com.example.racingpower.views.InfiniteBoatGameScreen
import com.example.racingpower.views.InfiniteGameScreen // Asumo que es el de los coches
import com.example.racingpower.views.InfinitePlaneGameScreen
import com.example.racingpower.views.LeaderboardScreen
import com.example.racingpower.views.LoginScreen
import com.example.racingpower.views.RegisterScreen
import com.example.racingpower.views.AvatarSelectionScreen

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel // ¡Importante!

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLanguage(newBase))
    }

    companion object {
        const val MY_CHANNEL_ID = "MyChannel"
        const val MY_CHANNEL_NAME = "General Notifications"
        const val MY_CHANNEL_DESCRIPTION = "Notifications for general app events"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Permiso de notificación denegado. Algunas notificaciones no se mostrarán.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            RacingPowerTheme {
                val navController = rememberNavController()
                val authState by authViewModel.authState.collectAsState()
                // Observa el userProfile del AuthViewModel para acceder al displayName
                val userProfile by authViewModel.userProfile.collectAsState()


                val showWelcomeNotification: (String) -> Unit = { username ->
                    showNotification(
                        title = getString(R.string.notification_welcome_title),
                        message = getString(R.string.notification_welcome_message, username),
                        notificationId = 1001
                    )
                }

                val showLogoutNotification: () -> Unit = {
                    showNotification(
                        title = getString(R.string.notification_logout_title),
                        message = getString(R.string.notification_logout_message),
                        notificationId = 1002
                    )
                }

                NavHost(navController = navController, startDestination = "splash_screen") {

                    composable("splash_screen") {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                        LaunchedEffect(authState, userProfile) { // Añade userProfile como key
                            when (val currentState = authState) {
                                is AuthState.Authenticated -> {
                                    // Espera a que userProfile no sea nulo y tenga un displayName
                                    // para usarlo en la notificación de bienvenida si es la primera vez que se lanza la app
                                    if (userProfile != null && userProfile?.displayName != null && userProfile?.displayName!!.isNotBlank()) {
                                        // La notificación de bienvenida ahora se puede disparar desde aquí
                                        // si el usuario ya estaba logueado al iniciar la app.
                                        // Si el login ocurre en LoginScreen, la notificación se dispara desde allí.
                                        // Este es un caso para cuando la app ya estaba abierta y el usuario autenticado.
                                        // showWelcomeNotification(userProfile?.displayName ?: currentState.user.email ?: "Usuario")
                                    }
                                    navController.navigate("game_selection_screen/${currentState.user.uid}") {
                                        popUpTo("splash_screen") { inclusive = true }
                                    }
                                }
                                AuthState.Unauthenticated, is AuthState.Error -> {
                                    navController.navigate("login_screen") {
                                        popUpTo("splash_screen") { inclusive = true }
                                    }
                                }
                                else -> {
                                    // loading state
                                }
                            }
                        }
                    }

                    composable("login_screen") {
                        LoginScreen(
                            navController = navController,
                            authViewModel = authViewModel, // Pasa el AuthViewModel a LoginScreen
                            onLoginSuccessNotification = showWelcomeNotification
                        )
                    }

                    composable("register_screen") {
                        RegisterScreen(
                            navController = navController,
                            authViewModel = authViewModel // Pasa el AuthViewModel a RegisterScreen
                        )
                    }

                    composable(
                        "game_selection_screen/{userId}",
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: "guest_user"
                        GameSelectionScreen(
                            userId = userId,
                            navController = navController,
                            onLogoutNotification = showLogoutNotification
                        )
                    }

                    composable(
                        "game_screen_cars/{userId}?displayName={displayName}",
                        arguments = listOf(
                            navArgument("userId") { type = NavType.StringType },
                            navArgument("displayName") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: "guest_user"
                        val displayName = backStackEntry.arguments?.getString("displayName")
                        InfiniteGameScreen( // Asumo que InfiniteGameScreen es para los coches
                            userId = userId,
                            displayName = displayName,
                            viewModel = viewModel(),
                            navController = navController
                        )
                    }

                    composable(
                        "game_screen_planes/{userId}?displayName={displayName}",
                        arguments = listOf(
                            navArgument("userId") { type = NavType.StringType },
                            navArgument("displayName") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: "guest_user"
                        val displayName = backStackEntry.arguments?.getString("displayName")
                        InfinitePlaneGameScreen(
                            userId = userId,
                            displayName = displayName,
                            viewModel = viewModel(),
                            navController = navController
                        )
                    }

                    composable(
                        "game_screen_boats/{userId}?displayName={displayName}",
                        arguments = listOf(
                            navArgument("userId") { type = NavType.StringType },
                            navArgument("displayName") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: "guest_user"
                        val displayName = backStackEntry.arguments?.getString("displayName")
                        InfiniteBoatGameScreen(
                            userId = userId,
                            displayName = displayName,
                            viewModel = viewModel(),
                            navController = navController
                        )
                    }

                    composable("leaderboard_screen") {
                        LeaderboardScreen(navController = navController)
                    }

                    composable(
                        "avatar_selection_screen/{userId}",
                        arguments = listOf(navArgument("userId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val userId = backStackEntry.arguments?.getString("userId") ?: "guest_user"
                        AvatarSelectionScreen(userId = userId, navController = navController)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = MY_CHANNEL_NAME
            val descriptionText = MY_CHANNEL_DESCRIPTION
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(MY_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        var builder = NotificationCompat.Builder(this, MY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
    }
}