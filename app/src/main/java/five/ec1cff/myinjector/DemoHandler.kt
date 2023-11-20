package five.ec1cff.myinjector

import android.system.Os
import android.util.Log
import de.robv.android.xposed.IXposedHookZygoteInit
import java.io.File

class DemoHandler : IXposedHookZygoteInit {
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam?) {
        Log.d("DemoHandler", "initZygote")
        for (file in File("/proc/self/fd").listFiles()!!) {
            runCatching {
                val link = Os.readlink(file.absolutePath)
                Log.d("DemoHandler", "$file -> $link")
            }.onFailure {
                Log.e("DemoHandler", "failed to read $file", it)
            }
        }
    }
}