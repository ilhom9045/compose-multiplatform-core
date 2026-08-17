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

package androidx.guestshim

/**
 * The canonical group key of every prop group the guest can emit, in the order used for groups the
 * chain did not touch. Mirrors the host's `NativeRenderTree.modifierCanonical`.
 */
private val AllGroups =
    intArrayOf(
        PropKey.BackgroundColor,
        PropKey.PaddingTop,
        PropKey.Width,
        PropKey.Height,
        PropKey.FillMaxWidth,
        PropKey.ClipShapeType,
    )

/**
 * Writes a whole collected [ShimProps] onto the node.
 *
 * **Order first.** The host rebuilds the chain by walking the order props arrived in, so groups the
 * chain actually used go out in the author's order; only then do the untouched ones follow. Sending
 * a fixed order would make `padding().background()` and `background().padding()` identical on the
 * wire, and they are not the same picture.
 *
 * **Then everything, touched or not.** A group left at its default is a *reset* — the modifier that
 * set it was removed on this recomposition — and skipping it would leave the host holding the old
 * value with nothing to correct it. `sendInt`/`sendFloat` already drop writes that did not change,
 * so an untouched group costs nothing after the first frame.
 */
fun VNode.sendProps(props: ShimProps) {
    props.order.forEach { sendGroup(it, props) }
    AllGroups.forEach { if (it !in props.order) sendGroup(it, props) }
}

private fun VNode.sendGroup(group: Int, props: ShimProps) {
    when (group) {
        PropKey.BackgroundColor -> sendInt(PropKey.BackgroundColor, props.backgroundColor)
        PropKey.PaddingTop -> {
            sendFloat(PropKey.PaddingStart, props.paddingStart)
            sendFloat(PropKey.PaddingTop, props.paddingTop)
            sendFloat(PropKey.PaddingEnd, props.paddingEnd)
            sendFloat(PropKey.PaddingBottom, props.paddingBottom)
        }
        PropKey.Width -> sendFloat(PropKey.Width, props.width)
        PropKey.Height -> sendFloat(PropKey.Height, props.height)
        PropKey.FillMaxWidth -> {
            sendFloat(PropKey.FillMaxWidth, props.fillMaxWidth)
            sendFloat(PropKey.FillMaxHeight, props.fillMaxHeight)
        }
        PropKey.ClipShapeType -> {
            sendInt(PropKey.ClipShapeType, props.clipShapeType)
            sendFloat(PropKey.CornerRadiusTopStart, props.cornerTopStart)
            sendFloat(PropKey.CornerRadiusTopEnd, props.cornerTopEnd)
            sendFloat(PropKey.CornerRadiusBottomEnd, props.cornerBottomEnd)
            sendFloat(PropKey.CornerRadiusBottomStart, props.cornerBottomStart)
        }
    }
}
