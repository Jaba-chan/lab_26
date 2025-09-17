package ru.evgenykuzakov.lab_26

import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import ru.evgenykuzakov.lab_26.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

	private lateinit var binding: ActivityMainBinding
	private lateinit var cameraExecutor: ExecutorService

	private var imageCapture: ImageCapture? = null

	private val requestPermission = registerForActivityResult(
		ActivityResultContracts.RequestPermission()
	) { granted ->
		if (granted) startCamera() else {
			Toast.makeText(this, "Требуется доступ к камере", Toast.LENGTH_LONG).show()
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)

		cameraExecutor = Executors.newSingleThreadExecutor()

		binding.btnShutter.setOnClickListener { takePhoto() }

		if (hasCameraPermission()) startCamera()
		else requestPermission.launch(android.Manifest.permission.CAMERA)
	}

	private fun hasCameraPermission(): Boolean =
		ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
			PackageManager.PERMISSION_GRANTED

	private fun startCamera() {
		val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

		cameraProviderFuture.addListener({
			val cameraProvider = cameraProviderFuture.get()
			val preview = Preview.Builder().build().also {
				it.surfaceProvider = binding.previewView.surfaceProvider
			}

			imageCapture = ImageCapture.Builder()
				.setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
				.build()

			val analyzer = ImageAnalysis.Builder()
				.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
				.build().also { analysis ->
					analysis.setAnalyzer(cameraExecutor,
						CenterColorAnalyzer { r, g, b ->
							runOnUiThread {
								val hex = String.format("#%02X%02X%02X", r, g, b)
								binding.colorHex.text = hex
								binding.colorSwatch.setBackgroundColor(
									(0xFF shl 24 or (r shl 16) or (g shl 8) or b)
								)
							}
						})
				}

			val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

			try {
				cameraProvider.unbindAll()
				cameraProvider.bindToLifecycle(
					this, cameraSelector, preview, imageCapture, analyzer
				)
				lockPreviewViewAspect(binding.previewView)
			} catch (e: Exception) {
				e.printStackTrace()
				Toast.makeText(this, "Ошибка запуска камеры: ${e.message}", Toast.LENGTH_LONG).show()
			}
		}, ContextCompat.getMainExecutor(this))
	}

	private fun lockPreviewViewAspect(pv: PreviewView) {
		pv.scaleType = PreviewView.ScaleType.FILL_CENTER
	}

	private fun takePhoto() {
		val imageCapture = imageCapture ?: return

		val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
			.format(System.currentTimeMillis())

		val contentValues = ContentValues().apply {
			put(MediaStore.Images.Media.DISPLAY_NAME, "ColorShot_$name.jpg")
			put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
				put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-ColorPicker")
			}
		}

		val outputOptions = ImageCapture.OutputFileOptions.Builder(
			contentResolver,
			MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
			contentValues
		).build()

		imageCapture.takePicture(
			outputOptions,
			ContextCompat.getMainExecutor(this),
			object : ImageCapture.OnImageSavedCallback {
				override fun onImageSaved(output: ImageCapture.OutputFileResults) {
					val uri: Uri? = output.savedUri
					Toast.makeText(
						this@MainActivity,
						"Снимок сохранён ${uri ?: ""}",
						Toast.LENGTH_SHORT
					).show()
				}

				override fun onError(exc: ImageCaptureException) {
					Toast.makeText(
						this@MainActivity,
						"Не удалось сохранить снимок: ${exc.message}",
						Toast.LENGTH_LONG
					).show()
				}
			}
		)
	}

	override fun onDestroy() {
		super.onDestroy()
		if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
	}
}