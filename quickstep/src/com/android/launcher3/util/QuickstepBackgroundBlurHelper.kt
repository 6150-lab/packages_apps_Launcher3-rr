/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.util

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.ColorUtils
import com.android.internal.graphics.drawable.BackgroundBlurDrawable
import com.android.launcher3.Flags.blurOnMoreSurfaces
import com.android.launcher3.R
import com.android.launcher3.dagger.ActivityContextSingleton
import com.android.launcher3.folder.Folder
import com.android.launcher3.graphics.PathWrapper
import com.android.launcher3.util.WindowBlurState.WINDOW_BLUR_STATE
import com.android.launcher3.views.ActivityContext
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.ceil

/**
 * Quickstep implementation of the helper class that creates and updates the blur drawable used
 * for folders and the homescreen popup.
 */
@ActivityContextSingleton
class QuickstepBackgroundBlurHelper
@Inject
constructor(
    private val activityContext: ActivityContext,
    @Named(WINDOW_BLUR_STATE) private val blurState: ListenableRef<Boolean>,
) : BlurBackgroundHelper() {

    private val folderBlurRadius = activityContext.asContext().resources.getDimension(
        R.dimen.folder_blur_radius
    )

    private val cornerRadius = Themes.getDialogCornerRadius(activityContext.asContext())
    private val workspaceBlurRenderNode = RenderNode("workspaceBlur")
    private val workspaceBlurRenderNodeOutline = Outline()
    private val bounds = Rect()
    private val folderBlurDrawable: BackgroundBlurDrawable? by lazy {
        if (!isBlurEnabled()) null
        else
            activityContext.dragLayer.getViewRootImpl()
                .createBackgroundBlurDrawable()?.apply {
                    setBlurRadius(folderBlurRadius.toInt())
                    setVisible(false, false)
                }
    }

    override fun prepareToOpenFolder(folder: Folder) {
        if (!isBlurEnabled()) {
            return
        }

        val folderIcon = folder.folderIcon
        val folderNameVisibility: Int = folderIcon.folderName.visibility
        val isIconVisible = folderIcon.iconVisible

        suppressDock(true)
        folderIcon.setTextVisible(false)
        folderIcon.setIconVisible(false)

        val dragLayer = activityContext.dragLayer
        val canvas =
            workspaceBlurRenderNode.beginRecording(dragLayer.getWidth(), dragLayer.getHeight())
        dragLayer.draw(canvas)
        workspaceBlurRenderNode.endRecording()
        workspaceBlurRenderNode.setPosition(0, 0, dragLayer.getWidth(), dragLayer.getHeight())

        folderIcon.folderName.visibility = folderNameVisibility
        folderIcon.setIconVisible(isIconVisible)
    }

    override fun drawFolderBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        if (!isBlurEnabled()) {
            return
        }

        drawCrossWindowBlur(canvas, pathWrapper, view)
        drawWorkspaceBlur(canvas, pathWrapper?.path, view)
    }

    private fun drawWorkspaceBlur(canvas: Canvas, path: Path?, view: View) {
        if (!workspaceBlurRenderNode.hasDisplayList()) {
            return
        }
        workspaceBlurRenderNode.translationX = -view.left.toFloat()
        workspaceBlurRenderNode.translationY = -view.top.toFloat()

        if (path != null && !path.isEmpty) {
            workspaceBlurRenderNodeOutline.setPath(path)
            workspaceBlurRenderNodeOutline.mPath.offset(view.left.toFloat(), view.top.toFloat())
        } else {
            workspaceBlurRenderNodeOutline.setRoundRect(
                view.left,
                view.top,
                view.right,
                view.bottom,
                cornerRadius,
            )
        }
        workspaceBlurRenderNode.setOutline(workspaceBlurRenderNodeOutline)
        workspaceBlurRenderNode.setClipToOutline(true)

        workspaceBlurRenderNode.setRenderEffect(
            RenderEffect.createBlurEffect(
                folderBlurRadius,
                folderBlurRadius,
                Shader.TileMode.CLAMP
            )
        )
        canvas.drawRenderNode(workspaceBlurRenderNode)
    }

    private fun drawCrossWindowBlur(canvas: Canvas, pathWrapper: PathWrapper?, view: View) {
        val d = folderBlurDrawable ?: return

        d.setVisible(true, false)
        if (pathWrapper != null) {
            pathWrapper.bounds.roundOut(bounds)
            d.bounds = bounds
            d.setCornerRadius(pathWrapper.cornerRadius)
        } else {
            d.setBounds(0, 0, view.width, view.height)
            d.setCornerRadius(cornerRadius)
        }
        d.draw(canvas)
    }

    override fun folderCloseComplete() {
        if (workspaceBlurRenderNode.hasDisplayList()) {
            workspaceBlurRenderNode.discardDisplayList()
            suppressDock(false)
        }
        folderBlurDrawable?.setVisible(false, false)
    }

    override fun isBlurEnabled(): Boolean {
        return blurOnMoreSurfaces() && blurState.value &&
            RrGlass.isOn(activityContext.asContext(), RrGlass.HOME)
    }

    // Liquid Glass long-press menus: the home screen under the popup, blurred in-app, over one
    // cross-window blur region per piece for the wallpaper.
    private val popupBlurRenderNode = RenderNode("popupBlur")
    private val popupBlurDrawables = ArrayList<BackgroundBlurDrawable>()
    private val popupPath = Path()

    override fun isPopupBlurEnabled(): Boolean {
        return blurState.value && RrGlass.isOn(activityContext.asContext(), RrGlass.MENUS)
    }

    override fun prepareToOpenPopup() {
        if (!isPopupBlurEnabled()) {
            return
        }
        suppressDock(true)
        val dragLayer = activityContext.dragLayer
        val canvas = popupBlurRenderNode.beginRecording(dragLayer.width, dragLayer.height)
        dragLayer.draw(canvas)
        popupBlurRenderNode.endRecording()
        popupBlurRenderNode.setPosition(0, 0, dragLayer.width, dragLayer.height)
        popupBlurRenderNode.setRenderEffect(
            RenderEffect.createBlurEffect(folderBlurRadius, folderBlurRadius, Shader.TileMode.CLAMP)
        )
    }

    override fun drawPopupBlur(
        canvas: Canvas,
        view: View,
        pieces: List<RectF>,
        radii: List<FloatArray>,
    ) {
        if (!isPopupBlurEnabled()) {
            return
        }
        val alpha = (view.alpha * 255).toInt()
        for (i in pieces.indices) {
            val d = popupBlurDrawable(i) ?: break
            val r = pieces[i]
            val c = radii[i]
            d.setVisible(true, false)
            d.alpha = alpha
            d.setBounds(r.left.toInt(), r.top.toInt(), ceil(r.right).toInt(), ceil(r.bottom).toInt())
            // Radii come top-left, top-right, bottom-right, bottom-left.
            d.setCornerRadius(c[0], c[2], c[6], c[4])
            d.draw(canvas)
        }
        for (i in pieces.size until popupBlurDrawables.size) {
            popupBlurDrawables[i].setVisible(false, false)
        }
        if (!popupBlurRenderNode.hasDisplayList()) {
            return
        }
        popupPath.reset()
        for (i in pieces.indices) {
            popupPath.addRoundRect(pieces[i], radii[i], Path.Direction.CW)
        }
        popupBlurRenderNode.translationX = -view.left.toFloat()
        popupBlurRenderNode.translationY = -view.top.toFloat()
        val count = canvas.save()
        canvas.clipPath(popupPath)
        canvas.drawRenderNode(popupBlurRenderNode)
        canvas.restoreToCount(count)
    }

    private fun popupBlurDrawable(i: Int): BackgroundBlurDrawable? {
        while (popupBlurDrawables.size <= i) {
            val d =
                activityContext.dragLayer.getViewRootImpl()?.createBackgroundBlurDrawable()
                    ?: return null
            d.setBlurRadius(folderBlurRadius.toInt())
            popupBlurDrawables.add(d)
        }
        return popupBlurDrawables[i]
    }

    override fun popupCloseComplete() {
        if (popupBlurRenderNode.hasDisplayList()) {
            popupBlurRenderNode.discardDisplayList()
            suppressDock(false)
        }
        popupBlurDrawables.forEach { it.setVisible(false, false) }
    }

    // Liquid Glass dock: the hotseat sits right over the wallpaper, so a cross-window blur region
    // is the whole effect.
    private val dockTint = ColorUtils.setAlphaComponent(
        activityContext.asContext().getColor(R.color.materialColorSurfaceContainer), 0x47
    )
    private var dockBlurDrawable: BackgroundBlurDrawable? = null
    private val dockTintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dockTint }
    // Popup and folder snapshots reference the hotseat's live display list, which would draw
    // the dock's blur region twice; while one is open the dock keeps only its tint.
    private var dockSuppressed = 0

    private fun suppressDock(suppress: Boolean) {
        dockSuppressed = maxOf(0, dockSuppressed + if (suppress) 1 else -1)
        if (dockSuppressed > 0) {
            dockBlurDrawable?.setVisible(false, false)
        }
        activityContext.dragLayer.findViewById<View>(R.id.hotseat)?.invalidate()
    }

    override fun isDockBlurEnabled(): Boolean {
        return blurState.value && RrGlass.isOn(activityContext.asContext(), RrGlass.HOME)
    }

    override fun drawDockBlur(canvas: Canvas, rect: RectF, radius: Float, alpha: Float) {
        if (!isDockBlurEnabled()) {
            hideDockBlur()
            return
        }
        if (dockSuppressed > 0) {
            canvas.drawRoundRect(rect, radius, radius, dockTintPaint)
            return
        }
        val d =
            dockBlurDrawable
                ?: activityContext.dragLayer.getViewRootImpl()?.createBackgroundBlurDrawable()
                    ?.also {
                        it.setBlurRadius(folderBlurRadius.toInt())
                        it.setColor(dockTint)
                        dockBlurDrawable = it
                    }
                ?: return
        d.setVisible(true, false)
        d.alpha = (alpha * 255).toInt()
        d.setBounds(rect.left.toInt(), rect.top.toInt(), ceil(rect.right).toInt(),
            ceil(rect.bottom).toInt())
        d.setCornerRadius(radius)
        d.draw(canvas)
    }

    override fun setDockAlpha(alpha: Float) {
        dockBlurDrawable?.alpha = (alpha * 255).toInt()
    }

    override fun hideDockBlur() {
        dockBlurDrawable?.setVisible(false, false)
    }
}
