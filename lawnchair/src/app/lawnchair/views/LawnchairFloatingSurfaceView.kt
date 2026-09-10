package app.lawnchair.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.util.Pair
import android.view.MotionEvent
import android.view.SurfaceControl
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.core.graphics.createBitmap
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import com.android.app.animation.Interpolators
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.CellLayout
import com.android.launcher3.GestureNavContract
import com.android.launcher3.Insettable
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.QuickstepTransitionManager.CONTENT_SCALE_DURATION
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.statehandlers.DepthController
import com.android.launcher3.util.Executors
import com.android.launcher3.util.MultiPropertyFactory
import com.android.launcher3.util.window.RefreshRateTracker.Companion.getSingleFrameMs
import com.android.launcher3.views.FloatingIconView.getLocationBoundsForView
import com.android.launcher3.views.FloatingIconViewCompanion.setPropertiesVisible
import java.util.function.Consumer
import kotlin.math.roundToInt

class LawnchairFloatingSurfaceView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbstractFloatingView(context, attrs, defStyleAttr),
    OnGlobalLayoutListener,
    Insettable,
    SurfaceHolder.Callback2 {
    private val mTmpPosition = RectF()

    private val mLauncher: LawnchairLauncher = context!!.launcher
    private val mIconPosition = RectF()
    private val mDeviceProfile = mLauncher.deviceProfile

    private val mIconBounds: Rect = Rect()
    private val mRemoveViewRunnable = Runnable { this.removeViewFromParent() }

    private val mSurfaceView: SurfaceView = SurfaceView(context)

    private var mIcon: View? = null
    private var mIconBitmap: Bitmap? = null
    private var mContract: GestureNavContract? = null
    private var mContentAnimator: AnimatorSet? = null
    private var mHasValidPosition = false

    // A dead/aborted OEM callback must never leave the workspace icon suppressed indefinitely.
    private val mFinishTimeout = Runnable { if (mIsOpen) close(false) }

    init {
        mSurfaceView.setLayerType(LAYER_TYPE_HARDWARE, null)
        mSurfaceView.setZOrderOnTop(true)

        mSurfaceView.holder.setFormat(PixelFormat.TRANSLUCENT)
        mSurfaceView.holder.addCallback(this)

        mIsOpen = true
        addView(mSurfaceView)
    }

    override fun handleClose(animate: Boolean) {
        removeCallbacks(mFinishTimeout)
        mContentAnimator?.cancel()
        mContentAnimator = null
        setCurrentIconVisible(true)
        mLauncher.viewCache.recycleView(R.layout.floating_surface_view, this)
        mContract = null
        mIcon = null
        mIsOpen = false

        // Remove after some time, to avoid flickering
        Executors.MAIN_EXECUTOR.handler.postDelayed(
            mRemoveViewRunnable,
            mLauncher.getSingleFrameMs().toLong(),
        )
    }

    private fun removeViewFromParent() {
        if (mIconBitmap != null) {
            mIconBitmap!!.recycle()
            mIconBitmap = null
        }
        mLauncher.dragLayer.removeViewInLayout(this)
    }

    private fun removeViewImmediate() {
        // Cancel any pending remove
        Executors.MAIN_EXECUTOR.handler.removeCallbacks(mRemoveViewRunnable)
        if (isAttachedToWindow) {
            removeViewFromParent()
        }
    }

    override fun isOfType(type: Int): Boolean {
        return (type and TYPE_ICON_SURFACE) != 0
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent?): Boolean {
        close(false)
        removeViewImmediate()
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        getViewTreeObserver().addOnGlobalLayoutListener(this)
        updateIconLocation()
    }

    fun getLauncherContentAnimator(
        startDelay: Int,
    ): Pair<AnimatorSet?, Runnable?> {
        val launcherAnimator = AnimatorSet()
        val endListener: Runnable?

        val scales = floatArrayOf(mDeviceProfile.workspaceContentScale, 1f)

        mLauncher.pauseExpensiveViewUpdates()

        val viewsToAnimate: MutableList<View?> = ArrayList<View?>()
        val workspace = mLauncher.workspace
        workspace.forEachVisiblePage(
            Consumer { view: View? -> viewsToAnimate.add((view as CellLayout).shortcutsAndWidgets) },
        )
        viewsToAnimate.add(mLauncher.hotseat)

        viewsToAnimate.forEach(
            Consumer { view: View? ->
                val scaleAnim =
                    ObjectAnimator.ofFloat<View?>(view, LauncherAnimUtils.SCALE_PROPERTY, *scales)
                        .setDuration(CONTENT_SCALE_DURATION.toLong() * 3)
                scaleAnim.interpolator = Interpolators.DECELERATE_1_5
                launcherAnimator.play(scaleAnim)
            },
        )

        endListener = Runnable {
            viewsToAnimate.forEach(
                Consumer { view: View? ->
                    LauncherAnimUtils.SCALE_PROPERTY.set(view, 1f)
                    view!!.setLayerType(LAYER_TYPE_NONE, null)
                },
            )
            mLauncher.resumeExpensiveViewUpdates()
        }

        launcherAnimator.setStartDelay(startDelay.toLong())
        return Pair<AnimatorSet?, Runnable?>(launcherAnimator, endListener)
    }

    private fun getBackgroundAnimator(): ObjectAnimator {
        val depthController = DepthController(mLauncher)
        val targetDepth = mLauncher.stateManager.state.getDepth<LawnchairLauncher?>(mLauncher)

        val backgroundRadiusAnim = createDepthAnimator(
            depthController,
            targetDepth,
            onEnd = {
                if (Utilities.ATLEAST_R) {
                    val viewRootImpl = mLauncher.dragLayer.getViewRootImpl()
                    val parent = viewRootImpl?.surfaceControl
                    val dimLayer: SurfaceControl = SurfaceControl.Builder()
                        .setName("Blur layer")
                        .setParent(parent)
                        .setOpaque(false)
                        .setEffectLayer()
                        .build()

                    createDepthAnimator(
                        depthController,
                        mLauncher.depthController.stateDepth.value,
                    ) {
                        depthController.dispose()
                        SurfaceControl.Transaction().remove(dimLayer).apply()
                    }.start()
                } else {
                    createDepthAnimator(
                        depthController,
                        mLauncher.depthController.stateDepth.value,
                    ) {
                        depthController.dispose()
                    }.start()
                }
            },
        )

        return backgroundRadiusAnim
    }

    private fun createDepthAnimator(
        depthController: DepthController,
        targetDepth: Float,
        onEnd: (() -> Unit)? = null,
    ): ObjectAnimator {
        return ObjectAnimator.ofFloat(
            depthController.stateDepth,
            MultiPropertyFactory.MULTI_PROPERTY_VALUE,
            targetDepth,
        ).apply {
            duration = CONTENT_SCALE_DURATION.toLong() * 2
            interpolator = Interpolators.DECELERATE_2
            onEnd?.let {
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            it()
                        }
                    },
                )
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        getViewTreeObserver().removeOnGlobalLayoutListener(this)
        removeCallbacks(mFinishTimeout)
        mContentAnimator?.cancel()
        mContentAnimator = null
        setCurrentIconVisible(true)
    }

    override fun onGlobalLayout() {
        updateIconLocation()
    }

    fun getIcon(): View? {
        return mLauncher.getFirstHomeElementForAppClose(
            null, /* StableViewInfo */
            mContract!!.componentName.packageName,
            mContract!!.user,
        )
    }

    override fun setInsets(insets: Rect?) {}

    private fun updateIconLocation() {
        mHasValidPosition = false
        if (mContract == null || !mIsOpen) return
        val icon = getIcon()
        if (icon == null || !icon.isAttachedToWindow || !icon.isLaidOut || icon.isLayoutRequested) {
            setCurrentIconVisible(true)
            return
        }
        mTmpPosition.setEmpty()
        mIconBounds.setEmpty()
        getLocationBoundsForView(mLauncher, icon, false, mTmpPosition, mIconBounds)
        val layer = mLauncher.dragLayer
        if (mIconBounds.isEmpty || mTmpPosition.isEmpty ||
            !mTmpPosition.left.isFinite() || !mTmpPosition.top.isFinite() ||
            !mTmpPosition.right.isFinite() || !mTmpPosition.bottom.isFinite() ||
            !RectF.intersects(mTmpPosition, RectF(0f, 0f, layer.width.toFloat(), layer.height.toFloat()))
        ) {
            setCurrentIconVisible(true)
            return
        }

        val changed = mIcon !== icon || mIconPosition != mTmpPosition || mIconBitmap == null
        if (mIcon !== icon) {
            setCurrentIconVisible(true)
            mIcon = icon
        }
        mIconPosition.set(mTmpPosition)
        val lp = mSurfaceView.layoutParams as LayoutParams
        val width = mIconPosition.width().roundToInt()
        val height = mIconPosition.height().roundToInt()
        val left = mIconPosition.left.roundToInt()
        val top = mIconPosition.top.roundToInt()
        if (width <= 0 || height <= 0) return
        if (lp.width != width || lp.height != height || lp.leftMargin != left || lp.topMargin != top) {
            lp.width = width
            lp.height = height
            lp.leftMargin = left
            lp.topMargin = top
            // Apply the layout; mutating LayoutParams in a posted task did not request layout.
            mSurfaceView.layoutParams = lp
        }
        if (changed) {
            if (mIconBitmap?.width != mIconBounds.width() || mIconBitmap?.height != mIconBounds.height()) {
                mIconBitmap?.recycle()
                mIconBitmap = createBitmap(mIconBounds.width(), mIconBounds.height(), Bitmap.Config.ARGB_8888)
            }
            setCurrentIconVisible(true)
            val canvas = Canvas(mIconBitmap!!)
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            canvas.translate(-mIconBounds.left.toFloat(), -mIconBounds.top.toFloat())
            icon.draw(canvas)
        }
        mHasValidPosition = true
        publishIconSurface()
    }

    private fun publishIconSurface() {
        val contract = mContract ?: return
        if (!mIsOpen || !mHasValidPosition || mIconBitmap == null || mIconPosition.isEmpty ||
            mIcon?.isAttachedToWindow != true || mSurfaceView.isLayoutRequested ||
            mSurfaceView.width != mIconPosition.width().roundToInt() ||
            mSurfaceView.height != mIconPosition.height().roundToInt() ||
            mSurfaceView.left != mIconPosition.left.roundToInt() ||
            mSurfaceView.top != mIconPosition.top.roundToInt() ||
            !Utilities.ATLEAST_Q || !mSurfaceView.surfaceControl.isValid
        ) {
            return
        }
        // Keep the real icon in its CellLayout transform. Only the remote surface is animated.
        if (drawOnSurface() && contract.sendEndPosition(mIconPosition, mLauncher, mSurfaceView.surfaceControl)) {
            setCurrentIconVisible(false)
        } else {
            close(false)
        }
    }

    override fun surfaceCreated(surfaceHolder: SurfaceHolder) {
        updateIconLocation()
    }

    override fun surfaceChanged(
        surfaceHolder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int,
    ) {
        updateIconLocation()
    }

    override fun surfaceDestroyed(surfaceHolder: SurfaceHolder) {
        setCurrentIconVisible(true)
    }

    override fun surfaceRedrawNeeded(surfaceHolder: SurfaceHolder) {
        publishIconSurface()
    }

    private fun drawOnSurface(): Boolean {
        val surfaceHolder = mSurfaceView.holder
        if (!surfaceHolder.surface.isValid || mIconBitmap == null) return false
        return try {
            val canvas = surfaceHolder.lockHardwareCanvas() ?: return false
            try {
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                canvas.drawBitmap(mIconBitmap!!, null, Rect(0, 0, canvas.width, canvas.height), null)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
            true
        } catch (exception: RuntimeException) {
            Log.w("LawnchairGnc", "Icon surface was lost during home return", exception)
            false
        }
    }

    private fun setCurrentIconVisible(isVisible: Boolean) {
        if (mIcon != null) {
            setPropertiesVisible(mIcon, isVisible)
        }
    }

    companion object {
        /**
         * Shows the surfaceView for the provided contract
         */
        fun show(launcher: LawnchairLauncher, contract: GestureNavContract?) {
            val view: LawnchairFloatingSurfaceView =
                launcher.viewCache.getView(
                    R.layout.floating_surface_view,
                    launcher,
                    launcher.dragLayer,
                )
            view.removeViewImmediate()
            view.mIconPosition.setEmpty()
            view.mIconBounds.setEmpty()
            view.mContract = contract
            view.mIsOpen = true

            val anim = AnimatorSet()
            val startDelay = launcher.getSingleFrameMs()
            val launcherContentAnimator: Pair<AnimatorSet?, Runnable?> =
                view.getLauncherContentAnimator(startDelay)
            anim.playTogether(launcherContentAnimator.first, view.getBackgroundAnimator())
            anim.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        launcherContentAnimator.second!!.run()
                    }
                },
            )

            view.mContentAnimator = anim
            launcher.dragLayer.addView(view)
            view.postDelayed(view.mFinishTimeout, 3000)
            anim.start()
            view.getIcon()?.let {
                launcher.showFullScreenOverlay(endView = it) {}
            }
        }
    }
}
