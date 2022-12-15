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
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class CollectNaturalLanguageActivity : AppCompatActivity() {

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

    val executor = Executors.newFixedThreadPool(12)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collect_natural_language)

        createScaleType()

        setupStartButton()
    }

    private fun setupStartButton() {
        btnStart.setOnClickListener {
            updateText("자연어 수집을 시작합니다! ")

            val inputStream = assets.open("product.csv")
            val reader = CSVReader(InputStreamReader(inputStream))
            contents = reader.readAll()

            val startIdx = editText.text.toString().toInt()
            collectNaturalLanguage2(startIdx)
//            collectNaturalLanguage(startIdx)
        }
    }

    private fun collectNaturalLanguage(startIdx: Int) {
        Observable.interval(10, TimeUnit.SECONDS)
            .take(contents.size.toLong())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { index ->
                Log.d("collectNatural", "start index : ${index + startIdx}")
                val (name, url) = contents[index.toInt() + startIdx]

                updateText("start index : ${index + startIdx}, $name")
                collectNatural(index.toInt() + startIdx, name, url)
            }
    }

    private fun collectNaturalLanguage2(startIndex: Int) {
        Observable
            .fromIterable(contents.subList(startIndex, contents.size))
            .zipWith(Observable.interval(10, TimeUnit.MILLISECONDS), BiFunction { t1, t2 -> t2 + startIndex to t1 })
            .concatMap { pair ->
                val (index, array) = pair
                val (name, url) = array
                collectNatural2(index.toInt(), name, url)
                    .toObservable()
                    .map { Triple(index, name, it) }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ triple ->
                val (index, name, list) = triple
                if (index.toInt() == -1) {
                    return@subscribe
                }
                updateText("start index : ${index}, $name")
                csvHelper.writeAllData("$index", list.map { arrayOf(name, it) })
            }, {
                Log.e("collectNatural", "error : ${it.message}")
            })
    }

    private fun collectNatural(index: Int, name: String, url: String) {

        val arrayList = arrayListOf<Array<String>>()
        Observable.just("https://dev.img.hwahae.co.kr/$url")
            .doOnNext {
                Log.d("collectNatural", "start $index, thread : ${Thread.currentThread().name} name : $name")
            }
            .map(::loadBitmap)
            .flatMap(::scaleBitmap)
            .flatMap(::processBitmap)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ sentence ->
                arrayList.add(arrayOf(name, sentence))
                if (arrayList.size == 100) {
                    Log.d("collectNatural", "writeAllData($index, arrayList)")
                    csvHelper.writeAllData("$index", arrayList)
                }
            }, { t ->
                Log.d("collectNatural", "${t.message}")
            })
    }

    private fun collectNatural2(index: Int, name: String, url: String): Single<MutableList<String>> {
        return Observable.just("https://dev.img.hwahae.co.kr/$url")
            .doOnNext { Log.d("collectNatural", "start $index, thread : ${Thread.currentThread().name} name : $name") }
            .map(::loadBitmap)
            .onErrorResumeNext { Observable.just(null) }
            .flatMap(::scaleBitmap)
            .flatMap(::processBitmap)
            .take(100)
            .toList()
            .subscribeOn(Schedulers.from(executor))
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
                    emitter.onComplete()
                }
            }
        }
    }

    private fun updateText(sentence: String) {
        textView.text = sentence
        Log.d("TAG", "updateText: $sentence")
    }
}