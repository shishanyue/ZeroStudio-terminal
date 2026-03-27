package com.rustywarfare.modstudio.app.fragments.settings;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.rustywarfare.modstudio.R;
import com.rustywarfare.modstudio.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences;

@Keep
public class TermuxFloatPreferencesFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;

        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setPreferenceDataStore(TermuxFloatPreferencesDataStore.getInstance(context));

        setPreferencesFromResource(R.xml.termux_float_preferences, rootKey);
    }

}

class TermuxFloatPreferencesDataStore extends PreferenceDataStore {

    private static TermuxFloatPreferencesDataStore mInstance;

    private TermuxFloatPreferencesDataStore(Context context) {
        TermuxFloatAppSharedPreferences mPreferences = TermuxFloatAppSharedPreferences.build(context, true);
    }

    public static synchronized TermuxFloatPreferencesDataStore getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new TermuxFloatPreferencesDataStore(context);
        }
        return mInstance;
    }

}
