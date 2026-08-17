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

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.guestshim.UnsupportedInGuestException

/**
 * The receivers of `Box`/`Column`/`Row` content lambdas, so those signatures match upstream and app
 * code compiles unchanged.
 *
 * Their members are declared and throw. Upstream `align` and `weight` return elements that only
 * mean something during the host's measure pass — `weight` in particular is resolved by the parent
 * against its siblings — so they are per-child layout props the wire does not carry yet. Declaring
 * them keeps the surface complete and makes an unsupported call fail loudly at the call site
 * instead of laying out wrong.
 */
interface BoxScope {
    fun Modifier.align(alignment: Alignment): Modifier

    fun Modifier.matchParentSize(): Modifier
}

interface ColumnScope {
    fun Modifier.align(alignment: Alignment.Horizontal): Modifier

    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

interface RowScope {
    fun Modifier.align(alignment: Alignment.Vertical): Modifier

    fun Modifier.weight(weight: Float, fill: Boolean = true): Modifier
}

internal object GuestBoxScope : BoxScope {
    override fun Modifier.align(alignment: Alignment): Modifier =
        throw UnsupportedInGuestException("BoxScope.align")

    override fun Modifier.matchParentSize(): Modifier =
        throw UnsupportedInGuestException("BoxScope.matchParentSize")
}

internal object GuestColumnScope : ColumnScope {
    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier =
        throw UnsupportedInGuestException("ColumnScope.align")

    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier =
        throw UnsupportedInGuestException("ColumnScope.weight")
}

internal object GuestRowScope : RowScope {
    override fun Modifier.align(alignment: Alignment.Vertical): Modifier =
        throw UnsupportedInGuestException("RowScope.align")

    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier =
        throw UnsupportedInGuestException("RowScope.weight")
}
