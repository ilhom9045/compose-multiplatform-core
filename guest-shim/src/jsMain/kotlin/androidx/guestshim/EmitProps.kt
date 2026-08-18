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

/**
 * Modifier props are written **without** the usual unchanged-value dedup.
 *
 * The host rebuilds a node's modifier order from the batch: the first modifier prop it sees clears
 * the whole order, and the order is then whatever that batch carried. So a batch holding only the
 * colour that changed would silently drop padding, clip and fill from the chain — the values are
 * still in its map, but nothing walks them any more.
 *
 * Skipping is safe at a coarser grain instead: `sendProps` is called from `Updater.set(modifier)`,
 * which fires only when the chain itself changed, so an idle node sends nothing at all.
 */
private fun VNode.writeInt(keyId: Int, value: Int) {
    intCache[keyId] = value
    __fh_prop(id, keyId, PropValueType.Int, value)
}

private fun VNode.writeFloat(keyId: Int, value: Float) {
    floatCache[keyId] = value
    __fh_prop(id, keyId, PropValueType.Float, value.toBits())
}

private fun VNode.sendGroup(group: Int, props: ShimProps) {
    when (group) {
        PropKey.BackgroundColor -> writeInt(PropKey.BackgroundColor, props.backgroundColor)
        PropKey.PaddingTop -> {
            writeFloat(PropKey.PaddingStart, props.paddingStart)
            writeFloat(PropKey.PaddingTop, props.paddingTop)
            writeFloat(PropKey.PaddingEnd, props.paddingEnd)
            writeFloat(PropKey.PaddingBottom, props.paddingBottom)
        }
        PropKey.Width -> writeFloat(PropKey.Width, props.width)
        PropKey.Height -> writeFloat(PropKey.Height, props.height)
        PropKey.FillMaxWidth -> {
            writeFloat(PropKey.FillMaxWidth, props.fillMaxWidth)
            writeFloat(PropKey.FillMaxHeight, props.fillMaxHeight)
        }
        // Deliberately absent from AllGroups: there is no value that means "no handler", so a
        // removed clickable travels as this prop not being in the batch at all.
        PropKey.OnClick -> props.onClick?.let { sendCallback(PropKey.OnClick, it) }
        PropKey.ClipShapeType -> {
            writeInt(PropKey.ClipShapeType, props.clipShapeType)
            writeFloat(PropKey.CornerRadiusTopStart, props.cornerTopStart)
            writeFloat(PropKey.CornerRadiusTopEnd, props.cornerTopEnd)
            writeFloat(PropKey.CornerRadiusBottomEnd, props.cornerBottomEnd)
            writeFloat(PropKey.CornerRadiusBottomStart, props.cornerBottomStart)
        }
    }
}
