package com.rell.cameraxlive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.net.URL


class CollectNaturalLanguageActivity : AppCompatActivity() {

    private val uiHandler = Handler()
    private val imageProcessor: VisionProcessorBase<Text> by lazy {
        TextRecognitionProcessor(this, KoreanTextRecognizerOptions.Builder().build())
    }

    private val imageView: ImageView by lazy {
        findViewById(R.id.imageView)
    }
    private val textView: TextView by lazy {
        findViewById(R.id.textView)
    }

    private val scaleType: ArrayList<Pair<Float, Float>> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collect_natural_language)

        createScaleType()
        loadImage("https://img.hwahae.co.kr/commerce/goods/20220704_120141_%EC%8B%AC%ED%94%8C%ED%86%A0%EB%84%88300%2B%EC%8B%AC%ED%94%8C%EB%A1%9C%EC%85%98260ml.jpg")
    }

    private fun createScaleType() {
        for (i in 100..150 step 1) {
            scaleType.add(i / 100f to 1.0f)
            scaleType.add(1.0f to i / 100f)
        }
    }

    private fun loadImage(imageUrl: String) {
        Thread {
            val url =
                URL(imageUrl)
            val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
            uiHandler.post {
                imageView.setImageBitmap(bitmap)
                scaleBitmap(bitmap,
                    onNext = { scaledBitmap ->
                        collectNaturalLanguage(scaledBitmap)
                    },
                    onDone = {
                        // todo save svg
                        // todo next
                    })
            }
        }.start()
    }

    private fun scaleBitmap(bitmap: Bitmap, onNext: (Bitmap) -> Unit, onDone: () -> Unit) {
        scaleType.forEach { (width, height) ->
            val matrix = Matrix()
            matrix.postScale(width, height)
            val newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
            onNext(newBitmap)
        }
        onDone.invoke()
    }

    private fun collectNaturalLanguage(bitmap: Bitmap) {
        imageProcessor.processBitmap(bitmap) { result ->
            updateText(result)
        }
    }

    private fun updateText(result: Text) {
        if (result.text.isNotEmpty()) {
            val lineText = System.getProperty("line.separator")?.let { result.text.replace(it, " ") }

            val sentence = lineText.toString()
            textView.text = sentence
            Log.d("TAG", "sentence: $sentence")
        }
    }
}