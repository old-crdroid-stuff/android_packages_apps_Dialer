/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.voicemail.impl.configui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.android.dialer.R;
import com.android.dialer.common.Assert;
import com.android.dialer.common.concurrent.ThreadUtil;
import com.android.voicemail.VoicemailComponent;

/**
 * Fragment to edit the override values for the {@link import
 * com.android.voicemail.impl.OmtpVvmCarrierConfigHelper}
 */
public class ConfigOverrideFragment extends PreferenceFragmentCompat
    implements OnPreferenceChangeListener {

  /**
   * Any preference with key that starts with this prefix will be written to the dialer carrier
   * config.
   */
  private static final String CONFIG_OVERRIDE_KEY_PREFIX = "vvm_config_override_key_";

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    PreferenceManager.setDefaultValues(getActivity(), R.xml.vvm_config_override, false);
    setPreferencesFromResource(R.xml.vvm_config_override, rootKey);

    // add listener so the value of a EditTextPreference will be updated to the summary.
    for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
      Preference preference = getPreferenceScreen().getPreference(i);
      preference.setOnPreferenceChangeListener(this);
      updatePreference(preference);
    }
  }

  @Override
  public boolean onPreferenceChange(Preference preference, Object newValue) {
    Assert.isMainThread();
    ThreadUtil.postOnUiThread(() -> updatePreference(preference));
    return true;
  }

  private void updatePreference(Preference preference) {
    if (preference instanceof EditTextPreference) {
      EditTextPreference editTextPreference = (EditTextPreference) preference;
      editTextPreference.setSummary(editTextPreference.getText());
    }
  }

  @Override
  public boolean onPreferenceTreeClick(Preference preference) {
      if (TextUtils.equals(
          preference.getKey(), getString(R.string.vvm_config_override_load_current_key))) {
        loadCurrentConfig();
      }
      return super.onPreferenceTreeClick(preference);
  }

  /**
   * Loads the config for the currently carrier into the override values, from the dialer or the
   * carrier config app. This is a "reset" button to load the defaults.
   */
  @SuppressLint("MissingPermission")
  private void loadCurrentConfig() {
    Context context = requireActivity();
    PhoneAccountHandle phoneAccountHandle =
        context
            .getSystemService(TelecomManager.class)
            .getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_VOICEMAIL);

    PersistableBundle config =
        VoicemailComponent.get(context).getVoicemailClient().getConfig(context, phoneAccountHandle);

    for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
      Preference preference = getPreferenceScreen().getPreference(i);
      String key = preference.getKey();
      if (!key.startsWith(CONFIG_OVERRIDE_KEY_PREFIX)) {
        continue;
      }

      String configKey = key.substring(CONFIG_OVERRIDE_KEY_PREFIX.length());

      if (configKey.endsWith("bool")) {
        ((SwitchPreferenceCompat) preference).setChecked(config.getBoolean(configKey));
      } else if (configKey.endsWith("int")) {
        ((EditTextPreference) preference).setText(String.valueOf(config.getInt(configKey)));
      } else if (configKey.endsWith("string")) {
        ((EditTextPreference) preference).setText(config.getString(configKey));
      } else if (configKey.endsWith("string_array")) {
        ((EditTextPreference) preference).setText(toCsv(config.getStringArray(configKey)));
      } else {
        throw Assert.createAssertionFailException("unknown type for key " + configKey);
      }
      updatePreference(preference);
    }
  }

  public static boolean isOverridden(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.vvm_config_override_enabled_key), false);
  }

  public static PersistableBundle getConfig(Context context) {
    Assert.checkState(isOverridden(context));
    PersistableBundle result = new PersistableBundle();

    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
    for (String key : preferences.getAll().keySet()) {
      if (!key.startsWith(CONFIG_OVERRIDE_KEY_PREFIX)) {
        continue;
      }
      String configKey = key.substring(CONFIG_OVERRIDE_KEY_PREFIX.length());
      if (configKey.endsWith("bool")) {
        result.putBoolean(configKey, preferences.getBoolean(key, false));
      } else if (configKey.endsWith("int")) {
        result.putInt(configKey, Integer.valueOf(preferences.getString(key, null)));
      } else if (configKey.endsWith("string")) {
        result.putString(configKey, preferences.getString(key, null));
      } else if (configKey.endsWith("string_array")) {
        result.putStringArray(configKey, fromCsv(preferences.getString(key, null)));
      } else {
        throw Assert.createAssertionFailException("unknown type for key " + configKey);
      }
    }
    return result;
  }

  private static String toCsv(String[] array) {
    if (array == null) {
      return "";
    }
    StringBuilder result = new StringBuilder();
    for (String element : array) {
      if (result.length() != 0) {
        result.append(",");
      }
      result.append(element);
    }
    return result.toString();
  };

  private static String[] fromCsv(String csv) {
    return csv.split(",");
  }
}
