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

    /** The first frame only. Most screens compose once and never change. */
    fun runFrame(screen: String): Mutations = runFrames(screen).first()

    /**
     * Composes [screen], then drives one more frame per entry in [flags], setting the guest's flag
     * to that value before each.
     *
     * A single frame cannot tell a prop that was never set from one that was set and then taken
     * away: at mount both look like a default being written. Only a later frame that turns a
     * modifier back off shows whether the reset actually travels, which is why this exists.
     */
    fun runFrames(screen: String, vararg flags: Boolean): List<Mutations> = runBlocking {
        val frames = mutableListOf<Mutations>()
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
            // Every frame ends with one commit, so this is the frame boundary: take what has piled
            // up since the last one and start fresh. String indices are per frame because the
            // records that reference them are.
            js.function("__fh_commit") {
                frames.add(Mutations(mutations.toList(), props.toList(), strings.toList()))
                mutations.clear()
                props.clear()
                strings.clear()
            }

            // Bare QuickJS ships neither a `console` nor timers. kotlinx-coroutines' default
            // dispatcher schedules continuations with setTimeout, and its uncaught-exception
            // path logs through console.error; without both, a real guest failure surfaces as
            // a ReferenceError from inside those paths instead of the original cause. A real
            // host embedding this guest needs to supply the same globals.
            //
            // setTimeout here runs its callback immediately, in-stack, instead of deferring it
            // to a later turn of an event loop — there is no queue, no delay, no ordering
            // relative to other timers. That is sufficient for composing exactly one frame and
            // returning, which is all this harness does. It is NOT a real timer: a later task
            // that drives multiple frames, or whose correctness depends on timer/microtask
            // ordering, needs a real queue here instead of this shim.
            //
            // console output from the guest is silently discarded. A test that wants to assert
            // on a guest-side console.warn/error needs to capture it here rather than relying
            // on this no-op.
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
                // The recomposer waits for a frame between compositions, so a state change alone
                // changes nothing until __frame() releases one.
                flags.forEach { value ->
                    js.evaluate<Any?>("globalThis.__setFlag($value); globalThis.__frame();")
                }
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

        check(frames.isNotEmpty()) { "the guest produced no commit for screen '$screen'" }
        frames
    }
}
