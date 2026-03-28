package com.rustywarfare.modstudio.shared.shell.am;

import android.Manifest;

import androidx.annotation.NonNull;

import com.rustywarfare.modstudio.shared.logger.Logger;
import com.rustywarfare.modstudio.shared.markdown.MarkdownUtils;
import com.rustywarfare.modstudio.shared.net.socket.local.ILocalSocketManager;
import com.rustywarfare.modstudio.shared.net.socket.local.LocalSocketRunConfig;

import java.io.Serializable;

/**
 * Run config for {@link AmSocketServer}.
 */
public class AmSocketServerRunConfig extends LocalSocketRunConfig implements Serializable {

    /**
     * Check if {@link Manifest.permission#SYSTEM_ALERT_WINDOW} has been granted if running on Android `>= 10`
     * if starting activities. Will also check when starting services in case starting foreground
     * service is not allowed.
     * <p>
     * <a href="https://developer.android.com/guide/components/activities/background-starts">...</a>
     */
    private Boolean mCheckDisplayOverAppsPermission;
    public static final boolean DEFAULT_CHECK_DISPLAY_OVER_APPS_PERMISSION = true;

    /**
     * Create an new instance of {@link AmSocketServerRunConfig}.
     *
     * @param title The {@link #mTitle} value.
     * @param path The {@link #mPath} value.
     * @param localSocketManagerClient The {@link #mLocalSocketManagerClient} value.
     */
    public AmSocketServerRunConfig(@NonNull String title, @NonNull String path, @NonNull ILocalSocketManager localSocketManagerClient) {
        super(title, path, localSocketManagerClient);
    }


    /** Get {@link #mCheckDisplayOverAppsPermission} if set, otherwise {@link #DEFAULT_CHECK_DISPLAY_OVER_APPS_PERMISSION}. */
    public boolean shouldCheckDisplayOverAppsPermission() {
        return mCheckDisplayOverAppsPermission != null ? mCheckDisplayOverAppsPermission : DEFAULT_CHECK_DISPLAY_OVER_APPS_PERMISSION;
    }

    /** Set {@link #mCheckDisplayOverAppsPermission}. */
    public void setCheckDisplayOverAppsPermission(Boolean checkDisplayOverAppsPermission) {
        mCheckDisplayOverAppsPermission = checkDisplayOverAppsPermission;
    }



    /**
     * Get a log {@link String} for {@link AmSocketServerRunConfig}.
     *
     * @param config The {@link AmSocketServerRunConfig} to get info of.
     * @return Returns the log {@link String}.
     */
    @NonNull
    public static String getRunConfigLogString(final AmSocketServerRunConfig config) {
        if (config == null) return "null";
        return config.getLogString();
    }

    /** Get a log {@link String} for the {@link AmSocketServerRunConfig}. */
    @NonNull
    public String getLogString() {

        return super.getLogString() + "\n\n\n" +
            "Am Command:" +
            "\n" + Logger.getSingleLineLogStringEntry("CheckDisplayOverAppsPermission", shouldCheckDisplayOverAppsPermission(), "-");
    }

    /**
     * Get a markdown {@link String} for {@link AmSocketServerRunConfig}.
     *
     * @param config The {@link AmSocketServerRunConfig} to get info of.
     * @return Returns the markdown {@link String}.
     */
    public static String getRunConfigMarkdownString(final AmSocketServerRunConfig config) {
        if (config == null) return "null";
        return config.getMarkdownString();
    }

    /** Get a markdown {@link String} for the {@link AmSocketServerRunConfig}. */
    @NonNull
    public String getMarkdownString() {

        return super.getMarkdownString() + "\n\n\n" +
            "## " + "Am Command" +
            "\n" + MarkdownUtils.getSingleLineMarkdownStringEntry("CheckDisplayOverAppsPermission", shouldCheckDisplayOverAppsPermission(), "-");
    }



    @NonNull
    @Override
    public String toString() {
        return getLogString();
    }

}
