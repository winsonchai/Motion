package com.winsonchai.motion

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.winsonchai.motion.core.*
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val mint = Color.rgb(95, 225, 180)
    private val ink = Color.rgb(12, 23, 25)
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var detector: PoseLandmarker? = null // Accessed only on worker.
    private var provider: ProcessCameraProvider? = null
    private var pose = YogaPose.MOUNTAIN
    private var lens = CameraSelector.LENS_FACING_FRONT
    private var running = false
    private var foreground = false
    private var generation = 0 // Reject callbacks from a previous session or camera.
    private var lastFrameMs = 0L
    private var tracker = HoldTracker()
    private var cueCandidate = ""
    private var cueSinceMs = 0L
    private lateinit var preview: PreviewView
    private lateinit var overlay: PoseOverlay
    private lateinit var status: TextView
    private lateinit var timer: TextView
    private lateinit var best: TextView
    private lateinit var angles: TextView
    private lateinit var instructions: TextView
    private lateinit var start: Button
    private lateinit var switchCamera: Button
    private lateinit var settings: Button

    private val watchdog = object : Runnable {
        override fun run() {
            if (running && lastFrameMs > 0 && SystemClock.uptimeMillis() - lastFrameMs > 900) {
                tracker.reset()
                overlay.frame = null; overlay.invalidate()
                timer.text = "0.0 / 10 s"
                status.text = "Tracking paused — step into view"
                status.setTextColor(Color.WHITE)
                angles.text = "Waiting for a clear frame"
            }
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = ink; window.navigationBarColor = ink
        buildUi()
        handler.post(watchdog)
        status.text = "Ready — place the phone upright and step back"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(ink)
        backgroundTintList = android.content.res.ColorStateList.valueOf(mint)
        setOnClickListener { action() }
    }
    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(ink); setPadding(dp(18), 0, dp(18), 0)
            fitsSystemWindows = true
        }
        setContentView(root)
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(dp(18), insets.systemWindowInsetTop, dp(18), insets.systemWindowInsetBottom)
            insets
        }
        val title = text("motion", 30f, mint).apply { typeface = Typeface.DEFAULT_BOLD }
        root.addView(title)
        root.addView(text("YOGA PRACTICE  ·  ON DEVICE", 11f, Color.LTGRAY))

        val row = LinearLayout(this)
        val selector = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, YogaPose.entries.map { it.title })
            backgroundTintList = android.content.res.ColorStateList.valueOf(mint)
        }
        row.addView(selector, LinearLayout.LayoutParams(0, dp(48), 1f))
        switchCamera = button("Flip camera") {
            stopPractice()
            lens = if (lens == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
            status.text = "Camera changed — tap Start practice"
        }
        row.addView(switchCamera)
        root.addView(row)
        instructions = text(pose.setup, 13f, Color.LTGRAY).apply { setPadding(0, dp(4), 0, dp(8)) }
        root.addView(instructions)
        val cameraFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            background = GradientDrawable().apply { setColor(Color.BLACK); cornerRadius = dp(16).toFloat() }
            clipToOutline = true
        }
        preview = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        overlay = PoseOverlay(this)
        cameraFrame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        cameraFrame.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        val label = text("Whole body in frame  ·  One person", 11f).apply {
            setBackgroundColor(0x99000000.toInt()); setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        cameraFrame.addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        root.addView(cameraFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        timer = text("0.0 / 10 s", 32f, mint).apply { gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD }
        root.addView(timer)
        best = text("Best hold this session: 0.0 s", 12f, Color.LTGRAY).apply { gravity = Gravity.CENTER }
        root.addView(best)
        status = text("", 16f).apply { minHeight = dp(52); gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(4)) }
        root.addView(status)
        angles = text("Approximate 2D checks · keep the camera level", 11f, Color.LTGRAY).apply { gravity = Gravity.CENTER }
        root.addView(angles)
        start = button("Start practice") { if (running) stopPractice() else startPractice() }
        root.addView(start, LinearLayout.LayoutParams(-1, dp(50)))
        settings = button("Open camera permission settings") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }.apply { visibility = View.GONE }
        root.addView(settings)
        val info = text("Experimental posture estimates · no video saved", 11f, Color.LTGRAY).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(7), 0, dp(7))
            setOnClickListener {
                android.app.AlertDialog.Builder(this@MainActivity).setTitle("About this preview")
                    .setMessage("Motion processes camera frames on your phone. It does not record, upload or retain images.\n\nAmber skeleton: tracking or adjustment. Green skeleton: within broad experimental 2D ranges. This does not confirm safe or correct technique. A level, fixed camera and an unobstructed front view are required.\n\nThe timer needs 0.6 seconds of steady matching first and resets when tracking is lost or the pose changes. Stop or choose an easier position if uncomfortable. Coach validation is still pending.")
                    .setPositiveButton("Close", null).show()
            }
        }
        root.addView(info)
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val next = YogaPose.entries[position]
                if (next != pose) {
                    stopPractice(); pose = next; tracker = HoldTracker()
                    best.text = "Best hold this session: 0.0 s"; instructions.text = pose.setup
                    status.text = "${pose.title} selected — tap Start practice"
                }
            }
        }
    }

    private fun startPractice() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
            return
        }
        settings.visibility = View.GONE
        running = true; generation++; tracker.reset(); lastFrameMs = 0
        cueCandidate = ""; start.text = "Pause practice"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        status.text = "Preparing on-device pose model…"
        val session = generation
        worker.execute {
            try {
                if (detector == null) {
                    val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                        .setBaseOptions(BaseOptions.builder().setModelAssetPath("pose_landmarker_lite.task").build())
                        .setRunningMode(RunningMode.VIDEO).setNumPoses(2)
                        .setMinPoseDetectionConfidence(.6f).setMinPosePresenceConfidence(.6f)
                        .setMinTrackingConfidence(.6f).build()
                    detector = PoseLandmarker.createFromOptions(this, options)
                }
                runOnUiThread { if (running && foreground && session == generation) bindCamera(session) }
            } catch (_: Exception) {
                runOnUiThread { if (session == generation) fail("Pose model could not start. Tap Start to retry.") }
            }
        }
    }

    private fun bindCamera(session: Int) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (!running || !foreground || session != generation) return@addListener
            try {
                val cameraProvider = future.get(); provider = cameraProvider
                var selection = CameraSelector.Builder().requireLensFacing(lens).build()
                if (!cameraProvider.hasCamera(selection)) {
                    lens = if (lens == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                    selection = CameraSelector.Builder().requireLensFacing(lens).build()
                    check(cameraProvider.hasCamera(selection))
                }
                overlay.mirrored = lens == CameraSelector.LENS_FACING_FRONT
                val resolution = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
                val rotation = preview.display?.rotation ?: android.view.Surface.ROTATION_0
                val cameraPreview = Preview.Builder().setResolutionSelector(resolution).setTargetRotation(rotation).build()
                cameraPreview.setSurfaceProvider(preview.surfaceProvider)
                val analysis = ImageAnalysis.Builder().setResolutionSelector(resolution)
                    .setTargetRotation(rotation).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build()
                analysis.setAnalyzer(worker) { image ->
                    val capturedAt = SystemClock.uptimeMillis()
                    try {
                        val original = image.toBitmap()
                        val rotationMatrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
                        val bitmap = Bitmap.createBitmap(original, 0, 0, original.width, original.height, rotationMatrix, false)
                        try {
                            val mpImage = BitmapImageBuilder(bitmap).build()
                            try {
                                val result = detector?.detectForVideo(mpImage, capturedAt)
                                val count = result?.landmarks()?.size ?: 0
                                val frame = if (count == 1) PoseFrame(result!!.landmarks()[0].map {
                                    Joint(it.x(), it.y(), minOf(it.visibility().orElse(0f), it.presence().orElse(0f)))
                                }, bitmap.width, bitmap.height) else null
                                runOnUiThread {
                                    if (running && foreground && session == generation) {
                                        if (SystemClock.uptimeMillis() - capturedAt > 750) {
                                            showAssessment(null, Assessment(Tracking.UNKNOWN, "Analysis is slow — waiting for a fresh frame"), capturedAt)
                                        } else {
                                            val assessment = if (count > 1) Assessment(Tracking.UNKNOWN, "One person at a time — clear the background")
                                                else PoseCoach.evaluate(frame, pose)
                                            showAssessment(frame, assessment, capturedAt)
                                        }
                                    }
                                }
                            } finally { mpImage.close() }
                        } finally {
                            if (bitmap !== original) bitmap.recycle()
                            original.recycle()
                        }
                    } catch (_: Exception) {
                        runOnUiThread { if (running && session == generation) fail("Camera analysis stopped. Tap Start to retry.") }
                    } finally { image.close() }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selection, cameraPreview, analysis)
                lastFrameMs = SystemClock.uptimeMillis()
                status.text = "Step back until your head, hands and feet are visible"
            } catch (_: Exception) { fail("Camera unavailable. Close other camera apps, then try again.") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showAssessment(frame: PoseFrame?, assessment: Assessment, now: Long) {
        lastFrameMs = now
        val matching = assessment.tracking == Tracking.MATCH
        val hold = tracker.update(now, matching)
        timer.text = String.format(Locale.US, "%.1f / 10 s", hold.currentMs / 1000.0)
        best.text = String.format(Locale.US, "Best hold this session: %.1f s", hold.bestMs / 1000.0)
        overlay.frame = frame; overlay.matched = matching; overlay.invalidate()
        angles.text = assessment.angles.ifEmpty { "Waiting for clear tracking" }
        if (assessment.tracking == Tracking.UNKNOWN) {
            status.text = assessment.cue; cueCandidate = ""
        } else {
            val cue = if (hold.goalReached) "10-second goal reached — release whenever comfortable" else assessment.cue
            if (cue != cueCandidate) { cueCandidate = cue; cueSinceMs = now }
            // Debounce advice only; timer and tracking gates always respond immediately.
            if (now - cueSinceMs >= 400) status.text = cue
        }
        status.setTextColor(if (matching) mint else Color.WHITE)
    }

    private fun fail(message: String) { stopPractice(); status.text = message }
    private fun stopPractice() {
        running = false; generation++; provider?.unbindAll(); tracker.reset()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::overlay.isInitialized) {
            overlay.frame = null; overlay.invalidate(); timer.text = "0.0 / 10 s"
            start.text = "Start practice"; status.text = "Paused — tap Start practice to continue"
            status.setTextColor(Color.WHITE)
            angles.text = "Camera stopped · no images saved"
        }
    }
    override fun onStart() { super.onStart(); foreground = true }
    override fun onStop() { foreground = false; stopPractice(); super.onStop() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        worker.execute { detector?.close(); detector = null }
        worker.shutdown(); super.onDestroy()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && foreground) startPractice()
            else {
                status.text = "Camera permission is needed for posture tracking"
                settings.visibility = View.VISIBLE
            }
        }
    }
}
