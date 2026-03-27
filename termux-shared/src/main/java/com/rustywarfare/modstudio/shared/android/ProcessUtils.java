package com.rustywarfare.modstudio.shared.android;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.rustywarfare.modstudio.shared.logger.Logger;

import java.util.List;

public class ProcessUtils {

    public static final String LOG_TAG = "ProcessUtils";

    /**
     * Get the app process name for a pid with a call to {@link ActivityManager#getRunningAppProcesses()}.
     * <p>
     * This will not return child process names. Android did not keep track of them before android 12
     * phantom process addition, but there is no API via IActivityManager to get them.
     * <p>
     * To get process name for pids of own app's child processes, check `get_process_name_from_cmdline()`
     * in `local-socket.cpp`.
     * <p>
     * <a href="https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/core/java/android/app/ActivityManager.java;l=3362">...</a>
     * <a href="https://cs.android.com/android/platform/superproject/+/android-12.0.0_r32:frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java;l=8434">...</a>
     * <a href="https://cs.android.com/android/_/android/platform/frameworks/base/+/refs/tags/android-12.0.0_r32:services/core/java/com/android/server/am/PhantomProcessList.java">...</a>
     * <a href="https://cs.android.com/android/_/android/platform/frameworks/base/+/refs/tags/android-12.0.0_r32:services/core/java/com/android/server/am/PhantomProcessRecord.java">...</a>
     *
     * @param context The {@link Context} for operations.
     * @param pid The pid of the process.
     * @return Returns the app process name if found, otherwise {@code null}.
     */
    @Nullable
    public static String getAppProcessNameForPid(@NonNull Context context, int pid) {
        if (pid < 0) return null;

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) return null;
        try {
            List<ActivityManager.RunningAppProcessInfo> runningApps = activityManager.getRunningAppProcesses();
            if (runningApps == null) {
                return null;
            }
            for (ActivityManager.RunningAppProcessInfo procInfo : runningApps) {
                if (procInfo.pid == pid) {
                    return procInfo.processName;
                }
            }
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to get app process name for pid " + pid, e);
        }

        return null;
    }

}
