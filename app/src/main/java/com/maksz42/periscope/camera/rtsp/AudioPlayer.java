package com.maksz42.periscope.camera.rtsp;

import android.media.AudioTrack;

import com.maksz42.periscope.media.audio.PCM;

import java.util.concurrent.locks.LockSupport;

class AudioPlayer {
    private final static int CAPACITY = (1 << 12);
    private volatile int head;
    private volatile int tail;
    private final byte[] ring_buf;
    private final short[] decodeBuffer;
    private final AudioEncoding audioEncoding;
    private final AudioTrack audioTrack;
    private final Thread audioThread = new Thread(this::playLoop);

    AudioPlayer(AudioEncoding audioEncoding, AudioTrack audioTrack) {
        this.ring_buf = new byte[CAPACITY];
        this.decodeBuffer = new short[CAPACITY];
        this.audioEncoding = audioEncoding;
        this.audioTrack = audioTrack;
        audioTrack.play();
        this.audioThread.start();
    }

    private static int masked(int counter, int capacity) {
        return counter & (capacity - 1);
    }

    private static int getCount(int head, int tail) {
        return head - tail;
    }

    private static int getCountNoWrap(int capacity, int head, int tail) {
        return Math.min(getCount(head, tail), capacity - masked(tail, capacity));
    }

    private static int getFree(int capacity, int head, int tail) {
        return capacity - getCount(head, tail);
    }

    private static int getFreeNoWrap(int capacity, int head, int tail) {
        return Math.min(getFree(capacity, head, tail), capacity - masked(head, capacity));
    }

    void write(byte[] data, int offset, int len) {
        int localHead = head;
        int localTail = tail;

        int toWrite;
        int free = getFree(ring_buf.length, localHead, localTail);
        if (len <= free) {
            toWrite = len;
        } else {
            toWrite = free;
            // drop data that don't fit
            offset += len - toWrite;
        }
        int toEnd = Math.min(toWrite, getFreeNoWrap(ring_buf.length, localHead, localTail));
        System.arraycopy(data, offset, ring_buf, masked(localHead, ring_buf.length), toEnd);
        int fromStart = toWrite - toEnd;
        System.arraycopy(data, offset + toEnd, ring_buf, 0, fromStart);
        head = localHead + toWrite;
        LockSupport.unpark(audioThread);
    }

    private boolean consume() {
        int localTail = tail;
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
                    int idx = masked(i + localTail, ring_buf.length);
                    decodeBuffer[i] = PCM.fromALaw(ring_buf[idx]);
                }
            }
            case PCMU -> {
                for (int i = 0; i < toRead; i++) {
                    int idx = masked(i + localTail, ring_buf.length);
                    decodeBuffer[i] = PCM.fromULaw(ring_buf[idx]);
                }
            }
        }
        tail = localTail + toRead;
        audioTrack.write(decodeBuffer, 0, toRead);
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
