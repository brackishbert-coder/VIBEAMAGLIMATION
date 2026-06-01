# VIBEAMAGLIMATION — Image → Dubstep

Proof that **vibe coding can merge separate projects without catastrophic failure**. Two
independently vibe-coded beat engines folded into one unified codebase — an integration test
as art project. Internally it runs as *Image → Dubstep (JavaFX) — Sliding Window*: the full,
studio-grade sibling of [VIBESMART2](../VIBESMART2).

Feed it an **image or a live webcam feed**; it slides a window across the frame, vectorizes
what it sees, lets a **Self-Organizing Map** organize those vectors, and routes the result
through a real **Web-Audio-style node graph** — synth voices, multiband buses, a full FX rack,
sidechain compression, and a master limiter — into live dubstep you can record. ~62 source
files; one running instrument.

A **JavaFX** desktop app, entry point `com.imagedubstep.app.DubstepApp`.

---

## The signal chain

1. **Source** — an uploaded image or a live **webcam** (camera selectable). A **sliding
   window** moves across the frame so the input is continuous.
2. **Vectorize** — the window is read as **8×8 feature vectors**: visual structure → numbers.
3. **Self-organize** — a **SOM** (trainer + runtime + view) learns the vector stream and maps
   visual texture to a 2-D organization that drives the music. Two visualizer walls (SOM
   Views, a 6×6 random-views grid).
4. **Two engines** — the merged beat engines (`engine` vector engine + `engine2` beat engine /
   sequencer / generator) turn the organized map into patterns and notes.
5. **Synthesize & mix** — voices (**kick, sub-bass, noise burst, one-shots, high lines**)
   render through a node graph into **Low / Mid / High buses** and a **Master** (with low-band
   mono).
6. **Effects & dynamics** — a 9-unit FX rack (**reverb, echo, flanger, tremolo/pan, auto-wah,
   widener, phaser, chorus, bit-crusher**), plus **sidechain ducking (kick → sub/bass)** and a
   **master look-ahead limiter**.
7. **Perform & record** — play loop / once, test sound, select audio output, and **record**
   both the **audio** (`.wav`) and your **actions** (every slider/button move, to a replayable
   `.actions` file). Save / load recordings.

---

## Requirements

- **JDK 21** (`maven.compiler.release = 21`).
- For source builds: **Maven**, which pulls JavaFX 21.0.2 (`controls`, `fxml`, `swing`) and
  `com.github.sarxos:webcam-capture`.
- An audio output device; a webcam if you want live-camera input.

---

## Build & run

### Easiest — the prebuilt jar

A runnable `VIBE.jar` ships with the repo. It is an Eclipse "jar-in-jar" build that **bundles
JavaFX**, so it runs on its own:

```bash
java -jar VIBE.jar
```

### From source (Maven)

```bash
git clone https://github.com/brackishbert-coder/VIBEAMAGLIMATION
cd VIBEAMAGLIMATION

mvn compile
mvn javafx:run        # entry point: com.imagedubstep.app.DubstepApp
```

The `javafx-maven-plugin` is preconfigured with the main class, so `mvn javafx:run` handles
the JavaFX module path.

### In Eclipse

Import the folder (the `.project` / `.classpath` are included) and run `DubstepApp` as a Java
Application.

---

## In the app

- **Source** — `Upload Image` or pick a `Camera:`; toggle the `Sliding Window`; `Vectors (8×8)`.
- **Transport** — `PLAY DUBSTEP` / `STOP`, `Play Loop`, `Play Once`, `Stop Play`, `TEST SOUND`.
- **Mix** — Low / Mid / High buses, `Master`, `Low-Band Mono`, `Master Limiter`.
- **Effects** (with an `Effects Enabler`) — Reverb, Echo, Flanger, Tremolo / Pan, Wah,
  Widener, Phaser, Chorus, Bit Crusher; `Sidechain (Kick → Sub/Bass)`.
- **Output** — choose the device (`Audio Output`, `Use Selected Output`); `FPS` readout.
- **Record** — `Record` / `Stop Rec` (audio → `.wav`), plus action automation; `Save Recording`
  / `Load Recording`.
- **Visualize** — `SOM Views`, `Open 6×6 Random Views`.

---

## Repo contents of note

| Item | What it is |
|------|-----------|
| `VIBE.jar` | prebuilt, self-contained runnable (JavaFX bundled) |
| `src/main/java/com/imagedubstep/` | all source (app, som, engine, engine2, nodes, fx, dynamics, filter, audio, webcam, visualizer, record, util) |
| `syve.wav`, `test.wav` | example rendered audio |
| `syve.actions` | an example action-automation recording (replayable performance) |
| `cool.gif` | demo capture |
| `pom.xml` | Maven (groupId: THE, JDK 21, JavaFX 21) |
| `.project` / `.classpath` | Eclipse project files |

---

*Part of the broader experimental ecosystem — aim a lens at the world and let it play itself.*
