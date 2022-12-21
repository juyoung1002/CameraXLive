package com.rell.cameraxlive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.rell.cameraxlive.cvs.CsvHelper
import com.rell.cameraxlive.record.RecordStatus

class CameraXLivePreviewActivity : AppCompatActivity() {
    private var previewView: PreviewView? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var fileName: EditText? = null
    private var labelName: EditText? = null
    private lateinit var imageProcessor: VisionProcessorBase<Text>
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var cameraSelector: CameraSelector? = null
    private var textView: TextView? = null
    private var recordStatus: RecordStatus = RecordStatus.STOP
    private val recordList = arrayListOf<Array<String>>()
    private lateinit var csvHelper: CsvHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        cameraSelector = createCameraSelector()

        setContentView(R.layout.activity_camera_xlive_preview)

        csvHelper = CsvHelper(filesDir.toString())

        previewView = findViewById(R.id.preview_view)
        graphicOverlay = findViewById(R.id.graphic_overlay)
        textView = findViewById(R.id.text)
        fileName = findViewById(R.id.fileName)
        labelName = findViewById(R.id.labelName)

        findViewById<View>(R.id.recordStart).setOnClickListener {
            // 녹화를 시작한다
            recordStatus = RecordStatus.START
        }
        findViewById<View>(R.id.recordStop).setOnClickListener {
            // 녹화를 일지 중지
            recordStatus = RecordStatus.STOP
        }
        findViewById<View>(R.id.recordSave).setOnClickListener {
            // 녹화된 정보를 저장한다
            recordStatus = RecordStatus.STOP

            csvHelper.writeAllData(fileName?.text.toString(), recordList)
            // arrayList 초기화
            recordList.clear()
        }

        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[CameraXViewModel::class.java]
            .processCameraProvider
            .observe(
                this
            ) { provider: ProcessCameraProvider? ->
                cameraProvider = provider
                bindAllCameraUseCases()
            }

        if (!allRuntimePermissionsGranted()) {
            getRuntimePermissions()
        }
    }

    /**
     * 카메라 셀렉터를 생성합니다.
     * 전면 렌즈 또는 후면 렌즈를 설정합니다.
     * 전면 렌즈를 사용할 일이 없기 때문에 후면 렌즈로 설정합니다.
     */
    private fun createCameraSelector() =
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

    override fun onResume() {
        super.onResume()
        bindAllCameraUseCases()
    }

    override fun onPause() {
        super.onPause()
        stopImageProcessor()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopImageProcessor()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUESTS) {
            if (allRuntimePermissionsGranted()) {
                bindAllCameraUseCases()
            }
        }
    }

    private fun stopImageProcessor() {
        if (::imageProcessor.isInitialized) {
            imageProcessor.stop()
        }
    }

    private fun getRuntimePermissions() {
        val permissionsToRequest = ArrayList<String>()
        for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
            if (!isPermissionGranted(this, permission)) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUESTS
            )
        }
    }

    private fun bindAllCameraUseCases() {
        unbindAnalysisUseCase()

        bindPreviewUseCase()
        bindAnalysisUseCase()
    }

    private fun unbindAnalysisUseCase() {
        if (cameraProvider != null) {
            // As required by CameraX API, unbinds all use cases before trying to re-bind any of them.
            cameraProvider!!.unbindAll()
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindAnalysisUseCase() {
        if (cameraProvider == null) {
            return
        }
        unbindAnalysisUseCase()
        stopImageProcessor()
        initImageProcessor()

        needUpdateGraphicOverlayImageSourceInfo = true
        initImageAnalysis()
        bindCameraProvider()
    }

    private fun bindCameraProvider() {
        cameraProvider!!.bindToLifecycle( /* lifecycleOwner= */this, cameraSelector!!, analysisUseCase)
    }

    private fun initImageAnalysis() {
        val builder = ImageAnalysis.Builder()
        analysisUseCase = builder.build()
        analysisUseCase!!.setAnalyzer( // imageProcessor.processImageProxy will use another thread to run the detection underneath,
            // thus we can just runs the analyzer itself on main thread.
            ContextCompat.getMainExecutor(this)
        ) { imageProxy: ImageProxy ->
            initGraphicOverlay(imageProxy)
            processImage(imageProxy)
        }
    }

    private fun initGraphicOverlay(imageProxy: ImageProxy) {
        if (needUpdateGraphicOverlayImageSourceInfo) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees == 0 || rotationDegrees == 180) {
                graphicOverlay!!.setImageSourceInfo(imageProxy.width, imageProxy.height, false)
            } else {
                graphicOverlay!!.setImageSourceInfo(imageProxy.height, imageProxy.width, false)
            }
            needUpdateGraphicOverlayImageSourceInfo = false
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImage(imageProxy: ImageProxy) {
        try {
            imageProcessor.processImageProxy(imageProxy) { result ->
                updateGraphicOverlay(imageProxy)
                updateText(result)
            }
        } catch (e: MlKitException) {
            Log.e(
                TAG,
                "Failed to process image. Error: " + e.localizedMessage
            )
            Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun Rect.area(): Int {
        return (bottom - top) * (right - left)
    }

    private fun updateText(result: Text) {
        val sortedBlocks = result.textBlocks.sortedByDescending { textBlock ->
            textBlock.boundingBox?.area()
        }
        sortedBlocks.forEachIndexed { index, textBlock ->
            val rect = textBlock.boundingBox
            if (rect != null) {
                val area = (rect.bottom - rect.top) * (rect.right - rect.left)
                Log.d("CameraXLive", "result > text[$index] : ${textBlock.text}")
                Log.d("CameraXLive", "result > area[$index] : $area")
            }
        }

        if (sortedBlocks.isNotEmpty()) {
            recordSentence(sortedBlocks[0].text)
        }

//        if (result.text.isNotEmpty()) {
//            val lineText = System.getProperty("line.separator")?.let { result.text.replace(it, " ") }
//            recordSentence(lineText.toString())
//        }
    }

    private fun recordSentence(sentence: String) {
        val label = labelName?.text.toString()
        if (recordStatus == RecordStatus.START) {
            recordList.add(arrayOf(label, sentence))
        }

        val text = "파일명 : ${fileName?.text.toString()}\n" +
                "index : ${recordList.size}\n" +
                "sentence : $sentence\n" +
                "label : $label\n" +
                "record status : $recordStatus\n"

        textView?.text = text
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun updateGraphicOverlay(imageProxy: ImageProxy) {
        val bitmap = BitmapUtils.getBitmap(imageProxy)
        graphicOverlay?.apply {
            clear()
            add(CameraImageGraphic(graphicOverlay, bitmap))
        }
        graphicOverlay?.postInvalidate()
    }

    private fun initImageProcessor() {
        try {
            imageProcessor = TextRecognitionProcessor(this, KoreanTextRecognizerOptions.Builder().build())
        } catch (e: Exception) {
            Log.e(TAG, "Can not create image processor: ", e)
            Toast.makeText(
                applicationContext,
                "Can not create image processor: " + e.localizedMessage,
                Toast.LENGTH_LONG
            ).show()
            return
        }
    }

    private fun bindPreviewUseCase() {
        if (!PreferenceUtils.isCameraLiveViewportEnabled(this)) {
            return
        }
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }
        val builder = Preview.Builder()
        previewUseCase = builder.build()
        previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)
        cameraProvider!!.bindToLifecycle( /* lifecycleOwner= */this, cameraSelector!!, previewUseCase)
    }

    private fun allRuntimePermissionsGranted(): Boolean {
        for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
            if (!isPermissionGranted(this, permission)) {
                return false
            }
        }
        return true
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Permission granted: $permission")
            return true
        }
        Log.i(TAG, "Permission NOT granted: $permission")
        return false
    }


    companion object {
        private const val TAG = "CameraXLivePreview"
        private const val STATE_SELECTED_MODEL = "selected_model"

        private const val PERMISSION_REQUESTS = 1

        private val REQUIRED_RUNTIME_PERMISSIONS =
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
    }
}