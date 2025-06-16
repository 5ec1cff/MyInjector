package io.github.a13e300.myinjector.system_server

import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel

open class ResultReceiver : Binder() {
    companion object {
        fun IBinder.writeResult(code: Int, data: Bundle?) {
            val p = Parcel.obtain()
            try {
                p.writeInt(code)
                p.writeTypedObject(data, 0)
                transact(1, p, null, FLAG_ONEWAY)
            } finally {
                p.recycle()
            }
        }
    }

    protected open fun onReceive(code: Int, data: Bundle?) {

    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == 1) {
            val c = data.readInt()
            val d = data.readTypedObject(Bundle.CREATOR)
            onReceive(c, d)
        }
        return super.onTransact(code, data, reply, flags)
    }
}
