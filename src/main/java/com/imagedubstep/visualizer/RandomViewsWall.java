package com.imagedubstep.visualizer;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToolBar;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

import com.imagedubstep.engine.VectorEngine;
import com.imagedubstep.util.*;
//--- RECORDER-ENABLED: 6x6 Random Views Wall ---
public class RandomViewsWall {
 private final Stage stage = new Stage();
 private final GridPane grid = new GridPane();
 private final java.util.Random rng = new java.util.Random();
 private final java.util.List<Tile> tiles = new java.util.ArrayList<>(36);

 // capture + export
 private final java.util.List<java.awt.image.BufferedImage> frames = new java.util.ArrayList<>();
 private java.util.concurrent.ScheduledExecutorService captureExec;
 private volatile boolean recording = false;
 private int targetFps = 30;

 // UI
 private final Button btnReshuffle = new Button("Reshuffle");
 private final Button btnStart = new Button("Start Rec");
 private final Button btnStop = new Button("Stop");
 private final Button btnGif = new Button("Export GIF");
 private final Button btnMp4 = new Button("Export MP4");
 private final Button btnPng = new Button("Export PNGs");
 private final Label lblFrames = new Label("Frames: 0");
 private final ChoiceBox<Integer> fpsChoice = new ChoiceBox<>(
     javafx.collections.FXCollections.observableArrayList(10, 15, 24, 30, 60));

 // drawing constants
 private static final int CELL_PX = 10;
 private static final int GRID_W  = 8 * CELL_PX; // 80
 private static final int GRID_H  = 8 * CELL_PX; // 80

 private final ViewMode[] ALL_MODES = new ViewMode[] {
     ViewMode.RGB, ViewMode.LUMA, ViewMode.ENERGY, ViewMode.SAT, ViewMode.CONTRAST, ViewMode.ENTROPY,
     ViewMode.HF, ViewMode.CORNERS, ViewMode.SYM, ViewMode.MOTION, ViewMode.ORIENT
 };

 private final class Tile {
     final Canvas canvas = new Canvas(GRID_W, GRID_H);
     ViewMode mode;
     int rotationDeg; // 0, 90, 180, 270

     Tile(ViewMode m) { setMode(m); randomizeRotation(); }
     void setMode(ViewMode m) { mode = m; }
     void randomizeRotation() {
         int[] choices = {0, 90, 180, 270};
         rotationDeg = choices[rng.nextInt(choices.length)];
     }
     void draw() {
         if (VectorEngine.vectors == null || VectorEngine.vectors.isEmpty()) return;
         GraphicsContext gc = canvas.getGraphicsContext2D();
         gc.save();
         try {
             gc.clearRect(0, 0, GRID_W, GRID_H);
             // rotate about center
             gc.translate(GRID_W * 0.5, GRID_H * 0.5);
             gc.rotate(rotationDeg);
             gc.translate(-GRID_W * 0.5, -GRID_H * 0.5);
             // draw 8x8
             for (int i = 0; i < VectorEngine.vectors.size(); i++) {
                 int x = (i % 8) * CELL_PX;
                 int y = (i / 8) * CELL_PX;
                 var v = VectorEngine.getVec(i);
                 gc.setFill(Util.colorFor(mode, v));
                 gc.fillRect(x, y, CELL_PX, CELL_PX);
             }
         } finally {
             gc.restore();
         }
     }
 }

 public RandomViewsWall() {
     grid.setHgap(0);
     grid.setVgap(0);
     grid.setPadding(Insets.EMPTY);

     // assign modes (≥1 of each, then random until 36)
     java.util.List<ViewMode> bag = new java.util.ArrayList<>();
     for (ViewMode m : ALL_MODES) bag.add(m);
     while (bag.size() < 36) bag.add(ALL_MODES[rng.nextInt(ALL_MODES.length)]);
     java.util.Collections.shuffle(bag, rng);

     for (int i = 0; i < 36; i++) {
         Tile t = new Tile(bag.get(i));
         tiles.add(t);
         grid.add(t.canvas, i % 6, i / 6);
     }

     btnReshuffle.setOnAction(e -> reshuffle());
     btnStart.setOnAction(e -> {
         if (recording) return;
         Integer sel = fpsChoice.getValue();
         targetFps = (sel == null ? 30 : sel);
         startCapture(targetFps);
     });
     btnStop.setOnAction(e -> stopCapture());
     btnGif.setOnAction(e -> exportGif());
     btnMp4.setOnAction(e -> exportMp4OrPngFallback());
     btnPng.setOnAction(e -> exportPngSequence());

     fpsChoice.setValue(30);

     ToolBar tb = new ToolBar(
         btnReshuffle,
         new Separator(),
         new Label("FPS:"), fpsChoice,
         btnStart, btnStop,
         new Separator(),
         btnGif, btnMp4, btnPng,
         new Separator(),
         lblFrames
     );

     BorderPane shell = new BorderPane();
     shell.setTop(tb);
     shell.setCenter(grid);

     Scene sc = new Scene(shell, 6*GRID_W + 2, 6*GRID_H + 48);
     stage.setTitle("Random Vector Views — 6×6 Wall (Recorder)");
     stage.setScene(sc);
 }

 public void show() { if (!stage.isShowing()) stage.show(); drawAll(); }
 public void close() { stopCapture(); stage.close(); }

 public void drawAll() { Platform.runLater(() -> tiles.forEach(Tile::draw)); }

 void reshuffle() {
     java.util.List<ViewMode> bag = new java.util.ArrayList<>();
     for (ViewMode m : ALL_MODES) bag.add(m);
     while (bag.size() < tiles.size()) bag.add(ALL_MODES[rng.nextInt(ALL_MODES.length)]);
     java.util.Collections.shuffle(bag, rng);
     for (int i = 0; i < tiles.size(); i++) {
         Tile t = tiles.get(i);
         t.setMode(bag.get(i));
         t.randomizeRotation();
     }
     drawAll();
     // optional: clear frames on reshuffle to avoid mixing styles
     // frames.clear(); lblFrames.setText("Frames: 0");
 }

 // --- Recording ---

 private void startCapture(int fps) {
     recording = true;
     frames.clear();
     lblFrames.setText("Frames: 0");

     long periodNs = Math.max(1, Math.round(1_000_000_000.0 / fps));
     captureExec = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "Wall-Capture");
         t.setDaemon(true);
         return t;
     });

     // Drive both draw + snapshot at a fixed cadence
     captureExec.scheduleAtFixedRate(() -> {
         if (!recording) return;
         try {
             // render latest first
             drawAll();
             // snapshot on FX thread & collect image
             Platform.runLater(() -> {
                 try {
                     WritableImage wi = grid.snapshot(new SnapshotParameters(), null);
                     java.awt.image.BufferedImage bi = javafx.embed.swing.SwingFXUtils.fromFXImage(wi, null);
                     frames.add(bi);
                     lblFrames.setText("Frames: " + frames.size());
                 } catch (Exception ignored) {}
             });
         } catch (Exception ignored) {}
     }, 0, periodNs, java.util.concurrent.TimeUnit.NANOSECONDS);
 }

 private void stopCapture() {
     recording = false;
     if (captureExec != null) {
         captureExec.shutdownNow();
         captureExec = null;
     }
 }

 private void exportGif() {
     if (frames.isEmpty()) {
         new Alert(Alert.AlertType.WARNING, "No frames to export. Start recording first.").showAndWait();
         return;
     }
     FileChooser fc = new FileChooser();
     fc.setTitle("Save Animated GIF");
     fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("GIF Image", "*.gif"));
     File f = fc.showSaveDialog(stage);
     if (f == null) return;
     try {
         writeAnimatedGif(f, frames, targetFps, true);
     } catch (Exception ex) {
         ex.printStackTrace();
         new Alert(Alert.AlertType.ERROR, "GIF export failed:\n" + ex.getMessage()).showAndWait();
     }
 }

 private void exportMp4OrPngFallback() {
     if (frames.isEmpty()) {
         new Alert(Alert.AlertType.WARNING, "No frames to export. Start recording first.").showAndWait();
         return;
     }
     try {
         // Try JCodec if available:
         Class<?> seqEnc = Class.forName("org.jcodec.api.awt.SequenceEncoder");
         exportMp4WithJCodec(); // compiles if dependency present
     } catch (ClassNotFoundException cnf) {
         // fallback: PNG sequence
         exportPngSequence();
         new Alert(Alert.AlertType.INFORMATION,
             "JCodec not found. Exported PNG sequence instead.\n" +
             "Tip: Add JCodec to enable MP4 export (org.jcodec:jcodec:0.2.5+).").showAndWait();
     } catch (Exception ex) {
         ex.printStackTrace();
         new Alert(Alert.AlertType.ERROR, "MP4 export failed:\n" + ex.getMessage()).showAndWait();
     }
 }

 private void exportMp4WithJCodec() throws Exception {
     FileChooser fc = new FileChooser();
     fc.setTitle("Save MP4 (H.264 via JCodec)");
     fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("MP4 Video", "*.mp4"));
     File f = fc.showSaveDialog(stage);
     if (f == null) return;

     // JCodec API:
     // SequenceEncoder enc = SequenceEncoder.createSequenceEncoder(f, Rational.R(targetFps,1));
     // for (BufferedImage bi : frames) enc.encodeImage(bi);
     // enc.finish();

     // Reflect to avoid hard dep at compile time:
     Class<?> seqEnc = Class.forName("org.jcodec.api.awt.SequenceEncoder");
     Class<?> rational = Class.forName("org.jcodec.common.model.Rational");
     java.lang.reflect.Method R = rational.getMethod("R", int.class, int.class);
     Object rat = R.invoke(null, targetFps, 1);
     java.lang.reflect.Method create = seqEnc.getMethod("createSequenceEncoder", File.class, rational);
     Object enc = create.invoke(null, f, rat);
     java.lang.reflect.Method encodeImage = seqEnc.getMethod("encodeImage", java.awt.image.BufferedImage.class);
     for (java.awt.image.BufferedImage bi : frames) encodeImage.invoke(enc, bi);
     java.lang.reflect.Method finish = seqEnc.getMethod("finish");
     finish.invoke(enc);
 }

 private void exportPngSequence() {
     DirectoryChooser dc = new DirectoryChooser();
     dc.setTitle("Choose Folder for PNG Frames");
     File dir = dc.showDialog(stage);
     if (dir == null) return;
     try {
         int idx = 0;
         for (java.awt.image.BufferedImage bi : frames) {
             File out = new File(dir, String.format("frame_%05d.png", idx++));
             javax.imageio.ImageIO.write(bi, "png", out);
         }
     } catch (Exception ex) {
         ex.printStackTrace();
         new Alert(Alert.AlertType.ERROR, "PNG export failed:\n" + ex.getMessage()).showAndWait();
     }
 }

// --- Pure-Java Animated GIF writing via ImageIO (no NPE) ---
private void writeAnimatedGif(
        File file,
        java.util.List<java.awt.image.BufferedImage> imgs,
        int fps,
        boolean loopForever) throws Exception {

    if (imgs == null || imgs.isEmpty())
        throw new IllegalArgumentException("No frames");

    // Choose a writer
    javax.imageio.ImageWriter iw = javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
    try (javax.imageio.stream.ImageOutputStream ios =
             javax.imageio.ImageIO.createImageOutputStream(file)) {
        iw.setOutput(ios);
        iw.prepareWriteSequence(null);

        final int delayCs = Math.max(1, Math.round(100f / Math.max(1, fps))); // delay in centiseconds

        // We’ll reuse the same metadata structure and just update per-frame values
        for (int i = 0; i < imgs.size(); i++) {
            java.awt.image.BufferedImage src = imgs.get(i);

            javax.imageio.metadata.IIOMetadata meta = iw.getDefaultImageMetadata(
                    new javax.imageio.ImageTypeSpecifier(src), null);

            String fmt = meta.getNativeMetadataFormatName(); // "javax_imageio_gif_image_1.0"

            // Root of the GIF metadata
            javax.imageio.metadata.IIOMetadataNode root =
                    new javax.imageio.metadata.IIOMetadataNode(fmt);

            // --- GraphicControlExtension (holds per-frame delay, transparency, etc.) ---
            javax.imageio.metadata.IIOMetadataNode gce =
                    new javax.imageio.metadata.IIOMetadataNode("GraphicControlExtension");
            gce.setAttribute("disposalMethod", "none");
            gce.setAttribute("userInputFlag", "FALSE");
            gce.setAttribute("transparentColorFlag", "FALSE");
            gce.setAttribute("delayTime", Integer.toString(delayCs));
            gce.setAttribute("transparentColorIndex", "0");
            root.appendChild(gce);

            // (Optional) You can attach a CommentExtensions node if you like:
            // IIOMetadataNode comments = new IIOMetadataNode("CommentExtensions");
            // IIOMetadataNode c = new IIOMetadataNode("CommentExtension");
            // c.setAttribute("value", "generated by RandomViewsWall");
            // comments.appendChild(c);
            // root.appendChild(comments);

            // --- ApplicationExtensions (NETSCAPE 2.0 loop) ---
            if (i == 0) {
                javax.imageio.metadata.IIOMetadataNode appExts =
                        new javax.imageio.metadata.IIOMetadataNode("ApplicationExtensions");

                javax.imageio.metadata.IIOMetadataNode app =
                        new javax.imageio.metadata.IIOMetadataNode("ApplicationExtension");
                app.setAttribute("applicationID", "NETSCAPE");
                app.setAttribute("authenticationCode", "2.0");

                int loopCount = loopForever ? 0 : 1; // 0 = forever
                byte[] appData = new byte[] {
                        0x1, (byte) (loopCount & 0xFF), (byte) ((loopCount >> 8) & 0xFF)
                };
                // Store raw bytes in user object (this is the supported way):
                app.setUserObject(appData);

                appExts.appendChild(app);
                root.appendChild(appExts);
            }

            // Push the tree back into metadata
            meta.setFromTree(fmt, root);

            // Finally write this frame
            iw.writeToSequence(new javax.imageio.IIOImage(src, null, meta), null);
        }

        iw.endWriteSequence();
    }
}

 private org.w3c.dom.Node findNode(org.w3c.dom.Node root, String name) {
     for (org.w3c.dom.Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
         if (name.equals(n.getNodeName())) return n;
         org.w3c.dom.Node rec = findNode(n, name);
         if (rec != null) return rec;
     }
     return null;
 }
 private void setAttr(org.w3c.dom.Node node, String key, String val) {
     org.w3c.dom.NamedNodeMap map = node.getAttributes();
     org.w3c.dom.Node att = map.getNamedItem(key);
     if (att != null) att.setNodeValue(val);
     else {
         org.w3c.dom.Attr a = node.getOwnerDocument().createAttribute(key);
         a.setValue(val);
         map.setNamedItem(a);
     }
 }
}
