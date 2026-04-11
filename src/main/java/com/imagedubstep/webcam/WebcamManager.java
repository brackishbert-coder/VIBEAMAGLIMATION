package com.imagedubstep.webcam;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

public final class WebcamManager {
    private Webcam cam;
    private Thread loop;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Settings
    private int targetFps = 15;           // throttle capture
    private Dimension requested = WebcamResolution.VGA.getSize(); // 640x480

    public static List<Webcam> listCameras() {
        return Webcam.getWebcams();
    }

    public boolean open(Webcam w) {
        close();
        if (w == null) return false;
        cam = w;
        try {
            cam.setCustomViewSizes(new Dimension[]{ requested });
            cam.setViewSize(requested);
            cam.open(true);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            cam = null;
            return false;
        }
    }

    /** Start a capture loop which calls onFrame(imageFx) on the FX thread. */
    public void start(java.util.function.Consumer<Image> onFrame) {
        if (cam == null || running.get()) return;
        running.set(true);
        loop = new Thread(() -> {
            final long sleepMs = Math.max(1, 1000 / Math.max(1, targetFps));
            while (running.get()) {
                try {
                    BufferedImage bi = cam.getImage();
                    if (bi != null) {
                        WritableImage fx = SwingFXUtils.toFXImage(bi, null);
                        Platform.runLater(() -> onFrame.accept(fx));
                    }
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ie) {
                    break;
                } catch (Throwable t) {
                    t.printStackTrace();
                    break;
                }
            }
        }, "WebcamCapture");
        loop.setDaemon(true);
        loop.start();
    }

    public void stop() {
        running.set(false);
        if (loop != null) {
            loop.interrupt();
            loop = null;
        }
            if (cam != null) cam.close();
        
    }

    public void close() {
        stop();
        if (cam != null) {
            try { cam.close(); } catch (Throwable ignored) {}
            cam = null;
        }
    }

    public void setTargetFps(int fps) { this.targetFps = Math.max(1, Math.min(60, fps)); }
    public void setResolution(Dimension size) {
        this.requested = size;
        if (cam != null && cam.isOpen()) {
            cam.setViewSize(size);
        }
    }
}
