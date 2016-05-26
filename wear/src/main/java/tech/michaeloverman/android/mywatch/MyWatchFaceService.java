/*
 * Copyright (C) 2014 The Android Open Source Project
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

package tech.michaeloverman.android.mywatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface HOUR_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    private static final Typeface MINUTE_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final float FONTSIZE = 25f;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }


    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHoursPaint;
        Paint mMinutesPaint;
        Paint mSecondsPaint;

        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        float mXOffset;
        float mYOffset;
//        float mCenterX;
//        float mCenterY;
        float mCenterX;
        float mCenterY;
        float mHourRadius;
        float mMinuteRadius;
        float mSecondRadius;
        boolean mShowSeconds = false;
        float mTextSize;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = MyWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHoursPaint = new Paint();
            mHoursPaint = createTextPaint(resources.getColor(R.color.clemson_hour_text), HOUR_TYPEFACE);

            mMinutesPaint = new Paint();
            mMinutesPaint = createTextPaint(resources.getColor(R.color.clemson_minute_text), MINUTE_TYPEFACE);

            mSecondsPaint = new Paint();
            mSecondsPaint.setColor(Color.WHITE);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor, Typeface type) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(type);
//            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mHoursPaint.setTextSize(mTextSize);
            mMinutesPaint.setTextSize(mTextSize * 0.5f);
            mSecondsPaint.setTextSize(mTextSize * 0.3f);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
/*            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHoursPaint.setAntiAlias(!inAmbientMode);
                    mMinutesPaint.setAntiAlias(!inAmbientMode);
                }
//                mHoursPaint.setColor(getResources().getColor(R.color.ambient_hour));
//                mMinutesPaint.setColor(getResources().getColor(R.color.ambient_minute));

            } //else {
               // mAmbient =  inAmbientMode;
               // mHoursPaint.setColor(getResources().getColor(R.color.hour_text));
              //  mMinutesPaint.setColor(getResources().getColor(R.color.minute_text));

          //  }*/

            mAmbient = inAmbientMode;
            updateColors();
            invalidate();

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void updateColors() {
            if (mAmbient) {
                mHoursPaint.setColor(getResources().getColor(R.color.ambient_hour));
                mMinutesPaint.setColor(getResources().getColor(R.color.ambient_minute));

            } else {
                mHoursPaint.setColor(getResources().getColor(R.color.clemson_hour_text));
                mMinutesPaint.setColor(getResources().getColor(R.color.clemson_minute_text));
            }
        }
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
//                canvas.drawLine(0.0f, 0.0f, 350f, 350f, mExperimentPaint);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
//                canvas.drawPoint(mCenterX, mCenterY, mExperimentPaint);
//                canvas.drawPoint(bounds.width() / 2, bounds.height() / 2, mExperimentPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            // some lines to test various times - BE SURE TO DELETE THESE!!!!

/*            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
*/
            int localHour = mTime.hour;
            if (localHour > 12) localHour -= 12;
            if (localHour == 0) localHour = 12;
            String hour = String.format("%d", localHour);
            String minute = String.format("%02d", mTime.minute);

            float hourRot = (float) (mTime.hour * Math.PI * 2 / 12);
            float minuteRot = (float) (mTime.minute * Math.PI * 2 / 60);
            float hourX = (float) ((mCenterX + ( Math.sin(hourRot) * mHourRadius))
                    - (0.5 * mHoursPaint.measureText(hour)));
            float hourY = (float) ((mCenterY + ( -Math.cos(hourRot) * mHourRadius))
                    + (0.4 * mHoursPaint.getTextSize()));
            float minuteX = (float) ((mCenterX + ( Math.sin(minuteRot) * mMinuteRadius))
                    - (0.5 * mMinutesPaint.measureText(minute)));
            float minuteY = (float) ((mCenterY + ( -Math.cos(minuteRot) * mMinuteRadius))
                    + (0.4 * mMinutesPaint.getTextSize()));

            canvas.drawText(hour, hourX, hourY, mHoursPaint);
            canvas.drawText(minute, minuteX, minuteY, mMinutesPaint);

            if (!isInAmbientMode() && mShowSeconds) {
                String second = String.format("%02d", mTime.second);
                float secondRot = (float) (mTime.second * Math.PI * 2 / 60);
                float secondX = (float) ((mCenterX + ( Math.sin(secondRot) * mSecondRadius))
                        - (0.5 * mSecondsPaint.measureText(second)));
                float secondY = (float) ((mCenterY + ( -Math.cos(secondRot) * mSecondRadius))
                        + (0.4 * mSecondsPaint.getTextSize()));
                canvas.drawText(second, secondX, secondY, mSecondsPaint);
            }
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            mCenterX = width / 2f;
            mCenterY = height / 2f;
            mHourRadius = (float) (mCenterX * 0.65);
            mMinuteRadius = (float) (mCenterX * 0.60);
            mSecondRadius = (float) (mCenterX * 0.50);
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFaceService.Engine> mWeakReference;

        public EngineHandler(MyWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
