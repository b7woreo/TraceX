package com.example.application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.application.databinding.ActivityMainBinding
import com.example.library.MainViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainViewModel>()
    private val viewBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        viewModel.count
            .onEach { viewBinding.tvCount.text = it.toString() }
            .launchIn(lifecycleScope)

        viewBinding.btnInc.setOnClickListener {
            viewModel.inc()
        }

        viewBinding.btnDec.setOnClickListener {
            viewModel.dec()
        }
    }

}