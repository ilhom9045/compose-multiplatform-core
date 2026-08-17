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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.guestshim.NodeType
import androidx.guestshim.PropKey
import androidx.guestshim.UnsupportedInGuestException
import androidx.guestshim.WireId
import androidx.guestshim.emitNode
import androidx.guestshim.sendInt
import androidx.guestshim.sendProps
import androidx.guestshim.toProps

/**
 * Signatures taken from compose/foundation/foundation-layout/src/commonMain/kotlin/androidx/compose/
 * foundation/layout/{Box,Column,Row}.kt.
 *
 * This is the half of the API the copy rule does not reach. Upstream these emit a `Layout` and
 * measure their children; here each is one host node carrying its parameters as props, and the host
 * renders the real upstream composable. Every component is its own node rather than a composition of
 * primitives, so ripple, animation and theming stay native and cost no per-frame wire traffic.
 *
 * `propagateMinConstraints` is a measure-pass instruction with no meaning on this side of the wire,
 * so it throws when set rather than being dropped.
 */
@Composable
fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    propagateMinConstraints: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    if (propagateMinConstraints) {
        throw UnsupportedInGuestException("Box(propagateMinConstraints = true)")
    }
    emitNode(
        nodeTypeId = NodeType.Box,
        content = { GuestBoxScope.content() },
    ) {
        set(modifier) { sendProps(it.toProps()) }
        set(contentAlignment) { sendInt(PropKey.ContentAlignment, WireId.of(it)) }
    }
}

@Composable
fun Column(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    emitNode(
        nodeTypeId = NodeType.Column,
        content = { GuestColumnScope.content() },
    ) {
        set(modifier) { sendProps(it.toProps()) }
        set(verticalArrangement) { sendInt(PropKey.VerticalArrangement, WireId.of(it)) }
        set(horizontalAlignment) { sendInt(PropKey.HorizontalAlignment, WireId.of(it)) }
    }
}

@Composable
fun Row(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    content: @Composable RowScope.() -> Unit,
) {
    emitNode(
        nodeTypeId = NodeType.Row,
        content = { GuestRowScope.content() },
    ) {
        set(modifier) { sendProps(it.toProps()) }
        set(horizontalArrangement) { sendInt(PropKey.HorizontalArrangement, WireId.of(it)) }
        set(verticalAlignment) { sendInt(PropKey.VerticalAlignment, WireId.of(it)) }
    }
}
