package com.pcsf.amplificador

import android.app.Application

class AmpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EqRepository.init(this)
    }
}
