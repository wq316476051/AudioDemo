package com.wang.review1.server;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MusicPlayer extends PlayerBase {

    private static final String TAG = "MusicPlayer";

    private static final int MSG_UPDATE_PROGRESS = 0;
    private static final int MSG_PLAY_STATE_CHANGE = 1;

    private static final long TIMEOUT_US = TimeUnit.MILLISECONDS.toMicros(10);

    private int streamType = AudioManager.STREAM_MUSIC;
    private int sampleRateInHz = 44100; // 44.1 kHz : CD。人能识别的 20 kHz的两倍
    private int channelConfig = AudioFormat.CHANNEL_OUT_STEREO; // 立体音（双声道）
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
    private int mode = AudioTrack.MODE_STREAM;
    private MediaExtractor mMediaExtractor;
    private MediaFormat mMediaFormat;
    private MediaCodec mMediaDecoder;
    private AudioTrack mAudioTrack;
    private ExecutorService mPlayThread;
    private MediaFormat mOutputFormat;
    private Callback mCallback;

    private Object mLock = new Object();

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_PROGRESS:
                    if (mCallback != null) {
                        mCallback.onProgress(msg.arg1/*max*/, msg.arg2/*progress*/);
                    }
                    break;
                case MSG_PLAY_STATE_CHANGE:
                    if (mCallback != null) {
                        mCallback.onStateChanged(msg.arg1/*play state*/);
                    }
                    break;
            }
        }
    };

    public MusicPlayer(Context context) {
        super(context);
        // TODO: 2018/10/28 构造 MediaExtractor
        try {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(context.getAssets().openFd("LaLaLoveOnMyMind.mp3"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "MusicPlayer: mediaExtractor = " + mMediaExtractor);

        // TODO: 2018/10/28 构造 AudioFormat
        mMediaFormat = getAudioMediaFormat(mMediaExtractor);
        Log.d(TAG, "MusicPlayer: mediaFormat = " + mMediaFormat);

        // TODO: 2018/10/28 构造 MediaCodec
        try {
            mMediaDecoder = MediaCodec.createDecoderByType(mMediaFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            e.printStackTrace();
        }

        // TODO: 2018/10/28 构造 AudioTrack
        mAudioTrack = new AudioTrack(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);

        // TODO: 2018/10/28 创建线程
        mPlayThread = Executors.newSingleThreadExecutor();
    }

    @Override
    protected boolean isPlaying() {
        return mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }

    private boolean isPaused() {
        return mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PAUSED;
    }

    private boolean isStopped() {
        return mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_STOPPED;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    protected void onPlay() {
        Log.d(TAG, "onPlay: isPaused = " + isPaused());
        if (isPaused()) {
            mAudioTrack.play();
            synchronized (mLock) {
                mLock.notifyAll();
            }
        } else {
            mPlayThread.execute(mPlayTask);
//            mPlayThread.submit(mPlayTask);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause: isPlaying = " + isPlaying());
        if (isPlaying()) {
            mAudioTrack.pause();
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop: isPlaying = " + isPlaying());
        Log.d(TAG, "onStop: isPaused = " + isPaused());
        if (isPlaying()) {
            mAudioTrack.stop();
        }
        if (isPaused()) {
            mAudioTrack.stop();
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }
    }

    @Override
    protected void setVolume(float volume) {
        mAudioTrack.setVolume(volume);
        mAudioTrack.getPlaybackHeadPosition();
    }

    public void release() {
        // TODO 释放资源
        mHandler.removeCallbacksAndMessages(null);

        mAudioTrack.release();
        mAudioTrack = null;

        mMediaDecoder.release();
        mMediaDecoder = null;

        mMediaExtractor.release();
        mMediaExtractor = null;
    }

    private Runnable mPlayTask = new Runnable() {
        @Override
        public void run() {
            mMediaExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            mMediaDecoder.configure(mMediaFormat, null, null, 0);
            MediaFormat inputFormat = mMediaDecoder.getInputFormat();
            Log.d(TAG, "run: inputFormat = " + inputFormat);
            mOutputFormat = mMediaDecoder.getOutputFormat();
            Log.d(TAG, "run: outputFormat = " + mOutputFormat);
            mMediaDecoder.start();

            mAudioTrack.play();
            sendStateChangeMessage(mAudioTrack.getPlayState());

            boolean isEndOfStream = false;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            byte[] tempBytes = new byte[0];
            while (!isEndOfStream) {
                // TODO: 2018/10/28 取数据
                // TODO: 2018/10/28 数据解码
                int inputBufferId = mMediaDecoder.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer = mMediaDecoder.getInputBuffer(inputBufferId);
                    int sampleSize = mMediaExtractor.readSampleData(inputBuffer, 0);
//                    long sampleSize1 = mMediaExtractor.getSampleSize();
                    long sampleTime = mMediaExtractor.getSampleTime();
                    int sampleFlags = mMediaExtractor.getSampleFlags();
                    Log.d(TAG, "run: sampleSize = " + sampleSize);
//                    Log.d(TAG, "run: sampleSize1 = " + sampleSize1);
                    Log.d(TAG, "run: sampleTime = " + sampleTime);
                    Log.d(TAG, "run: sampleFlags = " + sampleFlags);

                    if (sampleSize > 0) {
                        mMediaDecoder.queueInputBuffer(inputBufferId, 0, sampleSize, sampleTime, 0);
                        mMediaExtractor.advance();
                    } else {
                        mMediaDecoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }

                // TODO: 2018/10/28 处理暂停
                synchronized (mLock) {
                    if (isPaused()) {
                        try {
                            sendStateChangeMessage(mAudioTrack.getPlayState());
                            Log.d(TAG, "run: before wait");
                            mLock.wait();
                        } catch (InterruptedException e) {
                            Log.d(TAG, "Interrupted");
                        }
                        Log.d(TAG, "run: after wait");
                    }
                }

                // TODO: 2018/10/28 处理停止
                Log.d(TAG, "run: state = " + mAudioTrack.getPlayState());
                if (isStopped()) {
                    break;
                }


                // TODO: 2018/10/28 取解码数据
                // TODO: 2018/10/28 向AudioTrack 写数据
                int outputBufferId = mMediaDecoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                if (outputBufferId >= 0) {
                    ByteBuffer outputBuffer = mMediaDecoder.getOutputBuffer(outputBufferId);
                    if (tempBytes.length < bufferInfo.size) {
                        tempBytes = new byte[bufferInfo.size];
                    }
                    outputBuffer.position(0);
                    outputBuffer.get(tempBytes, 0, bufferInfo.size);
                    outputBuffer.clear();
                    mMediaDecoder.releaseOutputBuffer(outputBufferId, false);
                    mAudioTrack.write(outputBuffer, bufferInfo.size, AudioTrack.WRITE_BLOCKING);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutputFormat = mMediaDecoder.getOutputFormat();
                } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    Log.d(TAG, "run: MediaCodec.INFO_TRY_AGAIN_LATER");
                }

                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "run: End of Stream!!!");
                    isEndOfStream = true;
                }
            }

            mAudioTrack.stop();
            mMediaDecoder.stop();
            sendProgressMessage(100, 100);
            sendStateChangeMessage(mAudioTrack.getPlayState());
        }
    };

    private void sendProgressMessage(int max, int progress) {
        Message msg = Message.obtain(mHandler, MSG_UPDATE_PROGRESS, max, progress);
        mHandler.sendMessage(msg);
    }

    private void sendStateChangeMessage(int playState) {
        Message msg = Message.obtain(mHandler, MSG_UPDATE_PROGRESS, playState, 0);
        mHandler.sendMessage(msg);
    }

    private MediaFormat getAudioMediaFormat(MediaExtractor mediaExtractor) {
        for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
            MediaFormat trackFormat = mediaExtractor.getTrackFormat(i);
            if (trackFormat.containsKey(MediaFormat.KEY_MIME)) {
                String mimetype = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mimetype != null && mimetype.startsWith("audio")) {
                    mediaExtractor.selectTrack(i);
                    return trackFormat;
                }
            }
        }
        return null;
    }

    public interface Callback {
        void onProgress(int max, int progress);
        void onStateChanged(int playState);
    }
}
