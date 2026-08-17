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

import com.dokar.quickjs.ModuleContent
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.moduleLoader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Everything one composition frame pushed across the bridge. */
data class Mutations(
    val mutations: List<Int>,
    val props: List<Int>,
    val strings: List<String>,
) {
    /** Mutation records are 7 ints wide: [type, nodeId, parentId, index, from, to, nodeTypeId]. */
    fun records(): List<List<Int>> = mutations.chunked(7)

    /** Prop records are 4 ints wide: [nodeId, keyId, valueType, valueBits]. */
    fun propRecords(): List<List<Int>> = props.chunked(4)
}

object GuestHarness {

    private val bundleDir: File
        get() = File(
            System.getProperty("guestshim.guest.dir")
                ?: error("guestshim.guest.dir is unset; the Gradle test task should set it"),
        )

    /** QuickJS may ask by bare name or by the relative specifier written in the source. */
    private fun resolve(name: String): File? {
        val bare = name.removePrefix("./")
        val candidates = listOf(name, bare, "$name.mjs", "$bare.mjs")
        return candidates.map { File(bundleDir, it) }.firstOrNull { it.isFile }
    }

    fun runFrame(screen: String): Mutations = runBlocking {
        val mutations = mutableListOf<Int>()
        val props = mutableListOf<Int>()
        val strings = mutableListOf<String>()
        val missing = mutableListOf<String>()

        val loader = moduleLoader {
            load { name ->
                val file = resolve(name)
                if (file == null) {
                    missing.add(name)
                    null
                } else {
                    ModuleContent.Source(file.readText())
                }
            }
        }

        // QuickJs.create() takes the module loader at construction time (there is no
        // instance-level `moduleLoader { }` builder), and always requires a jobDispatcher —
        // there is no zero-arg overload.
        QuickJs.create(jobDispatcher = Dispatchers.Unconfined, moduleLoader = loader).use { js ->
            js.function("__fh_mut") { args ->
                repeat(7) { mutations.add((args[it] as Number).toInt()) }
            }
            js.function("__fh_prop") { args ->
                repeat(4) { props.add((args[it] as Number).toInt()) }
            }
            js.function("__fh_str") { args ->
                props.add((args[0] as Number).toInt())
                props.add((args[1] as Number).toInt())
                props.add(PropValueType.String)
                props.add(strings.size)
                strings.add(args[2] as String)
            }
            js.function("__fh_commit") { }

            // Bare QuickJS ships neither a `console` nor timers. kotlinx-coroutines' default
            // dispatcher schedules continuations with setTimeout, and its uncaught-exception
            // path logs through console.error; without both, a real guest failure surfaces as
            // a ReferenceError from inside those paths instead of the original cause. A real
            // host embedding this guest needs to supply the same globals.
            js.evaluate<Any?>(
                """
                globalThis.console = { log(){}, warn(){}, error(){}, info(){}, debug(){} };
                globalThis.setTimeout = function (fn) { fn(); return 0; };
                globalThis.clearTimeout = function () {};
                """.trimIndent(),
            )

            try {
                val entry = File(bundleDir, "guest-shim.mjs")
                require(entry.exists()) { "guest bundle entry not found: ${entry.absolutePath}" }
                js.evaluate<Any?>(
                    code = entry.readText(),
                    filename = "guest-shim.mjs",
                    asModule = true,
                )
                js.evaluate<Any?>("globalThis.__runFrame('$screen');")
            } catch (e: Throwable) {
                if (missing.isNotEmpty()) {
                    throw AssertionError(
                        "the guest bundle asked for modules the loader could not resolve: " +
                            "$missing (looked in $bundleDir)",
                        e,
                    )
                }
                throw e
            }
        }

        Mutations(mutations, props, strings)
    }
}
