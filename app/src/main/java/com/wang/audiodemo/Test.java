package com.wang.audiodemo;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;

public class Test {

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };

    public Test() throws IOException {

        MediaCodec mediaCodec = MediaCodec.createByCodecName("");
        mediaCodec.setCallback(new MediaCodec.Callback() {
            @Override
            public void onInputBufferAvailable(MediaCodec codec, int index) {

            }

            @Override
            public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {

            }

            @Override
            public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            }

            @Override
            public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            }
        });
        mediaCodec.configure(null, null, null, 0);
        mediaCodec.start();
        // wait for processing to complete
        mediaCodec.stop();
        mediaCodec.release();

    }
}
