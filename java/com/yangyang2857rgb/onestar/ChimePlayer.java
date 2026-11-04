package com.yangyang2857rgb.onestar;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * 拾光 v3.0 星光音：用 AudioTrack 实时合成钟磬，无音频资源、无权限。
 * 基频随心情变化（5 档），再按半音变调，让每个日期都有自己的一声。
 */
final class ChimePlayer {

    private static final int SR = 44100;

    /** 5 种心情对应的基频（Hz） */
    private static final float[] BASE = {528f, 660f, 594f, 440f, 792f};

    private ChimePlayer() {
    }

    static void play(int mood) {
        playNote(mood, 0, 1900);
    }

    /** mood 决定音色基频，semitone 为半音偏移，holdMs 为线程保持时长 */
    static void playNote(int mood, int semitone, final int holdMs) {
        final int m = (mood < 0 || mood >= BASE.length) ? 0 : mood;
        final float f0 = (float) (BASE[m] * Math.pow(2.0, semitone / 12.0));
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                AudioTrack track = null;
                try {
                    short[] pcm = synth(f0, holdMs);
                    int bytes = pcm.length * 2;
                    track = new AudioTrack(AudioManager.STREAM_MUSIC, SR,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            Math.max(bytes, 4096),
                            AudioTrack.MODE_STREAM);
                    if (track.getState() != AudioTrack.STATE_INITIALIZED) return;
                    track.play();
                    track.write(pcm, 0, pcm.length);
                    Thread.sleep(holdMs);
                } catch (Throwable ignored) {
                } finally {
                    if (track != null) {
                        try {
                            track.stop();
                        } catch (Throwable ignored) {
                        }
                        track.release();
                    }
                }
            }
        }, "chime");
        t.setDaemon(true);
        t.start();
    }

    private static short[] synth(float f0, int holdMs) {
        int n = (int) (Math.min(1.9f, Math.max(0.6f, holdMs / 1000f + 0.4f)) * SR);
        // v5.2：原生内核合成（libons_dsp.so），失败则退回下面的 Java 实现
        if (NativeCore.DSP) {
            try {
                short[] fast = new short[n];
                if (NativeCore.chime(fast, n, f0, SR) == 0) return fast;
            } catch (Throwable ignored) {
            }
        }
        float[] buf = new float[n];

        for (int i = 0; i < n; i++) {
            float t = i / (float) SR;
            float env = (float) Math.exp(-t * 3.1);
            float v = (float) (1.00 * Math.sin(2 * Math.PI * f0 * t) * env
                    + 0.40 * Math.sin(2 * Math.PI * f0 * 2.0 * t) * Math.exp(-t * 5.2)
                    + 0.18 * Math.sin(2 * Math.PI * f0 * 3.01 * t) * Math.exp(-t * 7.8)
                    + 0.08 * Math.sin(2 * Math.PI * f0 * 0.5 * t) * Math.exp(-t * 2.2));
            buf[i] = v;
        }

        // 短延迟余响：11cm 腔体感
        int d = (int) (0.105f * SR);
        for (int i = d; i < n; i++) {
            buf[i] += buf[i - d] * 0.28f;
        }

        // 6ms 起振 + 80ms 收尾淡出
        int atk = (int) (0.006f * SR);
        int out = (int) (0.08f * SR);
        for (int i = 0; i < atk && i < n; i++) buf[i] *= i / (float) atk;
        for (int i = 0; i < out && i < n; i++) buf[n - 1 - i] *= i / (float) out;

        short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            float v = buf[i] * 0.32f;
            if (v > 1f) v = 1f;
            if (v < -1f) v = -1f;
            pcm[i] = (short) (v * 32767);
        }
        return pcm;
    }
}
