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

package androidx.compose.foundation

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.guestshim.PropElement
import androidx.guestshim.PropKey
import androidx.guestshim.ShimProps

/**
 * Draws [color] behind the content.
 *
 * Signature taken from
 * compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/Background.kt,
 * minus its `shape: Shape = RectangleShape` parameter: `Shape` reaches the host through `Outline`
 * and `Path`, which the guest does not carry (see guest-shim/README.md). A shape should eventually
 * cross as a description the host can rebuild, not as a path.
 *
 * Upstream builds a `BackgroundElement` that draws in a `DrawModifierNode`. Here the element only
 * records the colour; the host draws it with the real modifier.
 */
@Stable
fun Modifier.background(color: Color): Modifier = this.then(BackgroundElement(color))

private data class BackgroundElement(val color: Color) : PropElement {
    override fun applyTo(props: ShimProps) {
        props.touch(PropKey.BackgroundColor)
        props.backgroundColor = color.toArgb()
    }
}
