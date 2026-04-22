package com.maksz42.periscope.camera.rtsp;

import android.media.AudioTrack;

import com.maksz42.periscope.media.audio.PCM;

import java.util.concurrent.locks.LockSupport;

class AudioPlayer {
    private final static int CAPACITY = (1 << 12);

    private final byte[] ringBuf = new byte[CAPACITY];
    private final short[] decodeBuffer = new short[CAPACITY];
    private final Thread audioThread = new Thread(this::playLoop);
    private final AudioEncoding audioEncoding;
    private final AudioTrack audioTrack;

    private volatile int head;
    private int headShadow;
    private volatile int tail;
    private int tailShadow;

    AudioPlayer(AudioEncoding audioEncoding, AudioTrack audioTrack) {
        this.audioEncoding = audioEncoding;
        this.audioTrack = audioTrack;
        audioTrack.play();
        this.audioThread.start();
    }

    private static int masked(int counter) {
        return counter & (CAPACITY - 1);
    }

    private static int getCount(int head, int tail) {
        return head - tail;
    }

    private static int getCountNoWrap(int head, int tail) {
        return Math.min(getCount(head, tail), CAPACITY - masked(tail));
    }

    private static int getFree(int head, int tail) {
        return CAPACITY - getCount(head, tail);
    }

    private static int getFreeNoWrap(int head, int tail) {
        return Math.min(getFree(head, tail), CAPACITY - masked(head));
    }

    void write(byte[] data, int offset, int len) {
        int localHead = headShadow;
        int localTail = tail;

        int toWrite;
        int free = getFree(localHead, localTail);
        if (len <= free) {
            toWrite = len;
        } else {
            toWrite = free;
            // drop data that don't fit
            offset += len - toWrite;
        }
        int toEnd = Math.min(toWrite, getFreeNoWrap(localHead, localTail));
        System.arraycopy(data, offset, ringBuf, masked(localHead), toEnd);
        int fromStart = toWrite - toEnd;
        System.arraycopy(data, offset + toEnd, ringBuf, 0, fromStart);
        localHead += toWrite;
        head = localHead;
        LockSupport.unpark(audioThread);
        headShadow = localHead;
    }

    private boolean consume() {
        int localTail = tailShadow;
        int localHead;

        while (true) {
            if (audioThread.isInterrupted()) return false;
            localHead = head;
            if (localHead != localTail) break;
            LockSupport.park();
        }

        int toRead = getCount(localHead, localTail);
        switch (audioEncoding) {
            case PCMA -> {
                for (int i = 0; i < toRead; i++) {
                    int idx = masked(i + localTail);
                    decodeBuffer[i] = PCM.fromALaw(ringBuf[idx]);
                }
            }
            case PCMU -> {
                for (int i = 0; i < toRead; i++) {
                    int idx = masked(i + localTail);
                    decodeBuffer[i] = PCM.fromULaw(ringBuf[idx]);
                }
            }
        }
        localTail += toRead;
        tail = localTail;
        audioTrack.write(decodeBuffer, 0, toRead);
        tailShadow = localTail;
        return true;
    }

    private void playLoop() {
        while (consume());
        audioTrack.release();
    }

    void shutdown() {
        // Hope this makes stop() thread-safe
        try {
            audioTrack.pause();
        } catch (IllegalStateException ignored) { }
        audioThread.interrupt();
    }
}
