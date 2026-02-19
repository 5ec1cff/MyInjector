package io.github.a13e300.myinjector.arch

import android.content.pm.ApplicationInfo
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.logI
import org.json.JSONObject
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import java.io.Closeable
import java.io.File

val _loadDexKit by lazy { System.loadLibrary("dexkit") }

typealias ObfsTable = Map<String, ObfsInfo>
typealias MutableObfsTable = MutableMap<String, ObfsInfo>

data class ObfsInfo(
    val className: String,
    val memberName: String,
)

fun ClassData.toObfsInfo() = ObfsInfo(className = name, memberName = "")
fun MethodData.toObfsInfo() = ObfsInfo(className = className, memberName = methodName)
fun FieldData.toObfsInfo() = ObfsInfo(className = className, memberName = fieldName)

const val OBFS_KEY_APK = "apkPath"
const val OBFS_KEY_TABLE_VERSION = "tableVersion"
const val OBFS_KEY_FRAMEWORK_VERSION = "frameworkVersion"
const val OBFS_KEY_PREFIX_METHOD = "method_"
const val OBFS_KEY_MethodInfo_methodName = "methodName"
const val OBFS_KEY_MethodInfo_className = "className"
const val OBFS_FRAMEWORK_VERSION = 1

class ObfsTableCreator(
    val name: String,
    val tableVersion: Int,
    pathProvider: (ApplicationInfo) -> String = { it.sourceDir },
    classLoader: ClassLoader? = null,
    appInfo: ApplicationInfo,
    force: Boolean = false
) : Closeable {
    val apkPath = pathProvider(appInfo)
    val tableFile = File(appInfo.dataDir, "cache/obfs_table_$name.json")
    val obfsTable: MutableObfsTable = mutableMapOf()

    val bridgeDelegate = lazy {
        _loadDexKit
        if (classLoader == null)
            DexKitBridge.create(apkPath)
        else DexKitBridge.create(classLoader, true)
    }
    val bridge by bridgeDelegate

    init {
        tableFile.parentFile!!.mkdirs()
        runCatching {
            if (!force && tableFile.isFile) {
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
                for (k in json.keys()) {
                    if (k.startsWith("method")) {
                        val rk = k.removePrefix(OBFS_KEY_PREFIX_METHOD)
                        val rv = json[k] as JSONObject
                        val methodInfo = ObfsInfo(
                            className = rv.getString(OBFS_KEY_MethodInfo_className),
                            memberName = rv.getString(OBFS_KEY_MethodInfo_methodName),
                        )
                        obfsTable[rk] = methodInfo
                    }
                }
            }.onFailure {
                logE("parsing obfs-table $tableFile", it)
            }
        }
    }

    fun create(key: String, callback: (DexKitBridge) -> ObfsInfo) =
        obfsTable.computeIfAbsent(key) {
            callback(bridge)
        }

    fun persist() {
        runCatching {
            val outJsonObj = JSONObject()
            outJsonObj.put(OBFS_KEY_APK, apkPath)
            outJsonObj.put(OBFS_KEY_TABLE_VERSION, tableVersion)
            outJsonObj.put(OBFS_KEY_FRAMEWORK_VERSION, OBFS_FRAMEWORK_VERSION)
            for ((k, v) in obfsTable) {
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

    override fun close() {
        if (bridgeDelegate.isInitialized()) {
            bridge.close()
        }
    }

}

fun IHook.createObfsTable(
    name: String,
    tableVersion: Int,
    pathProvider: (ApplicationInfo) -> String = { it.sourceDir },
    classLoader: ClassLoader? = null,
    force: Boolean = false,
    creator: (DexKitBridge) -> ObfsTable
): ObfsTable {
    val appInfo = loadPackageParam.appInfo

    val creator = ObfsTableCreator(
        name, tableVersion, pathProvider, classLoader, appInfo, force
    )
    // failed or no need to load result, create a new one!
    creator.obfsTable.putAll(creator(creator.bridge))
    creator.persist()
    return creator.obfsTable
}

