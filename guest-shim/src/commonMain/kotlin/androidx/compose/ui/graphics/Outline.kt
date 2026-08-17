/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.graphics

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.boundingRect

/**
 * Defines a simple shape, used for bounding graphical regions.
 *
 * Can be used for defining a shape of the component background, a shape of shadows cast by the
 * component, or to clip the contents.
 */
sealed class Outline {
    /** Rectangular area. */
    @Immutable
    class Rectangle(val rect: Rect) : Outline() {

        override val bounds: Rect
            get() = rect

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Rectangle) return false

            if (rect != other.rect) return false

            return true
        }

        override fun hashCode(): Int {
            return rect.hashCode()
        }
    }

    /** Rectangular area with rounded corners. */
    @Immutable
    class Rounded(val roundRect: RoundRect) : Outline() {

        // Deviation: upstream also builds a `roundRectPath` here, for the case where the four corner
        // radii differ and Canvas cannot draw the round rect directly. That is a drawing concern and
        // needs Path; nothing on this side draws.

        override val bounds: Rect
            get() = roundRect.boundingRect

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Rounded) return false

            if (roundRect != other.roundRect) return false

            return true
        }

        override fun hashCode(): Int {
            return roundRect.hashCode()
        }
    }


    // Deviation from the verbatim copy: `Outline.Generic` and everything below it are removed.
    // Generic wraps a Path, and the rest of the file draws an outline through DrawScope, Canvas and
    // Path — the drawing surface the guest does not carry (see guest-shim/README.md). Rectangle and
    // Rounded are pure geometry and are copied unchanged; a shape crosses the wire as a description
    // the host rebuilds, so nothing here is ever asked to produce a path.

    /** Return the bounds of the outline */
    abstract val bounds: Rect
}
