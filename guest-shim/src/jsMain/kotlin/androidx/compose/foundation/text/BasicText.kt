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

package androidx.compose.foundation.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.guestshim.NodeType
import androidx.guestshim.PropKey
import androidx.guestshim.emitNode
import androidx.guestshim.sendProps
import androidx.guestshim.sendStr
import androidx.guestshim.toProps

/**
 * Signature taken from compose/foundation/foundation/src/commonMain/kotlin/androidx/compose/
 * foundation/text/BasicText.kt, reduced to the parameters the wire carries. The rest of upstream's
 * list — `style`, `onTextLayout`, `overflow`, `softWrap`, `maxLines`, `minLines`, `color` — needs
 * `TextStyle` and `TextLayoutResult` from ui-text, which the guest has not copied.
 *
 * Upstream lays the text out and draws it. Here the string is a prop on a host node, and the host
 * measures and renders with the real component.
 */
@Composable
fun BasicText(text: String, modifier: Modifier = Modifier) {
    emitNode(nodeTypeId = NodeType.Text) {
        set(text) { sendStr(PropKey.Text, it) }
        set(modifier) { sendProps(it.toProps()) }
    }
}
