package com.singalong07.app.service.overlay

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.singalong07.app.R
import com.singalong07.app.SingAlongApp
import com.singalong07.app.service.media.MediaSessionControllerBridge
import com.singalong07.app.ui.main.MainActivity
import com.singalong07.app.ui.overlay.FloatingOverlayRoot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class FloatingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceViewModelStore = ViewModelStore()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var windowManager: WindowManager? = null
    private var overlayComposeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var controllerBridge: MediaSessionControllerBridge? = null

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = serviceViewModelStore
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val app = SingAlongApp.instance
        controllerBridge = app.controllerBridge

        startForeground(NOTIFICATION_ID, createForegroundNotification())
        initOverlayWindow()
        observePreferences()
    }

    private fun initOverlayWindow() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        overlayComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            setContent {
                controllerBridge?.let { bridge ->
                    FloatingOverlayRoot(
                        controllerBridge = bridge,
                        onWindowResizeRequested = { isExpanded ->
                            updateWindowDimensions(isExpanded)
                        }
                    )
                }
            }

            setOnTouchListener(createDragTouchListener())
        }

        try {
            windowManager?.addView(overlayComposeView, layoutParams)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (_: Exception) {}
    }

    private var isCurrentWindowExpanded: Boolean = false
    private var exitGhostComposeView: ComposeView? = null
    private var exitGhostLayoutParams: WindowManager.LayoutParams? = null

    private fun updateWindowDimensions(isExpanded: Boolean) {
        isCurrentWindowExpanded = isExpanded
        val app = SingAlongApp.instance
        val isGhostMode = app.preferencesManager.settingsFlow.value.isClickThroughEnabled
        val shouldGhost = isExpanded && isGhostMode

        layoutParams?.let { params ->
            val density = resources.displayMetrics.density
            if (isExpanded) {
                params.width = (320 * density).toInt()
                params.height = (440 * density).toInt()
            } else {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
            }

            // The Pill is NEVER affected by ghost mode! Only expanded card can pass touches.
            params.flags = if (shouldGhost) {
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            }

            windowManager?.updateViewLayout(overlayComposeView, params)
        }

        if (shouldGhost) {
            showExitGhostButton()
        } else {
            hideExitGhostButton()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE_GHOST_MODE) {
            val app = SingAlongApp.instance
            val current = app.preferencesManager.settingsFlow.value.isClickThroughEnabled
            app.preferencesManager.toggleClickThrough(!current)
        }
        return START_STICKY
    }

    private fun observePreferences() {
        val app = SingAlongApp.instance
        serviceScope.launch {
            app.preferencesManager.settingsFlow.collectLatest { settings ->
                val shouldGhost = isCurrentWindowExpanded && settings.isClickThroughEnabled

                layoutParams?.let { params ->
                    // The Pill is NEVER affected by ghost mode!
                    params.flags = if (shouldGhost) {
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    } else {
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    }
                    windowManager?.updateViewLayout(overlayComposeView, params)
                }

                if (shouldGhost) {
                    showExitGhostButton()
                } else {
                    hideExitGhostButton()
                }

                val notificationManager = getSystemService(android.app.NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, createForegroundNotification(settings.isClickThroughEnabled))
            }
        }
    }

    private fun showExitGhostButton() {
        if (exitGhostComposeView != null) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val density = resources.displayMetrics.density
        val cardX = layoutParams?.x ?: 100
        val cardY = layoutParams?.y ?: 200

        exitGhostLayoutParams = WindowManager.LayoutParams(
            (44 * density).toInt(),
            (44 * density).toInt(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cardX + (276 * density).toInt()
            y = (cardY - 10 * density).toInt().coerceAtLeast(10)
        }

        exitGhostComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeViewModelStoreOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)

            setContent {
                val app = SingAlongApp.instance
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF0F172A),
                    border = BorderStroke(2.dp, Color(0xFF00FFFF)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            // Cross icon is NEVER affected by ghost: tap immediately exits ghost mode!
                            app.preferencesManager.toggleClickThrough(false)
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Exit Ghost Mode",
                            tint = Color(0xFF00FFFF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        try {
            windowManager?.addView(exitGhostComposeView, exitGhostLayoutParams)
        } catch (_: Exception) {}
    }

    private fun hideExitGhostButton() {
        exitGhostComposeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {}
            exitGhostComposeView = null
            exitGhostLayoutParams = null
        }
    }

    private fun createDragTouchListener(): View.OnTouchListener {
        return object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val params = layoutParams ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return false // Allow child clicks if not dragging
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()

                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            params.x = initialX + deltaX
                            params.y = initialY + deltaY
                            windowManager?.updateViewLayout(overlayComposeView, params)
                            return true
                        }
                    }
                }
                return false
            }
        }
    }

    private fun createForegroundNotification(isGhostActive: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val toggleGhostPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingOverlayService::class.java).apply {
                action = ACTION_TOGGLE_GHOST_MODE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, SingAlongApp.CHANNEL_OVERLAY_SERVICE)
            .setContentTitle("SingAlong07 Active")
            .setContentText(if (isGhostActive) "Ghost Mode ON (Pass-through touch)" else "Floating timed lyrics synchronized")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (isGhostActive) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Exit Ghost Mode (Enable Touch)",
                toggleGhostPendingIntent
            )
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()

        overlayComposeView?.let {
            windowManager?.removeView(it)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 7007
        const val ACTION_TOGGLE_GHOST_MODE = "com.singalong07.app.ACTION_TOGGLE_GHOST_MODE"
    }
}
