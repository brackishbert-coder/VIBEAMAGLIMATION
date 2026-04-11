package com.imagedubstep.audio;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** Simple streaming 16-bit PCM WAV writer. Thread-safe start/stop/write. */
public final class AudioRecorder {
    private final int sampleRate;
    private final int channels = 2;
    private final int bytesPerSample = 2; // 16-bit
    private final Object lock = new Object();

    private RandomAccessFile raf;
    private volatile boolean recording = false;
    private long dataBytesWritten = 0;

    public AudioRecorder(int sampleRate) {
        this.sampleRate = sampleRate;
    }

    public boolean isRecording() { return recording; }

    /** Begin writing to given WAV file. If exists, it will be overwritten. */
    public void start(File wavFile) throws IOException {
        synchronized (lock) {
            stopInternalSilently(); // if already open
            raf = new RandomAccessFile(wavFile, "rw");
            raf.setLength(0); // overwrite
            writeWavHeaderPlaceholder();
            dataBytesWritten = 0;
            recording = true;
        }
    }

    /** Write a block of float stereo samples in [-1,1]. No dither here (clean). */
    public void writeBlock(float[] L, float[] R, int n) throws IOException {
        if (!recording) return;
        synchronized (lock) {
            if (raf == null) return;
            // interleave LR as 16-bit little-endian
            // we don’t allocate every call for GC: reuse a byte[] big enough
            int frameBytes = n * channels * bytesPerSample;
            byte[] buf = new byte[frameBytes];
            int bi = 0;
            for (int i = 0; i < n; i++) {
                int li = clamp16((int)Math.round(L[i] * 32767.0));
                int ri = clamp16((int)Math.round(R[i] * 32767.0));
                // little-endian
                buf[bi++] = (byte)(li & 0xFF);
                buf[bi++] = (byte)((li >>> 8) & 0xFF);
                buf[bi++] = (byte)(ri & 0xFF);
                buf[bi++] = (byte)((ri >>> 8) & 0xFF);
            }
            raf.write(buf, 0, buf.length);
            dataBytesWritten += buf.length;
        }
    }

    public void stop() throws IOException {
        synchronized (lock) {
            stopInternalSilently();
        }
    }

    private void stopInternalSilently() throws IOException {
        if (raf != null) {
            // finalize header sizes
            finalizeWavHeader();
            raf.getFD().sync();
            raf.close();
            raf = null;
        }
        recording = false;
        dataBytesWritten = 0;
    }

    private static int clamp16(int v){
        if (v > 32767) return 32767;
        if (v < -32768) return -32768;
        return v;
    }

    private void writeWavHeaderPlaceholder() throws IOException {
        // RIFF header with 0 data size; we’ll fix sizes on stop()
        raf.seek(0);
        writeAscii("RIFF");                 // ChunkID
        writeLE32(36);                      // ChunkSize (placeholder; 36 + data)
        writeAscii("WAVE");                 // Format

        // fmt  subchunk
        writeAscii("fmt ");                 // Subchunk1ID
        writeLE32(16);                      // Subchunk1Size (PCM)
        writeLE16(1);                       // AudioFormat = PCM
        writeLE16((short)channels);         // NumChannels
        writeLE32(sampleRate);              // SampleRate
        writeLE32(sampleRate * channels * bytesPerSample); // ByteRate
        writeLE16((short)(channels * bytesPerSample));     // BlockAlign
        writeLE16((short)(bytesPerSample * 8));            // BitsPerSample

        // data subchunk
        writeAscii("data");                 // Subchunk2ID
        writeLE32(0);                       // Subchunk2Size (placeholder)
    }

    private void finalizeWavHeader() throws IOException {
        long dataSize = dataBytesWritten;
        long riffSize = 36 + dataSize;
        raf.seek(4);  writeLE32((int)riffSize);
        raf.seek(40); writeLE32((int)dataSize);
    }

    private void writeAscii(String s) throws IOException {
        raf.write(s.getBytes(StandardCharsets.US_ASCII));
    }
    private void writeLE16(int v) throws IOException {
        raf.write(v & 0xFF);
        raf.write((v >>> 8) & 0xFF);
    }
    private void writeLE32(int v) throws IOException {
        raf.write(v & 0xFF);
        raf.write((v >>> 8) & 0xFF);
        raf.write((v >>> 16) & 0xFF);
        raf.write((v >>> 24) & 0xFF);
    }
}
