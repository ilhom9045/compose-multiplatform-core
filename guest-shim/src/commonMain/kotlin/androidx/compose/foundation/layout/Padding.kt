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

package androidx.compose.foundation.layout

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.guestshim.PropElement
import androidx.guestshim.PropKey
import androidx.guestshim.ShimProps

/**
 * Signatures taken from compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/
 * foundation/layout/Padding.kt. Upstream's `padding(paddingValues: PaddingValues)` overload is not
 * here yet — `PaddingValues` is its own type with a `Density`-aware interface.
 *
 * Upstream applies padding by wrapping the layout in a `PaddingElement` that shrinks the incoming
 * constraints. Here the values only accumulate onto the node's props; the host insets with the real
 * modifier. Chained padding adds up the way nested upstream modifiers do.
 */
@Stable
fun Modifier.padding(all: Dp): Modifier = this.then(PaddingElement(all, all, all, all))

@Stable
fun Modifier.padding(horizontal: Dp = 0.dp, vertical: Dp = 0.dp): Modifier =
    this.then(PaddingElement(horizontal, vertical, horizontal, vertical))

@Stable
fun Modifier.padding(
    start: Dp = 0.dp,
    top: Dp = 0.dp,
    end: Dp = 0.dp,
    bottom: Dp = 0.dp,
): Modifier = this.then(PaddingElement(start, top, end, bottom))

private data class PaddingElement(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp) :
    PropElement {
    override fun applyTo(props: ShimProps) {
        props.touch(PropKey.PaddingTop)
        props.paddingStart += start.value
        props.paddingTop += top.value
        props.paddingEnd += end.value
        props.paddingBottom += bottom.value
    }
}
