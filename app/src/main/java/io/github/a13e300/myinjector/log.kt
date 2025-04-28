package io.github.a13e300.myinjector

import android.util.Log

var logPrefix: String = ""
const val LOG_TAG = "MyInjector"

fun logD(msg: String, t: Throwable? = null) {
    Log.d(LOG_TAG, "$logPrefix$msg", t)
}

fun logE(msg: String, t: Throwable? = null) {
    Log.e(LOG_TAG, "$logPrefix$msg", t)
}

fun logI(msg: String, t: Throwable? = null) {
    Log.i(LOG_TAG, "$logPrefix$msg", t)
}

fun logW(msg: String, t: Throwable? = null) {
    Log.w(LOG_TAG, "$logPrefix$msg", t)
}
