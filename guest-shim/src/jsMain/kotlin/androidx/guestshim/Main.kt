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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * Screens the harness can ask for by name. Task 5 adds the real one.
 *
 * "probe" exists to prove the bridge actually runs: it emits exactly one node, so a test can
 * assert on a real `MutationType.Create`/`Insert` pair instead of only ever checking for the
 * absence of mutations, which a harness that never executes JavaScript would also satisfy.
 */
/**
 * The one piece of state a screen can read and the harness can write, so a test can compose more
 * than once. A modifier that appears and disappears with it is the only way to see the difference
 * between a prop that was never set and one that was set and then taken away.
 */
private val flag = mutableStateOf(false)

private val screens: Map<String, @Composable () -> Unit> = mapOf(
    "empty" to {},
    "probe" to { emitNode(nodeTypeId = NodeType.Box) {} },
    "color" to {
        emitNode(nodeTypeId = NodeType.Box) {
            set(Unit) { sendInt(PropKey.BackgroundColor, Color.Red.toArgb()) }
        }
    },
    // The real API: a modifier chain and three composables, so a test can assert that a chain
    // collapses into props and that each composable becomes one host node.
    "layout" to {
        Column(
            modifier = Modifier.padding(8.dp).background(Color.Red).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText("hi", Modifier.padding(4.dp).padding(2.dp))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {}
        }
    },
    // A modifier that comes and goes with `flag`, so a test can watch a prop be set and then
    // taken away. The second half is the half a one-frame harness cannot see.
    "toggle" to {
        Box(if (flag.value) Modifier.width(24.dp).background(Color.Red) else Modifier) {}
    },
)

fun main() {
    val g: dynamic = js("globalThis")

    // The host's half of the contract, matching the names
    // runtime/src/commonCpp/quickjs_runtime.cpp looks up: the engine injects __fh_* and calls back
    // through these. The guest never drives its own frames — the host decides when one happens,
    // which is what keeps an idle screen silent.
    g.__runtime_sendFrame = { nanos: Double -> GuestRuntime.frame(nanos.toLong()) }
    g.__runtime_onEvent = { nodeId: Int, keyId: Int, value: String? ->
        throw UnsupportedInGuestException("__runtime_onEvent($nodeId, $keyId, $value)")
    }

    // Test-only: poke the one piece of state a screen can read.
    g.__setFlag = { value: Boolean -> flag.value = value }

    // The guest composes its own root as it loads — the host evaluates the bundle and calls nothing
    // afterwards. `__screen`, if the embedder set it before loading, picks which of the screens
    // above to mount; that is how the test harness chooses one.
    val requested = if (jsTypeOf(g.__screen) == "string") g.__screen as String else "layout"
    val screen = screens[requested] ?: throw IllegalArgumentException("unknown screen: $requested")
    GuestRuntime.start(screen)
}
