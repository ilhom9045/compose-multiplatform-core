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

/** Component type, sent as an Int. The C bridge never interprets these. */
object NodeType {
    const val Root = 0
    const val Text = 1
    const val Column = 2
    const val Row = 3
    const val Box = 4
}

/** Prop keys, sent as Ints. */
object PropKey {
    const val PaddingTop = 10
    const val PaddingBottom = 11
    const val PaddingStart = 12
    const val PaddingEnd = 13
    const val Width = 14
    const val Height = 15
    const val BackgroundColor = 16
    const val FillMaxWidth = 20
    const val FillMaxHeight = 21
    const val HorizontalArrangement = 60
    const val VerticalArrangement = 61
    const val HorizontalAlignment = 62
    const val VerticalAlignment = 63
    const val ContentAlignment = 87
}

/** How the host should read a prop's Int bits. */
object PropValueType {
    const val Int = 0
    const val Float = 1
    const val String = 2
    const val Bool = 3
    const val Callback = 4
}

/** Mutation kinds, sent as the first Int of every 7-Int mutation record. */
object MutationType {
    const val Create = 0
    const val Insert = 1
    const val Remove = 2
    const val Move = 3
    const val Delete = 4
}

/** Thrown by declarations that exist for source compatibility but have no wire mapping. */
class UnsupportedInGuestException(call: String) :
    UnsupportedOperationException("$call is not supported in the guest runtime")
