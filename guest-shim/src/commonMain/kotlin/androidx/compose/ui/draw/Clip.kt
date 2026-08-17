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

package androidx.compose.ui.draw

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.guestshim.PropElement
import androidx.guestshim.PropKey
import androidx.guestshim.ShimProps
import androidx.guestshim.UnsupportedInGuestException

/**
 * Clips the content to [shape].
 *
 * Signature taken from
 * compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/draw/Clip.kt. Upstream this is
 * `graphicsLayer(shape = shape, clip = true)`, which asks the shape for an `Outline` during layout.
 * The guest has neither a size nor a density then, so it sends what the shape *is* instead and the
 * host rebuilds it: a rectangle, a circle, or four corner radii.
 */
@Stable fun Modifier.clip(shape: Shape): Modifier = this.then(ClipElement(shape))

private data class ClipElement(val shape: Shape) : PropElement {
    override fun applyTo(props: ShimProps) {
        props.touch(PropKey.ClipShapeType)
        when {
            shape === RectangleShape -> props.clipShapeType = 0
            // CircleShape is RoundedCornerShape(50), and any other 50% shape is the same circle,
            // so this compares by value rather than identity.
            shape == CircleShape -> props.clipShapeType = 2
            shape is RoundedCornerShape -> {
                props.clipShapeType = 1
                props.cornerTopStart = shape.topStart.toDp()
                props.cornerTopEnd = shape.topEnd.toDp()
                props.cornerBottomEnd = shape.bottomEnd.toDp()
                props.cornerBottomStart = shape.bottomStart.toDp()
            }
            else -> throw UnsupportedInGuestException("Modifier.clip($shape)")
        }
    }
}

/**
 * The corner's size in dp, for the wire.
 *
 * `CornerSize` only exposes `toPx(shapeSize, density)`, and its implementations are private, so the
 * value is read by asking. At density 1 a dp corner answers its own number; a percent corner answers
 * a fraction of the size it was handed. Asking twice with different sizes is what tells them apart —
 * a percent corner cannot be sent, because the size it would be a percentage of is the host's to
 * know.
 */
private fun CornerSize.toDp(): Float {
    val density = Density(1f)
    val atZero = toPx(Size.Zero, density)
    val atSize = toPx(Size(1000f, 1000f), density)
    if (atZero != atSize) {
        throw UnsupportedInGuestException("percent-based CornerSize in Modifier.clip")
    }
    return atZero
}
