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
import androidx.guestshim.PropElement
import androidx.guestshim.PropKey
import androidx.guestshim.ShimProps
import androidx.guestshim.UnsupportedInGuestException

/**
 * Signatures taken from compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/
 * foundation/layout/Size.kt. Upstream's `DpSize` overloads and the `requiredSize`/`sizeIn` family
 * are not here yet: they need min/max constraint props the wire does not carry.
 *
 * Upstream measures with a `SizeElement` that rewrites the incoming constraints. Here the values
 * only land on the node's props; the host applies the real modifier.
 */
@Stable fun Modifier.width(width: Dp): Modifier = this.then(SizeElement(width = width))

@Stable fun Modifier.height(height: Dp): Modifier = this.then(SizeElement(height = height))

@Stable fun Modifier.size(size: Dp): Modifier = this.then(SizeElement(size, size))

@Stable
fun Modifier.size(width: Dp, height: Dp): Modifier = this.then(SizeElement(width, height))

/**
 * [fraction] is declared to match upstream but only `1f` reaches the host: it reads `FillMaxWidth`
 * as a flag, not a fraction, so a partial fill would silently become a full one. Throwing keeps
 * that from being a layout that is quietly wrong.
 */
@Stable
fun Modifier.fillMaxWidth(fraction: Float = 1f): Modifier =
    this.then(FillElement(width = requireFullFill(fraction, "fillMaxWidth")))

@Stable
fun Modifier.fillMaxHeight(fraction: Float = 1f): Modifier =
    this.then(FillElement(height = requireFullFill(fraction, "fillMaxHeight")))

@Stable
fun Modifier.fillMaxSize(fraction: Float = 1f): Modifier =
    requireFullFill(fraction, "fillMaxSize").let { this.then(FillElement(it, it)) }

private fun requireFullFill(fraction: Float, call: String): Float =
    if (fraction == 1f) fraction
    else throw UnsupportedInGuestException("$call(fraction = $fraction)")

private data class SizeElement(val width: Dp? = null, val height: Dp? = null) : PropElement {
    override fun applyTo(props: ShimProps) {
        width?.let {
            props.touch(PropKey.Width)
            props.width = it.value
        }
        height?.let {
            props.touch(PropKey.Height)
            props.height = it.value
        }
    }
}

private data class FillElement(val width: Float = 0f, val height: Float = 0f) : PropElement {
    override fun applyTo(props: ShimProps) {
        props.touch(PropKey.FillMaxWidth)
        if (width != 0f) props.fillMaxWidth = width
        if (height != 0f) props.fillMaxHeight = height
    }
}
