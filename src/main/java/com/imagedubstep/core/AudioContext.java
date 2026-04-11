
package com.imagedubstep.core;

import javax.sound.sampled.*;

import com.imagedubstep.audio.AudioRecorder;

public class AudioContext {
    private final double sampleRate;
    private final int blockSize;
    private final DestinationNode destination;
    private SourceDataLine line;
    private volatile boolean running=false;
    private Thread audioThread;
    private double currentTime = 0.0;
    private final java.util.Random ditherRng = new java.util.Random();
    private volatile AudioRecorder recorder;
 // --- add fields at top of class ---
    private volatile String preferredMixerSubstring = null;

    // Allow UI to set a preferred output by substring match (case-insensitive)
    public void setPreferredMixerSubstring(String s) { this.preferredMixerSubstring = s; }

    public AudioContext(double sampleRate, int blockSize){
        this.sampleRate = sampleRate; this.blockSize = blockSize;
        this.destination = new DestinationNode(this);
    }
    public double getSampleRate(){ return sampleRate; }
    public int getBlockSize(){ return blockSize; }
    public double currentTime(){ return currentTime; }
    public DestinationNode destination(){ return destination; }

    
    
    
    
    
    public void start(){
        if(running) return;
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            System.out.println("Mixer: " + mi.getName() + " - " + mi.getDescription());
            Mixer m = AudioSystem.getMixer(mi);
            for (Line.Info li : m.getSourceLineInfo()) {
                System.out.println("  Line: " + li);
            }
        }
        try{
            AudioFormat fmt = new AudioFormat((float)sampleRate, 16, 2, true, false);
            SourceDataLine candidate = null;
            
            if (preferredMixerSubstring != null && !preferredMixerSubstring.isEmpty()) {
                String needle = preferredMixerSubstring.toLowerCase();
                for (Mixer.Info info : AudioSystem.getMixerInfo()) {
                    if (info.getName().toLowerCase().contains(needle) ||
                        info.getDescription().toLowerCase().contains(needle)) {
                        Mixer m = AudioSystem.getMixer(info);
                        try {
                            candidate = (SourceDataLine) m.getLine(new DataLine.Info(SourceDataLine.class, fmt));
                            break;
                        } catch (Exception ignore) {}
                    }
                }
            }

            if (candidate == null) {
                // Default device
                candidate = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
            }
            line = candidate;
            line.open(fmt, blockSize * 8);
            line.start();
        }catch(Exception e){ throw new RuntimeException("Audio device failed: " + e.getMessage(), e); }

        running = true;
        audioThread = new Thread(() -> {
            float[] L = new float[blockSize];
            float[] R = new float[blockSize];
            byte[] out = new byte[blockSize*4];
            while (running) {
                destination.process(L, R, blockSize);
             // Tee the float mix to recorder (pre-dither/quantize)
                if (recorder != null && recorder.isRecording()) {
                    try { recorder.writeBlock(L, R, blockSize); } catch (Exception ignored) {}
                }

                int idx = 0;
                for (int i = 0; i < blockSize; i++) {
                    // TPDF dither: ±1 LSB in float domain (1/32768)
                    float dl = (float)((ditherRng.nextDouble() - ditherRng.nextDouble()) / 32768.0);
                    float dr = (float)((ditherRng.nextDouble() - ditherRng.nextDouble()) / 32768.0);

                    int li = (int) Math.max(-32768, Math.min(32767, (L[i] + dl) * 32767.0f));
                    int ri = (int) Math.max(-32768, Math.min(32767, (R[i] + dr) * 32767.0f));

                    out[idx++] = (byte) (li & 0xFF);
                    out[idx++] = (byte) ((li >> 8) & 0xFF);
                    out[idx++] = (byte) (ri & 0xFF);
                    out[idx++] = (byte) ((ri >> 8) & 0xFF);
                }
                line.write(out, 0, out.length);
                currentTime += blockSize / sampleRate;
            }


        }, "AudioContext");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void stop(){
        running = false;
        try { if(audioThread != null) audioThread.join(500); } catch(InterruptedException ignored){}
        if(line != null){ line.stop(); line.flush(); line.close(); }
    }
    
    
    
    public synchronized void startWavRecording(java.io.File wavFile) {
        try {
            if (recorder == null) recorder = new AudioRecorder((int)sampleRate);
            recorder.start(wavFile);
        } catch (Exception e) {
            throw new RuntimeException("WAV record start failed: " + e.getMessage(), e);
        }
    }
    public synchronized void stopWavRecording() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
        }
    }
    public boolean isRecordingWav() { return recorder != null && recorder.isRecording(); }
}
