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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            modifier = Modifier
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Red)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText("hi", Modifier.padding(4.dp).padding(2.dp))
            var on by remember { mutableStateOf(false) }
            Box(
                Modifier.size(24.dp)
                    .clip(CircleShape)
                    .background(if (on) Color.Green else Color.Blue)
                    .clickable { on = !on },
                contentAlignment = Alignment.Center,
            ) {}
        }
    },
    // A percentage corner has no dp to send: what it is a percentage *of* is the host's size, known
    // only at layout. The guest must refuse rather than send a plausible number.
    "percentClip" to {
        Box(Modifier.clip(RoundedCornerShape(percent = 25))) {}
    },
    // The wire's first round trip: the host reports a click, the guest's own state moves, and the
    // new text goes back on the next frame.
    "click" to {
        var count by remember { mutableStateOf(0) }
        Column(Modifier.clickable { count++ }) { BasicText("count=$count") }
    },
    // Only the colour changes between frames. The rest of the chain has to travel anyway: the host
    // rebuilds a node's modifier order out of the batch, so a batch carrying just the colour would
    // drop the padding from the chain.
    "partial" to {
        Box(Modifier.padding(8.dp).background(if (flag.value) Color.Blue else Color.Red)) {}
    },
    // A modifier that comes and goes with `flag`, so a test can watch a prop be set and then
    // taken away. The second half is the half a one-frame harness cannot see.
    "toggle" to {
        Box(if (flag.value) Modifier.width(24.dp).background(Color.Red) else Modifier) {}
    },
)

/**
 * A real app is `fun main() = setContent { … }` and nothing else. The two globals below are this
 * module's own test scaffolding: `__screen` picks one of the screens above so the harness can drive
 * each in turn, and `__setFlag` pokes the one piece of state they read.
 */
fun main() {
    val global: dynamic = js("globalThis")
    global.__setFlag = { value: Boolean -> flag.value = value }

    val requested = if (jsTypeOf(global.__screen) == "string") global.__screen as String else "layout"
    setContent(screens[requested] ?: throw IllegalArgumentException("unknown screen: $requested"))
}
