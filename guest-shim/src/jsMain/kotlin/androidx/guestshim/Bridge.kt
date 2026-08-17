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

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.Updater
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

external fun __fh_mut(
    type: Int, nodeId: Int, parentId: Int,
    index: Int, fromIndex: Int, toIndex: Int, nodeTypeId: Int,
)

external fun __fh_prop(nodeId: Int, keyId: Int, valueType: Int, valueBits: Int)

external fun __fh_str(nodeId: Int, keyId: Int, value: String)

external fun __fh_commit()

/** A guest-side node. Holds only what prop diffing needs; the host owns the real tree. */
class VNode(val id: Int, val nodeTypeId: Int) {
    internal val intCache = HashMap<Int, Int>()
    internal val floatCache = HashMap<Int, Float>()
    internal val strCache = HashMap<Int, String>()
    internal val children = mutableListOf<VNode>()
}

fun VNode.sendInt(keyId: Int, value: Int) {
    if (intCache[keyId] == value) return
    intCache[keyId] = value
    __fh_prop(id, keyId, PropValueType.Int, value)
}

fun VNode.sendFloat(keyId: Int, value: Float) {
    val bits = value.toBits()
    if (floatCache[keyId]?.toBits() == bits) return
    floatCache[keyId] = value
    __fh_prop(id, keyId, PropValueType.Float, bits)
}

fun VNode.sendBool(keyId: Int, value: Boolean) {
    val bits = if (value) 1 else 0
    if (intCache[keyId] == bits) return
    intCache[keyId] = bits
    __fh_prop(id, keyId, PropValueType.Bool, bits)
}

fun VNode.sendStr(keyId: Int, value: String) {
    if (strCache[keyId] == value) return
    strCache[keyId] = value
    __fh_str(id, keyId, value)
}

class GuestApplier(root: VNode) : AbstractApplier<VNode>(root) {
    private var nextId = 1

    override fun insertTopDown(index: Int, instance: VNode) {}

    override fun insertBottomUp(index: Int, instance: VNode) {
        current.children.add(index, instance)
        __fh_mut(MutationType.Insert, instance.id, current.id, index, -1, -1, -1)
    }

    override fun remove(index: Int, count: Int) {
        repeat(count) {
            val node = current.children.removeAt(index)
            __fh_mut(MutationType.Remove, node.id, current.id, index, -1, -1, -1)
            // Ids are never reused and movable content is not supported, so a removed node
            // is gone for good. The host frees the subtree from its own child lists.
            __fh_mut(MutationType.Delete, node.id, -1, -1, -1, -1, -1)
        }
    }

    override fun move(from: Int, to: Int, count: Int) {
        val nodes = current.children.subList(from, from + count).toList()
        current.children.subList(from, from + count).clear()
        current.children.addAll(to, nodes)
        __fh_mut(MutationType.Move, -1, current.id, -1, from, to, -1)
    }

    override fun onClear() {
        current.children.clear()
    }

    override fun onEndChanges() {
        __fh_commit()
    }

    fun createNode(nodeTypeId: Int): VNode {
        val node = VNode(id = nextId++, nodeTypeId = nodeTypeId)
        __fh_mut(MutationType.Create, node.id, -1, -1, -1, -1, nodeTypeId)
        return node
    }
}

@Composable
fun emitNode(
    nodeTypeId: Int,
    content: @Composable () -> Unit = {},
    update: Updater<VNode>.() -> Unit,
) {
    val applier = currentComposer.applier as GuestApplier
    ComposeNode<VNode, GuestApplier>(
        factory = { applier.createNode(nodeTypeId) },
        update = update,
        content = content,
    )
}

/** Starts the Compose runtime in the guest and composes [content]. */
object GuestRuntime {
    private var clock: BroadcastFrameClock? = null

    fun start(content: @Composable () -> Unit) {
        val clock = BroadcastFrameClock().also { this.clock = it }
        val scope = CoroutineScope(clock + Job())
        val recomposer = Recomposer(scope.coroutineContext)
        val composition = Composition(GuestApplier(VNode(0, NodeType.Root)), recomposer)

        composition.setContent(content)
        Snapshot.registerGlobalWriteObserver { Snapshot.sendApplyNotifications() }
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
    }

    /**
     * Drives one recomposition, for a host that wants to control when a frame happens.
     *
     * The recomposer waits on [BroadcastFrameClock.withFrameNanos] between compositions, so a state
     * change alone produces nothing: without a frame the guest stays silent, which is what keeps an
     * idle screen off the wire. Everything a real frame would carry is in the batch that follows.
     */
    fun frame(timeNanos: Long = 0L) {
        clock?.sendFrame(timeNanos)
    }
}
