package android.app;

import android.os.IBinder;

public interface IActivityTaskManager {
    ActivityTaskManager.RootTaskInfo getFocusedRootTaskInfo();

    class Stub {
        public static IActivityTaskManager asInterface(IBinder binder) {
            throw new UnsupportedOperationException();
        }
    }
}
