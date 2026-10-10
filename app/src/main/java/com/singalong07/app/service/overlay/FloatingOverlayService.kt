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
import androidx.compose.ui.platform.ComposeView
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
        controllerBridge = MediaSessionControllerBridge(app.syncEngine, serviceScope)

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUS_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

    private fun updateWindowDimensions(isExpanded: Boolean) {
        layoutParams?.let { params ->
            if (isExpanded) {
                val density = resources.displayMetrics.density
                params.width = (320 * density).toInt()
                params.height = (440 * density).toInt()
            } else {
                params.width = WindowManager.LayoutParams.WRAP_CONTENT
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
            }
            windowManager?.updateViewLayout(overlayComposeView, params)
        }
    }

    private fun observePreferences() {
        val app = SingAlongApp.instance
        serviceScope.launch {
            app.preferencesManager.settingsFlow.collectLatest { settings ->
                layoutParams?.let { params ->
                    val touchableFlags = if (settings.isClickThroughEnabled) {
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    } else {
                        WindowManager.LayoutParams.FLAG_NOT_FOCUS_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    }

                    params.flags = touchableFlags
                    windowManager?.updateViewLayout(overlayComposeView, params)
                }
            }
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

    private fun createForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SingAlongApp.CHANNEL_OVERLAY_SERVICE)
            .setContentTitle("SingAlong07 Active")
            .setContentText("Floating timed lyrics synchronized")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
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
        controllerBridge?.detachMediaController()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 7007
    }
}
