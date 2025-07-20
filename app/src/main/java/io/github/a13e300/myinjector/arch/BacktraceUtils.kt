@file:Suppress("DiscouragedPrivateApi")

package io.github.a13e300.myinjector.arch

import io.github.a13e300.myinjector.logE
import kotlin.math.min

val Throwable_field_backTrace by lazy {
    runCatching {
        Throwable::class.java.getDeclaredField("backtrace")
            .also { it.isAccessible = true }
    }.onFailure {
        logE("Throwable_field_backTrace", it)
    }.getOrNull()
}

val Class_field_dexCache by lazy {
    runCatching {
        Class::class.java.getDeclaredField("dexCache")
            .also { it.isAccessible = true }
    }.onFailure {
        logE("Class_field_dexCache", it)
    }.getOrNull()
}

val DexCache_field_location by lazy {
    runCatching {
        val dexCacheClass = Class.forName("java.lang.DexCache")
        dexCacheClass.getDeclaredField("location")
            .also { it.isAccessible = true }
    }.onFailure {
        logE("Class_field_dexCache", it)
    }.getOrNull()
}

class StackTraceElementEx {
    var stackTraceElement: StackTraceElement? = null
    var dexLocation: String? = null
    var dexPc: Long = -1L

    override fun toString(): String {
        return "$stackTraceElement (dexPc=$dexPc, location=$dexLocation)"
    }
}

fun getStackTraceEx(throwable: Throwable): List<StackTraceElementEx> {
    runCatching {
        val backtrace = Throwable_field_backTrace?.get(throwable) as? Array<*>
        if (backtrace == null) {
            logE("You must not call getStackTrace!")
            return@runCatching
        }
        val elements = mutableListOf<StackTraceElementEx>()
        val arr = backtrace[0]
        if (arr is LongArray) {
            val len = arr.size
            for (j in len / 2 until len) {
                elements.add(StackTraceElementEx().also { it.dexPc = arr[j] })
            }
        } else if (arr is IntArray) {
            val len = arr.size
            for (j in len / 2 until len) {
                elements.add(StackTraceElementEx().also { it.dexPc = arr[j].toLong() })
            }
        }

        for (i in 1 until backtrace.size) {
            (backtrace[i] as? Class<*>)?.let { clz ->
                Class_field_dexCache?.get(clz)?.let { dexCache ->
                    (DexCache_field_location?.get(dexCache) as? String)?.let { location ->
                        elements[i - 1].dexLocation = location
                    }
                }
            }
        }

        val stackTrace = throwable.stackTrace

        for (i in 0 until min(elements.size, stackTrace.size)) {
            elements[i].stackTraceElement = stackTrace[i]
        }
        return elements
    }.onFailure {
        logE("failed to getStackTraceEx", it)
    }

    return throwable.stackTrace.map {
        StackTraceElementEx().also { ex ->
            ex.stackTraceElement = it
        }
    }
}
