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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "layout" screen draws `Text("hi", …, color = Color.White, fontSize = 18.sp)`.
 *
 * Upstream resolves both against the ambient TextStyle and lays the text out. Here they are props,
 * and the host builds the real TextStyle around them.
 */
class TextTest {

    private val frame = GuestHarness.runFrame("layout")

    private val textNode: Int
        get() = frame.records().single {
            it[0] == MutationType.Create && it[6] == NodeType.Text
        }[1]

    private fun prop(keyId: Int): List<Int> =
        frame.propRecords().filter { it[0] == textNode && it[1] == keyId }.map { it[3] }

    @Test
    fun `colour crosses as ARGB`() {
        assertEquals(listOf(0xFFFFFFFF.toInt()), prop(PropKey.Color))
    }

    @Test
    fun `font size crosses as a float in sp`() {
        assertEquals(listOf(18f), prop(PropKey.FontSize).map { Float.fromBits(it) })
    }

    @Test
    fun `a text without a size says so, rather than saying nothing`() {
        // NaN is the host's "unspecified", so an unsized Text still writes the key and a size that
        // goes back to the default can reset. Colour has no such value and is written only when
        // set — see Text.kt.
        val other = GuestHarness.runFrame("click")
        val node = other.records().single {
            it[0] == MutationType.Create && it[6] == NodeType.Text
        }[1]
        val sizes = other.propRecords()
            .filter { it[0] == node && it[1] == PropKey.FontSize }
            .map { Float.fromBits(it[3]) }
        assertEquals(1, sizes.size)
        assertEquals(true, sizes.single().isNaN())
    }
}
