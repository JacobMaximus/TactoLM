package com.tactolm
import android.util.Log
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TactoActivity : BaseActivity() {

    // ── API Key ───────────────────────────────────────────────────────────────
    private companion object {
        val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
        const val REQUEST_CAMERA_PERMISSION = 1001
    }

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var btnScan: View
    private lateinit var tvScanLabel: TextView
    private lateinit var tvSceneSummary: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var cardContainer: LinearLayout

    // ── Camera ────────────────────────────────────────────────────────────────
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    // ── Haptics ───────────────────────────────────────────────────────────────
    private lateinit var vibrator: Vibrator
    private lateinit var hapticPlayer: HapticSequencePlayer

    // ── Gesture ───────────────────────────────────────────────────────────────
    private lateinit var gestureDetector: GestureDetector

    // ── State ─────────────────────────────────────────────────────────────────
    @Volatile private var isScanning = false

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Force edge-to-edge dark
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars    = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_tacto)

        bindViews()
        setupNavBar(NAV_VISION)
        setupVibrator()
        hapticPlayer = HapticSequencePlayer(vibrator, lifecycleScope)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupGestureDetector()
        setupScanButton()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        vibrator.cancel()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required for scanning.", Toast.LENGTH_LONG)
                .show()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View binding
    // ─────────────────────────────────────────────────────────────────────────

    private fun bindViews() {
        btnScan          = findViewById(R.id.tacto_btn_scan)
        tvScanLabel      = findViewById(R.id.tacto_tv_scan_label)
        tvSceneSummary   = findViewById(R.id.tacto_tv_scene_summary)
        scrollView       = findViewById(R.id.tacto_scroll_view)
        cardContainer    = findViewById(R.id.tacto_card_container)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Vibrator helper
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Long-press gesture for replay
    // ─────────────────────────────────────────────────────────────────────────

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(e: MotionEvent) {
                    if (!isScanning) {
                        Log.d("TactoLM_UI", "Long press detected. Replaying last sequence.")
                        if (cardContainer.childCount == 0) {
                            Log.d("TactoLM_UI", "Long press detected but no previous sequence exists.")
                        }
                        hapticPlayer.replay()
                        // Re-animate all existing cards
                        reanimateCards()
                    }
                }
            }
        )

        // Attach to the root view so any touch on screen triggers it
        val rootView = window.decorView.rootView
        rootView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCAN button
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupScanButton() {
        btnScan.setOnClickListener {
            Log.d("TactoLM_UI", "=== SCAN BUTTON TAPPED ===")
            Log.d("TactoLM_UI", "Timestamp: " + System.currentTimeMillis())
            if (!isScanning) {
                startScanFlow()
            }
        }
    }

    private fun startScanFlow() {
        isScanning = true
        setScanButtonEnabled(false)

        Log.d("TactoLM_UI", "Confirmation haptic firing")
        // Step 2: confirmation pulses
        hapticPlayer.playConfirmation()

        // Step 3 & 4: clear previous cards, clear summary
        clearCards()
        tvSceneSummary.text = ""
        tvSceneSummary.visibility = View.INVISIBLE

        // Step 5–10: capture and analyse
        captureAndAnalyse()
    }

    private fun setScanButtonEnabled(enabled: Boolean) {
        btnScan.isEnabled = enabled
        btnScan.alpha = if (enabled) 1.0f else 0.55f
        if (!enabled) {
            Log.d("TactoLM_UI", "Scan button disabled")
        } else {
            Log.d("TactoLM_UI", "Scan button re-enabled")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CameraX
    // ─────────────────────────────────────────────────────────────────────────

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)
            } catch (e: Exception) {
                android.util.Log.e("TactoActivity", "Camera bind failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndAnalyse() {
        val capture = imageCapture ?: run {
            showError("Camera not ready. Please restart.")
            return
        }

        Log.d("TactoLM_UI", "CameraX capture started")
        capture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {

            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                Log.d("TactoLM_UI", "CameraX capture SUCCESS")
                val bitmap = imageProxyToBitmap(imageProxy)
                imageProxy.close()
                Log.d("TactoLM_UI", "Captured bitmap dimensions: " + bitmap.width + "x" + bitmap.height)
                
                Log.d("TactoLM_UI", "Starting image scale down")
                Log.d("TactoLM_UI", "Image scale complete: " + bitmap.width + "x" + bitmap.height)

                // Start processing vibration ASAP (on background thread is fine)
                Log.d("TactoLM_UI", "Processing haptic loop started")
                hapticPlayer.startProcessing()

                lifecycleScope.launch {
                    Log.d("TactoLM_UI", "Handing off to GeminiVisionGateway")
                    val result = GeminiVisionGateway.analyzeImage(bitmap, GEMINI_API_KEY)
                    
                    Log.d("TactoLM_UI", "GeminiVisionGateway returned result")
                    Log.d("TactoLM_UI", "Result is null: " + (result == null))
                    Log.d("TactoLM_UI", "Processing haptic loop stopped")
                    hapticPlayer.stopProcessing()

                    when (result) {
                        is AnalysisResult.Failure -> {
                            Log.e("TactoLM_UI", "Result was null. Showing error message to user.")
                            hapticPlayer.playError()
                            showError("Something went wrong. Please try again.")
                            isScanning = false
                            setScanButtonEnabled(true)
                            return@launch
                        }
                        is AnalysisResult.Success -> {
                            Log.d("TactoLM_UI", "Valid result received. Starting haptic sequence.")
                            val sceneResult = result.scene

                            // Sort by priority, deduplicate by category, skip ERROR items
                            val sortedItems = sceneResult.items
                                .filter { it.category != "ERROR" }
                                .sortedBy { it.priority }
                                .distinctBy { it.category }

                            Log.d("TactoLM_UI", "Total items to play: " + sceneResult.items.size)

                            // Play sequence
                            hapticPlayer.play(sortedItems) { item ->
                                // This fires on the main thread for each item
                                addCardForItem(item)
                                // Scroll to bottom so latest card is visible
                                scrollView.post {
                                    scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                                }
                            }

                            // Show scene summary after all items done
                            if (sceneResult.summary.isNotBlank()) {
                                withContext(Dispatchers.Main) {
                                    Log.d("TactoLM_UI", "Scene summary displayed: " + sceneResult.summary)
                                    tvSceneSummary.text = sceneResult.summary
                                    tvSceneSummary.visibility = View.VISIBLE
                                    val anim = AlphaAnimation(0f, 1f).apply { duration = 600 }
                                    tvSceneSummary.startAnimation(anim)
                                }
                            }

                            // Cooldown then re-enable
                            delay(2000L)
                            withContext(Dispatchers.Main) {
                                isScanning = false
                                setScanButtonEnabled(true)
                            }
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("TactoLM_UI", "CameraX capture FAILED")
                hapticPlayer.stopProcessing()
                runOnUiThread {
                    showError("Camera capture failed. Please retry.")
                    isScanning = false
                    setScanButtonEnabled(true)
                }
            }
        })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Card management
    // ─────────────────────────────────────────────────────────────────────────

    private fun clearCards() {
        cardContainer.removeAllViews()
    }

    private fun addCardForItem(item: SceneItem) {
        Log.d("TactoLM_UI", "Showing card for item: " + item.label + " category: " + item.category)
        val card = layoutInflater.inflate(R.layout.item_tacto_card, cardContainer, false)

        val tvLabel      = card.findViewById<TextView>(R.id.tacto_card_tv_label)
        val tvCategory   = card.findViewById<TextView>(R.id.tacto_card_tv_category)
        val tvConfidence = card.findViewById<TextView>(R.id.tacto_card_tv_confidence)

        tvLabel.text = item.label
        tvCategory.text = item.category
        tvCategory.setTextColor(TactonLibrary.getCategoryColor(item.category))
        tvConfidence.text = item.confidence

        card.alpha = 0f
        cardContainer.addView(card)

        // Fade in
        val fadeIn = AlphaAnimation(0f, 1f).apply {
            duration = 400
            fillAfter = true
        }
        Log.d("TactoLM_UI", "Card fade-in animation started for: " + item.label)
        card.startAnimation(fadeIn)
        card.animate().alpha(1f).setDuration(400).start()
    }

    private fun reanimateCards() {
        // Reset all cards to invisible then re-run fade-in one by one
        val count = cardContainer.childCount
        if (count == 0) return

        lifecycleScope.launch {
            for (i in 0 until count) {
                val card = cardContainer.getChildAt(i)
                withContext(Dispatchers.Main) {
                    card.alpha = 0f
                }
                delay(1000L)
                withContext(Dispatchers.Main) {
                    card.animate().alpha(1f).setDuration(400).start()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error display
    // ─────────────────────────────────────────────────────────────────────────

    private fun showError(message: String) {
        runOnUiThread {
            tvSceneSummary.text = message
            tvSceneSummary.setTextColor(android.graphics.Color.parseColor("#FF0000"))
            tvSceneSummary.visibility = View.VISIBLE
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image conversion
    // ─────────────────────────────────────────────────────────────────────────

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
