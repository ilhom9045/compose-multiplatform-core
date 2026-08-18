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

package sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ordinary Compose. Nothing here knows about the guest.
 *
 * This file is compiled twice from one place: by `guest-shim` against the shim, and by
 * `guest-shim-check` against the real Compose in this tree. That is what makes "the shim is a
 * drop-in replacement" a claim the compiler checks rather than one that has to be believed.
 *
 * Anything that compiles here and not there — a parameter the shim dropped, a modifier it has not
 * copied — turns into a build failure instead of a screen that renders slightly wrong.
 */
@Composable
fun App() {
    var on by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Red)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "hi",
            Modifier.padding(4.dp).padding(2.dp),
            color = Color.White,
            fontSize = 18.sp,
        )
        Box(
            Modifier.size(24.dp)
                .clip(CircleShape)
                .background(if (on) Color.Green else Color.Blue)
                .clickable { on = !on },
            contentAlignment = Alignment.Center,
        ) {}
    }
}
