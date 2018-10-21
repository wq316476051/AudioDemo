package com.wang.audiodemo;

import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioPlayer {

    private static final String TAG = "AudioPlayer";

    private static final long TIME_OUT_US = 1000 * 10/*10 毫秒*/; // 单位 microseconds

    private Object mLock = new Object(); // 锁对象，用于线程间通信

    private State mState = State.IDLE;

    private AssetFileDescriptor mAssetFileDescriptor;
    private MediaExtractor mMediaExtractor;
    private MediaFormat mMediaFormat;
    private MediaCodec mMediaDecoder;
    private ExecutorService mExecutor;
    private String mMimetype;
    private AudioTrack mAudioTrack;
    private Callback mCallback;
    private long mDuration;

    public void setDataSource(AssetFileDescriptor fd) {
        mAssetFileDescriptor = fd;
    }

    public boolean isPlaying() {
        return mState == State.PLAYING;
    }

    public boolean isPaused() {
        return mState == State.PAUSED;
    }

    public void prepare() throws IOException {
        Log.d(TAG, "prepare: ");
        // 1. 创建 MediaExtractor
        Log.d(TAG, "prepare: 创建 MediaExtractor");
        mMediaExtractor = new MediaExtractor();
        mMediaExtractor.setDataSource(mAssetFileDescriptor);

        // 2. 确定音轨
        int trackCount = mMediaExtractor.getTrackCount();
        for (int index = 0; index < trackCount; index++) {
            MediaFormat trackFormat = mMediaExtractor.getTrackFormat(index);
            String mimetype = trackFormat.getString(MediaFormat.KEY_MIME);
            Log.d(TAG, "prepare: mimetpe = " + mimetype);
            if (mimetype.startsWith("audio")) {
                mMediaExtractor.selectTrack(index);
                mMediaFormat = trackFormat;
                mMimetype = mimetype;
                mDuration = mMediaFormat.getLong(MediaFormat.KEY_DURATION); // 单位 microseconds
                Log.d(TAG, "prepare: 确定音轨 mMimetype = " + mMimetype + "; mDuration = " + mDuration);
                break;
            }
        }

        // 3. 创建 MediaCodec
        Log.d(TAG, "prepare: 创建 MediaCodec");
        try {
            mMediaDecoder = MediaCodec.createDecoderByType(mMimetype);
            mMediaDecoder.configure(mMediaFormat, null, null, 0);
            Log.d(TAG, "prepare: MediaCodec.name = " + mMediaDecoder.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 4. 创建 AudioTrack
        Log.d(TAG, "prepare: 创建 AudioTrack");
        int streamType = AudioManager.STREAM_MUSIC;
        int sampleRateInHz = mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channelConfig = mMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1 ?
                AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);    // 10. 计算最小缓冲区
        int mode = AudioTrack.MODE_STREAM;
        mAudioTrack = new AudioTrack(streamType, sampleRateInHz,          // 11. 创建 AudioTrack
                channelConfig, audioFormat, bufferSizeInBytes, mode);

        // 5. 创建任务线程池
        Log.d(TAG, "prepare: 任务线程池");
        mExecutor = Executors.newSingleThreadExecutor();

        mState = State.PREPARED;
    }

    public void play() {
        Log.d(TAG, "play: mState = " + mState);
        if (mState == State.IDLE || mState == State.STOPPED) {
            try {
                prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (mState == State.PREPARED ) {
            mExecutor.submit(new PlayTask());
            mState = State.PLAYING;
        } else if (mState == State.PAUSED) {
            mState = State.PLAYING;
            synchronized (mLock) {
                mLock.notify();
            }
        }
    }

    public void pause() {
        if (mState == State.PLAYING) {
            mState = State.PAUSED;
        }
    }

    public void stop() {
        if (mState == State.PLAYING) {
            mState = State.STOPPED;
        } else if (mState == State.PAUSED) {
            mState = State.STOPPED;
            synchronized (mLock) {
                mLock.notify();
            }
        }
    }

    public void setVolume(float gain) {
        if (mState == State.PLAYING || mState == State.PAUSED) {
            mAudioTrack.setVolume(gain);
            Log.d(TAG, "setVolume: gain = " + gain + "; " + AudioTrack.getMaxVolume());
        }
    }

    public void release() {
        if (mMediaExtractor != null) {
            mMediaExtractor.release();
            mMediaExtractor = null;
        }

        if (mMediaDecoder != null) {
            mMediaDecoder.release();
            mMediaDecoder = null;
        }

        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }

        if (mAudioTrack != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
        }
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    protected void finalize() throws Throwable {
        release();
        super.finalize();
    }

    private class PlayTask implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "run: ");
            mMediaDecoder.start();
            mAudioTrack.play();

            byte[] tempBuffer = new byte[1024];
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            long startMs = System.currentTimeMillis();
            while (mState != State.STOPPED) {
                // 拉取数据，解码
                int inputBufferId = mMediaDecoder.dequeueInputBuffer(TIME_OUT_US);
                Log.d(TAG, "run: inputBufferId = " + inputBufferId);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = mMediaDecoder.getInputBuffer(inputBufferId);
                    int sampleSize = mMediaExtractor.readSampleData(inputBuffer, 0);
                    long sampleTime = mMediaExtractor.getSampleTime();
                    Log.d(TAG, "run: sampleSize = " + sampleSize + "; sampleTime = " + sampleTime);
                    if (sampleSize > 0) {
                        // 正常读取
                        mMediaDecoder.queueInputBuffer(inputBufferId, 0, sampleSize, sampleTime, 0);
                        mMediaExtractor.advance();
                    } else {
                        // 到末尾了
                        mMediaDecoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }

                if (mState == State.PAUSED) {
                    synchronized (mLock) {
                        Log.d(TAG, "run: pause");
                        mAudioTrack.pause();
                        try {
                            mLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        mAudioTrack.play();
                    }
                }

                // 获取已解码数据
                int outputBufferId = mMediaDecoder.dequeueOutputBuffer(bufferInfo, TIME_OUT_US);
                Log.d(TAG, "run: outputBufferId = " + outputBufferId);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = mMediaDecoder.getOutputBuffer(outputBufferId);
                    if (tempBuffer.length < bufferInfo.size) {
                        tempBuffer = new byte[bufferInfo.size];
                    }
                    outputBuffer.position(0);
                    outputBuffer.get(tempBuffer, 0, bufferInfo.size);
                    outputBuffer.clear();

                    mAudioTrack.write(tempBuffer, 0, bufferInfo.size);
                    mMediaDecoder.releaseOutputBuffer(outputBufferId, false);

                    if (mCallback != null) {
                        mCallback.onDuration(bufferInfo.presentationTimeUs, mDuration);
                        Log.d(TAG, "run: bufferInfo.presentationTimeUs = " + bufferInfo.presentationTimeUs);
                        Log.d(TAG, "run: mDuration = " + mDuration);
                        mCallback.onVolume(tempBuffer[0], 128);
                    }

                    // 等待
                    Log.d(TAG, "onCreate: 延时等待，如果缓冲区里的可展示时间 > 当前视频播放的进度，就休眠一下");
                    while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs + 10/* left a little bit */) {     // 25. 延迟，避免渲染过快
                        long presentationTime = bufferInfo.presentationTimeUs / 1000;
                        long presentedTime = System.currentTimeMillis() - startMs;
                        Log.d(TAG, "onCreate: presentationTimeUs = " + presentationTime);
                        Log.d(TAG, "onCreate: presentedTime = " + presentedTime);
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "run: INFO_OUTPUT_FORMAT_CHANGED");
                }

                // 判断是否已在文件末尾
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "run: End of Stream");
                    break;
                }
            }

            mAudioTrack.stop();
            mMediaDecoder.stop();
            mState = State.STOPPED;
            Log.d(TAG, "run: Task End");
        }
    };

    public enum State {
        IDLE, PREPARED, PLAYING, PAUSED, STOPPED
    }

    public interface Callback {
        void onVolume(long current, long max);
        void onDuration(long current, long max);
    }
}
