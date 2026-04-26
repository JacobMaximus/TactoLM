package com.tactolm
import android.view.KeyEvent
import android.util.Log
object TestPowerReceiver {
    fun log(event: KeyEvent) {
        Log.d("TactoLM_POWER", "Key event: ${event.keyCode}")
    }
}
