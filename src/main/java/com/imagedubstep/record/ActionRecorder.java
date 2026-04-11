package com.imagedubstep.record;

//ActionRecorder.java
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public final class ActionRecorder {
 private final List<Action> timeline = new CopyOnWriteArrayList<>();
 private final Set<String> blacklistKeys = new HashSet<>(List.of(
     // exclude settings and any keys you don’t want recorded
     "loadSettings", "saveSettings", "prefsLoad", "prefsSave"
 ));

 private volatile boolean recording = false;
 private volatile boolean playing   = false;
 private volatile boolean loop      = false;
 private long t0 = 0L;

 // ===== Recording control =====
 public void startRecording() {
     if (playing) return;
     timeline.clear();
     t0 = System.currentTimeMillis();
     recording = true;
 }
 public void stopRecording() { recording = false; }

 public boolean isRecording() { return recording; }
 public boolean isPlaying()   { return playing;   }

 public List<Action> getSnapshot() { return List.copyOf(timeline); }

 // Call from handlers (or helper attachers below)
 public void capture(String key, String kind, Object value) {
     if (!recording || playing) return;
     if (blacklistKeys.contains(key)) return;
     long t = System.currentTimeMillis() - t0;
     timeline.add(new Action(key, kind, value, t));
 }

 // ===== Playback (time-accurate, loopable) =====
 public void play(boolean loop) {
     if (timeline.isEmpty() || playing) return;
     this.loop = loop;
     playing = true;
     new Thread(() -> {
         do {
             long start = System.currentTimeMillis();
             int i = 0;
             while (i < timeline.size() && playing) {
                 Action a = timeline.get(i);
                 long due = start + a.tMillis;
                 long now;
                 while (playing && (now = System.currentTimeMillis()) < due) {
                     try { Thread.sleep(Math.min(5, (int) (due - now))); } catch (InterruptedException ignored) {}
                 }
                 if (!playing) break;
                 // Dispatch on FX thread
                 Platform.runLater(() -> apply(a));
                 i++;
             }
         } while (playing && this.loop);
         playing = false;
     }, "ActionPlayback").start();
 }

 public void stopPlayback() { playing = false; }

 // ===== Mapping from recorded actions -> actual UI mutation =====
 // Provide setters for each key you attach (or plug in callbacks).
 private final Map<String, RunnableAction> appliers = new HashMap<>();
 public interface RunnableAction { void run(Action a); }

 public void registerApplier(String key, RunnableAction fn) {
     appliers.put(key, fn);
 }

 private void apply(Action a) {
     var fn = appliers.get(a.key);
     if (fn != null) fn.run(a);
 }

 // ===== Helpers: one-liners to attach common controls =====
 public void attachSlider(Slider slider, String key) {
     slider.valueProperty().addListener((obs, o, v) ->
         capture(key, "slider", v.doubleValue()));
     // For playback:
     registerApplier(key, act -> {
         double val = ((Number) act.value).doubleValue();
         slider.setValue(val);
     });
 }

 public void attachButton(Button btn, String key) {
     btn.setOnAction(e -> capture(key, "button", "pressed"));
     registerApplier(key, act -> btn.fire()); // simulate click
 }

 public void attachToggle(ToggleButton t, String key) {
     t.selectedProperty().addListener((obs, o, v) ->
         capture(key, "toggle", v.booleanValue()));
     registerApplier(key, act -> t.setSelected((Boolean) act.value));
 }

 public <T> void attachChoiceBox(ChoiceBox<T> cb, String key) {
     cb.getSelectionModel().selectedItemProperty().addListener((obs, o, v) ->
         capture(key, "choice", v));
     registerApplier(key, act -> {
         @SuppressWarnings("unchecked")
         T v = (T) act.value;
         cb.getSelectionModel().select(v);
     });
 }
 public <T> void attachCheckBox(CheckBox cb, String key) {
	 cb.selectedProperty().addListener((obs, o, v) ->
     capture(key, "checked", v.booleanValue()));
 registerApplier(key, act -> cb.setSelected((Boolean) act.value));
}
 

 public void attachTextField(TextField tf, String key) {
     tf.textProperty().addListener((obs, o, v) -> capture(key, "text", v));
     registerApplier(key, act -> tf.setText((String) act.value));
 }
 
 /** Save the current recording timeline to a file (TSV, string values Base64-encoded). */
 public void saveRecording(File f) throws IOException {
     try (PrintWriter pw = new PrintWriter(
             new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
         pw.println("# ActionRecorder v1");
         for (Action a : getSnapshot()) {
             String type, val;
             if (a.value instanceof Number) {
                 type = "D";
                 val = a.value.toString();
             } else if (a.value instanceof Boolean) {
                 type = "B";
                 val = ((Boolean)a.value) ? "true" : "false";
             } else {
                 type = "S";
                 String s = (a.value == null) ? "" : a.value.toString();
                 val = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
             }
             pw.printf("%s\t%s\t%s\t%s\t%d%n",
                     a.key, a.kind, type, val, a.tMillis);
         }
     }
 }

 /** Load a previously saved recording file. Replaces the current timeline. */
 public void loadRecording(File f) throws IOException {
     List<Action> loaded = new ArrayList<>();
     try (BufferedReader br = new BufferedReader(
             new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
         String line;
         while ((line = br.readLine()) != null) {
             if (line.isEmpty() || line.startsWith("#")) continue;
             String[] parts = line.split("\t", -1);
             if (parts.length < 5) continue;
             String key = parts[0];
             String kind = parts[1];
             String type = parts[2];
             String valStr = parts[3];
             long tMillis;
             try { tMillis = Long.parseLong(parts[4]); } catch (Exception e) { continue; }

             Object value;
             switch (type) {
                 case "D": value = Double.parseDouble(valStr); break;
                 case "B": value = "true".equalsIgnoreCase(valStr); break;
                 case "S":
                 default:
                     try {
                         byte[] bytes = Base64.getDecoder().decode(valStr);
                         value = new String(bytes, StandardCharsets.UTF_8);
                     } catch (Exception e) {
                         value = valStr; // fallback
                     }
             }
             loaded.add(new Action(key, kind, value, tMillis));
         }
     }
     // Replace current timeline atomically and reset state
     timeline.clear();
     timeline.addAll(loaded);
     recording = false;
     playing = false;
     loop = false;
     t0 = 0L;
 }

 // Blacklist control
 public void addToBlacklist(String key)   { blacklistKeys.add(key); }
 public void removeFromBlacklist(String k){ blacklistKeys.remove(k); }
}
