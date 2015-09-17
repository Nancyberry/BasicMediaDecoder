/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.example.android.basicmediadecoder;


import android.animation.TimeAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;

import com.example.android.common.media.MediaCodecWrapper;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This activity uses a {@link android.view.TextureView} to render the frames of a video decoded using
 * {@link android.media.MediaCodec} API.
 */
public class MainActivity extends Activity {
    private final String TAG = "MainActivity";

    private TextureView mPlaybackView;
    private TimeAnimator mTimeAnimator = new TimeAnimator();

    // A utility that wraps up the underlying input and output buffer processing operations
    // into an east to use API.
    private MediaCodecWrapper mCodecWrapper;
    private MediaExtractor mExtractor = new MediaExtractor();
    TextView mAttribView = null;

    // for simple codec logic
    private MediaCodec mMediaCodec = null;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);
        mPlaybackView = (TextureView) findViewById(R.id.PlaybackView);
        mAttribView = (TextView) findViewById(R.id.AttribView);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_menu, menu);
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mTimeAnimator != null && mTimeAnimator.isRunning()) {
            mTimeAnimator.end();
        }

        if (mCodecWrapper != null) {
            mCodecWrapper.stopAndRelease();
            mExtractor.release();
        }

        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mExtractor.release();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_play) {
            mAttribView.setVisibility(View.VISIBLE);
            startPlayback();
            item.setEnabled(false);
        }
        return true;
    }

    public void stopPlayback() {
        mTimeAnimator.end();

        if (mMediaCodec != null) {
            mMediaCodec.stop();
            mMediaCodec.release();
            mExtractor.release();
        }
    }

    @TargetApi(21)
    public void startPlayback() {

        // Construct a URI that points to the video resource that we want to play
        Uri videoUri = Uri.parse("android.resource://"
                + getPackageName() + "/"
                + R.raw.vid_bigbuckbunny);

        try {

            // BEGIN_INCLUDE(initialize_extractor)
            mExtractor.setDataSource(this, videoUri, null);
            int nTracks = mExtractor.getTrackCount();

            // Begin by unselecting all of the tracks in the extractor, so we won't see
            // any tracks that we haven't explicitly selected.
            for (int i = 0; i < nTracks; ++i) {
                mExtractor.unselectTrack(i);
            }


            // Find the first video track in the stream. In a real-world application
            // it's possible that the stream would contain multiple tracks, but this
            // sample assumes that we just want to play the first one.
            for (int i = 0; i < nTracks; ++i) {
                // Try to create a video codec for this track. This call will return null if the
                // track is not a video track, or not a recognized video format. Once it returns
                // a valid MediaCodecWrapper, we can break out of the loop.

//                // START (Solution 1: decode sync deprecated (using ByteBuffer array))
//                mCodecWrapper = MediaCodecWrapper.fromVideoFormat(mExtractor.getTrackFormat(i),
//                        new Surface(mPlaybackView.getSurfaceTexture()));
//                // END (Solution 1: decode sync deprecated (using ByteBuffer array))

//                // START (Solution 2: decode sync)
                String mimeType = mExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mimeType.contains("video")) {    // create, configure and start codec
                    mMediaCodec = MediaCodec.createDecoderByType(mimeType);
                    mMediaCodec.configure(mExtractor.getTrackFormat(i), new Surface(mPlaybackView.getSurfaceTexture()), null, 0);
                    mMediaCodec.start();
                }
                // END (Solution 2: decode sync)

/*
                // START (Solution 3: decode async)
                // BUT not using TimeAnimator makes decoding too fast
                String mimeType = mExtractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
                if (mimeType.contains("video")) {
                    mMediaCodec = MediaCodec.createDecoderByType(mimeType);
                    mMediaCodec.setCallback(new MediaCodec.Callback() {
                        @Override
                        public void onInputBufferAvailable(MediaCodec mediaCodec, int inputBufferId) {

                            ByteBuffer inputBuffer = getInputBuffer(inputBufferId);
                            int size = mExtractor.readSampleData(inputBuffer, inputBufferId);

                            if (size > 0) {
                                mediaCodec.queueInputBuffer(inputBufferId, 0, size, mExtractor.getSampleTime(), mExtractor.getSampleFlags());
                            } else {    // EOS
                                mediaCodec.queueInputBuffer(inputBufferId, 0, 0, 0, mExtractor.getSampleFlags() | MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            }

                            mExtractor.advance();
                        }

                        @Override
                        public void onOutputBufferAvailable(MediaCodec mediaCodec, int outputBufferId, MediaCodec.BufferInfo bufferInfo) {

                            switch (outputBufferId) {
                                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mMediaCodec.getOutputFormat());
                                    break;
                                case MediaCodec.INFO_TRY_AGAIN_LATER:
                                    Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                                    break;
                                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                                    break;
                                default:
                                    mMediaCodec.releaseOutputBuffer(outputBufferId, true);
                                    Log.d(TAG, "Render");
                                    break;
                            }

                            if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {   // EOS
                                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                                stopPlayback();
                            }
                        }

                        @Override
                        public void onError(MediaCodec mediaCodec, MediaCodec.CodecException e) {
                            e.printStackTrace();
                        }

                        @Override
                        public void onOutputFormatChanged(MediaCodec mediaCodec, MediaFormat mediaFormat) {

                        }
                    });

                    mMediaCodec.configure(mExtractor.getTrackFormat(i), new Surface(mPlaybackView.getSurfaceTexture()), null, 0);
                    mMediaCodec.start();
                }
                // END (Solution 3: decode async)
*/

                if (mCodecWrapper != null || mMediaCodec != null) {
                    mExtractor.selectTrack(i);
                    break;
                }
            }
            // END_INCLUDE(initialize_extractor)


            // By using a {@link TimeAnimator}, we can sync our media rendering commands with
            // the system display frame rendering. The animator ticks as the {@link Choreographer}
            // receives ASYNC events.
            mTimeAnimator.setTimeListener(new TimeAnimator.TimeListener() {
                @Override
                public void onTimeUpdate(final TimeAnimator animation,
                                         final long totalTime,
                                         final long deltaTime) {
                    // Switch decoding solution here
                    decode();
//                    decodeDeprecated(totalTime);
//                    decodeAsync();
                }
            });

            // We're all set. Kick off the animator to process buffers and render video frames as
            // they become available
            mTimeAnimator.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Solution 1: Synchronous Processing using Buffer Arrays (deprecated)
     * But it's using to Queues to record current available input/output buffer indexes, which is interesting.
     *
     * @param totalTime
     */
    private void decodeDeprecated(long totalTime) {
        boolean isEos = ((mExtractor.getSampleFlags() & MediaCodec
                .BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM);

        // BEGIN_INCLUDE(write_sample)
        if (!isEos) {
            // Try to submit the sample to the codec and if successful advance the
            // extractor to the next available sample to read.
            boolean result = mCodecWrapper.writeSample(mExtractor, false,
                    mExtractor.getSampleTime(), mExtractor.getSampleFlags());

            if (result) {
                // Advancing the extractor is a blocking operation and it MUST be
                // executed outside the main thread in real applications.
                mExtractor.advance();
            }
        }
        // END_INCLUDE(write_sample)

        // Examine the sample at the head of the queue to see if its ready to be
        // rendered and is not zero sized End-of-Stream record.
        MediaCodec.BufferInfo out_bufferInfo = new MediaCodec.BufferInfo();
        mCodecWrapper.peekSample(out_bufferInfo);

        // BEGIN_INCLUDE(render_sample)
        if (out_bufferInfo.size <= 0 && isEos) {
            mTimeAnimator.end();
            mCodecWrapper.stopAndRelease();
            mExtractor.release();
        } else if (out_bufferInfo.presentationTimeUs / 1000 < totalTime) {
            // Pop the sample off the queue and send it to {@link Surface}
            mCodecWrapper.popSample(true);
        }
        // END_INCLUDE(render_sample)
    }

    /**
     * Solution 2: Synchronous Processing using Buffers
     */
    private void decode() {
        // producer
        int inputBufferId = mMediaCodec.dequeueInputBuffer(0);
        if (inputBufferId >= 0) {   // if valid input buffer, feed it with valid data
            ByteBuffer inputBuffer = getInputBuffer(inputBufferId);
            int flags = mExtractor.getSampleFlags();
            int size = mExtractor.readSampleData(inputBuffer, 0);

            if (size > 0) {     // advance extractor to the next sample
                mMediaCodec.queueInputBuffer(inputBufferId, 0, size, mExtractor.getSampleTime(), flags);
            } else {    // EOS, note that size and sample time will all be -1
                Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM. size = " + size + ", sample time = " + mExtractor.getSampleTime());
                mMediaCodec.queueInputBuffer(inputBufferId, 0, 0, 0, flags | MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }

            mExtractor.advance();
        }

        // consumer
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferId = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);

        switch (outputBufferId) {
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mMediaCodec.getOutputFormat());
                break;
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                Log.d(TAG, "INFO_TRY_AGAIN_LATER");
                break;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                break;
            default:
                mMediaCodec.releaseOutputBuffer(outputBufferId, true);
                Log.d(TAG, "Render");
                break;
        }

        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {   // EOS
            Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
            stopPlayback();
        }
    }

    /**
     * TODO Solution 3: Asynchronous processing using callback (recommended)
     * Currently it is done when the MediaCodec is initialized, with some flaws
     */
    private void decodeAsync() {

    }


    /**
     * Back compatible with getInputBuffers
     * @param index
     * @return
     */
    private ByteBuffer getInputBuffer(int index) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return mMediaCodec.getInputBuffers()[index];
        } else {
            return mMediaCodec.getInputBuffer(index);
        }
    }

    /**
     * Back compatible with getOutputBuffers
     * @param index
     * @return
     */
    private ByteBuffer getOutputBuffer(int index) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return mMediaCodec.getOutputBuffers()[index];
        } else {
            return mMediaCodec.getOutputBuffer(index);
        }
    }
}
