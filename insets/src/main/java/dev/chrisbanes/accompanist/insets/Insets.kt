/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("NOTHING_TO_INLINE", "unused")

@file:JvmName("ComposeInsets")
@file:JvmMultifileClass

package dev.chrisbanes.accompanist.insets

import android.os.Build
import android.view.View
import android.view.WindowInsetsAnimation
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Providers
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticAmbientOf
import androidx.compose.ui.platform.ViewAmbient
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import android.view.WindowInsets as WindowInsetsPlatform

/**
 * Main holder of our inset values.
 */
@Stable
class WindowInsets {
    /**
     * Inset values which match [WindowInsetsCompat.Type.systemBars]
     */
    val systemBars = Insets()

    /**
     * Inset values which match [WindowInsetsCompat.Type.systemGestures]
     */
    val systemGestures = Insets()

    /**
     * Inset values which match [WindowInsetsCompat.Type.navigationBars]
     */
    val navigationBars = Insets()

    /**
     * Inset values which match [WindowInsetsCompat.Type.statusBars]
     */
    val statusBars = Insets()

    /**
     * Inset values which match [WindowInsetsCompat.Type.ime]
     */
    val ime = Insets()
}

@Stable
class Insets {
    /**
     * The left dimension of these insets in pixels.
     */
    var left by mutableStateOf(0)
        internal set

    /**
     * The top dimension of these insets in pixels.
     */
    var top by mutableStateOf(0)
        internal set

    /**
     * The right dimension of these insets in pixels.
     */
    var right by mutableStateOf(0)
        internal set

    /**
     * The bottom dimension of these insets in pixels.
     */
    var bottom by mutableStateOf(0)
        internal set

    /**
     * Whether the insets are currently visible.
     */
    var isVisible by mutableStateOf(true)
        internal set

    /**
     * Whether the insets are currently visible.
     */
    var beingAnimated by mutableStateOf(false)
        internal set

    fun copy(
        left: Int = this.left,
        top: Int = this.top,
        right: Int = this.right,
        bottom: Int = this.bottom,
        isVisible: Boolean = this.isVisible,
        beingAnimated: Boolean = this.beingAnimated,
    ): Insets = Insets().apply {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        this.isVisible = isVisible
        this.beingAnimated = beingAnimated
    }

    operator fun minus(other: Insets): Insets = copy(
        left = this@Insets.left - other.left,
        top = this@Insets.top - other.top,
        right = this@Insets.right - other.right,
        bottom = this@Insets.bottom - other.bottom,
    )

    operator fun plus(other: Insets): Insets = copy(
        left = this@Insets.left + other.left,
        top = this@Insets.top + other.top,
        right = this@Insets.right + other.right,
        bottom = this@Insets.bottom + other.bottom,
    )
}

val AmbientWindowInsets = staticAmbientOf<WindowInsets> {
    error("AmbientInsets value not available. Are you using ProvideWindowInsets?")
}

/**
 * Applies any [WindowInsetsCompat] values to [AmbientWindowInsets], which are then available
 * within [content].
 *
 * @param consumeWindowInsets Whether to consume any [WindowInsetsCompat]s which are dispatched to
 * the host view. Defaults to `true`.
 */
@Composable
fun ProvideWindowInsets(
    consumeWindowInsets: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = ViewAmbient.current

    val windowInsets = remember { WindowInsets() }

    DisposableEffect(view) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, wic ->
            windowInsets.systemBars.updateFrom(wic, Type.systemBars())
            windowInsets.systemGestures.updateFrom(wic, Type.systemGestures())
            windowInsets.statusBars.updateFrom(wic, Type.statusBars())
            windowInsets.navigationBars.updateFrom(wic, Type.navigationBars())

            if (!windowInsets.ime.beingAnimated) {
                // If this inset type is being animated, we'll ignored
                windowInsets.ime.updateFrom(wic, Type.ime())
            }

            if (consumeWindowInsets) WindowInsetsCompat.CONSUMED else wic
        }

        // Add an OnAttachStateChangeListener to request an inset pass each time we're attached
        // to the window
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = v.requestApplyInsets()
            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        view.addOnAttachStateChangeListener(attachListener)

        if (view.isAttachedToWindow) {
            // If the view is already attached, we can request an inset pass now
            view.requestApplyInsets()
        }

        if (Build.VERSION.SDK_INT >= 30) {
            view.setWindowInsetsAnimationCallback(
                object : WindowInsetsAnimation.Callback(DISPATCH_MODE_STOP) {
                    override fun onPrepare(animation: WindowInsetsAnimation) {
                        if (animation.typeMask and WindowInsetsPlatform.Type.ime() != 0) {
                            windowInsets.ime.beingAnimated = true
                        }
                        // TODO add rest of types
                    }

                    override fun onProgress(
                        insets: android.view.WindowInsets,
                        runningAnimations: MutableList<WindowInsetsAnimation>
                    ): android.view.WindowInsets {
                        windowInsets.ime.updateFrom(insets, WindowInsetsPlatform.Type.ime())
                        // TODO add rest of types

                        return insets
                    }

                    override fun onEnd(animation: WindowInsetsAnimation) {
                        if (animation.typeMask and WindowInsetsPlatform.Type.ime() != 0) {
                            windowInsets.ime.beingAnimated = false
                        }
                        // TODO add rest of types
                    }
                }
            )
        }

        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
        }
    }

    Providers(AmbientWindowInsets provides windowInsets) {
        content()
    }
}

/**
 * Updates our mutable state backed [Insets] from an Android system insets.
 */
private fun Insets.updateFrom(wic: WindowInsetsCompat, type: Int) {
    val insets = wic.getInsets(type)
    left = insets.left
    top = insets.top
    right = insets.right
    bottom = insets.bottom

    isVisible = wic.isVisible(type)
}

/**
 * Updates our mutable state backed [Insets] from an Android system insets.
 */
@RequiresApi(30)
private fun Insets.updateFrom(windowInsets: WindowInsetsPlatform, type: Int) {
    val insets = windowInsets.getInsets(type)
    left = insets.left
    top = insets.top
    right = insets.right
    bottom = insets.bottom

    isVisible = windowInsets.isVisible(type)
}

internal fun Insets.coerceAtLeastEachDimension(other: Insets): Insets = copy(
    left = left.coerceAtLeast(other.left),
    top = top.coerceAtLeast(other.top),
    right = right.coerceAtLeast(other.top),
    bottom = bottom.coerceAtLeast(other.bottom),
)

enum class HorizontalSide { Left, Right }
enum class VerticalSide { Top, Bottom }
