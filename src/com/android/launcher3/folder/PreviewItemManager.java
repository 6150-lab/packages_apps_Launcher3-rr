/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.folder;

import static com.android.launcher3.BubbleTextView.DISPLAY_FOLDER;
import static com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG;
import static com.android.launcher3.Utilities.dpToPx;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.ENTER_INDEX;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.EXIT_INDEX;
import static com.android.launcher3.folder.ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;
import static com.android.launcher3.folder.FolderIcon.DROP_IN_ANIMATION_DURATION;
import static com.android.launcher3.graphics.AutomatedIconDelegate.newAutomatedIcon;
import static com.android.launcher3.graphics.PreloadIconDelegate.newPendingIcon;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_AUTOMATED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.apppairs.AppPairIcon;
import com.android.launcher3.apppairs.AppPairIconDrawingParams;
import com.android.launcher3.apppairs.AppPairIconGraphic;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dot.NotificationBadgeCounter;
import com.android.launcher3.graphics.AutomatedIconDelegate;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.model.data.AppPairInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Manages the drawing and animations of {@link PreviewItemDrawingParams} for a {@link FolderIcon}.
 */
public class PreviewItemManager {

    private static final String TAG = "PreviewItemManager";

    private static final FloatProperty<PreviewItemManager> CURRENT_PAGE_ITEMS_TRANS_X =
            new FloatProperty<PreviewItemManager>("currentPageItemsTransX") {
                @Override
                public void setValue(PreviewItemManager manager, float v) {
                    manager.mCurrentPageItemsTransX = v;
                    manager.onParamsChanged();
                }

                @Override
                public Float get(PreviewItemManager manager) {
                    return manager.mCurrentPageItemsTransX;
                }
            };

    private final Context mContext;
    private final FolderIcon mIcon;
    @VisibleForTesting
    public final int mIconSize;

    // These variables are all associated with the drawing of the preview; they are stored
    // as member variables for shared usage and to avoid computation on each frame
    private float mIntrinsicIconSize = -1;
    private int mTotalWidth = -1;
    private int mTotalHeight = -1;
    private int mPrevSpanX = -1;
    private int mPrevSpanY = -1;
    private int mPrevTopPadding = -1;
    private boolean mPrevUsesWorkspacePreviewLayout;
    private Drawable mReferenceDrawable = null;

    private int mNumOfPrevItems = 0;

    // These hold the first page preview items
    private ArrayList<PreviewItemDrawingParams> mFirstPageParams = new ArrayList<>();
    // These hold the current page preview items. It is empty if the current page is the first page.
    private ArrayList<PreviewItemDrawingParams> mCurrentPageParams = new ArrayList<>();

    // We clip the preview items during the middle of the animation, so that it does not go outside
    // of the visual shape. We stop clipping at this threshold, since the preview items ultimately
    // do not get cropped in their resting state.
    private final float mClipThreshold;
    private float mCurrentPageItemsTransX = 0;
    private float mPageSlideDistance;
    private boolean mShouldSlideInFirstPage;

    // Super folders: the page of apps a big folder shows, and the swipe to the next one.
    private int mPreviewPage;
    private float mPageOffset;
    private int mNeighborPage = -1;
    private final ArrayList<PreviewItemDrawingParams> mNeighborPageParams = new ArrayList<>();
    private final Path mCardPath = new Path();
    private final Paint mPageDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float mPageDotRadius;
    private float mPageDotsAlpha;

    // Super folders: notification dots on the apps of a big folder.
    private final DotRenderer.DrawParams mItemDotParams = new DotRenderer.DrawParams();
    private final NotificationBadgeCounter mItemBadgeCounter = new NotificationBadgeCounter();
    private final int mItemDotColor;
    private DotRenderer mItemDotRenderer;
    private int mItemDotRendererSize;
    private boolean mDrawItemDotCounts;

    static final int INITIAL_ITEM_ANIMATION_DURATION = 350;
    private static final int FINAL_ITEM_ANIMATION_DURATION = 200;

    private static final int SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION_DELAY = 100;
    private static final int SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION = 300;

    public PreviewItemManager(FolderIcon icon) {
        mContext = icon.getContext();
        mIcon = icon;
        mIconSize = ActivityContext.lookupContext(
                mContext).getDeviceProfile().getFolderProfile().getChildIconSizePx();
        mClipThreshold = dpToPx(1f);
        mPageDotRadius = dpToPx(5f) / 2f;
        mPageDotPaint.setColor(Themes.getAttrColor(mContext, R.attr.workspaceTextColor));
        mItemDotColor = Themes.getAttrColor(mContext, R.attr.notificationDotColor);
        mItemDotParams.setDotColor(mItemDotColor);
        mItemDotParams.shapeInfo =
                ThemeManager.INSTANCE.get(mContext).getIconState().getIconShapeInfo();
    }

    /**
     * @param reverse If true, animates the final item in the preview to be full size. If false,
     *                animates the first item to its position in the preview.
     */
    public FolderPreviewItemAnim createFirstItemAnimation(final boolean reverse,
            final Runnable onCompleteRunnable) {
        return reverse
                ? new FolderPreviewItemAnim(this, mFirstPageParams.get(0), 0, 2, -1, -1,
                FINAL_ITEM_ANIMATION_DURATION, onCompleteRunnable)
                : new FolderPreviewItemAnim(this, mFirstPageParams.get(0), -1, -1, 0, 2,
                        INITIAL_ITEM_ANIMATION_DURATION, onCompleteRunnable);
    }

    Drawable prepareCreateAnimation(final View destView) {
        Drawable animateDrawable = destView instanceof AppPairIcon
                ? ((AppPairIcon) destView).getIconDrawableArea().getDrawable()
                : ((BubbleTextView) destView).getIcon();
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                destView.getMeasuredWidth(),
                destView.getMeasuredHeight());
        mReferenceDrawable = animateDrawable;
        return animateDrawable;
    }

    public void recomputePreviewDrawingParams() {
        if (mReferenceDrawable != null) {
            computePreviewDrawingParams(mReferenceDrawable.getIntrinsicWidth(),
                    mIcon.getMeasuredWidth(),
                    mIcon.getMeasuredHeight());
        }
    }

    private void computePreviewDrawingParams(
            int drawableSize, int totalWidth, int totalHeight) {
        int spanX = mIcon.getCurrentSpanX();
        int spanY = mIcon.getCurrentSpanY();
        boolean usesWorkspacePreview = mIcon.usesWorkspacePreviewLayout();

        boolean geometryChanged =
                mTotalWidth != totalWidth
                        || mTotalHeight != totalHeight
                        || mPrevSpanX != spanX
                        || mPrevSpanY != spanY
                        || mPrevUsesWorkspacePreviewLayout != usesWorkspacePreview
                        || mPrevTopPadding != mIcon.getPaddingTop();

        if (mIntrinsicIconSize != drawableSize || geometryChanged) {
            boolean animateResize =
                    geometryChanged
                            && mIntrinsicIconSize > 0
                            && !mFirstPageParams.isEmpty()
                            && mIcon.isLaidOut()
                            && mPrevUsesWorkspacePreviewLayout
                            && usesWorkspacePreview;

            Rect previousBackgroundBounds = null;
            FolderPreviewLayout.Snapshot previousSnapshot = null;

            if (animateResize) {
                previousBackgroundBounds = new Rect();
                mIcon.mBackground.getBounds(previousBackgroundBounds);
                previousSnapshot = calculateWorkspacePreviewSnapshot(
                        mIcon.mInfo.getContents(), mPrevSpanX, mPrevSpanY,
                        clampPage(mPreviewPage, mPrevSpanX, mPrevSpanY));
            }
            if (mPrevSpanX != spanX || mPrevSpanY != spanY) {
                resetPage();
            }

            mIntrinsicIconSize = drawableSize;
            mTotalWidth = totalWidth;
            mTotalHeight = totalHeight;
            mPrevSpanX = spanX;
            mPrevSpanY = spanY;
            mPrevTopPadding = mIcon.getPaddingTop();
            mPrevUsesWorkspacePreviewLayout = usesWorkspacePreview;

            mIcon.mBackground.setup(
                    mIcon.getContext(),
                    mIcon.mActivity,
                    mIcon,
                    mTotalWidth,
                    mTotalHeight,
                    mIcon.getPaddingTop(),
                    spanX,
                    spanY);

            mIcon.mPreviewLayoutRule.init(
                    mIcon.mBackground.previewSize,
                    mIntrinsicIconSize,
                    Utilities.isRtl(mIcon.getResources()),
                    mIcon.mActivity.getDeviceProfile()
                            .getFolderProfile()
                            .getNumColumns());

            if (animateResize) {
                FolderPreviewLayout.Snapshot newSnapshot =
                        calculateWorkspacePreviewSnapshot();
                animateWorkspacePreviewResize(previousSnapshot, newSnapshot);
                mIcon.mBackground.animateBoundsFrom(
                        previousBackgroundBounds,
                        DROP_IN_ANIMATION_DURATION);
            } else {
                updatePreviewItems(false);
            }
        }
    }

    private FolderPreviewLayout.Grid calculateWorkspacePreviewGrid(
            RectF backgroundBounds,
            int spanX,
            int spanY) {
        Resources resources = mContext.getResources();
        DeviceProfile deviceProfile = mIcon.mActivity.getDeviceProfile();

        float defaultPadding = Math.min(
                resources.getDimension(R.dimen.folder_workspace_preview_padding),
                Math.min(backgroundBounds.width(), backgroundBounds.height()) / 4f);
        float baseContentSize =
                deviceProfile.getFolderProfile().getFolderIconSizePx() - 2 * defaultPadding;

        if (spanX == 1 && spanY == 1) {
            // Standard legacy 1x1 circular folder preview grid layout
            float minGap = resources.getDimension(R.dimen.folder_workspace_preview_min_gap);
            RectF availableBounds = new RectF(backgroundBounds);
            availableBounds.inset(defaultPadding, defaultPadding);

            float maxItemSize = Math.min(availableBounds.width(), availableBounds.height());
            float itemSize = Math.min(baseContentSize, maxItemSize);

            return FolderPreviewLayout.calculateGrid(availableBounds, itemSize, minGap);
        } else {
            // Smaller icons than on the home screen: a 2x2 folder holds three rows of three.
            int columns = getBigFolderLineCount(spanX);
            int rows = getBigFolderLineCount(spanY);

            float availableWidth = backgroundBounds.width();
            float availableHeight = backgroundBounds.height();
            float itemSize = Math.min(
                    availableWidth / getBigFolderLineWeight(columns),
                    availableHeight / getBigFolderLineWeight(rows));
            float idealPadding = itemSize * BIG_FOLDER_PADDING_RATIO;
            float idealGap = itemSize * BIG_FOLDER_GAP_RATIO;

            RectF availableBounds = new RectF(backgroundBounds);
            availableBounds.inset(idealPadding, idealPadding);

            // Calculate expanding gaps, but clamp them to a maximum of 1.5x idealGap to prevent icons from flying apart
            float columnGap = columns > 1 ? Math.min(idealGap * 1.5f, (availableBounds.width() - columns * itemSize) / (columns - 1)) : 0f;
            float rowGap = rows > 1 ? Math.min(idealGap * 1.5f, (availableBounds.height() - rows * itemSize) / (rows - 1)) : 0f;

            float gridWidth = columns * itemSize + (columns - 1) * columnGap;
            float gridHeight = rows * itemSize + (rows - 1) * rowGap;

            // Center the grid inside the background bounds
            float startX = backgroundBounds.left + (backgroundBounds.width() - gridWidth) / 2f;
            float startY = backgroundBounds.top + (backgroundBounds.height() - gridHeight) / 2f;

            return new FolderPreviewLayout.Grid(
                    columns,
                    rows,
                    startX,
                    startY,
                    itemSize,
                    columnGap,
                    rowGap
            );
        }
    }

    private static final float BIG_FOLDER_PADDING_RATIO = 0.12f;
    private static final float BIG_FOLDER_GAP_RATIO = 0.16f;

    /** Icons along one side of a big folder: one and a half per cell, so 3 across 2 cells. */
    private static int getBigFolderLineCount(int span) {
        return span + span / 2;
    }

    /** The length of a line of {@code count} icons, padding included, in icon sizes. */
    private static float getBigFolderLineWeight(int count) {
        return count + (count - 1) * BIG_FOLDER_GAP_RATIO + 2 * BIG_FOLDER_PADDING_RATIO;
    }

    FolderPreviewLayout.Snapshot calculateWorkspacePreviewSnapshot() {
        return calculateWorkspacePreviewSnapshot(getPreviewPage());
    }

    private FolderPreviewLayout.Snapshot calculateWorkspacePreviewSnapshot(int page) {
        return calculateWorkspacePreviewSnapshot(mIcon.mInfo.getContents(),
                mIcon.getCurrentSpanX(), mIcon.getCurrentSpanY(), page);
    }

    FolderPreviewLayout.Snapshot calculateWorkspacePreviewSnapshotForPage(int page) {
        List<View> pageViews = mIcon.getFolder().getItemsOnPage(page);
        List<ItemInfo> pageItems = new ArrayList<>(pageViews.size());

        for (View view : pageViews) {
            pageItems.add((ItemInfo) view.getTag());
        }

        if (!mIcon.isMultiSpanFolder()) {
            return calculateWorkspacePreviewSnapshot(pageItems,
                    mIcon.getCurrentSpanX(), mIcon.getCurrentSpanY(), 0);
        }
        // A big folder moves only the apps its shown page has.
        FolderPreviewLayout.Snapshot shown = calculateWorkspacePreviewSnapshot();
        List<FolderPreviewLayout.ItemPlacement> placements = new ArrayList<>();
        for (FolderPreviewLayout.ItemPlacement placement : shown.getItems()) {
            if (pageItems.contains(placement.getItem())) {
                placements.add(placement);
            }
        }
        return new FolderPreviewLayout.Snapshot(
                shown.getBackgroundBounds(), shown.getOverviewBounds(), placements);
    }

    private FolderPreviewLayout.Snapshot calculateWorkspacePreviewSnapshot(
            List<ItemInfo> items, int spanX, int spanY, int page) {
        Rect backgroundBounds = new Rect();
        mIcon.mBackground.getTargetBounds(backgroundBounds);

        RectF snapshotBounds = new RectF(backgroundBounds);
        FolderPreviewLayout.Grid grid =
                calculateWorkspacePreviewGrid(snapshotBounds, spanX, spanY);

        int folderColumnCount = mIcon.mActivity.getDeviceProfile()
                .getFolderProfile().getNumColumns();
        boolean isRtl = Utilities.isRtl(mIcon.getResources());

        return FolderPreviewLayout.calculateSnapshot(
                items,
                snapshotBounds,
                grid,
                mIntrinsicIconSize,
                isRtl,
                folderColumnCount,
                page);
    }

    /** Apps one page of a big folder holds: three rows of three in a 2x2. */
    private static int getBigFolderCapacity(int spanX, int spanY) {
        return getBigFolderLineCount(spanX) * getBigFolderLineCount(spanY);
    }

    private int getPageCount(int spanX, int spanY) {
        if (spanX <= 1 && spanY <= 1) return 1;
        int capacity = getBigFolderCapacity(spanX, spanY);
        return Math.max(1, (mIcon.mInfo.getContents().size() + capacity - 1) / capacity);
    }

    private int clampPage(int page, int spanX, int spanY) {
        return Math.max(0, Math.min(page, getPageCount(spanX, spanY) - 1));
    }

    int getPreviewPageCount() {
        return getPageCount(mIcon.getCurrentSpanX(), mIcon.getCurrentSpanY());
    }

    int getPreviewPage() {
        return clampPage(mPreviewPage, mIcon.getCurrentSpanX(), mIcon.getCurrentSpanY());
    }

    /** Width of one page: the folder's card. */
    float getPageWidth() {
        Rect bounds = new Rect();
        mIcon.mBackground.getTargetBounds(bounds);
        return bounds.width();
    }

    float getPageOffset() {
        return mPageOffset;
    }

    /**
     * Moves the shown page sideways by {@code offset} pixels during a swipe, showing the page next
     * to it in the gap. {@code neighborPage} is that page, or -1 for none.
     */
    void setPageOffset(float offset, int neighborPage) {
        if (neighborPage != mNeighborPage) {
            mNeighborPage = neighborPage;
            if (neighborPage >= 0) {
                applySnapshot(calculateWorkspacePreviewSnapshot(neighborPage), mNeighborPageParams);
            } else {
                mNeighborPageParams.clear();
            }
        }
        mPageOffset = offset;
        onParamsChanged();
    }

    /** Ends a swipe on {@code page}. */
    void setPreviewPage(int page) {
        mPreviewPage = Math.max(0, page);
        mPageOffset = 0;
        mNeighborPage = -1;
        mNeighborPageParams.clear();
        if (mIcon.usesWorkspacePreviewLayout() && mIntrinsicIconSize > 0) {
            applySnapshot(calculateWorkspacePreviewSnapshot(), mFirstPageParams);
        }
        onParamsChanged();
    }

    private void resetPage() {
        mPreviewPage = 0;
        mPageOffset = 0;
        mNeighborPage = -1;
        mNeighborPageParams.clear();
    }

    void setPageDotsAlpha(float alpha) {
        mPageDotsAlpha = alpha;
        onParamsChanged();
    }

    /**
     * Draws one dot per page under a big folder's card, centered on {@code centerY}. The shown
     * page's dot is solid and stretches toward the page a swipe brings in.
     */
    void drawPageDots(Canvas canvas, float centerY) {
        int count = getPreviewPageCount();
        if (count <= 1 || mPageDotsAlpha <= 0f) return;

        Rect bounds = new Rect();
        mIcon.mBackground.getBounds(bounds);
        boolean isRtl = Utilities.isRtl(mIcon.getResources());
        float radius = mPageDotRadius;
        float step = radius * 4.4f;
        float startX = bounds.centerX() - (count - 1) * step / 2f;
        int alpha = Math.round(255 * mPageDotsAlpha);

        mPageDotPaint.setAlpha(Math.round(alpha * 0.4f));
        for (int i = 0; i < count; i++) {
            float x = startX + (isRtl ? count - 1 - i : i) * step;
            canvas.drawCircle(x, centerY, radius, mPageDotPaint);
        }

        int shown = getPreviewPage();
        float from = startX + (isRtl ? count - 1 - shown : shown) * step;
        float to = from;
        float progress = 0f;
        float pageWidth = getPageWidth();
        if (mNeighborPage >= 0 && mNeighborPage < count && pageWidth > 0) {
            to = startX + (isRtl ? count - 1 - mNeighborPage : mNeighborPage) * step;
            progress = Math.min(1f, Math.abs(mPageOffset) / pageWidth);
        }
        // The leading end gets there in the first half, the trailing end in the second.
        float lead = from + (to - from) * Math.min(1f, progress * 2f);
        float trail = from + (to - from) * Math.max(0f, progress * 2f - 1f);
        mPageDotPaint.setAlpha(alpha);
        canvas.drawRoundRect(Math.min(lead, trail) - radius, centerY - radius,
                Math.max(lead, trail) + radius, centerY + radius, radius, radius, mPageDotPaint);
    }

    @Nullable
    FolderPreviewLayout.ItemPlacement findDirectItemAt(float x, float y) {
        if (!mIcon.usesWorkspacePreviewLayout() || mIntrinsicIconSize <= 0) {
            return null;
        }

        for (FolderPreviewLayout.ItemPlacement placement
                : calculateWorkspacePreviewSnapshot().getItems()) {
            if (placement.getRole() == FolderPreviewLayout.ItemRole.DIRECT
                    && placement.getBounds().contains(x, y)) {
                return placement;
            }
        }

        return null;
    }

    @Nullable
    FolderPreviewLayout.ItemPlacement findWorkspacePreviewPlacement(
            FolderPreviewLayout.Snapshot snapshot, ItemInfo item) {
        for (FolderPreviewLayout.ItemPlacement placement : snapshot.getItems()) {
            if (placement.getItem() == item) {
                return placement;
            }
        }
        return null;
    }

    void setWorkspacePreviewItemHidden(ItemInfo item, boolean hidden) {
        for (PreviewItemDrawingParams params : mFirstPageParams) {
            if (params.item == item) {
                params.hidden = hidden;
                onParamsChanged();
                return;
            }
        }
    }

    PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        // We use an index of -1 to represent an icon on the workspace for the destroy and
        // create animations
        if (index == -1) {
            return getFinalIconParams(params);
        }
        return mIcon.mPreviewLayoutRule.computePreviewItemDrawingParams(index, curNumItems, params);
    }

    private PreviewItemDrawingParams getFinalIconParams(PreviewItemDrawingParams params) {
        float iconSize = mIcon.mActivity.getDeviceProfile().getWorkspaceProfile().getIconSizePx();

        final float scale = iconSize / mReferenceDrawable.getIntrinsicWidth();
        final float trans = (mIcon.mBackground.previewSize - iconSize) / 2;

        if (params == null) {
            params = new PreviewItemDrawingParams(0, 0, 0);
        }
        params.update(trans, trans, scale);
        return params;
    }

    public void drawParams(Canvas canvas, ArrayList<PreviewItemDrawingParams> params,
            PointF offset, boolean shouldClipPath, Path clipPath) {
        // The first item should be drawn last (ie. on top of later items)
        for (int i = params.size() - 1; i >= 0; i--) {
            PreviewItemDrawingParams p = params.get(i);
            if (!p.hidden) {
                // Exiting param should always be clipped.
                boolean isExiting = p.index == EXIT_INDEX;
                drawPreviewItem(canvas, p, offset, isExiting | shouldClipPath, clipPath);
            }
        }
    }

    /**
     * Draws the preview items on {@param canvas}.
     */
    public void draw(Canvas canvas) {
        mDrawItemDotCounts = LauncherPrefs.NOTIFICATION_BADGE_COUNT.get(mContext);
        int saveCount = canvas.save();
        // The items are drawn in coordinates relative to the preview offset
        PreviewBackground bg = mIcon.getFolderBackground();

        // Dynamically scale the nested preview icons in unison with the background scale (e.g., during accept-bounce states)
        if (bg.mScale != 1f) {
            RectF bounds = new RectF();
            bg.getScaledBounds(bounds);
            float centerX = bounds.centerX();
            float centerY = bounds.centerY();
            canvas.scale(bg.mScale, bg.mScale, centerX, centerY);
        }

        Path clipPath = bg.getClipPath().getPath();
        boolean shouldClipResize = bg.isBoundsAnimating();
        float firstPageItemsTransX = 0;
        if (mShouldSlideInFirstPage) {
            PointF firstPageOffset = new PointF(bg.getPreviewLeft() + mCurrentPageItemsTransX,
                    bg.getPreviewTop());
            boolean shouldClip =
                    Math.abs(mCurrentPageItemsTransX) > mClipThreshold;

            drawParams(
                    canvas,
                    mCurrentPageParams,
                    firstPageOffset,
                    (shouldClip || shouldClipResize || mIcon.usesWorkspacePreviewLayout()) && !mIcon.isMultiSpanFolder(),
                    clipPath);

            firstPageItemsTransX = -mPageSlideDistance + mCurrentPageItemsTransX;
        }

        PointF firstPageOffset = new PointF(bg.getPreviewLeft() + firstPageItemsTransX,
                bg.getPreviewTop());
        if (mPageOffset != 0 && mIcon.isMultiSpanFolder()) {
            // A page swipe: both pages slide inside the card.
            bg.getDrawnShapePath(mCardPath);
            canvas.clipPath(mCardPath);
            drawParams(canvas, mFirstPageParams,
                    new PointF(firstPageOffset.x + mPageOffset, firstPageOffset.y), false, null);
            if (mNeighborPage >= 0) {
                float pageWidth = getPageWidth();
                float neighborOffset = mPageOffset > 0
                        ? mPageOffset - pageWidth : mPageOffset + pageWidth;
                drawParams(canvas, mNeighborPageParams,
                        new PointF(firstPageOffset.x + neighborOffset, firstPageOffset.y),
                        false, null);
            }
            canvas.restoreToCount(saveCount);
            return;
        }
        boolean shouldClipFirstPage =
                (shouldClipResize
                        || Math.abs(firstPageItemsTransX) > mClipThreshold
                        || mIcon.usesWorkspacePreviewLayout())
                && !mIcon.isMultiSpanFolder();
        drawParams(
                canvas,
                mFirstPageParams,
                firstPageOffset,
                shouldClipFirstPage,
                clipPath);
        canvas.restoreToCount(saveCount);
    }

    public void onParamsChanged() {
        mIcon.invalidate();
    }

    /**
     * Draws each preview item.
     *
     * @param offset         The offset needed to draw the preview items.
     * @param shouldClipPath Iff true, clip path using {@param clipPath}.
     * @param clipPath       The clip path of the folder icon.
     */
    private void drawPreviewItem(Canvas canvas, PreviewItemDrawingParams params, PointF offset,
            boolean shouldClipPath, Path clipPath) {
        canvas.save();
        if (shouldClipPath) {
            canvas.clipPath(clipPath);
        }
        canvas.translate(offset.x + params.transX, offset.y + params.transY);
        canvas.scale(params.scale, params.scale);
        Drawable d = params.drawable;

        if (d != null) {
            Rect bounds = d.getBounds();
            canvas.save();
            canvas.translate(-bounds.left, -bounds.top);
            canvas.scale(mIntrinsicIconSize / bounds.width(), mIntrinsicIconSize / bounds.height());
            d.draw(canvas);
            canvas.restore();
            drawItemDot(canvas, params);
        }
        canvas.restore();
    }

    /** Draws an app's own dot, or its count, on its icon in a big folder. */
    private void drawItemDot(Canvas canvas, PreviewItemDrawingParams params) {
        if (params.item == null || !mIcon.shouldDrawItemDots()) return;
        DotInfo dotInfo = mIcon.getItemDotInfo(params.item);
        if (dotInfo == null) return;

        int size = Math.round(mIntrinsicIconSize);
        if (mItemDotRenderer == null || mItemDotRendererSize != size) {
            mItemDotRenderer = new DotRenderer(size);
            mItemDotRendererSize = size;
        }
        mItemDotParams.iconBounds.set(0, 0, size, size);
        Utilities.scaleRectAboutCenter(mItemDotParams.iconBounds, ICON_VISIBLE_AREA_FACTOR);
        mItemDotParams.scale = 1f;
        if (mDrawItemDotCounts) {
            mItemBadgeCounter.draw(canvas, mItemDotParams, mItemDotColor,
                    dotInfo.getNotificationCount());
        } else {
            mItemDotRenderer.draw(canvas, mItemDotParams);
        }
    }

    public void hidePreviewItem(int index, boolean hidden) {
        int paramIndex = index;

        // If there are more params than visible in the preview, they are used for enter/exit
        // animation purposes and they were added to the front of the list.
        // To index the params properly, we need to skip these params.
        if (!mIcon.usesWorkspacePreviewLayout()) {
            paramIndex += Math.max(
                mFirstPageParams.size() - MAX_NUM_ITEMS_IN_PREVIEW,
                0);
        }

        if (paramIndex < 0 || paramIndex >= mFirstPageParams.size()) {
            return;
        }

        mFirstPageParams.get(paramIndex).hidden = hidden;
    }

    private static void matchParamCount(
            ArrayList<PreviewItemDrawingParams> params, int itemCount) {
        while (itemCount < params.size()) {
            params.remove(params.size() - 1);
        }

        while (itemCount > params.size()) {
            params.add(new PreviewItemDrawingParams(0, 0, 0));
        }
    }

    private void applyPlacement(
            FolderPreviewLayout.ItemPlacement placement,
            PreviewItemDrawingParams params) {
        RectF bounds = placement.getBounds();

        float scale = bounds.width() / mIntrinsicIconSize;
        float transX = bounds.left - mIcon.mBackground.getTargetPreviewLeft();
        float transY = bounds.top - mIcon.mBackground.getTargetPreviewTop();

        params.update(transX, transY, scale);
    }

    private PreviewItemDrawingParams createPlacementParams(
            FolderPreviewLayout.ItemPlacement placement) {
        PreviewItemDrawingParams params = new PreviewItemDrawingParams(0, 0, 0);
        applyPlacement(placement, params);
        return params;
    }

    private PreviewItemDrawingParams createCollapsedParams(
            FolderPreviewLayout.Snapshot snapshot) {
        RectF collapseBounds = snapshot.getOverviewBounds();
        if (collapseBounds == null) {
            collapseBounds = snapshot.getBackgroundBounds();
        }

        RectF backgroundBounds = snapshot.getBackgroundBounds();
        float previewLeft =
                backgroundBounds.centerX()
                        - mIcon.mBackground.previewSize / 2f;
        float previewTop =
                backgroundBounds.centerY()
                        - mIcon.mBackground.previewSize / 2f;

        return new PreviewItemDrawingParams(
                collapseBounds.centerX() - previewLeft,
                collapseBounds.centerY() - previewTop,
                0f);
    }

    private void animateToPlacement(
            PreviewItemDrawingParams params,
            FolderPreviewLayout.ItemPlacement placement,
            Runnable onComplete) {
        PreviewItemDrawingParams target = createPlacementParams(placement);
        FolderPreviewItemAnim anim = new FolderPreviewItemAnim(
                this,
                params,
                target.scale,
                target.transX,
                target.transY,
                DROP_IN_ANIMATION_DURATION,
                onComplete);

        if (params.anim != null) {
            if (params.anim.hasEqualFinalState(anim)) return;
            params.anim.cancel();
        }
        params.anim = anim;
        anim.start();
    }

    @Nullable
    private static PreviewItemDrawingParams removeParamForItem(
            List<PreviewItemDrawingParams> params, ItemInfo item) {
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).item == item) {
                return params.remove(i);
            }
        }
        return null;
    }

    void animateWorkspacePreviewSnapshot(
            FolderPreviewLayout.Snapshot oldSnapshot,
            FolderPreviewLayout.Snapshot newSnapshot,
            @Nullable ItemInfo droppedItem) {
        ArrayList<PreviewItemDrawingParams> unmatchedParams = new ArrayList<>();

        for (FolderPreviewLayout.ItemPlacement placement : oldSnapshot.getItems()) {
            PreviewItemDrawingParams params = createPlacementParams(placement);
            setDrawable(params, placement.getItem());
            unmatchedParams.add(params);
        }

        animateWorkspacePreviewParams(
                unmatchedParams,
                newSnapshot,
                droppedItem,
                true,
                null);
    }

    private void animateWorkspacePreviewParams(
            ArrayList<PreviewItemDrawingParams> unmatchedParams,
            FolderPreviewLayout.Snapshot newSnapshot,
            @Nullable ItemInfo hiddenItem,
            boolean updateHiddenState,
            @Nullable FolderPreviewLayout.Snapshot resizeStartSnapshot) {
        ArrayList<PreviewItemDrawingParams> nextParams =
                new ArrayList<>();

        for (FolderPreviewLayout.ItemPlacement placement
                : newSnapshot.getItems()) {
            PreviewItemDrawingParams params =
                    removeParamForItem(
                            unmatchedParams, placement.getItem());

            if (params == null) {
                params = resizeStartSnapshot == null
                        ? createPlacementParams(placement)
                        : createCollapsedParams(resizeStartSnapshot);
                params.scale = 0f;
                setDrawable(params, placement.getItem());
            }

            if (updateHiddenState) {
                params.hidden = placement.getItem() == hiddenItem;
            }
            nextParams.add(params);
            animateToPlacement(params, placement, null);
        }

        for (PreviewItemDrawingParams params : unmatchedParams) {
            float exitTransX = params.transX;
            float exitTransY = params.transY;

            if (resizeStartSnapshot != null) {
                PreviewItemDrawingParams collapsed =
                        createCollapsedParams(newSnapshot);
                exitTransX = collapsed.transX;
                exitTransY = collapsed.transY;
            }

            FolderPreviewItemAnim anim = new FolderPreviewItemAnim(
                    this,
                    params,
                    0f,
                    exitTransX,
                    exitTransY,
                    DROP_IN_ANIMATION_DURATION,
                    () -> {
                        mFirstPageParams.remove(params);
                        onParamsChanged();
                    });

            params.anim = anim;
            nextParams.add(0, params);
            anim.start();
        }

        mFirstPageParams.clear();
        mFirstPageParams.addAll(nextParams);
        onParamsChanged();
    }

    private void animateWorkspacePreviewResize(
            FolderPreviewLayout.Snapshot oldSnapshot,
            FolderPreviewLayout.Snapshot newSnapshot) {
        animateWorkspacePreviewParams(
                new ArrayList<>(mFirstPageParams),
                newSnapshot,
                null,
                false,
                oldSnapshot);
    }

    private void applySnapshot(
            FolderPreviewLayout.Snapshot snapshot,
            ArrayList<PreviewItemDrawingParams> params) {
        List<FolderPreviewLayout.ItemPlacement> placements = snapshot.getItems();

        for (PreviewItemDrawingParams drawingParams : new ArrayList<>(params)) {
            if (drawingParams.anim != null) {
                drawingParams.anim.cancel();
            }
        }

        matchParamCount(params, placements.size());

        for (int i = 0; i < placements.size(); i++) {
            FolderPreviewLayout.ItemPlacement placement = placements.get(i);
            PreviewItemDrawingParams drawingParams = params.get(i);

            setDrawable(drawingParams, placement.getItem());
            applyPlacement(placement, drawingParams);
        }
    }

    void buildParamsForPage(int page, ArrayList<PreviewItemDrawingParams> params, boolean animate) {
        if (mIcon.usesWorkspacePreviewLayout() && mIntrinsicIconSize > 0) {
            FolderPreviewLayout.Snapshot snapshot = page == 0
                    ? calculateWorkspacePreviewSnapshot()
                    : calculateWorkspacePreviewSnapshotForPage(page);
            applySnapshot(snapshot, params);
            return;
        }

        List<ItemInfo> items = mIcon.getPreviewItemsOnPage(page);

        matchParamCount(params, items.size());

        int numItemsInFirstPagePreview = page == 0 ? items.size() : MAX_NUM_ITEMS_IN_PREVIEW;
        for (int i = 0; i < params.size(); i++) {
            PreviewItemDrawingParams p = params.get(i);
            setDrawable(p, items.get(i));

            if (!animate) {
                if (p.anim != null) {
                    p.anim.cancel();
                }
                computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, p);
                if (mReferenceDrawable == null) {
                    mReferenceDrawable = p.drawable;
                }
            } else {
                FolderPreviewItemAnim anim = new FolderPreviewItemAnim(this, p, i,
                        mNumOfPrevItems, i, numItemsInFirstPagePreview, DROP_IN_ANIMATION_DURATION,
                        null);

                if (p.anim != null) {
                    if (p.anim.hasEqualFinalState(anim)) {
                        // do nothing, let the current animation finish
                        continue;
                    }
                    p.anim.cancel();
                }
                p.anim = anim;
                p.anim.start();
            }
        }
    }

    void onFolderClose(int currentPage) {
        // If we are not closing on the first page, we animate the current page preview items
        // out, and animate the first page preview items in.
        mShouldSlideInFirstPage = currentPage != 0 && !mIcon.isMultiSpanFolder();
        if (mShouldSlideInFirstPage) {
            Rect backgroundBounds = new Rect();
            mIcon.mBackground.getBounds(backgroundBounds);

            float slideDirection =
                    Utilities.isRtl(mIcon.getResources()) ? -1f : 1f;
            mPageSlideDistance = backgroundBounds.width() * slideDirection;

            mCurrentPageItemsTransX = 0;
            buildParamsForPage(currentPage, mCurrentPageParams, false);
            onParamsChanged();

            ValueAnimator slideAnimator = ObjectAnimator
                    .ofFloat(this, CURRENT_PAGE_ITEMS_TRANS_X, 0f, mPageSlideDistance);
            slideAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mCurrentPageParams.clear();
                }
            });
            slideAnimator.setStartDelay(SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION_DELAY);
            slideAnimator.setDuration(SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION);
            slideAnimator.start();
        }
    }

    void updatePreviewItems(boolean animate) {
        int numOfPrevItemsAux = mFirstPageParams.size();
        buildParamsForPage(0, mFirstPageParams, animate);
        mNumOfPrevItems = numOfPrevItemsAux;
    }

    void updatePreviewItems(Predicate<ItemInfo> itemCheck) {
        boolean modified = false;
        for (PreviewItemDrawingParams param : mFirstPageParams) {
            if (itemCheck.test(param.item)
                    || (param.item instanceof AppPairInfo api && api.anyMatch(itemCheck))) {
                setDrawable(param, param.item);
                modified = true;
            }
        }
        for (PreviewItemDrawingParams param : mCurrentPageParams) {
            if (itemCheck.test(param.item)
                    || (param.item instanceof AppPairInfo api && api.anyMatch(itemCheck))) {
                setDrawable(param, param.item);
                modified = true;
            }
        }
        if (modified) {
            mIcon.invalidate();
        }
    }

    boolean verifyDrawable(@NonNull Drawable who) {
        for (int i = 0; i < mFirstPageParams.size(); i++) {
            if (mFirstPageParams.get(i).drawable == who) {
                return true;
            }
        }
        for (int i = 0; i < mNeighborPageParams.size(); i++) {
            if (mNeighborPageParams.get(i).drawable == who) {
                return true;
            }
        }
        return false;
    }

    float getIntrinsicIconSize() {
        return mIntrinsicIconSize;
    }

    /**
     * Handles the case where items in the preview are either:
     * - Moving into the preview
     * - Moving into a new position
     * - Moving out of the preview
     *
     * @param oldItems The list of items in the old preview.
     * @param newItems The list of items in the new preview.
     * @param dropped  The item that was dropped onto the FolderIcon.
     */
    public void onDrop(List<ItemInfo> oldItems, List<ItemInfo> newItems, ItemInfo dropped) {
        int numItems = newItems.size();
        final ArrayList<PreviewItemDrawingParams> params = mFirstPageParams;
        buildParamsForPage(0, params, false);

        if (mIcon.usesWorkspacePreviewLayout()) {
            onParamsChanged();
            return;
        }

        // New preview items for items that are moving in (except for the dropped item).
        List<ItemInfo> moveIn = new ArrayList<>();
        for (ItemInfo newItem : newItems) {
            if (!oldItems.contains(newItem) && !newItem.equals(dropped)) {
                moveIn.add(newItem);
            }
        }
        for (int i = 0; i < moveIn.size(); ++i) {
            int prevIndex = newItems.indexOf(moveIn.get(i));
            PreviewItemDrawingParams p = params.get(prevIndex);
            computePreviewItemDrawingParams(prevIndex, numItems, p);
            updateTransitionParam(p, moveIn.get(i), ENTER_INDEX, newItems.indexOf(moveIn.get(i)),
                    numItems);
        }

        // Items that are moving into new positions within the preview.
        for (int newIndex = 0; newIndex < newItems.size(); ++newIndex) {
            int oldIndex = oldItems.indexOf(newItems.get(newIndex));
            if (oldIndex >= 0 && newIndex != oldIndex) {
                PreviewItemDrawingParams p = params.get(newIndex);
                updateTransitionParam(p, newItems.get(newIndex), oldIndex, newIndex, numItems);
            }
        }

        // Old preview items that need to be moved out.
        List<ItemInfo> moveOut = new ArrayList<>(oldItems);
        moveOut.removeAll(newItems);
        for (int i = 0; i < moveOut.size(); ++i) {
            ItemInfo item = moveOut.get(i);
            int oldIndex = oldItems.indexOf(item);
            PreviewItemDrawingParams p = computePreviewItemDrawingParams(oldIndex, numItems, null);
            updateTransitionParam(p, item, oldIndex, EXIT_INDEX, numItems);
            params.add(0, p); // We want these items first so that they are on drawn last.
        }

        for (int i = 0; i < params.size(); ++i) {
            if (params.get(i).anim != null) {
                params.get(i).anim.start();
            }
        }
    }

    private void updateTransitionParam(final PreviewItemDrawingParams p, ItemInfo item,
            int prevIndex, int newIndex, int numItems) {
        setDrawable(p, item);

        FolderPreviewItemAnim anim = new FolderPreviewItemAnim(this, p, prevIndex, numItems,
                newIndex, numItems, DROP_IN_ANIMATION_DURATION, null);
        if (p.anim != null && !p.anim.hasEqualFinalState(anim)) {
            p.anim.cancel();
        }
        p.anim = anim;
    }

    @VisibleForTesting
    public void setDrawable(PreviewItemDrawingParams p, ItemInfo item) {
        setDrawableInternal(p, item, true /* loadHighResIcon */);
    }

    private void setDrawableInternal(
            PreviewItemDrawingParams p, ItemInfo item, boolean loadHighResIcon) {
        if (item instanceof WorkspaceItemInfo wii) {
            if (wii.shouldShowPendingIcon()) {
                p.drawable = newPendingIcon(wii, mContext, FLAG_THEMED);
            } else if (Flags.enableAppAutomationIndicator()
                    && (wii.runtimeStatusFlags & FLAG_AUTOMATED) != 0) {
                p.drawable = newAutomatedIcon(mContext, wii, FLAG_THEMED);
            } else if (!maybeHandleAutomationExit(wii, p)) {
                p.drawable = wii.newIcon(mContext, FLAG_THEMED);
            }
            p.drawable.setBounds(0, 0, mIconSize, mIconSize);
        } else if (item instanceof AppPairInfo api) {
            AppPairIconDrawingParams appPairParams =
                    new AppPairIconDrawingParams(mContext, DISPLAY_FOLDER);
            p.drawable = AppPairIconGraphic.composeDrawable(api, appPairParams);
            p.drawable.setBounds(0, 0, mIconSize, mIconSize);
        } else if (item instanceof ItemInfoWithIcon withIcon) {
            p.drawable = withIcon.newIcon(mContext,
                    ThemeManager.INSTANCE.get(mContext).isIconThemeEnabled() ? FLAG_THEMED : 0);
            p.drawable.setBounds(0, 0, mIconSize, mIconSize);
        }

        p.item = item;
        // Set the callback to FolderIcon as it is responsible to drawing the icon. The
        // callback will be released when the folder is opened.
        p.drawable.setCallback(mIcon);

        // Verify high res
        if (item instanceof ItemInfoWithIcon info
                && info.getMatchingLookupFlag().isVisuallyLessThan(DESKTOP_ICON_FLAG)) {
            if (loadHighResIcon) {
                LauncherAppState.getInstance(mContext).getIconCache().updateIconInBackground(
                        mContext.getMainExecutor(),
                        newInfo -> {
                            if (p.item == newInfo) {
                                setDrawableInternal(p, newInfo, false /* loadHighResIcon */);
                                mIcon.invalidate();
                            }
                        }, info, DESKTOP_ICON_FLAG);
            } else {
                Log.d(TAG, "Skipping high res icon load with flags: " + info.getMatchingLookupFlag()
                        + " for " + info);
            }
        }
    }

    private boolean maybeHandleAutomationExit(WorkspaceItemInfo wii, PreviewItemDrawingParams p) {
        if ((wii.runtimeStatusFlags & FLAG_AUTOMATED) == 0
                && p.drawable instanceof FastBitmapDrawable fbd
                && fbd.getDelegate() instanceof AutomatedIconDelegate aid) {
            aid.startExitAnimation(() -> {
                p.drawable = wii.newIcon(mContext, FLAG_THEMED);
                if (p.drawable != null) {
                    p.drawable.setBounds(0, 0, mIconSize, mIconSize);
                    p.drawable.setCallback(mIcon);
                }
                mIcon.invalidate();
            });
            return true;
        }
        return false;
    }
}
