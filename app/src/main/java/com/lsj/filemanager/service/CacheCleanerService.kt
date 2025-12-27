package com.lsj.filemanager.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.lsj.filemanager.util.GlobalEvents
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue

class CacheCleanerService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val packageQueue = LinkedBlockingQueue<String>()
    private var isRunning = false
    private var currentPackage: String? = null
    private var step = 0 // 0: App Info, 1: Storage, 2: Clear Cache

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var statusTextView: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        serviceScope.launch {
            GlobalEvents.startCacheCleaning.collect { packages ->
                if (!isRunning) {
                    packageQueue.addAll(packages)
                    processNext()
                }
            }
        }

        serviceScope.launch {
            GlobalEvents.stopCacheCleaning.collect {
                packageQueue.clear()
                isRunning = false
                currentPackage = null
                GlobalEvents.updateCacheCleaningRunning(false)
                GlobalEvents.updateCacheCleaningStatus(null)
            }
        }

        serviceScope.launch {
            GlobalEvents.isCacheCleaningRunning.collect { running ->
                if (running) {
                    showOverlay()
                } else {
                    hideOverlay()
                }
            }
        }

        serviceScope.launch {
            GlobalEvents.cacheCleaningStatus.collect { status ->
                statusTextView?.text = status ?: "Clearing cache..."
            }
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val context = this
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                setColor(0xCC000000.toInt()) // Semi-transparent black
                cornerRadius = 28 * resources.displayMetrics.density
            }
        }

        statusTextView = TextView(context).apply {
            text = "Cleaning cache..."
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (8 * resources.displayMetrics.density).toInt())
        }
        layout.addView(statusTextView)

        val cancelButton = Button(context).apply {
            text = "CANCEL"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val btnPadding = (12 * resources.displayMetrics.density).toInt()
            val btnPaddingSide = (24 * resources.displayMetrics.density).toInt()
            setPadding(btnPaddingSide, btnPadding, btnPaddingSide, btnPadding)
            background = GradientDrawable().apply {
                setColor(0x44FFFFFF.toInt()) // subtle white background
                cornerRadius = 16 * resources.displayMetrics.density
            }
            setOnClickListener {
                serviceScope.launch {
                    GlobalEvents.triggerStopCacheCleaning()
                }
            }
        }
        layout.addView(cancelButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (8 * resources.displayMetrics.density).toInt()
        })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (100 * resources.displayMetrics.density).toInt()
        }

        overlayView = layout
        windowManager.addView(overlayView, params)
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
            statusTextView = null
        }
    }

    private fun processNext() {
        if (packageQueue.isEmpty()) {
            isRunning = false
            currentPackage = null
            GlobalEvents.updateCacheCleaningRunning(false)
            serviceScope.launch { GlobalEvents.updateCacheCleaningStatus(null) }
            return
        }

        isRunning = true
        GlobalEvents.updateCacheCleaningRunning(true)
        currentPackage = packageQueue.poll()
        step = 0
        serviceScope.launch {
            GlobalEvents.updateCacheCleaningStatus("Cleaning cache: $currentPackage")
            openAppDetails(currentPackage!!)
        }
    }

    private fun openAppDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isRunning || currentPackage == null) return

        val rootNode = rootInActiveWindow ?: return

        when (step) {
            0 -> {
                // In App Info, find "Storage"
                if (findAndClick(rootNode, listOf("Storage", "Storage & cache"))) {
                    step = 1
                }
            }
            1 -> {
                // In Storage, find "Clear cache"
                if (findAndClick(rootNode, listOf("Clear cache"))) {
                    step = 2
                    // After clicking clear cache, we wait a bit then go back or next
                    serviceScope.launch {
                        delay(500)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(200)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        delay(200)
                        processNext()
                    }
                }
            }
        }
    }

    private fun findAndClick(rootNode: AccessibilityNodeInfo, texts: List<String>): Boolean {
        for (text in texts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
                // If not clickable, try to click the parent
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    parent = parent.parent
                }
            }
        }
        return false
    }

    override fun onInterrupt() {
        isRunning = false
        GlobalEvents.updateCacheCleaningRunning(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
