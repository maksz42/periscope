package com.maksz42.periscope.camera.rtsp;

import android.media.AudioTrack;

import com.maksz42.periscope.utils.IO;

import java.io.DataInputStream;
import java.io.IOException;

class AudioPlayer {
    static final int CAPACITY = (1 << 13);

    private final short[] decodeBuffer = new short[CAPACITY];
    private final short[] lawToPcmTable;
    private final AudioTrack audioTrack;

    private int head;
    private int tail;

    AudioPlayer(AudioEncoding audioEncoding, AudioTrack audioTrack) {
        this.lawToPcmTable = switch (audioEncoding) {
          case PCMA -> PCM.alawToPcmTable;
          case PCMU -> PCM.ulawToPcmTable;
        };
        this.audioTrack = audioTrack;
        audioTrack.play();
    }

    private static int masked(int counter) {
        return counter & (CAPACITY - 1);
    }

    int getCount() {
        return head - tail;
    }

    private int getCountNoWrap() {
        return Math.min(getCount(), CAPACITY - masked(tail));
    }

    private int getFree() {
        return CAPACITY - getCount();
    }

    private int getFreeNoWrap() {
        return Math.min(getFree(), CAPACITY - masked(head));
    }

    void halfFlush() {
        tail += Math.max(getCount() - (CAPACITY / 2), 0);
    }

    void feed(DataInputStream in, int len) throws IOException {
        int toWrite = Math.min(len, CAPACITY);
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
