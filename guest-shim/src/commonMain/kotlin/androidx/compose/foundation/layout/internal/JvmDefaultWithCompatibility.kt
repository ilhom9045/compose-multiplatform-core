/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.foundation.layout.internal

// Flattened expect/actual: upstream declares this in commonMain and supplies the body from a
// platform source set. An expect here would need an actual in every guest target for a
// declaration nothing calls, so the nonJvm body is inlined unchanged.

internal annotation class NoOp

internal typealias JvmDefaultWithCompatibility = NoOp
