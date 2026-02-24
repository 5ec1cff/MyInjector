package io.github.a13e300.myinjector.arch

import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import java.util.LinkedList

sealed class Edge(open val obj: Any, open val prev: Edge?, val len: Int = prev?.len?.inc() ?: 0) {
    data class StartEdge(override val obj: Any) : Edge(obj, null) {
        override fun toString(): String {
            return toStringSafe(newLine = true)
        }
    }

    data class FieldEdge(override val obj: Any, override val prev: Edge?, val field: Field) :
        Edge(obj, prev) {
        override fun toString(): String {
            return toStringSafe(newLine = true)
        }
    }

    data class ArrayIndexEdge(override val obj: Any, override val prev: Edge?, val index: Int) :
        Edge(obj, prev) {
        override fun toString(): String {
            return toStringSafe(newLine = true)
        }
    }

    data class WeakRefEdge(override val obj: Any, override val prev: Edge?) : Edge(obj, prev) {
        override fun toString(): String {
            return toStringSafe(newLine = true)
        }
    }

}

fun Edge.toStringSafe(limit: Int = -1, newLine: Boolean = false): String {
    var edge: Edge? = this
    var i = 0
    return StringBuilder().apply {
        append("Path{len=")
        append(this@toStringSafe.len)
        append(" ")
        while (edge != null) {
            if (i != 0) {
                append(" -> ")
            }
            if (limit > 0 && i >= limit) {
                append(" ... ")
                break
            }
            if (newLine) {
                append("\n  ")
            }
            when (edge) {
                is Edge.StartEdge -> {
                    append(" at Start")
                }

                is Edge.FieldEdge -> {
                    append(" at Field ")
                    append(edge.field)
                }

                is Edge.ArrayIndexEdge -> {
                    append(" at Array ")
                    append(edge.index)
                }

                is Edge.WeakRefEdge -> {
                    append(" at WeakRef")
                }
            }
            i += 1
            edge = edge.prev
        }
        if (newLine) {
            append("\n")
        }
        append("}")
    }.toString()
}

data class FindObjectConfiguration(
    val cond: ((Any) -> Boolean)? = null,
    val maxDepth: Int,
    val maxTries: Int,
    val targetObj: Any? = null,
    val targetClz: Class<*>? = null,
    val first: Boolean = true,
)

fun findPathToObject(start: Any, conf: FindObjectConfiguration): List<Edge> {
    val visited = IdentityHashMap<Any, Boolean>()
    val visiting = LinkedList<Edge>()
    var cnt = 0
    val result = mutableListOf<Edge>()

    fun isValid(o: Any): Boolean {
        return !visited.contains(o) && !(o is Int
                || o is Boolean
                || o is Long
                || o is Char
                || o is Float
                || o is Double
                || o is Short
                || o is Byte
                || o is IntArray
                || o is BooleanArray
                || o is LongArray
                || o is CharArray
                || o is FloatArray
                || o is DoubleArray
                || o is ShortArray
                || o is ByteArray
                || o is String
                || o is Class<*>
                || o is Member
                || o is ClassLoader
                )
    }

    fun matches(obj: Any): Boolean {
        return (conf.targetObj != null && obj === conf.targetObj)
                || (conf.targetClz != null && conf.targetClz.isInstance(obj))
                || (conf.cond?.invoke(obj) == true)
    }

    visiting.push(Edge.StartEdge(start))
    visited[start] = java.lang.Boolean.TRUE
    // logD("start scan from $start")

    while (visiting.isNotEmpty()) {
        val edge = visiting.pop()
        val o = edge.obj
        cnt += 1
        if (cnt >= conf.maxTries) {
            // Logger.d("visited cnt ${visited.size}")
            // visited.forEach { k, _ -> Logger.d("visited: $k ${System.identityHashCode(k)}") }
            // error("too many tries!!!")
            break
        }

        if (o is Array<*>) {
            for (i in 0 until o.size) {
                val newObj = o[i] ?: continue
                if (matches(newObj)) {
                    result.add(Edge.ArrayIndexEdge(newObj, edge, i))
                    if (conf.first) break
                    continue
                }
                if (edge.len < conf.maxDepth && isValid(newObj)) {
                    visiting.push(Edge.ArrayIndexEdge(newObj, edge, i))
                    visited[newObj] = java.lang.Boolean.TRUE
                }
            }
        } else if (o is WeakReference<*>) {
            o.get()?.let { newObj ->
                if (matches(newObj)) {
                    result.add(Edge.WeakRefEdge(newObj, edge))
                    if (conf.first) break
                    return@let
                }
                if (edge.len < conf.maxDepth && isValid(newObj)) {
                    visiting.push(Edge.WeakRefEdge(newObj, edge))
                    visited[newObj] = java.lang.Boolean.TRUE
                }
            }
        } else {
            var clz = o.javaClass
            while (clz != Object::class.java) {
                clz.declaredFields.forEach {
                    if (Modifier.isStatic(it.modifiers)) return@forEach
                    it.isAccessible = true
                    val newObj = it.get(o) ?: return@forEach
                    if (matches(newObj)) {
                        result.add(Edge.FieldEdge(newObj, edge, it))
                        if (conf.first) break
                        return@forEach
                    }
                    if (edge.len < conf.maxDepth && isValid(newObj)) {
                        visiting.push(Edge.FieldEdge(newObj, edge, it))
                        visited[newObj] = java.lang.Boolean.TRUE
                    }
                }
                clz = clz.superclass
            }
        }
    }

    if (result.isEmpty()) {
        error("object not found (traversal $cnt objects)")
    }
    return result
}
