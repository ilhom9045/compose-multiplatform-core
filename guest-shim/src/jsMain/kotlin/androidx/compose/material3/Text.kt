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

package androidx.compose.material3

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.guestshim.NodeType
import androidx.guestshim.PropKey
import androidx.guestshim.UnsupportedInGuestException
import androidx.guestshim.emitNode
import androidx.guestshim.sendFloat
import androidx.guestshim.sendInt
import androidx.guestshim.sendProps
import androidx.guestshim.sendStr
import androidx.guestshim.toProps

/**
 * Signature taken from
 * compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/Text.kt, reduced to
 * the parameters this wire carries. Upstream also takes `fontStyle`, `fontWeight`, `fontFamily`,
 * `letterSpacing`, `textDecoration`, `textAlign`, `lineHeight`, `overflow`, `softWrap`, `maxLines`,
 * `minLines`, `onTextLayout` and `style`; the host has prop keys for most of those, and they can be
 * added one at a time. `style` and `onTextLayout` cannot: they need `TextStyle` and
 * `TextLayoutResult` from ui-text.
 *
 * Upstream resolves colour and size against the ambient `TextStyle` and lays the text out. Here they
 * are props on a host node, and the host builds the real `TextStyle` and renders material3's `Text`.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    emitNode(nodeTypeId = NodeType.Text) {
        set(text) { sendStr(PropKey.Text, it) }
        set(modifier) { sendProps(it.toProps()) }

        // NaN is the host's "unspecified", so this can be written every time and a size that goes
        // back to the default resets properly.
        set(fontSize) { sendFloat(PropKey.FontSize, it.toSpOrNaN()) }

        // Colour cannot. The host reads an *absent* key as Color.Unspecified, and every Int is a
        // real colour — 0 is transparent black, not "no colour". So a Text that has been given a
        // colour cannot go back to the theme default: this guard is the trap that Updater.set
        // usually punishes, kept deliberately because the wire has no value to send instead.
        // Fixing it means a sentinel on both sides, not a change here.
        set(color) { if (it.isSpecified) sendInt(PropKey.Color, it.toArgb()) }
    }
}

/** The host reads font size as a float in sp; `em` would need the parent size it does not send. */
private fun TextUnit.toSpOrNaN(): Float =
    when {
        !isSpecified -> Float.NaN
        isSp -> value
        else -> throw UnsupportedInGuestException("fontSize in $type")
    }
