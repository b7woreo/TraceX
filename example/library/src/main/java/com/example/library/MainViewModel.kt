package com.example.library

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate

class MainViewModel : ViewModel() {

    private val mutableCount = MutableStateFlow(0)

    val count: StateFlow<Int> = mutableCount

    fun inc() {
        runCatching { incInternal() }
    }

    private fun incInternal() {
        val currentCount = mutableCount.getAndUpdate { it + 1 }
        Thread.sleep(500)
        if (currentCount % 2 == 0) {
            throw RuntimeException()
        } else {
            return
        }
    }

}