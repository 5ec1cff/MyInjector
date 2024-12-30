package android.app;

import android.os.IBinder;

public interface IActivityManager {
    void forceStopPackage(String packageName, int userId);

    class Stub {
        public static IActivityManager asInterface(IBinder binder) {
            throw new UnsupportedOperationException("");
        }
    }
}
