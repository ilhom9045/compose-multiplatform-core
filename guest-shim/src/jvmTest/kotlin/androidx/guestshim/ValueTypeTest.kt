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

class ValueTypeTest {

    @Test
    fun `Color Red reaches the host as opaque ARGB red`() {
        val frame = GuestHarness.runFrame("color")
        val backgroundProps = frame.propRecords().filter { it[1] == PropKey.BackgroundColor }
        assertEquals(1, backgroundProps.size, "expected one background prop, got $backgroundProps")
        assertEquals(0xFFFF0000.toInt(), backgroundProps.single()[3])
    }
}
