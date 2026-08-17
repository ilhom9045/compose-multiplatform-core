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

kotlin {
    jvm()
    js {
        outputModuleName = "guest-shim"
        nodejs()
        binaries.executable()
        compilerOptions {
            // Do not add -Xes-long-as-bigint here. es2015 alone leaves Long as the two-Int
            // emulation, which is correct; the flag that changes it hangs the guest, because
            // SnapshotIdSet needs 64-bit wrap-around that arbitrary-precision BigInt lacks.
            target.set("es2015")
        }
    }

    sourceSets {
        // AndroidXComposePlugin attaches the Compose compiler plugin to every compilation in
        // this module, and that plugin refuses to run without compose-runtime on the classpath.
        commonMain.dependencies {
            implementation(project(":compose:runtime:runtime"))
            // Needed by the verbatim upstream copies under androidx/compose/, not by the guest
            // itself: annotations are erased and emit no code, and colorspace/Connector.kt caches
            // its connectors in an IntObjectMap. Same coordinates ui-graphics uses in commonMain.
            implementation("androidx.annotation:annotation:1.9.1")
            implementation("androidx.collection:collection:1.5.0")
        }

        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }

        jsMain.dependencies {
            implementation(libs.kotlinCoroutinesCore)
            // Same reason ui-graphics pins it for nonJvm: the klib resolver needs the JetBrains
            // repackaging of androidx.collection until KT-61096 lands.
            implementation("org.jetbrains.compose.collection-internal:collection:1.10.0")
        }

        jvmTest.dependencies {
            implementation(libs.kotlinTestJunit)
            // Substituted by includeBuild("quickjs-kt") in settings-fork.gradle.
            implementation("io.github.dokar3:quickjs-kt:1.0.11")
        }
    }
}

/** Where the compiled guest bundle lands: one .mjs per module, plus source maps. */
val guestBundleDir = layout.buildDirectory
    .dir("compileSync/js/main/productionExecutable/kotlin")

tasks.withType<Test>().configureEach {
    dependsOn("compileProductionExecutableKotlinJs")
    systemProperty("guestshim.guest.dir", guestBundleDir.get().asFile.absolutePath)
}
