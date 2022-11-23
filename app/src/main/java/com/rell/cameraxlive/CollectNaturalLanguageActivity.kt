package com.rell.cameraxlive

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.net.URL


class CollectNaturalLanguageActivity : AppCompatActivity() {

    private val uiHandler = Handler()

    private val imageView: ImageView by lazy {
        findViewById(R.id.imageView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_collect_natural_language)

        loadImage("https://img.hwahae.co.kr/commerce/goods/20220704_120141_%EC%8B%AC%ED%94%8C%ED%86%A0%EB%84%88300%2B%EC%8B%AC%ED%94%8C%EB%A1%9C%EC%85%98260ml.jpg")
    }

    private fun loadImage(imageUrl: String) {
        Thread {
            val url =
                URL(imageUrl)
            val image = BitmapFactory.decodeStream(url.openConnection().getInputStream())
            uiHandler.post {
                imageView.setImageBitmap(image)
            }
        }.start()
    }
}