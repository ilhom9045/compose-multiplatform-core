/*
 * Copyright 2026 The Android Open Source Project
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

package androidx.guestshim

import androidx.compose.runtime.Composable

/** Screens the harness can ask for by name. Task 5 adds the real one. */
private val screens: Map<String, @Composable () -> Unit> = mapOf(
    "empty" to {},
)

fun main() {
    val g: dynamic = js("globalThis")
    g.__runFrame = { name: String ->
        val screen = screens[name] ?: throw IllegalArgumentException("unknown screen: $name")
        GuestRuntime.start(screen)
    }
}
