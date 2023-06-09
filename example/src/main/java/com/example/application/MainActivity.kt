package com.example.application

import android.app.Activity
import android.os.Bundle
import android.os.Trace
import android.view.View

class MainActivity : Activity() {

    private var clickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.btn).setOnClickListener {
            runCatching { onClick() }
        }
    }

    private fun onClick() {
        clickCount += 1
        Thread.sleep(500)
        if (clickCount % 2 == 0) {
            throw RuntimeException()
        } else {
            return
        }
    }

}