package com.rell.cameraxlive.collectNaturalLaunguage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.opencsv.CSVReader
import com.rell.cameraxlive.R
import com.rell.cameraxlive.TextRecognitionProcessor
import com.rell.cameraxlive.VisionProcessorBase
import com.rell.cameraxlive.cvs.CsvHelper
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.InputStreamReader
import java.net.URL


class CollectNaturalLanguageActivity : AppCompatActivity() {

    private var disposable: Disposable? = null
    private val uiHandler = Handler()
    private val imageProcessor: VisionProcessorBase<Text> by lazy {
        TextRecognitionProcessor(this, KoreanTextRecognizerOptions.Builder().build())
    }
    private val csvHelper: CsvHelper by lazy {
        CsvHelper(filesDir.toString())
    }

    private val imageView: ImageView by lazy {
        findViewById(R.id.imageView)
    }
    private val btnStart: Button by lazy {
        findViewById(R.id.btnStart)
    }
    private val editText: EditText by lazy {
        findViewById(R.id.editText)
    }
    private val textView: TextView by lazy {
        findViewById(R.id.textView)
    }

    private val scaleType: ArrayList<Pair<Float, Float>> = arrayListOf()

    private lateinit var contents: List<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collect_natural_language)

        val inputStream = assets.open("product.csv")
        val reader = CSVReader(InputStreamReader(inputStream))
        contents = reader.readAll()

        createScaleType()

        setupStartButton()
    }

    private fun setupStartButton() {
        btnStart.setOnClickListener {
            updateText("자연어 수집을 시작합니다! ")

            val startIdx = editText.text.toString().toInt()
            collectNaturalLanguage(startIdx)
        }
    }

    private fun collectNaturalLanguage(index: Int) {
        disposable?.dispose()

        val (name, url) = contents[index]
        collectNatural(index, name, url)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ list ->
                updateText("start index : ${index}, $name")
                csvHelper.writeAllData("$index", list.map { arrayOf(name, it) })
                collectNaturalLanguage(index + 1)
            }, {
                Log.e("collectNatural", "error : ${it.message}")
                collectNaturalLanguage(index + 1)
            })
            .also { this.disposable = it }
    }

    private fun collectNatural(index: Int, name: String, url: String): Single<MutableList<String>> {
        return Observable.just("https://dev.img.hwahae.co.kr/$url")
            .doOnNext { Log.d("collectNatural", "start $index, thread : ${Thread.currentThread().name} name : $name") }
            .map(::loadBitmap)
            .flatMap(::scaleBitmap)
            .flatMap(::processBitmap)
            .take(100)
            .toList()
    }

    private fun loadBitmap(imageUrl: String): Bitmap {
        Log.d("CollectNatural", "loadBitmap > imageUrl : $imageUrl")
        val url = URL(imageUrl)
        return BitmapFactory.decodeStream(url.openConnection().getInputStream())
    }

    private fun createScaleType() {
        for (i in 100..150 step 1) {
            scaleType.add(i / 100f to 1.0f)
            scaleType.add(1.0f to i / 100f)
        }
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

    private fun scaleBitmap(bitmap: Bitmap): Observable<Bitmap> {
        return Observable.create { emitter ->
            scaleType.forEach { (width, height) ->
                val matrix = Matrix()
                matrix.postScale(width, height)
                val newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
                emitter.onNext(newBitmap)
            }
            emitter.onComplete()
        }
    }

    private fun processBitmap(bitmap: Bitmap): Observable<String> {
        return Observable.create { emitter ->
            imageProcessor.processBitmap(bitmap) { result ->
                if (result.text.isNotEmpty()) {
                    val lineText = System.getProperty("line.separator")?.let { result.text.replace(it, " ") }
                    val sentence = lineText.toString()
                    emitter.onNext(sentence)
                }
                emitter.onComplete()
            }
        }
    }

    private fun updateText(sentence: String) {
        textView.text = sentence
        Log.d("TAG", "updateText: $sentence")
    }
}