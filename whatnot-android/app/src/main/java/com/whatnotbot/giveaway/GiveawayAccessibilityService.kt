package com.whatnotbot.giveaway

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GiveawayAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "GiveawayBot"
        private const val WHATNOT_PACKAGE = "com.whatnot_mobile"
        private const val SCAN_INTERVAL_MS = 1000L
        private const val ACTION_TIMEOUT_MS = 10000L
        private const val COOLDOWN_MS = 3000L

        var instance: GiveawayAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var connected = false
    private var stateSince = 0L
    private var awaitingConfirmation = false
    private var currentState = BotState.IDLE

    enum class BotState { IDLE, WATCHING, ENTERING, ENTERED, COOLDOWN }

    // One polling chain: frequent content events must not postpone scans indefinitely.
    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!isScanning) return
            if (MainActivity.isBotActive) scanForGiveaway() else resetState()
            if (isScanning) handler.postDelayed(this, SCAN_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected = true
        instance = this
        MainActivity.giveawaysEntered = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(MainActivity.KEY_GIVEAWAY_COUNT, 0)
        if (MainActivity.isBotActive) startScanning()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != WHATNOT_PACKAGE || !MainActivity.isBotActive) return
        // Also recover after an interruption; leave the existing poll deadline alone.
        if (!isScanning) startScanning()
    }

    override fun onInterrupt() = stopScanning()

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        connected = false
        stopScanning()
        if (instance === this) instance = null
    }

    fun startScanning() {
        if (!connected || !MainActivity.isBotActive || isScanning) return
        isScanning = true
        resetState()
        handler.post(scanRunnable)
    }

    fun stopScanning() {
        isScanning = false
        handler.removeCallbacks(scanRunnable)
        resetState()
    }

    private fun resetState() {
        awaitingConfirmation = false
        transition(if (isScanning && MainActivity.isBotActive) BotState.WATCHING else BotState.IDLE)
    }

    private fun transition(state: BotState) {
        currentState = state
        stateSince = SystemClock.elapsedRealtime()
    }

    private fun scanForGiveaway() {
        val root = rootInActiveWindow ?: return
        try {
            // Check the same snapshot we act on; never inspect or click another app's tree.
            if (root.packageName?.toString() != WHATNOT_PACKAGE) {
                resetState()
                return
            }
            val entered = hasLabel(root, GiveawayLabels::isEntered)
            val elapsed = SystemClock.elapsedRealtime() - stateSince
            if (entered) {
                if (awaitingConfirmation) incrementGiveawayCount()
                awaitingConfirmation = false
                transition(BotState.ENTERED)
                return
            }
            when (currentState) {
                BotState.ENTERED -> transition(BotState.WATCHING)
                BotState.COOLDOWN -> if (elapsed >= COOLDOWN_MS) resetState()
                BotState.ENTERING -> {
                    if (elapsed >= ACTION_TIMEOUT_MS) {
                        awaitingConfirmation = false
                        transition(BotState.COOLDOWN)
                    } else if (!awaitingConfirmation && clickLabel(root, GiveawayLabels::isEntry)) {
                        awaitingConfirmation = true
                        transition(BotState.ENTERING)
                    }
                }
                BotState.IDLE, BotState.WATCHING -> {
                    // An expanded card takes priority over its giveaway heading/badge.
                    if (clickLabel(root, GiveawayLabels::isEntry)) {
                        awaitingConfirmation = true
                        transition(BotState.ENTERING)
                    } else if (clickLabel(root, GiveawayLabels::isBadge)) {
                        transition(BotState.ENTERING)
                    }
                }
            }
        } catch (e: Exception) {
            // Do not log screen text or exception messages that could contain it.
            Log.w(TAG, "Accessibility scan failed: ${e.javaClass.simpleName}")
            resetState()
        } finally {
            root.recycle()
        }
    }

    private fun matches(node: AccessibilityNodeInfo, predicate: (String) -> Boolean): Boolean =
        node.isVisibleToUser && (predicate(node.text?.toString().orEmpty()) ||
            predicate(node.contentDescription?.toString().orEmpty()))

    private fun hasLabel(node: AccessibilityNodeInfo, predicate: (String) -> Boolean): Boolean {
        if (matches(node, predicate)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (hasLabel(child, predicate)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun clickLabel(node: AccessibilityNodeInfo, predicate: (String) -> Boolean): Boolean {
        if (matches(node, predicate) && clickTarget(node)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (clickLabel(child, predicate)) return true
            } finally {
                child.recycle()
            }
        }
        return false
    }

    private fun clickTarget(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser || !node.isEnabled) return false
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val parent = node.parent ?: return false
        try {
            if (parent.packageName?.toString() != WHATNOT_PACKAGE) return false
            return clickTarget(parent)
        } finally {
            parent.recycle()
        }
    }

    private fun incrementGiveawayCount() {
        MainActivity.giveawaysEntered++
        getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(MainActivity.KEY_GIVEAWAY_COUNT, MainActivity.giveawaysEntered).apply()
        OverlayService.instance?.updateStatus("Entered! Total: ${MainActivity.giveawaysEntered}")
    }

    fun getCurrentState(): BotState = currentState
}
