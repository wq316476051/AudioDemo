package com.wang.audiodemo;

import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.JetPlayer;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.PlaybackParams;
import android.media.VolumeShaper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AudioPlayer {

    private static final String TAG = "AudioPlayer";

    private static final long TIME_OUT_US = 10_000 /*10 毫秒*/; // 单位 microseconds

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
    private Handler mLoudlessHandler;
    private int mSampleRateInHz;
    private int mChannelCount;
    private int mPcmEncoding;
    private int mBitRate;

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
                Log.d(TAG, "prepare: mMediaFormat = " + mMediaFormat);
                // mMediaFormat = {sample-rate=44100, track-id=1, durationUs=180618562, mime=audio/mpeg, channel-count=2, bitrate=128000}

                mSampleRateInHz = mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE); // 采样率单位 Hz，个每秒，
                mDuration = mMediaFormat.getLong(MediaFormat.KEY_DURATION); // 时间长度，单位 microseconds
                mMimetype = mimetype;
                mChannelCount = mMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT); // 声道数
                mBitRate = mMediaFormat.getInteger(MediaFormat.KEY_BIT_RATE); // 比特率， 单位 bps: bits per second【此处应该是压缩后的比特率】
                break;
            }
        }

        // 3. 创建 MediaCodec
        Log.d(TAG, "prepare: 创建 MediaCodec");
        try {
            mMediaDecoder = MediaCodec.createDecoderByType(mMimetype);
            mMediaDecoder.configure(mMediaFormat, null, null, 0); // start later


            // TODO for test
            MediaFormat inputFormat = mMediaDecoder.getInputFormat();
            Log.d(TAG, "prepare: inputFormat = " + inputFormat);
            // inputFormat = {sample-rate=44100, mime=audio/mpeg, channel-count=2}


            // TODO for test
            MediaFormat outputFormat = mMediaDecoder.getOutputFormat();
            Log.d(TAG, "prepare: outputFormat = " + outputFormat);
            // outputFormat = {sample-rate=44100, pcm-encoding=2, mime=audio/raw, channel-count=2}
            // 计算比特率：
            // int bitRate = sample-rate * (pcm-encoding * 8) * 2;



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
        mAudioTrack = new AudioTrack(streamType, sampleRateInHz,          // 11. 创建 AudioTrack，play later
                channelConfig, audioFormat, bufferSizeInBytes, mode);
        PlaybackParams params = new PlaybackParams();
        params.setSpeed(0.5f); // 慢速有效，快速无效。设置快速时，必须调整bufferSize，如2倍速就乘以2
        mAudioTrack.setPlaybackParams(params);
//        mAudioTrack = new AudioTrack.Builder()
//                .setAudioAttributes(new AudioAttributes.Builder()
//                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
//                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
//                        .setUsage(AudioAttributes.USAGE_MEDIA)
//                        .build())
//                .setAudioFormat(new AudioFormat.Builder()
//                        .setSampleRate(sampleRateInHz)
//                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
//                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
//                        .build())
//                .setBufferSizeInBytes(bufferSizeInBytes)
//                .setTransferMode(AudioTrack.MODE_STREAM)
//                .build();
//        VolumeShaper.Configuration config =
//                new VolumeShaper.Configuration.Builder()
//                        .setDuration(3000)
//                        .setCurve(new float[] {0.f, 1.f}, new float[] {0.f, 1.f})
//                        .setInterpolatorType(VolumeShaper.Configuration.INTERPOLATOR_TYPE_LINEAR)
//                        .build();
//        VolumeShaper volumeShaper = mAudioTrack.createVolumeShaper(config);
//        mAudioTrack.getTimestamp(new AudioTimestamp());

        // 5. 创建任务线程池
        Log.d(TAG, "prepare: 任务线程池");
        mExecutor = Executors.newSingleThreadExecutor();

        // 6. 处理响度和播放进度
        HandlerThread thread = new HandlerThread("loudless");
        thread.start();
        mLoudlessHandler = new Handler(thread.getLooper(), mLoudlessHandlerCallback);

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
                // 第一阶段：从文件中拉取数据，解码
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
                        mMediaExtractor.advance(); // 继续
                    } else {
                        // 到末尾了，End of Stream
                        mMediaDecoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }

                // 处理暂停、继续播放
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
                        tempBuffer = new byte[bufferInfo.size]; // 缓冲区不够的话，重新分配
                    }

                    outputBuffer.position(0);
                    outputBuffer.get(tempBuffer, 0, bufferInfo.size); // 转移数据
                    outputBuffer.clear();

                    mAudioTrack.write(tempBuffer, 0, bufferInfo.size); // AudioTrack 播放
                    mMediaDecoder.releaseOutputBuffer(outputBufferId, false); // 释放


                    // 展示响度变化
                    for (int i = 0; i < bufferInfo.size; i += (mBitRate / 8) * mChannelCount * (1000 / 50)) { //
                        Message msg = Message.obtain();
                        msg.what = MSG_OFFER_LOUDLESS;
                        msg.arg1 = tempBuffer[i];
                        mLoudlessHandler.sendMessage(msg);
                    }

                    // 展示播放进度
                    Message msg = Message.obtain();
                    msg.what = MSG_PLAY_PROCESS;
                    msg.arg1 = (int) bufferInfo.presentationTimeUs;
                    msg.arg2 = (int) mDuration;
                    mLoudlessHandler.sendMessage(msg);

                    // 等待
                    Log.d(TAG, "run: 延时等待，如果缓冲区里的可展示时间 > 当前视频播放的进度，就休眠一下");
                    while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs + 10/* leave a little bit */) {     // 25. 延迟，避免渲染过快
                        long presentationTime = bufferInfo.presentationTimeUs / 1000;
                        long presentedTime = System.currentTimeMillis() - startMs;
                        Log.d(TAG, "run: presentationTimeUs = " + presentationTime);
                        Log.d(TAG, "run: presentedTime = " + presentedTime);
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    Log.d(TAG, "run: INFO_OUTPUT_FORMAT_CHANGED");
                }

                // 判断是否已在文件末尾，结束
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

    private static final int MSG_OFFER_LOUDLESS = 1024;
    private static final int MSG_POLL_LOUDLESS = 1025;
    private static final int MSG_PLAY_PROCESS = 1026;
    private Handler.Callback mLoudlessHandlerCallback = new Handler.Callback() {
        private Queue<Byte> mQueue = new LinkedList<>();
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_OFFER_LOUDLESS:
                    mQueue.offer(Byte.valueOf((byte) msg.arg1));
                    if (!mLoudlessHandler.hasMessages(MSG_POLL_LOUDLESS)) {
//                    Message.obtain(mLoudlessHandler, MSG_POLL_LOUDLESS).sendToTarget();
                        handleMessage(Message.obtain(mLoudlessHandler, MSG_POLL_LOUDLESS));
                    }
                    return true;
                case MSG_POLL_LOUDLESS:
                    if (mLoudlessHandler.hasMessages(MSG_POLL_LOUDLESS)) {
                        mLoudlessHandler.removeMessages(MSG_POLL_LOUDLESS);
                    }
                    mLoudlessHandler.sendMessageDelayed(Message.obtain(mLoudlessHandler, MSG_POLL_LOUDLESS), 1000 / 50);
                    Byte loud = mQueue.poll();
                    if (mCallback != null && loud != null) {
                        mCallback.onLoudlessChange(loud, 128);
                    }
                    return true;
                case MSG_PLAY_PROCESS:
                    int process = msg.arg1;
                    int max = msg.arg2;
                    if (mCallback != null) {
                        mCallback.onPlayProgressChange(process, max);
                    }
                    return true;
            }
            return false;
        }
    };

    public enum State {
        IDLE, PREPARED, PLAYING, PAUSED, STOPPED
    }

    public interface Callback {
        void onLoudlessChange(long loudless, long max);
        void onPlayProgressChange(long progress, long max);
    }
}
