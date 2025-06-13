package io.github.a13e300.myinjector.arch

import android.content.pm.ApplicationInfo
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.logI
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.io.File

val _loadDexKit by lazy { System.loadLibrary("dexkit") }

typealias ObfsTable = Map<String, ObfsInfo>

data class ObfsInfo(
    val className: String,
    val memberName: String,
)

fun MethodData.toObfsInfo() = ObfsInfo(className = className, memberName = methodName)
fun FieldData.toObfsInfo() = ObfsInfo(className = className, memberName = fieldName)

const val OBFS_KEY_APK = "apkPath"
const val OBFS_KEY_TABLE_VERSION = "tableVersion"
const val OBFS_KEY_FRAMEWORK_VERSION = "frameworkVersion"
const val OBFS_KEY_PREFIX_METHOD = "method_"
const val OBFS_KEY_MethodInfo_methodName = "methodName"
const val OBFS_KEY_MethodInfo_className = "className"
const val OBFS_FRAMEWORK_VERSION = 1

fun IHook.createObfsTable(
    name: String,
    tableVersion: Int,
    pathProvider: (ApplicationInfo) -> String = { it.sourceDir },
    classLoader: ClassLoader? = null,
    creator: (DexKitBridge) -> ObfsTable
): ObfsTable {
    val appInfo = loadPackageParam.appInfo
    val apkPath = pathProvider(appInfo)

    val tableFile = File(appInfo.dataDir, "cache/obfs_table_$name.json")
    tableFile.parentFile!!.mkdirs()

    runCatching {
        if (tableFile.isFile) {
            JSONObject(tableFile.readText())
        } else {
            null
        }
    }.onFailure {
        logE("read obfs-table $tableFile", it)
    }.getOrNull()?.let { json ->
        runCatching {
            if (json.getInt(OBFS_KEY_FRAMEWORK_VERSION) != OBFS_FRAMEWORK_VERSION) {
                logE("obfs-table $tableFile framework version mismatch, update!")
                return@runCatching
            }
            if (json.getString(OBFS_KEY_APK) != apkPath) {
                logE("obfs-table $tableFile apk mismatch, update!")
                return@runCatching
            }
            if (json.getInt(OBFS_KEY_TABLE_VERSION) != tableVersion) {
                logE("obfs-table $tableFile version mismatch, update!")
                return@runCatching
            }
            val outMap = mutableMapOf<String, ObfsInfo>()
            for (k in json.keys()) {
                if (k.startsWith("method")) {
                    val rk = k.removePrefix(OBFS_KEY_PREFIX_METHOD)
                    val rv = json[k] as JSONObject
                    val methodInfo = ObfsInfo(
                        className = rv.getString(OBFS_KEY_MethodInfo_className),
                        memberName = rv.getString(OBFS_KEY_MethodInfo_methodName),
                    )
                    outMap[rk] = methodInfo
                }
            }
            logI("use cached obfs-table $name $tableFile")
            return outMap
        }.onFailure {
            logE("parsing obfs-table $tableFile", it)
        }
    }
    // failed or no need to load result, create a new one!
    _loadDexKit
    val dexKitBridge = if (classLoader == null)
        DexKitBridge.create(apkPath)
    else DexKitBridge.create(classLoader, true)
    return creator(dexKitBridge).also {
        runCatching {
            val outJsonObj = JSONObject()
            outJsonObj.put(OBFS_KEY_APK, apkPath)
            outJsonObj.put(OBFS_KEY_TABLE_VERSION, tableVersion)
            outJsonObj.put(OBFS_KEY_FRAMEWORK_VERSION, OBFS_FRAMEWORK_VERSION)
            for ((k, v) in it) {
                outJsonObj.put(OBFS_KEY_PREFIX_METHOD + k, JSONObject().apply {
                    put(OBFS_KEY_MethodInfo_className, v.className)
                    put(OBFS_KEY_MethodInfo_methodName, v.memberName)
                })
            }
            tableFile.delete()
            tableFile.writeText(outJsonObj.toString())
        }.onFailure {
            logE("save obfs-table $tableFile", it)
        }.onSuccess {
            logI("created obfs-table $name $tableFile")
        }
    }
}
