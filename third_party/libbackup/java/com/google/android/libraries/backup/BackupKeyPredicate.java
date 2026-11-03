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

/**
 * Decides whether a shared preference key is included in a key/value backup.
 *
 * <p>Source-compatible stand-in for AOSP {@code external/libbackup}, used by the standalone Gradle
 * build. The platform build keeps using the real {@code libbackup} module.
 */
public interface BackupKeyPredicate {
  /** Returns true if {@code key} should be backed up and restored. */
  boolean shouldBeBackedUp(String key);
}
