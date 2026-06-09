package com.maksz42.periscope.camera.rtsp;

import android.media.AudioTrack;
import android.os.Process;

import com.maksz42.periscope.utils.IO;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.concurrent.locks.LockSupport;

class AudioPlayer {
    private final static int CAPACITY = (1 << 12);

    private final byte[] ringBuf = new byte[CAPACITY];
    private final short[] decodeBuffer = new short[CAPACITY];
    private final Thread audioThread = new Thread(this::playLoop);
    private final short[] lawToPcmTable;
    private final AudioTrack audioTrack;

    private volatile int head;
    private int headShadow;
    private volatile int tail;
    private int tailShadow;

    AudioPlayer(AudioEncoding audioEncoding, AudioTrack audioTrack) {
        this.lawToPcmTable = switch (audioEncoding) {
          case PCMA -> PCM.alawToPcmTable;
          case PCMU -> PCM.ulawToPcmTable;
        };
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

    void write(DataInputStream in, int len) throws IOException {
        int localHead = headShadow;
        int localTail = tail;

        int toWrite;
        int free = getFree(localHead, localTail);
        if (len <= free) {
            toWrite = len;
        } else {
            toWrite = free;
            // drop data that don't fit
            IO.skipNBytes(in, len - toWrite);
        }
        int toEnd = Math.min(toWrite, getFreeNoWrap(localHead, localTail));
        in.readFully(ringBuf, masked(localHead), toEnd);
        int fromStart = toWrite - toEnd;
        in.readFully(ringBuf, 0, fromStart);
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
        for (int i = 0; i < toRead; i++) {
            int idx = masked(i + localTail);
            decodeBuffer[i] = lawToPcmTable[ringBuf[idx] & 0xff];
        }
        localTail += toRead;
        tail = localTail;
        audioTrack.write(decodeBuffer, 0, toRead);
        tailShadow = localTail;
        return true;
    }

    private void playLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
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
