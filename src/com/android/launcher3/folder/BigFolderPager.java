/*
 * SPDX-FileCopyrightText: The Resurrected Remix Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Utilities;

/** Super folders: swipes a big folder on the home screen through its pages of apps. */
final class BigFolderPager {

    private static final int SETTLE_DURATION = 250;
    // How far a slow swipe has to go, as a share of the page, to turn it.
    private static final float TURN_FRACTION = 0.3f;
    // How much a swipe moves the apps when there is no page that way.
    private static final float EDGE_RESISTANCE = 0.25f;

    private final FolderIcon mIcon;
    private final PreviewItemManager mPreview;
    private final int mTouchSlop;
    private final float mFlingVelocity;

    private VelocityTracker mVelocityTracker;
    private float mDownX;
    private float mDownY;
    private float mSwipeStartX;
    private boolean mTracking;
    private boolean mSwiping;
    private ValueAnimator mSettleAnimator;

    BigFolderPager(FolderIcon icon, PreviewItemManager preview) {
        mIcon = icon;
        mPreview = preview;
        mTouchSlop = ViewConfiguration.get(icon.getContext()).getScaledTouchSlop();
        mFlingVelocity = Utilities.dpToPx(400f);
    }

    /**
     * Whether there is a page to show, with View#canScrollHorizontally's direction: positive asks
     * for what lies to the right.
     */
    boolean canScroll(int direction) {
        if (!mIcon.canSwipePages()) return false;
        int page = getPageToward(direction);
        return page >= 0 && page < mPreview.getPreviewPageCount();
    }

    private int getPageToward(int direction) {
        // The next page lies to the right, or to the left in right-to-left layouts.
        boolean next = (direction > 0) != Utilities.isRtl(mIcon.getResources());
        return mPreview.getPreviewPage() + (next ? 1 : -1);
    }

    /** Returns true while a swipe owns the gesture. */
    boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (mSettleAnimator != null) {
                    mSettleAnimator.end();
                }
                mSwiping = false;
                mTracking = mIcon.canSwipePages();
                if (mTracking) {
                    mDownX = ev.getX();
                    mDownY = ev.getY();
                    recycleVelocityTracker();
                    mVelocityTracker = VelocityTracker.obtain();
                    mVelocityTracker.addMovement(ev);
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (!mTracking) return false;
                mVelocityTracker.addMovement(ev);
                if (!mSwiping) {
                    float dx = ev.getX() - mDownX;
                    float dy = ev.getY() - mDownY;
                    if (Math.abs(dy) > mTouchSlop && Math.abs(dy) >= Math.abs(dx)) {
                        // An up or down swipe belongs to the home screen.
                        mTracking = false;
                        return false;
                    }
                    if (Math.abs(dx) <= mTouchSlop || !canScroll(dx < 0 ? 1 : -1)) {
                        return false;
                    }
                    mSwiping = true;
                    mSwipeStartX = ev.getX();
                    mIcon.onPageSwipeStarted(ev);
                }
                moveTo(ev.getX() - mSwipeStartX);
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean wasSwiping = mSwiping;
                if (mSwiping) {
                    float velocity = 0;
                    if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                        mVelocityTracker.addMovement(ev);
                        mVelocityTracker.computeCurrentVelocity(1000);
                        velocity = mVelocityTracker.getXVelocity();
                    }
                    settle(velocity);
                }
                mSwiping = false;
                mTracking = false;
                recycleVelocityTracker();
                return wasSwiping;
        }
        return mSwiping;
    }

    private void moveTo(float distance) {
        if (distance == 0) {
            mPreview.setPageOffset(0, -1);
            return;
        }
        int direction = distance < 0 ? 1 : -1;
        if (!canScroll(direction)) {
            mPreview.setPageOffset(distance * EDGE_RESISTANCE, -1);
            return;
        }
        float width = mPreview.getPageWidth();
        mPreview.setPageOffset(Math.max(-width, Math.min(width, distance)),
                getPageToward(direction));
    }

    private void settle(float velocity) {
        float offset = mPreview.getPageOffset();
        int direction = offset < 0 ? 1 : -1;
        boolean hasPage = offset != 0 && canScroll(direction);
        float width = mPreview.getPageWidth();
        boolean flung = Math.abs(velocity) > mFlingVelocity
                && Math.signum(velocity) == Math.signum(offset);
        boolean turn = hasPage && (Math.abs(offset) > width * TURN_FRACTION || flung);

        int neighbor = hasPage ? getPageToward(direction) : -1;
        int page = turn ? neighbor : mPreview.getPreviewPage();
        ValueAnimator settle =
                ValueAnimator.ofFloat(offset, turn ? Math.signum(offset) * width : 0f);
        settle.setDuration(SETTLE_DURATION);
        settle.setInterpolator(Interpolators.DECELERATE_1_5);
        settle.addUpdateListener(
                a -> mPreview.setPageOffset((float) a.getAnimatedValue(), neighbor));
        settle.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mSettleAnimator == animation) {
                    mSettleAnimator = null;
                }
                mPreview.setPreviewPage(page);
            }
        });
        mSettleAnimator = settle;
        settle.start();
    }

    private void recycleVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }
}
