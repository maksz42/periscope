package com.maksz42.periscope.camera.rtsp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.maksz42.periscope.utils.IO;

import java.io.DataInputStream;
import java.io.IOException;

class AudioPlayer {
    private final short[] decodeBuffer;
    private final short[] lawToPcmTable;
    private final AudioTrack audioTrack;

    private int head;
    private int tail;

    AudioPlayer(AudioEncoding audioEncoding, int rate) {
        this.lawToPcmTable = switch (audioEncoding) {
          case PCMA -> PCM.alawToPcmTable;
          case PCMU -> PCM.ulawToPcmTable;
        };

        int bufSize = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        this.audioTrack = new AudioTrack(
            AudioManager.STREAM_MUSIC,
            rate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufSize,
            AudioTrack.MODE_STREAM);
        audioTrack.play();

        int halfSecSamples = (rate + 1) >> 1;
        int halfSecCeilBufSize = Integer.highestOneBit(halfSecSamples - 1) << 1;
        decodeBuffer = new short[halfSecCeilBufSize];
    }

    int getCapacity() {
        return decodeBuffer.length;
    }

    private int masked(int counter) {
        return counter & (decodeBuffer.length - 1);
    }

    int getCount() {
        return head - tail;
    }

    private int getCountNoWrap() {
        return Math.min(getCount(), decodeBuffer.length - masked(tail));
    }

    private int getFree() {
        return decodeBuffer.length - getCount();
    }

    private int getFreeNoWrap() {
        return Math.min(getFree(), decodeBuffer.length - masked(head));
    }

    void halfFlush() {
        tail += Math.max(getCount() - (decodeBuffer.length / 2), 0);
    }

    void feed(DataInputStream in, int len) throws IOException {
        int toWrite = Math.min(len, decodeBuffer.length);
        int toDiscard = Math.max(toWrite - getFree(), 0);
        int toSkip = len - toWrite;
        IO.skipNBytes(in, toSkip);

        tail += toDiscard;

        int toEnd = Math.min(toWrite, getFreeNoWrap());
        for (int i = masked(head); i < masked(head) + toEnd; i++) {
            decodeBuffer[i] = lawToPcmTable[in.read()];
        }

        int fromStart = toWrite - toEnd;
        for (int i = 0; i < fromStart; i++) {
            decodeBuffer[i] = lawToPcmTable[in.read()];
        }

        head += toWrite;
    }

    void consume() {
        int toEnd = getCountNoWrap();
        audioTrack.write(decodeBuffer, masked(tail), toEnd);

        int toRead = getCount();
        int fromStart = toRead - toEnd;
        audioTrack.write(decodeBuffer, 0, fromStart);

        tail += toRead;
    }

    void shutdown() {
        try {
            audioTrack.pause();
        } catch (IllegalStateException ignored) { }
        audioTrack.release();
    }
}
