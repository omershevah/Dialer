/*
 * Copyright (C) 2026 The Android Open Source Project
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
 * limitations under the License.
 */

package com.google.android.libraries.backup;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Key/value backup agent that backs up only the shared preference keys accepted by the
 * subclass's {@link #getBackupSpecification()}.
 *
 * <p>Source-compatible stand-in for AOSP {@code external/libbackup}, used by the standalone Gradle
 * build. The platform build keeps using the real {@code libbackup} module.
 *
 * <p>Filtered copies of each preference file are staged under a prefixed name. On backup the
 * staged copies are what get written; on restore the data lands in the staged copies and is then
 * copied, filtered again, into the real files in {@link #onRestoreFinished()}.
 */
public abstract class PersistentBackupAgentHelper extends BackupAgentHelper {

  private static final String HELPER_KEY = "persistent_backup_agent_helper";
  private static final String STAGING_PREFIX = "persistent_backup_agent_helper_";

  /**
   * Returns a map from shared preference file name to the predicate that selects which of its keys
   * are backed up.
   */
  public abstract Map<String, BackupKeyPredicate> getBackupSpecification();

  @Override
  public void onBackup(
      ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState)
      throws IOException {
    Map<String, BackupKeyPredicate> spec = getBackupSpecification();
    for (Map.Entry<String, BackupKeyPredicate> entry : spec.entrySet()) {
      copyFiltered(
          getSharedPreferences(entry.getKey(), Context.MODE_PRIVATE),
          getSharedPreferences(stagingName(entry.getKey()), Context.MODE_PRIVATE),
          entry.getValue());
    }
    registerHelper(spec);
    super.onBackup(oldState, data, newState);
  }

  @Override
  public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
      throws IOException {
    registerHelper(getBackupSpecification());
    super.onRestore(data, appVersionCode, newState);
  }

  @Override
  public void onRestoreFinished() {
    for (Map.Entry<String, BackupKeyPredicate> entry : getBackupSpecification().entrySet()) {
      SharedPreferences staging =
          getSharedPreferences(stagingName(entry.getKey()), Context.MODE_PRIVATE);
      SharedPreferences target = getSharedPreferences(entry.getKey(), Context.MODE_PRIVATE);
      SharedPreferences.Editor editor = target.edit();
      for (Map.Entry<String, ?> value : staging.getAll().entrySet()) {
        if (entry.getValue().shouldBeBackedUp(value.getKey())) {
          put(editor, value.getKey(), value.getValue());
        }
      }
      editor.apply();
      staging.edit().clear().apply();
    }
    super.onRestoreFinished();
  }

  private void registerHelper(Map<String, BackupKeyPredicate> spec) {
    String[] names = new String[spec.size()];
    int i = 0;
    for (String name : spec.keySet()) {
      names[i++] = stagingName(name);
    }
    addHelper(HELPER_KEY, new SharedPreferencesBackupHelper(this, names));
  }

  private static String stagingName(String prefsName) {
    return STAGING_PREFIX + prefsName;
  }

  private static void copyFiltered(
      SharedPreferences from, SharedPreferences to, BackupKeyPredicate predicate) {
    SharedPreferences.Editor editor = to.edit().clear();
    for (Map.Entry<String, ?> value : from.getAll().entrySet()) {
      if (predicate.shouldBeBackedUp(value.getKey())) {
        put(editor, value.getKey(), value.getValue());
      }
    }
    // Backup reads the file from disk, so the write must be synchronous.
    editor.commit();
  }

  @SuppressWarnings("unchecked")
  private static void put(SharedPreferences.Editor editor, String key, Object value) {
    if (value instanceof String) {
      editor.putString(key, (String) value);
    } else if (value instanceof Boolean) {
      editor.putBoolean(key, (Boolean) value);
    } else if (value instanceof Integer) {
      editor.putInt(key, (Integer) value);
    } else if (value instanceof Long) {
      editor.putLong(key, (Long) value);
    } else if (value instanceof Float) {
      editor.putFloat(key, (Float) value);
    } else if (value instanceof Set) {
      editor.putStringSet(key, (Set<String>) value);
    }
  }
}
