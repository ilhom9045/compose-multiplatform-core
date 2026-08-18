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

plugins {
    id("AndroidXComposePlugin")
    id("kotlin-multiplatform")
}

/**
 * Compiles guest-shim/sample against the real Compose in this tree.
 *
 * There is no source of its own here on purpose: the same directory is compiled by `guest-shim`
 * against the shim. Two compilations, one file — which is the only way "the shim is a drop-in
 * replacement" stays true. A parameter the shim dropped or a signature that drifted stops being a
 * screen that renders slightly wrong and becomes a build failure.
 *
 * The dependencies are project references, not artifacts: the oracle is the very upstream the shim
 * was copied from, in the state this tree has it.
 */
kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            kotlin.srcDir("../guest-shim/sample")
            dependencies {
                implementation(project(":compose:foundation:foundation"))
                implementation(project(":compose:foundation:foundation-layout"))
                implementation(project(":compose:material3:material3"))
                implementation(project(":compose:runtime:runtime"))
                implementation(project(":compose:ui:ui"))
                implementation(project(":compose:ui:ui-graphics"))
                implementation(project(":compose:ui:ui-unit"))
            }
        }
    }
}
