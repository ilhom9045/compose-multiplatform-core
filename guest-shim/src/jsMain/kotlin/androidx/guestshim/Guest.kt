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

private var started = false

/**
 * The guest's entry point. An app is a `main` and this call:
 *
 * ```kotlin
 * fun main() = setContent {
 *     Column(Modifier.padding(8.dp).background(Color.Red)) {
 *         BasicText("hi")
 *     }
 * }
 * ```
 *
 * Everything the host expects on the other side of the bridge is wired up here, so nothing about
 * frames, events or globals has to appear in an app. The host evaluates the bundle and calls
 * nothing afterwards, which is why composition starts as this runs.
 *
 * The guest never drives its own frames — the host decides when one happens, and that is what keeps
 * an idle screen off the wire entirely.
 */
fun setContent(content: @Composable () -> Unit) {
    check(!started) {
        "setContent was already called. The guest composes a single root: node ids are handed out " +
            "per composition, so a second one would hand the host ids it has already seen."
    }
    started = true

    // The names runtime/src/commonCpp/quickjs_runtime.cpp looks up. The engine injects __fh_* going
    // out and calls back through these coming in.
    val global: dynamic = js("globalThis")
    global.__runtime_sendFrame = { nanos: Double -> GuestRuntime.frame(nanos.toLong()) }
    global.__runtime_onEvent = { nodeId: Int, keyId: Int -> dispatchEvent(nodeId, keyId) }

    GuestRuntime.start(content)
}
