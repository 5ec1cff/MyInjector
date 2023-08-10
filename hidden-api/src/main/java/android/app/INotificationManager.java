package android.app;

import android.os.IBinder;

public interface INotificationManager {
    class Stub {
        public static INotificationManager asInterface(IBinder b) {
            throw new RuntimeException("");
        }
    }

    NotificationChannel getNotificationChannel(String callingPkg, int userId, String pkg, String channelId);
}
