package com.mertjr.brawlbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BotService : AccessibilityService() {

    @Volatile var currentPkg: String = ""

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var lastShot = 0L

    private var brain: Brain? = null
    private var thread: Thread? = null

    private var box: LinearLayout? = null
    private var logView: TextView? = null
    private var b1: Button? = null
    private var b2: Button? = null
    private val lines = ArrayList<String>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        buildOverlay()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null) return
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val p = e.packageName?.toString() ?: return
            if (p == packageName || p == "com.android.systemui") return
            if (p.contains("inputmethod") || p.contains("keyboard")) return
            currentPkg = p
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        stopBot()
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            if (box != null) wm.removeView(box)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ---------- Overlay ----------
    private fun buildOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(0x88000000.toInt())

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        val a = Button(this)
        a.text = "BASLAT"
        a.textSize = 10f
        a.minHeight = 0
        a.minimumHeight = 0
        a.setOnClickListener { toggle(false) }

        val c = Button(this)
        c.text = "KALIB."
        c.textSize = 10f
        c.minHeight = 0
        c.minimumHeight = 0
        c.setOnClickListener { toggle(true) }

        row.addView(a)
        row.addView(c)

        val tv = TextView(this)
        tv.textSize = 9f
        tv.setTextColor(Color.WHITE)
        tv.maxLines = 10

        panel.addView(row)
        panel.addView(tv)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        wm.addView(panel, lp)

        box = panel
        logView = tv
        b1 = a
        b2 = c
    }

    private fun updateButtons() {
        val calisiyor = thread?.isAlive == true
        main.post {
            b1?.text = if (calisiyor) "DURDUR" else "BASLAT"
            b2?.text = if (calisiyor) "DURDUR" else "KALIB."
        }
    }

    fun log(m: String) {
        Log.d("BrawlBot", m)
        val t: String
        synchronized(lines) {
            lines.add(m)
            while (lines.size > 6) lines.removeAt(0)
            t = lines.joinToString("\n")
        }
        main.post { logView?.text = t }
    }

    fun setLines(l: List<String>) {
        val t = l.joinToString("\n")
        main.post { logView?.text = t }
    }

    // ---------- Bot kontrol ----------
    private fun toggle(kalibrasyon: Boolean) {
        if (thread?.isAlive == true) {
            stopBot()
            return
        }
        val rootPkg = rootInActiveWindow?.packageName?.toString()
        if (rootPkg != null && rootPkg != packageName) currentPkg = rootPkg
        val b = Brain(this)
        b.calibrate = kalibrasyon
        b.running = true
        brain = b
        val t = Thread { b.loop() }
        thread = t
        t.start()
        updateButtons()
    }

    fun stopBot() {
        brain?.running = false
        thread?.interrupt()
        thread = null
        updateButtons()
    }

    fun onBrainStopped() {
        main.post { b1?.text = "BASLAT"; b2?.text = "KALIB." }
    }

    // ---------- Ekran goruntusu ----------
    fun shot(): Bitmap? {
        val bekle = 400 - (System.currentTimeMillis() - lastShot)
        if (bekle > 0) Thread.sleep(bekle)
        lastShot = System.currentTimeMillis()

        val latch = CountDownLatch(1)
        var out: Bitmap? = null
        takeScreenshot(Display.DEFAULT_DISPLAY, exec, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                try {
                    val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                    if (hw != null) {
                        out = hw.copy(Bitmap.Config.ARGB_8888, false)
                        hw.recycle()
                    }
                } catch (e: Exception) {
                }
                try {
                    r.hardwareBuffer.close()
                } catch (e: Exception) {
                }
                latch.countDown()
            }

            override fun onFailure(errorCode: Int) {
                latch.countDown()
            }
        })
        latch.await(3, TimeUnit.SECONDS)
        return out
    }

    // ---------- Dokunma ----------
    fun gesture(strokes: List<Stroke>, ms: Long, bekle: Boolean) {
        val b = GestureDescription.Builder()
        for (st in strokes.take(10)) {
            val p = Path()
            p.moveTo(st.x1, st.y1)
            if (st.x1 != st.x2 || st.y1 != st.y2) p.lineTo(st.x2, st.y2)
            b.addStroke(GestureDescription.StrokeDescription(p, 0, ms))
        }
        val latch = CountDownLatch(1)
        val ok = dispatchGesture(b.build(), object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                latch.countDown()
            }

            override fun onCancelled(g: GestureDescription?) {
                latch.countDown()
            }
        }, null)
        if (ok && bekle) latch.await(ms + 1500, TimeUnit.MILLISECONDS)
    }

    fun back() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun openGame() {
        val i = packageManager.getLaunchIntentForPackage(Config.PKG) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }
}
