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
import kotlin.test.assertTrue

class HarnessTest {

    @Test
    fun `an empty screen composes without emitting nodes`() {
        val frame = GuestHarness.runFrame("empty")
        assertTrue(
            frame.records().none { it[0] == MutationType.Create },
            "empty screen should create no nodes, got ${frame.records()}",
        )
    }

    /**
     * Proves the bridge really executes: the empty-screen test above is satisfied by a harness
     * that never runs any JavaScript at all, since zero mutations is also what "nothing
     * happened" looks like. This one composes a screen that emits exactly one Box and checks
     * for the specific Create/Insert records that only a real composition through
     * GuestApplier -> __fh_mut can produce. If `GuestHarness.runFrame` were stubbed to return
     * empty `Mutations` unconditionally, this test goes red.
     */
    @Test
    fun `a probe screen creates and inserts a single Box node`() {
        val frame = GuestHarness.runFrame("probe")
        val records = frame.records()

        assertTrue(
            records.any { it[0] == MutationType.Create && it[6] == NodeType.Box },
            "expected a Create record for a Box node, got $records",
        )
        assertTrue(
            records.any { it[0] == MutationType.Insert },
            "expected an Insert record, got $records",
        )
    }
}
