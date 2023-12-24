package com.example.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val mutableCount = MutableStateFlow(0)

    val count: StateFlow<Int> = mutableCount

    fun inc() {
        runCatching { incInternal() }
    }

    fun dec() {
        viewModelScope.launch {
            decInternal()
        }
    }

    private fun incInternal() {
        val currentCount = mutableCount.getAndUpdate { it + 1 }
        if (currentCount % 2 == 0) {
            Thread.sleep(500)
            throw RuntimeException()
        }
    }

    private fun decInternal() {
        mutableCount.getAndUpdate { it - 1 }
    }

}