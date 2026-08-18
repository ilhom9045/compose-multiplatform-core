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

import androidx.compose.ui.Modifier
import androidx.guestshim.PropElement
import androidx.guestshim.PropKey
import androidx.guestshim.ShimProps

/**
 * Calls [onClick] when the content is clicked.
 *
 * Signature taken from
 * compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/foundation/Clickable.kt,
 * minus `onClickLabel` and `role`: both are semantics, which the guest has not copied.
 *
 * This is the first prop that travels in both directions. The lambda itself never crosses — the
 * host is told only that a handler exists, registers a stub, and calls back with the node id and
 * prop key that fired. The guest looks the handler up and runs it, and whatever state it touches
 * shows up on the next frame the host asks for.
 *
 * [enabled] is honoured by not sending the prop at all, which is also how a removed `clickable`
 * travels: the host rebuilds a node's modifier order from each batch, so a batch that stops
 * carrying this prop is what takes the handler off. There is no "no callback" value to send.
 */
fun Modifier.clickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.then(ClickableElement(enabled, onClick))

private data class ClickableElement(val enabled: Boolean, val onClick: () -> Unit) : PropElement {
    override fun applyTo(props: ShimProps) {
        if (!enabled) return
        props.touch(PropKey.OnClick)
        props.onClick = onClick
    }
}
