package com.wanghoufan.zaijiuyidian

import android.app.Application
import com.wanghoufan.zaijiuyidian.audio.VoiceCues

class ZaiJiuApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        VoiceCues.warmUp()
    }

    companion object {
        lateinit var instance: ZaiJiuApp
            private set
    }
}
