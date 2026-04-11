
package com.imagedubstep.app;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import javax.sound.sampled.FloatControl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.imagedubstep.core.*;
import com.imagedubstep.engine.VectorEngine;
import com.imagedubstep.engine.VectorEngine.Bus;
import com.imagedubstep.engine.VectorEngine.Rate;
import com.imagedubstep.engine.VectorEngine.Vector;
import com.imagedubstep.nodes.*;
import com.imagedubstep.record.ActionRecorder;
import com.imagedubstep.util.Biquad;
import com.imagedubstep.util.Util;
import com.imagedubstep.util.ViewMode;
import com.imagedubstep.visualizer.RandomSOMWall;
import com.imagedubstep.visualizer.RandomViewsWall;

public class DubstepApp extends Application {

	private Timer loopTimer;

	private ImageView imageView;
	private Image loadedImage;
	private Canvas overlay;
	private Canvas vecGrid;
	private RandomViewsWall randomWall;
	private RandomSOMWall somWall;
	private final java.util.Map<String, javafx.scene.control.Slider> sliderRegistry = new java.util.LinkedHashMap<>();
	private final java.util.Map<String, javafx.scene.control.CheckBox> checkRegistry = new java.util.LinkedHashMap<>();
	private final java.util.Map<String, javafx.scene.control.ChoiceBox<String>> choiceRegistry = new java.util.LinkedHashMap<>();

	private VectorEngine eng;
	private final ActionRecorder recorder = new ActionRecorder();

	private com.imagedubstep.webcam.WebcamManager webcamMgr;
	private javafx.scene.image.ImageView webcamPreview;

	private Stage primaryStage;

	// --- Webcam integration (top of DubstepApp)
	private boolean webcamLive = false;
	private javafx.scene.image.Image lastStillImage = null; // we keep the last loaded picture
	private com.github.sarxos.webcam.Webcam selectedCam = null;

	// VectorEngine fields
	public com.imagedubstep.som.SOM som;
	public com.imagedubstep.som.SOMTrainer somTrainer;
	public com.imagedubstep.som.SOMView somView; // optional if you place the view here
	public com.imagedubstep.som.SOMRuntime somRt;

	private javafx.animation.AnimationTimer animationTimer;

	// Helpers to register controls
	private void registerSlider(String key, javafx.scene.control.Slider s) {
		sliderRegistry.put(key, s);
		recorder.attachSlider(s, key);
	}

	private void registerCheck(String key, javafx.scene.control.CheckBox c) {
		checkRegistry.put(key, c);
		recorder.attachCheckBox(c, key);
		;
	}

	private void registerChoice(String key, javafx.scene.control.ChoiceBox<String> c) {
		choiceRegistry.put(key, c);
		recorder.attachChoiceBox(c, key);
		;
	}

	@Override
	public void start(Stage stage) {

		this.primaryStage = stage;
		var root = new BorderPane();
		root.setPadding(new Insets(12));

		// Left panel: image + overlay + vectors
		VBox left = new VBox(10);
		left.setPrefWidth(460);
		left.setPadding(new Insets(8));

		Button upload = new Button("Upload Image");
		imageView = new ImageView();
		imageView.setFitWidth(400);
		imageView.setPreserveRatio(true);

		overlay = new Canvas(400, 400);
		StackPane imageStack = new StackPane(imageView, overlay);
		imageStack.setPrefWidth(400);
		imageStack.setPrefHeight(400);

		vecGrid = new Canvas(8 * 16, 8 * 16);

		upload.setOnAction(e -> {
			FileChooser fc = new FileChooser();
			fc.getExtensionFilters()
					.add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp"));
			File f = fc.showOpenDialog(stage);
			if (f != null) {
				try {
					BufferedImage bimg = ImageIO.read(f);
					loadedImage = SwingFXUtils.toFXImage(bimg, null);
					imageView.setImage(loadedImage);
					refreshOverlay();
					eng.setImage(loadedImage);
					lastStillImage = loadedImage;
					drawVectors();
					eng.setImageView(imageView);
					eng.queueAnalysis();
					new Thread(() -> {
						try {
							Thread.sleep(120);
						} catch (InterruptedException ignored) {
						}
						Platform.runLater(this::drawVectors);
					}).start();

					for (int i = 0; i < Math.min(3, VectorEngine.vectors.size()); i++) {
						var ve = VectorEngine.vectors.get(i);
//						System.out.printf("v%d rgb(%.2f,%.2f,%.2f) bytes(%d,%d,%d)%n", i, ve.r, ve.g, ve.b, ve.cr,
//								ve.cg, ve.cb);
					}

				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});

		eng = new VectorEngine(imageView, loadedImage, new AudioContext(44100, 2048));
		som = new com.imagedubstep.som.SOM(16, 16, com.imagedubstep.som.BeatFeatureExtractor.DIM, 0.5f, 6.0f, 20_000);
		somTrainer = new com.imagedubstep.som.SOMTrainer(som);
		somView = new com.imagedubstep.som.SOMView(som, 320, 320);
		somRt = new com.imagedubstep.som.SOMRuntime(som);
		somView = new com.imagedubstep.som.SOMView(som, 320, 320);

		Button openRandomWall = new Button("Open 6×6 Random Views");
		openRandomWall.setOnAction(e -> {
			if (randomWall == null)
				randomWall = new RandomViewsWall();
			randomWall.show();
		});
		// after you build eng and right-side UI:
		
		
		somWall = new RandomSOMWall(eng, som, somRt,6, 6);
		
		Button openSOMWall = new Button("SOM Views");
		openSOMWall.setOnAction(e -> {
			if (somWall == null)
				somWall = new RandomSOMWall(eng, som, somRt,6, 6);
			somWall.show();
		});
		somRt.start(); // start background trainer

		// left panel contents (augment your current line):
		left.getChildren().addAll(upload, imageStack, new Label("Vectors (8×8)"), vecGrid, openRandomWall, openSOMWall);
		try {
			eng.ctx.start();
		} catch (Throwable ignore) {
		}
//
		HBox sourceBar = webcam();
		left.getChildren().add(1, sourceBar);
		// Right panel: controls
		VBox right = new VBox(10);
		right.setPadding(new Insets(8));

		animationTimer = new javafx.animation.AnimationTimer() {
			private long last = 0;

			@Override
			public void handle(long now) {
				if (now - last < 33_000_000)
					return; // ~30 fps
				last = now;

				// drain a few BMUs to visualize recency
				for (int i = 0; i < 4; i++) {
					int[] b = (somRt != null) ? somRt.pollBMU() : null;
					if (b == null)
						break;
					somView.pingBMU(b[0], b[1]);
				}
				somView.redraw();
			}
		};
		animationTimer.start();

		TitledPane masterPane = new TitledPane();
		VBox masterBox = new VBox(6);
		masterBox.getChildren().add(makeSlider("Master Gain (dB)", 0, 100, 100,
				db -> eng.ctx.destination().setMasterGain(Math.pow(10.0, db / 20.0))));

		// And set the engine output to neutral once on start:
		eng.ctx.destination().setMasterGain(1.0);

		masterBox.getChildren().add(makeSlider("Tempo (BPM)", 60, 200, eng.tempo, v -> eng.tempo = (int) v));
		masterBox.getChildren()
				.add(makeSlider("Bass Pitch (Hz)", 40, 150, eng.bassPitch, v -> eng.bassPitch = (int) v));

		// Loop checkbox
		CheckBox loop = makeCheck("Loop.Enable", "Loop continuously", eng.loopEnabled, v -> eng.loopEnabled = v);
		loop.setSelected(eng.loopEnabled);
		loop.selectedProperty().addListener((o, ov, nv) -> eng.loopEnabled = nv);
		masterBox.getChildren().add(loop);
		masterPane.setText("Master");
		masterPane.setContent(masterBox);
		right.getChildren().add(masterPane);

		// Sliding window controls
		TitledPane winPane = new TitledPane();
		winPane.setText("Sliding Window");
		VBox winBox = new VBox(6);
		winBox.getChildren().add(makeSlider("Window Size (%)", 10, 100, VectorEngine.winSize * 100, v -> {
			VectorEngine.winSize = v / 100.0;
			refreshOverlay();
			eng.queueAnalysis();
			drawVectors();
		}));

		winBox.getChildren().add(makeSlider("Window X (%)", 0, 100, VectorEngine.winX * 100, v -> {
			VectorEngine.winX = v / 100.0;
			refreshOverlay();
			eng.queueAnalysis();
			drawVectors();

		}));
		winBox.getChildren().add(makeSlider("Window Y (%)", 0, 100, VectorEngine.winY * 100, v -> {
			VectorEngine.winY = v / 100.0;
			refreshOverlay();
			eng.queueAnalysis();
			drawVectors();
		}));
		winPane.setContent(winBox);
		right.getChildren().add(winPane);
		TitledPane audioOutputPane = new TitledPane("Audio Output", makeAudioOutputControls());
		TitledPane vectorPane = new TitledPane("Vectors", makeVectorControls());
		TitledPane kickPane = new TitledPane("Kicks", makeKickControls());
		TitledPane bassPane = new TitledPane("Bass", makeBassControls());
		TitledPane subPane = new TitledPane("Sub Bass", makeSubBassControls());
		TitledPane snrPane = new TitledPane("Snare", makeSnareControls());
		TitledPane hatPane = new TitledPane("Hi-Hat", makeHiHatControls());
		TitledPane linePane = new TitledPane("Line Percussion", makeLinePercControls());
		TitledPane hiPane = new TitledPane("High Frequency", makeHighFreqControls());
		TitledPane mixPane = new TitledPane("Mixing & Layering", makeMixControls());
		Accordion acc = new Accordion(vectorPane, kickPane, bassPane, subPane, snrPane, hatPane, linePane, hiPane);
		right.getChildren().add(acc);

		Accordion acc2 = new Accordion(makeEffectsPane(eng));
		right.getChildren().add(acc2);

		Accordion acc3 = new Accordion(audioOutputPane, mixPane, makeDynamicsPane(eng),
				makeBusEqPane(eng.busLow, eng.busMid, eng.busHigh));

		right.getChildren().add(acc3);

		// Play/Stop
		HBox controls = new HBox(10);
		Button play = new Button("PLAY DUBSTEP");
		Button stop = new Button("STOP");
		Button test = new Button("TEST SOUND");

		Button recordBtn = new Button("Record");
		Button stopRecordBtn = new Button("Stop Rec");
		Button playOnceBtn = new Button("Play Once");
		Button playLoopBtn = new Button("Play Loop");
		Button stopPlayBtn = new Button("Stop Play");
		stop.setDisable(true);
		controls.getChildren().addAll(play, stop, test, recordBtn, stopRecordBtn, playOnceBtn, playLoopBtn,
				stopPlayBtn);
		right.getChildren().add(controls);

		recordBtn.setOnAction(e -> recorder.startRecording());
		stopRecordBtn.setOnAction(e -> recorder.stopRecording());
		playOnceBtn.setOnAction(e -> recorder.play(false));
		playLoopBtn.setOnAction(e -> recorder.play(true));
		stopPlayBtn.setOnAction(e -> recorder.stopPlayback());

		play.setOnAction(e -> {
			if (eng.vectors == null || eng.vectors.isEmpty())
				return;
			eng.startNewSession(); // <-- new
			eng.ctx.start();

			eng.forceDubstepNow(somRt); // <-- new: pass som + trainer);

			stop.setDisable(false);
			play.setDisable(true);
		});

		// Stop
		stop.setOnAction(e -> {
			eng.cancelLoop(); // <-- new: stop engine’s scheduled re-loop
			eng.ctx.stop();
			if (eng != null && somRt != null)
				somRt.stop();
			somWall.close();
			stop.setDisable(true);
			play.setDisable(false);
		});
		test.setOnAction(e -> {
			eng.cancelLoop(); // <-- prevent any loop overlap
			eng.ctx.start();
			double t = eng.ctx.currentTime() + 0.12;

			// KICK: clear + loud
			// KICK: clear + loud (now with drive/tone controls)
//			eng.addToMix(new KickNode(eng.ctx, t, /* length */ 0.28,
//					/* startHz */ VectorEngine.clamp(eng.kickStartHz, 50, 160),
//					/* endHz */ VectorEngine.clamp(eng.kickEndHz, 32, 90),
//					/* attack */ VectorEngine.clamp(eng.kickAttack, 0.005, 0.5),
//					/* decay */ VectorEngine.clamp(eng.kickDecay, 0.18, 1.0),
//					/* amp */ VectorEngine.clamp(eng.kickVolume, 0.0, 1.0),
//					/* drive */ VectorEngine.clamp(eng.kickDrive, 1.0, 3.5),
//					/* toneMix */ VectorEngine.clamp(eng.kickToneMix, 0.0, 1.0)));

			// SUB fundamental (45 Hz instead of 25 — far more audible on most speakers)
			eng.addToMix(new SubBassNode(eng.ctx, t + 0.14, Math.max(0.65, eng.subBassDecay),
					VectorEngine.clamp(eng.subBassFreq, 30, 90), VectorEngine.clamp(eng.subBassVolume, 0.0, 1.0),
					eng.subBassAttack, eng.subBassHarmonicMix, eng.subBassDrive, eng.subBassStereo));
//			System.out.println("[TEST] Kick + Sub extended fired");

//			System.out.println("[TEST] Kick + Sub(45) + Harm(90) fired");
		});

		root.setLeft(left);
		root.setCenter(right);

		Scene sc = new Scene(root, 1060, 720);
		stage.setScene(sc);
		stage.setTitle("Image → Dubstep (JavaFX) — Sliding Window");
		stage.setOnCloseRequest(ev -> {
			if (loopTimer != null)
				loopTimer.cancel();
			if (somRt != null) {
				somRt.stop();
			}
			if (webcamMgr != null) {
				webcamMgr.stop();
				webcamMgr.close();
			}
			if (somWall != null) {
				somWall.close();
			}
			if (randomWall != null) {
				randomWall.close();
			}
			if (animationTimer != null) {
				animationTimer.stop();
			}

			if (eng != null && eng.ctx != null) {
				eng.ctx.stop();
			}
			Platform.exit();
			System.exit(0);
		});

		javafx.scene.control.Menu settingsMenu = new javafx.scene.control.Menu("Settings");

		// existing Settings menu is already created above...

		// NEW: Recording menu for action timelines
		javafx.scene.control.Menu recMenu = new javafx.scene.control.Menu("Recording");
		javafx.scene.control.MenuItem miRecLoad = new javafx.scene.control.MenuItem("Load Recording…");
		javafx.scene.control.MenuItem miRecSave = new javafx.scene.control.MenuItem("Save Recording…");
		recMenu.getItems().addAll(miRecLoad, miRecSave);

		// Put both menus on the bar:

		// Handlers:
		miRecSave.setOnAction(ev -> {
			javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
			fc.setTitle("Save Recording");
			fc.getExtensionFilters()
					.add(new javafx.stage.FileChooser.ExtensionFilter("Action Recording (*.actions)", "*.actions"));
			java.io.File f = fc.showSaveDialog(stage);
			if (f != null) {
				try {
					recorder.stopRecording(); // ensure stable snapshot
					recorder.saveRecording(f);
				} catch (Exception ex) {
					ex.printStackTrace();
					new Alert(Alert.AlertType.ERROR, "Failed to save recording:\n" + ex.getMessage()).showAndWait();
				}
			}
		});

		miRecLoad.setOnAction(ev -> {
			javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
			fc.setTitle("Load Recording");
			fc.getExtensionFilters()
					.add(new javafx.stage.FileChooser.ExtensionFilter("Action Recording (*.actions)", "*.actions"));
			java.io.File f = fc.showOpenDialog(stage);
			if (f != null) {
				try {
					recorder.loadRecording(f);
				} catch (Exception ex) {
					ex.printStackTrace();
					new Alert(Alert.AlertType.ERROR, "Failed to load recording:\n" + ex.getMessage()).showAndWait();
				}
			}
		});

		javafx.scene.control.MenuItem miLoad = new javafx.scene.control.MenuItem("Load…");
		javafx.scene.control.MenuItem miSave = new javafx.scene.control.MenuItem("Save…");
		settingsMenu.getItems().addAll(miLoad, miSave);

		miLoad.setOnAction(ev -> {
			javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
			fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Properties", "*.properties"));
			fc.setTitle("Load Settings");
			java.io.File f = fc.showOpenDialog(stage);
			if (f != null)
				loadSettingsFrom(f);
		});
		miSave.setOnAction(ev -> {
			javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
			fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Properties", "*.properties"));
			fc.setTitle("Save Settings");
			java.io.File f = fc.showSaveDialog(stage);
			if (f != null)
				saveSettingsTo(f);
		});

		// After you build (or alongside) your menus:
		javafx.scene.control.Menu audioMenu = new javafx.scene.control.Menu("Audio");
		javafx.scene.control.MenuItem miStartWav = new javafx.scene.control.MenuItem("Start WAV Capture…");
		javafx.scene.control.MenuItem miStopWav = new javafx.scene.control.MenuItem("Stop WAV Capture");
		audioMenu.getItems().addAll(miStartWav, miStopWav);

		// Put it on the bar with your others:
		javafx.scene.control.MenuBar mb = new javafx.scene.control.MenuBar(settingsMenu, recMenu, audioMenu);
		root.setTop(mb);

		// Handlers:
		miStartWav.setOnAction(ev -> {
			javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
			fc.setTitle("Choose WAV file");
			fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("WAV audio", "*.wav"));
			java.io.File f = fc.showSaveDialog(stage);
			if (f != null) {
				try {
					eng.ctx.startWavRecording(f);
				} catch (Exception ex) {
					ex.printStackTrace();
					new Alert(Alert.AlertType.ERROR, "Failed to start capture:\n" + ex.getMessage()).showAndWait();
				}
			}
		});
		miStopWav.setOnAction(ev -> {
			try {
				eng.ctx.stopWavRecording();
			} catch (Exception ex) {
				ex.printStackTrace();
				new Alert(Alert.AlertType.ERROR, "Failed to stop capture:\n" + ex.getMessage()).showAndWait();
			}
		});

		stage.show();
	}

	private HBox webcam() {
		// After you create: ImageView imageView and VectorEngine eng
		webcamMgr = new com.imagedubstep.webcam.WebcamManager();

		// Source chooser: Still | Webcam
		ToggleGroup tg = new ToggleGroup();
		RadioButton rbStill = new RadioButton("Still");
		RadioButton rbCam = new RadioButton("Webcam");
		rbStill.setToggleGroup(tg);
		rbCam.setToggleGroup(tg);
		rbStill.setSelected(true);

		// Camera selector + Start/Stop
		ComboBox<com.github.sarxos.webcam.Webcam> cbCams = new ComboBox<>();
		cbCams.getItems().setAll(com.imagedubstep.webcam.WebcamManager.listCameras());
		cbCams.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(com.github.sarxos.webcam.Webcam w) {
				return w == null ? "Select Camera" : w.getName();
			}

			@Override
			public com.github.sarxos.webcam.Webcam fromString(String s) {
				return null;
			}
		});
		Button btnStart = new Button("Start");
		Button btnStop = new Button("Stop");
		btnStop.setDisable(true);

		// FPS (optional)
		Label fpsLab = new Label("FPS");
		Slider fps = new Slider(5, 30, 15);
		fps.setPrefWidth(120);
		fps.valueProperty().addListener((o, ov, nv) -> {
			if (webcamMgr != null)
				webcamMgr.setTargetFps(nv.intValue());
		});

		// Assemble the bar (put it above or below the ImageView)
		HBox sourceBar = new HBox(10, new Label("Source:"), rbStill, rbCam, new Separator(), new Label("Camera:"),
				cbCams, btnStart, btnStop, new Separator(), fpsLab, fps);
		sourceBar.setAlignment(Pos.CENTER_LEFT);

		// Switching to STILL:
		rbStill.setOnAction(e -> {
			webcamLive = false;
			// stop webcam if running
			if (webcamMgr != null) {
				webcamMgr.stop();
				webcamMgr.close();
			}
			btnStart.setDisable(false);
			btnStop.setDisable(true);
			// restore last still if we have one
			if (lastStillImage != null) {
				imageView.setImage(lastStillImage);
				eng.setImage(lastStillImage);
				lastStillImage = imageView.getImage();
				drawVectors();
				eng.queueAnalysis();
			}
		});

		// Pick camera
		cbCams.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
			selectedCam = nv;
		});

		// Start webcam
		btnStart.setOnAction(e -> {
			if (selectedCam == null)
				return;
			if (webcamMgr.open(selectedCam)) {
				webcamLive = true;
				rbCam.setSelected(true); // switch the toggle
				btnStart.setDisable(true);
				btnStop.setDisable(false);
				// stream frames into the SAME ImageView + engine
				webcamMgr.start(fxImg -> {
					imageView.setImage(fxImg); // ← shows live
					eng.setImage(fxImg); // ← drives your analysis
					lastStillImage = imageView.getImage();
					drawVectors();
					eng.queueAnalysis();
				});
			}
		});

		// Stop webcam (stays on last frame until you switch to Still or load a picture)
		btnStop.setOnAction(e -> {
			webcamLive = false;
			if (webcamMgr != null) {
				webcamMgr.stop();
				webcamMgr.close();
			}
			btnStart.setDisable(false);
			btnStop.setDisable(true);
		});

		// Switching to WEBCAM via toggle (same as Start, but without auto-open)
		rbCam.setOnAction(e -> {
			// if user toggles to Webcam without pressing Start yet, do nothing
			// you can auto-start if you prefer:
			if (selectedCam != null)
				btnStart.fire();
		});

		return sourceBar;
	}

	private Node makeAudioOutputControls() {

		VBox box = new VBox(6);

		javafx.scene.control.ChoiceBox<String> mixerChoice = new javafx.scene.control.ChoiceBox<>();
		for (javax.sound.sampled.Mixer.Info mi : javax.sound.sampled.AudioSystem.getMixerInfo()) {
			mixerChoice.getItems().add(mi.getName() + " — " + mi.getDescription());
		}
		mixerChoice.getSelectionModel().selectFirst();

		Button useMixer = new Button("Use Selected Output");
		useMixer.setOnAction(ev -> {
			String sel = mixerChoice.getSelectionModel().getSelectedItem();
			if (sel != null) {
				// pick a short substring that is likely unique, e.g., the part before " — "
				String key = sel.split(" — ")[0];
				eng.ctx.stop();
				eng.ctx.setPreferredMixerSubstring(key);
				eng.ctx.start();
			}
		});

		// add to your right-side VBox:
		box.getChildren().add(new Label("Audio Output"));
		box.getChildren().add(mixerChoice);
		box.getChildren().add(useMixer);

		return box;
	}

	private Node makeVectorControls() {
		VBox box = new VBox(6);

		box.getChildren().add(makeChoice("View", "Vector View", new String[] { "RGB", "LUMA", "ENERGY", "SAT",
				"CONTRAST", "ENTROPY", "HF", "CORNERS", "SYM", "MOTION", "ORIENT" }, "RGB", sel -> {
					Util.viewMode = ViewMode.valueOf(sel);
					drawVectors(); // redraw immediately
				}));

		// In the right-hand controls, perhaps inside a new "Analysis" TitledPane:
		box.getChildren().add(makeSlider("Corner Tau", 0.01, 3.0, eng.cornerTauScale, v -> {
			eng.cornerTauScale = v;
			eng.queueAnalysis();
			drawVectors();
		}));

		return box;
	}

	private TitledPane makeEffectsPane(VectorEngine eng) {

		// toggles
		CheckBox cbEcho = new CheckBox("Echo");
		CheckBox cbChor = new CheckBox("Chorus");
		CheckBox cbWide = new CheckBox("Widener");
		CheckBox cbVerb = new CheckBox("Reverb");
		CheckBox cbWah = new CheckBox("Wah");
		CheckBox cbPh = new CheckBox("Phaser");
		CheckBox cbFl = new CheckBox("Flanger");
		CheckBox cbTr = new CheckBox("Trem/Pan");
		CheckBox cbCr = new CheckBox("Crusher");
		cbWah.setSelected(false);
		cbPh.setSelected(false);
		cbFl.setSelected(false);
		cbTr.setSelected(false);
		cbCr.setSelected(false);
		cbEcho.setSelected(false);
		cbWide.setSelected(false);
		cbVerb.setSelected(false);

		cbEcho.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onEcho = nv;
		});
		cbChor.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onChorus = nv;
		});
		cbWide.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onWiden = nv;
		});
		cbVerb.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onReverb = nv;
		});
		cbWah.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onWah = nv;
		});
		cbPh.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onPhaser = nv;
		});
		cbFl.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onFlanger = nv;
		});
		cbTr.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onTrem = nv;
		});
		cbCr.selectedProperty().addListener((o, ov, nv) -> {
			if (eng.fx != null)
				eng.fx.onCrush = nv;
		});

		VBox box0 = new VBox(10);
		box0.getChildren().add(new Label("Effects Enabler"));
		box0.getChildren().add(new HBox(10, cbEcho, cbChor, cbWide, cbVerb, cbWah, cbPh, cbFl, cbTr, cbCr));
		VBox box = new VBox(10);
//		box.getChildren().add(new HBox(10, cbEcho));
		VBox box1 = new VBox(10);
//		box1.getChildren().add(new HBox(10, cbChor));
		VBox box2 = new VBox(10);
//		box2.getChildren().add(new HBox(10, cbWide));
		VBox box3 = new VBox(10);
//		box3.getChildren().add(new HBox(10, cbVerb));
		VBox box4 = new VBox(10);
//		box4.getChildren().add(new HBox(10, cbWah));
		VBox box5 = new VBox(10);
//		box5.getChildren().add(new HBox(10, cbPh));
		VBox box6 = new VBox(10);
//		box6.getChildren().add(new HBox(10, cbFl));
		VBox box7 = new VBox(10);
//		box7.getChildren().add(new HBox(10, cbTr));
		VBox box8 = new VBox(10);
//		box8.getChildren().add(new HBox(10, cbCr));

		// Echo controls
		box.getChildren().add(new Label("Echo"));
		box.getChildren().add(makeSlider("FX.Echo.Time", "Time (ms)", 60, 900, 280, v -> {
			if (eng.fx != null)
				eng.fx.echo.setTimeMs(v);
		}));
		box.getChildren().add(makeSlider("FX.Echo.Fb", "Feedback", 0.0, 0.95, 0.35, v -> {
			if (eng.fx != null)
				eng.fx.echo.setFeedback(v);
		}));
		box.getChildren().add(makeSlider("FX.Echo.Wet", "Wet", 0.0, 1.0, 0.25, v -> {
			if (eng.fx != null)
				eng.fx.echo.setWet(v);
		}));

		// Chorus

		box1.getChildren().add(new Label("Chorus"));
		box1.getChildren().add(makeSlider("FX.Ch.Depth", "Depth (ms)", 2, 12, 8, v -> {
			if (eng.fx != null)
				eng.fx.chorus.setDepthMs(v);
		}));
		box1.getChildren().add(makeSlider("FX.Ch.Rate", "Rate (Hz)", 0.1, 2.0, 0.6, v -> {
			if (eng.fx != null)
				eng.fx.chorus.setRate(v);
		}));
		box1.getChildren().add(makeSlider("FX.Ch.Wet", "Wet", 0.0, 1.0, 0.2, v -> {
			if (eng.fx != null)
				eng.fx.chorus.setWet(v);
		}));

		// Widener
		box2.getChildren().add(new Label("Widener"));
		box2.getChildren().add(makeSlider("FX.Wide.Side", "Side Gain", 0.6, 1.8, 1.2, v -> {
			if (eng.fx != null)
				eng.fx.widen.setSideGain(v);
		}));

		// Reverb
		box3.getChildren().add(new Label("Reverb"));
		box3.getChildren().add(makeSlider("FX.Rev.Wet", "Wet", 0.0, 1.0, 0.2, v -> {
			if (eng.fx != null)
				eng.fx.reverb.setWet(v);
		}));
		box3.getChildren().add(makeSlider("FX.Rev.Room", "Room", 0.2, 0.98, 0.78, v -> {
			if (eng.fx != null)
				eng.fx.reverb.setRoom(v);
		}));
		box3.getChildren().add(makeSlider("FX.Rev.Damp", "Damp", 0.0, 1.0, 0.3, v -> {
			if (eng.fx != null)
				eng.fx.reverb.setDamp(v);
		}));

		box4.getChildren().add(new Label("Wah"));
		box4.getChildren().add(makeSlider("FX.Wah.Min", "Min Hz", 150, 1000, 300, v -> {
			if (eng.fx != null)
				eng.fx.wah.setRange(v, eng.fx.wah == null ? 2000 : 2000);
		}));
		box4.getChildren().add(makeSlider("FX.Wah.Max", "Max Hz", 1200, 4500, 2000, v -> {
			if (eng.fx != null)
				eng.fx.wah.setRange(eng.fx.wah == null ? 300 : eng.fx.wah == null ? 300 : 300, v);
		}));
		box4.getChildren().add(makeSlider("FX.Wah.Q", "Resonance (Q)", 1.0, 12.0, 6.0, v -> {
			if (eng.fx != null)
				eng.fx.wah.setQ(v);
		}));
		box4.getChildren().add(makeSlider("FX.Wah.Sens", "Sensitivity", 0.2, 3.0, 1.0, v -> {
			if (eng.fx != null)
				eng.fx.wah.setSensitivity(v);
		}));
		box4.getChildren().add(makeSlider("FX.Wah.Wet", "Wet", 0.0, 1.0, 0.7, v -> {
			if (eng.fx != null)
				eng.fx.wah.setWet(v);
		}));

		box5.getChildren().add(new Label("Phaser"));
		box5.getChildren().add(makeSlider("FX.Ph.Stages", "Stages", 2, 8, 6, v -> {
			if (eng.fx != null)
				eng.fx.phaser.setStages((int) Math.round(v));
		}));
		box5.getChildren().add(makeSlider("FX.Ph.Rate", "Rate (Hz)", 0.05, 1.5, 0.3, v -> {
			if (eng.fx != null)
				eng.fx.phaser.setRate(v);
		}));
		box5.getChildren().add(makeSlider("FX.Ph.Depth", "Depth", 0.0, 1.0, 0.9, v -> {
			if (eng.fx != null)
				eng.fx.phaser.setDepth(v);
		}));
		box5.getChildren().add(makeSlider("FX.Ph.Fb", "Feedback", -0.9, 0.9, 0.25, v -> {
			if (eng.fx != null)
				eng.fx.phaser.setFeedback(v);
		}));
		box5.getChildren().add(makeSlider("FX.Ph.Wet", "Wet", 0.0, 1.0, 0.35, v -> {
			if (eng.fx != null)
				eng.fx.phaser.setWet(v);
		}));

		box6.getChildren().add(new Label("Flanger"));
		box6.getChildren().add(makeSlider("FX.Fl.Base", "Base (ms)", 0.5, 6.0, 2.0, v -> {
			if (eng.fx != null)
				eng.fx.flanger.setBaseMs(v);
		}));
		box6.getChildren().add(makeSlider("FX.Fl.Depth", "Depth (ms)", 0.1, 4.0, 1.2, v -> {
			if (eng.fx != null)
				eng.fx.flanger.setDepthMs(v);
		}));
		box6.getChildren().add(makeSlider("FX.Fl.Rate", "Rate (Hz)", 0.05, 1.5, 0.25, v -> {
			if (eng.fx != null)
				eng.fx.flanger.setRate(v);
		}));
		box6.getChildren().add(makeSlider("FX.Fl.Fb", "Feedback", -0.9, 0.9, 0.3, v -> {
			if (eng.fx != null)
				eng.fx.flanger.setFeedback(v);
		}));
		box6.getChildren().add(makeSlider("FX.Fl.Wet", "Wet", 0.0, 1.0, 0.4, v -> {
			if (eng.fx != null)
				eng.fx.flanger.setWet(v);
		}));

		box7.getChildren().add(new Label("Tremolo / Pan"));
		box7.getChildren().add(makeSlider("FX.Tr.Depth", "Trem Depth", 0.0, 1.0, 0.35, v -> {
			if (eng.fx != null)
				eng.fx.tremPan.setTremolo(v, eng.fx == null ? 4.0 : 4.0);
		}));
		box7.getChildren().add(makeSlider("FX.Tr.Rate", "Trem Rate (Hz)", 0.1, 12.0, 4.0, v -> {
			if (eng.fx != null)
				eng.fx.tremPan.setTremolo(0.35, v);
		}));
		box7.getChildren().add(makeSlider("FX.Pan.Depth", "Pan Depth", 0.0, 1.0, 0.5, v -> {
			if (eng.fx != null)
				eng.fx.tremPan.setPan(v, eng.fx == null ? 0.25 : 0.25);
		}));
		box7.getChildren().add(makeSlider("FX.Pan.Rate", "Pan Rate (Hz)", 0.05, 2.0, 0.25, v -> {
			if (eng.fx != null)
				eng.fx.tremPan.setPan(0.5, v);
		}));

		box8.getChildren().add(new Label("Bit Crusher"));
		box8.getChildren().add(makeSlider("FX.Cr.Bits", "Bits", 4, 16, 10, v -> {
			if (eng.fx != null)
				eng.fx.crusher.setBits((int) Math.round(v));
		}));
		box8.getChildren().add(makeSlider("FX.Cr.Down", "Downsample (samples)", 1, 12, 2, v -> {
			if (eng.fx != null)
				eng.fx.crusher.setDownsample((int) Math.round(v));
		}));
		box8.getChildren().add(makeSlider("FX.Cr.Wet", "Wet", 0.0, 1.0, 0.25, v -> {
			if (eng.fx != null)
				eng.fx.crusher.setWet(v);
		}));

		Accordion acc = new Accordion(new TitledPane("Quick Effects", box0), new TitledPane("Echo", box),
				new TitledPane("Chorus", box1), new TitledPane("Widener", box2), new TitledPane("Reverb", box3),
				new TitledPane("Wah", box4), new TitledPane("Phaser", box5), new TitledPane("Flanger", box6),
				new TitledPane("Trem/Pan", box7), new TitledPane("Crusher", box8));

		return new TitledPane("Effects", acc);
	}

	private TitledPane makeBusEqPane(Bus low, Bus mid, Bus high) {
		VBox box = new VBox(8);

		// LOW lane
		box.getChildren().add(new Label("Low Bus"));
		box.getChildren().add(makeSlider("BusLow.HP", "HP (Hz)", 10, 80, 28, v -> low.carve.setHP(v, 0.707)));
		box.getChildren().add(makeSlider("BusLow.LP", "LP (Hz)", 80, 240, 160, v -> low.carve.setLP(v, 0.707)));

		// MID lane
		box.getChildren().add(new Label("Mid Bus"));
		box.getChildren().add(makeSlider("BusMid.HP", "HP (Hz)", 80, 400, 140, v -> mid.carve.setHP(v, 0.9)));
		box.getChildren().add(makeSlider("BusMid.LP", "LP (Hz)", 1500, 8000, 4000, v -> mid.carve.setLP(v, 0.8)));

		// HIGH lane
		box.getChildren().add(new Label("High Bus"));
		box.getChildren().add(makeSlider("BusHigh.HP", "HP (Hz)", 2000, 9000, 3500, v -> high.carve.setHP(v, 0.7)));
		box.getChildren().add(makeSlider("BusHigh.LP", "LP (Hz)", 10000, 20000, 16000, v -> high.carve.setLP(v, 0.7)));

		// Optional trims using your MixerChannel gains
		box.getChildren().add(new Separator());
		box.getChildren().add(makeSlider("BusLow.Gain", "Low Trim", 0.2, 1.5, 1.0, v -> low.mix.setGain(v)));
		box.getChildren().add(makeSlider("BusMid.Gain", "Mid Trim", 0.2, 1.5, 1.0, v -> mid.mix.setGain(v)));
		box.getChildren().add(makeSlider("BusHigh.Gain", "High Trim", 0.2, 1.5, 1.0, v -> high.mix.setGain(v)));

		return new TitledPane("Bus EQ", box);
	}

	private static double harrisCellScore(float[] gx, float[] gy, int W, int x0, int y0) {
		// Second-moment matrix (structure tensor) over the 8×8 cell
		double Sxx = 0, Syy = 0, Sxy = 0;
		for (int dy = 0; dy < 8; dy++) {
			int y = y0 + dy;
			for (int dx = 0; dx < 8; dx++) {
				int x = x0 + dx;
				int i = y * W + x;
				double ix = gx[i], iy = gy[i];
				Sxx += ix * ix;
				Syy += iy * iy;
				Sxy += ix * iy;
			}
		}
		// Normalize by cell area (keeps values scale-stable)
		Sxx /= 64.0;
		Syy /= 64.0;
		Sxy /= 64.0;

		// Harris response: R = det(M) - k * trace(M)^2
		// k ~ 0.04..0.08; 0.06 is a good general choice
		double det = Sxx * Syy - Sxy * Sxy;
		double tr = Sxx + Syy;
		double R = det - 0.06 * tr * tr;

		// Return only positive "corner-like" energy; clamp to [0,1] later
		return Math.max(0.0, R);
	}

	private TitledPane makeDynamicsPane(VectorEngine eng) {
		VBox box = new VBox(10);

		// --- Sidechain (global for all duckers)
		box.getChildren().add(new Label("Sidechain (Kick → Sub/Bass)"));
		box.getChildren().add(makeSlider("SC.Depth", "Depth (dB)", -18, 0, -6, v -> eng.scParams.depthDb = (float) v));
		box.getChildren()
				.add(makeSlider("SC.Threshold", "Threshold", 0.0, 0.2, 0.02, v -> eng.scParams.threshold = (float) v));
		box.getChildren().add(makeSlider("SC.Ratio", "Ratio", 1.0, 6.0, 3.0, v -> eng.scParams.ratio = (float) v));
		box.getChildren()
				.add(makeSlider("SC.Attack", "Attack (ms)", 0.5, 20, 3.0, v -> eng.scParams.attackMs = (float) v));
		box.getChildren()
				.add(makeSlider("SC.Release", "Release (ms)", 40, 300, 120, v -> eng.scParams.releaseMs = (float) v));

		// --- Low mono
		box.getChildren().add(new Separator());
		box.getChildren().add(new Label("Low-Band Mono"));
		box.getChildren().add(makeSlider("Mono.Cut", "Cutoff (Hz)", 60, 200, 120, v -> eng.monoLow.setCutoff(v)));
		box.getChildren().add(makeSlider("Mono.Side", "Side Amount", 0.0, 1.0, 0.3, v -> eng.monoLow.setSideGain(v)));

		// --- Limiter
		box.getChildren().add(new Separator());
		box.getChildren().add(new Label("Master Limiter"));
		box.getChildren().add(makeSlider("Lim.Thr", "Threshold", 0.80, 1.05, 0.98, v -> eng.limiter.setThreshold(v)));
		box.getChildren().add(makeSlider("Lim.Attack", "Attack (ms)", 0.2, 10, 1.0, v -> eng.limiter.setAttackMs(v)));
		box.getChildren()
				.add(makeSlider("Lim.Release", "Release (ms)", 40, 400, 120, v -> eng.limiter.setReleaseMs(v)));

		return new TitledPane("Dynamics", box);
	}

	private Pane makeKickControls() {
		VBox box = new VBox(6);
		CheckBox en = makeCheck("Kick.Enable", "Enable", eng.kickEnabled, v -> eng.kickEnabled = v);
		en.setSelected(eng.kickEnabled);
		en.selectedProperty().addListener((o, ov, nv) -> eng.kickEnabled = nv);

		box.getChildren().addAll(en, makeSlider("Kick.Volume", 0, 1, eng.kickVolume, v -> eng.kickVolume = v),
				makeSlider("Kick.Attack (s)", 0.05, 0.5, eng.kickAttack, v -> eng.kickAttack = v),
				makeSlider("Kick.Decay (s)", 0.1, 1.0, eng.kickDecay, v -> eng.kickDecay = v),

				// NEW:
				makeSlider("Kick.Start Freq (Hz)", 10, 160, eng.kickStartHz, v -> eng.kickStartHz = v),
				makeSlider("Kick.End Freq (Hz)", 10, 160, eng.kickEndHz, v -> eng.kickEndHz = v),
				makeSlider("Kick.Punch / Drive", 1.0, 3.5, eng.kickDrive, v -> eng.kickDrive = v),
				makeSlider("Kick.Tone Mix", 0.0, 1.0, eng.kickToneMix, v -> eng.kickToneMix = v),
				makeSlider("Kick.Red Amp", 0.0, 1.0, eng.redKickAmp, v -> eng.redKickAmp = v),
				makeSlider("Kick.Red Threshold", 0.0, 1.5, eng.kickRedThreshold, v -> eng.kickRedThreshold = v),
				makeSlider("Kick.Energy Amp", 0.0, 1.0, eng.energyKickAmp, v -> eng.energyKickAmp = v), makeSlider(
						"Kick.Energy Threshold", 0.0, 1.5, eng.kickEnergyThreshold, v -> eng.kickEnergyThreshold = v));

		// --- Tempo controls (Kick) ---
		HBox kickRateRow = new HBox(8);
		HBox kickRateBox = makeChoice("Kick.Rate", "Tempo Rate", new String[] { "Half", "Normal", "Double" }, "Normal",
				s -> eng.kickRate = "Half".equals(s) ? Rate.HALF : "Double".equals(s) ? Rate.DOUBLE : Rate.NORMAL);

		kickRateRow.getChildren().addAll(kickRateBox);

		HBox kickPhaseRow = new HBox(8);
		HBox kickPhaseBox = makeChoice("Kick.HalfPhase", "Half Phase", new String[] { "0", "1" }, "0",
				s -> eng.kickPhase = "1".equals(s) ? 1 : 0);
		// Only relevant when HALF is selected
		kickPhaseRow.getChildren().addAll(kickPhaseBox);
		box.getChildren().addAll(kickRateRow, kickPhaseRow);

		return box;
	}

	private Pane makeBassControls() {
		VBox box = new VBox(6);

		CheckBox en = makeCheck("Bass.Enable", "Enable", eng.bassEnabled, v -> eng.bassEnabled = v);
		en.setSelected(eng.bassEnabled);
		en.selectedProperty().addListener((o, ov, nv) -> eng.bassEnabled = nv);

		// Waveform selector
		HBox wfRow = new HBox(8);
		HBox wf = makeChoice("Bass.WaveForm", "Waveform", new String[] { "Saw", "Square", "Tri" }, "Saw", (nv) -> {
			switch (nv) {
			case "Square":
				eng.bassWaveform = com.imagedubstep.nodes.BassWobbleNode.Waveform.SQUARE;
				break;
			case "Tri":
				eng.bassWaveform = com.imagedubstep.nodes.BassWobbleNode.Waveform.TRI;
				break;
			default:
				eng.bassWaveform = com.imagedubstep.nodes.BassWobbleNode.Waveform.SAW;
			}
		});
		wfRow.getChildren().addAll(wf);

		box.getChildren().addAll(en, makeSlider("Bass.Volume", 0, 10, eng.bassVolume, v -> eng.bassVolume = v),
				makeSlider("Bass.Filter (Hz)", 0, 1200, eng.bassFilterFreq, v -> eng.bassFilterFreq = v),
				makeSlider("Bass.Resonance (Q)", 0.5, 20.0, eng.bassResonance, v -> eng.bassResonance = v),
				makeSlider("Bass.LFO Rate (Hz)", 0.1, 12, eng.bassLfoRate, v -> eng.bassLfoRate = v),
				makeSlider("Bass.LFO Depth (Hz)", 0, 1000, eng.bassLfoDepth, v -> eng.bassLfoDepth = v),
				makeSlider("Bass.Duration (s)", 0.2, 1.5, eng.bassDuration, v -> eng.bassDuration = v),
				makeSlider("Bass.Blue Threshold", 0.01, 1.5, eng.wantBassBlueThreshold,
						v -> eng.wantBassBlueThreshold = v),
				makeSlider("Bass.Mid Blue Threshold", 0.01, 1.5, eng.midTakenBrightnessThreshold,
						v -> eng.midTakenBlueThreshold = v),
				makeSlider("Bass.Brightness Threshold", 0.01, 1.5, eng.wantBassbrightnessThreshold,
						v -> eng.wantBassbrightnessThreshold = v),
				makeSlider("Bass.Mid Brightness Threshold", 0.01, 1.5, eng.midTakenBrightnessThreshold,
						v -> eng.midTakenBrightnessThreshold = v),
				wfRow);
		HBox bassRateRow = new HBox(8);
		HBox bassRateBox = makeChoice("Bass.Rate", "Tempo Rate", new String[] { "Half", "Normal", "Double" }, "Normal",
				(nv) -> {
					if ("Half".equals(nv))
						eng.bassRate = Rate.HALF;
					else if ("Double".equals(nv))
						eng.bassRate = Rate.DOUBLE;
					else
						eng.bassRate = Rate.NORMAL;
				});

		bassRateRow.getChildren().addAll(bassRateBox);

		HBox bassPhaseRow = new HBox(8);
		HBox bassPhaseBox = makeChoice("Bass.HalfPhase", "Half Phase", new String[] { "0", "1" }, "0", (nv) -> {
			eng.bassPhase = "1".equals(nv) ? 1 : 0;
		});
		bassPhaseRow.getChildren().addAll(bassPhaseBox);
		box.getChildren().addAll(bassRateRow, bassPhaseRow);

		return box;
	}

	private Pane makeSubBassControls() {
		VBox box = new VBox(6);
		CheckBox en = makeCheck("Sub.Enable", "Enable", eng.subBassEnabled, v -> eng.subBassEnabled = v);
		en.setSelected(eng.subBassEnabled);
		en.selectedProperty().addListener((o, ov, nv) -> eng.subBassEnabled = nv);

		HBox subRateRow = new HBox(8);

		// Tempo Rate -> write to subRate (not bassRate)
		HBox subRateBox = makeChoice("Sub.Rate", "Tempo Rate", new String[] { "Half", "Normal", "Double" }, "Normal",
				(nv) -> {
					if ("Half".equals(nv))
						eng.subRate = Rate.HALF;
					else if ("Double".equals(nv))
						eng.subRate = Rate.DOUBLE;
					else
						eng.subRate = Rate.NORMAL;
				});

		// Half Phase -> write to subPhase (not bassPhase)

		subRateRow.getChildren().addAll(subRateBox);

		HBox subPhaseRow = new HBox(8);
		HBox subPhaseBox = makeChoice("Sub.HalfPhase", "Half Phase", new String[] { "0", "1" }, "0",
				(nv) -> eng.subPhase = "1".equals(nv) ? 1 : 0);
		subPhaseRow.getChildren().addAll(subPhaseBox);
		box.getChildren().addAll(subRateRow, subPhaseRow);
		box.getChildren().addAll(en, makeSlider("Sub.Volume", 0, 1, eng.subBassVolume, v -> eng.subBassVolume = v),
				makeSlider("Sub.Decay (s)", 0.3, 2.0, eng.subBassDecay, v -> eng.subBassDecay = v),

				// NEW musical controls:
				makeSlider("Sub.Frequency (Hz)", 30, 90, eng.subBassFreq, v -> eng.subBassFreq = v),
				makeSlider("Sub.Attack (ms)", 0, 30, eng.subBassAttack * 1000.0, v -> eng.subBassAttack = v / 1000.0),
				makeSlider("Sub.Harmonic Mix", 0.0, 1.0, eng.subBassHarmonicMix, v -> eng.subBassHarmonicMix = v),
				makeSlider("Sub.Drive", 1.0, 3.5, eng.subBassDrive, v -> eng.subBassDrive = v),
				makeSlider("Sub.Stereo Width", 0.0, 0.6, eng.subBassStereo, v -> eng.subBassStereo = v),
				makeSlider("Sub.Blue Threshold", 0.0, 1, eng.subBassBlueTreshold, v -> eng.subBassBlueTreshold = v),
				makeSlider("Sub.Brightness Threshold", 0.0, 0.6, eng.subBassBrightnessThreshold,
						v -> eng.subBassBrightnessThreshold = v));

		return box;
	}

	private Pane makeSnareControls() {
		VBox box = new VBox(6);
		CheckBox en = makeCheck("Snare.Enable", "Enable", eng.snareEnabled, v -> eng.snareEnabled = v);
		en.setSelected(eng.snareEnabled);
		en.selectedProperty().addListener((o, ov, nv) -> eng.snareEnabled = nv);
		box.getChildren().addAll(en, makeSlider("Snare.Volume", 0, 1, eng.snareVolume, v -> eng.snareVolume = v),
				makeSlider("Snare.Filter (Hz)", 100, 1000, eng.snareFilterFreq, v -> eng.snareFilterFreq = v),
				makeSlider("Snare.Decay (s)", 0.05, 0.3, eng.snareDecay, v -> eng.snareDecay = v),
				makeSlider("Snare.Green Threshold", 0.0, 1, eng.snareGreenThreshold, v -> eng.snareGreenThreshold = v));
		return box;
	}

	private Pane makeHiHatControls() {
		VBox box = new VBox(6);
		CheckBox en = makeCheck("HiHat.Enable", "Enable", eng.hiHatEnabled, v -> eng.hiHatEnabled = v);
		en.setSelected(eng.hiHatEnabled);
		en.selectedProperty().addListener((o, ov, nv) -> eng.hiHatEnabled = nv);
		box.getChildren().addAll(en, makeSlider("HiHat.Volume", 0, 1, eng.hiHatVolume, v -> eng.hiHatVolume = v),
				makeSlider("HiHat.HP Filter (Hz)", 4000, 15000, eng.hiHatFilterFreq, v -> eng.hiHatFilterFreq = v),
				makeSlider("HiHat.Saturation Threshold", 0, 1, eng.hiHatSaturationThreshold,
						v -> eng.hiHatSaturationThreshold = v));
		return box;
	}

	private Pane makeLinePercControls() {
		VBox box = new VBox(6);
		CheckBox en = makeCheck("LinePerc.Enable", "Enable", eng.linePercEnabled, v -> eng.linePercEnabled = v);
		en.setSelected(eng.linePercEnabled);
		en.selectedProperty().addListener((o, ov, nv) -> eng.linePercEnabled = nv);

		box.getChildren().addAll(en,
				makeSlider("LinePerc.Volume", 0, 1, eng.linePercVolume, v -> eng.linePercVolume = v),
				makeSlider("LinePerc.Decay (s)", 0.05, 0.5, eng.linePercDecay, v -> eng.linePercDecay = v),

				// NEW musical controls:
				makeSlider("LinePerc.Tone Freq Bias (Hz)", 80, 600, eng.linePercBaseFreq,
						v -> eng.linePercBaseFreq = v),
				makeSlider("LinePerc.Tone Range (Hz)", 0, 800, eng.linePercFreqRange, v -> eng.linePercFreqRange = v),
				makeSlider("LinePerc.Noise Blend", 0.0, 1.0, eng.linePercNoiseBlend, v -> eng.linePercNoiseBlend = v),
				makeSlider("LinePerc.Resonance Q", 0.5, 20.0, eng.linePercQ, v -> eng.linePercQ = v),
				makeSlider("LinePerc.Density Threshold", 0, 1.0, eng.lineLineDensityThreshold,
						v -> eng.lineLineDensityThreshold = v));
		return box;
	}

	private Pane makeHighFreqControls() {
		VBox box = new VBox(6);
		CheckBox en = makeCheck("HighFreq.Enable", "Enable", eng.highFreqEnabled, v -> eng.highFreqEnabled = v);
		en.setSelected(eng.highFreqEnabled);
		en.selectedProperty().addListener((o, ov, nv) -> eng.highFreqEnabled = nv);

		box.getChildren().addAll(en,
				makeSlider("HighFreq.Volume", 0, 1, eng.highFreqVolume, v -> eng.highFreqVolume = v),
				makeSlider("HighFreq.Decay (s)", 0.02, 0.3, eng.highFreqDecay, v -> eng.highFreqDecay = v),

				// NEW
				makeSlider("HighFreq.Osc Mix (saw→square)", 0.0, 1.0, eng.highFreqOscMix, v -> eng.highFreqOscMix = v),
				makeSlider("HighFreq.HP Cutoff (Hz)", 400, 3000, eng.highFreqHpCut, v -> eng.highFreqHpCut = v),
				makeSlider("HighFreq.Detune (Hz)", -400, 400, eng.highFreqDetune, v -> eng.highFreqDetune = v),
				makeSlider("HighFreq.Density Threshold", 0, 1, eng.freqLineDensityThreshold,
						v -> eng.freqLineDensityThreshold = v),
				makeSlider("HighFreq.Brightness Threshold", 0, 1, eng.freqBrightnessThreshold,
						v -> eng.freqBrightnessThreshold = v));
		return box;
	}

	private Pane makeMixControls() {
		VBox box = new VBox(8);

		// Checkboxes
		CheckBox lowBlend = makeCheck("MixLow.Enable", "Low Lane: Blend Kick + Sub", eng.mixLowBlend,
				v -> eng.mixLowBlend = v);
		lowBlend.setSelected(eng.mixLowBlend);
		lowBlend.selectedProperty().addListener((o, ov, nv) -> eng.mixLowBlend = nv);

		CheckBox highLayers = makeCheck("MixHigh.Enable", "High Lane: Allow 2 Layers", eng.mixHighTwoLayers,
				v -> eng.mixHighTwoLayers = v);
		highLayers.setSelected(eng.mixHighTwoLayers);
		highLayers.selectedProperty().addListener((o, ov, nv) -> eng.mixHighTwoLayers = nv);

		// Sliders (ms / trims)
		box.getChildren().addAll(lowBlend, highLayers,
				makeSlider("MixLow.Sub Nudge (ms)", 0, 40, eng.mixSubNudgeMs, v -> eng.mixSubNudgeMs = v),
				makeSlider("MixLow.Sub Duck (ms)", 0, 150, eng.mixSubDuckMs, v -> eng.mixSubDuckMs = v),
				makeSlider("MixLow.High Stagger (ms)", 0, 15, eng.mixHighStaggerMs, v -> eng.mixHighStaggerMs = v),
				makeSlider("MixLow.Low Blend Trim", 0.6, 1.0, eng.mixLowBlendTrim, v -> eng.mixLowBlendTrim = v),
				makeSlider("MixLow.High Layer Trim", 0.6, 1.0, eng.mixHighTrim, v -> eng.mixHighTrim = v),
				makeSlider("MixLow.Hat Trim", 0.6, 1.0, eng.mixHatTrim, v -> eng.mixHatTrim = v));
		return box;
	}

	// Convenience overload: label auto-derived from the part after the dot
	private Pane makeSlider(String key, double min, double max, double initial,
			java.util.function.DoubleConsumer onChange) {
		String label = key.contains(".") ? key.substring(key.indexOf('.') + 1) : key;
		label = label.replace('_', ' ').replace('-', ' ');
		return makeSlider(key, label, min, max, initial, onChange);
	}

	private TitledPane makeWebcamPane(VectorEngine eng) {
		webcamMgr = new com.imagedubstep.webcam.WebcamManager();
		VBox box = new VBox(10);

		// Camera selector
		var cams = com.imagedubstep.webcam.WebcamManager.listCameras();
		ComboBox<com.github.sarxos.webcam.Webcam> cb = new ComboBox<>();
		cb.getItems().addAll(cams);
		cb.setCellFactory(list -> new ListCell<>() {
			@Override
			protected void updateItem(com.github.sarxos.webcam.Webcam w, boolean empty) {
				super.updateItem(w, empty);
				setText((empty || w == null) ? "" : w.getName());
			}
		});
		cb.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(com.github.sarxos.webcam.Webcam w, boolean empty) {
				super.updateItem(w, empty);
				setText((empty || w == null) ? "Select Camera" : w.getName());
			}
		});

		Button btnStart = new Button("Start");
		Button btnStop = new Button("Stop");
		btnStop.setDisable(true);

		// FPS control
		Label fpsLab = new Label("FPS");
		Slider fps = new Slider(5, 30, 15);
		fps.setShowTickMarks(true);
		fps.valueProperty().addListener((o, ov, nv) -> {
			if (webcamMgr != null)
				webcamMgr.setTargetFps(nv.intValue());
		});

		// Preview
		webcamPreview = new ImageView();
		webcamPreview.setFitWidth(240);
		webcamPreview.setFitHeight(180);
		webcamPreview.setPreserveRatio(true);
		webcamPreview.setSmooth(true);

		btnStart.setOnAction(e -> {
			var sel = cb.getSelectionModel().getSelectedItem();
			if (sel == null)
				return;
			if (webcamMgr.open(sel)) {
				btnStart.setDisable(true);
				btnStop.setDisable(false);
				// Start feeding frames: preview + engine analysis
				webcamMgr.start(fxImg -> {
					webcamPreview.setImage(fxImg);
					eng.setImage(fxImg);
					lastStillImage = imageView.getImage();
					drawVectors();
					eng.queueAnalysis();
				});
			}
		});
		btnStop.setOnAction(e -> {
			if (webcamMgr != null) {
				webcamMgr.stop();
				webcamMgr.close();
				btnStart.setDisable(false);
				btnStop.setDisable(true);
			}
		});

		HBox row1 = new HBox(10, cb, btnStart, btnStop, fpsLab, fps);
		box.getChildren().addAll(row1, webcamPreview);
		return new TitledPane("Webcam", box);
	}

	private void refreshOverlay() {
		GraphicsContext g = overlay.getGraphicsContext2D();
		g.clearRect(0, 0, overlay.getWidth(), overlay.getHeight());
		if (VectorEngine.loadedImage == null)
			return;

		// Compute displayed size
		double imgW = VectorEngine.loadedImage.getWidth();
		double imgH = VectorEngine.loadedImage.getHeight();
		double scale = eng.imageView.getFitWidth() / imgW;
		double dispW = imgW * scale;
		double dispH = imgH * scale;
		overlay.setWidth(dispW);
		overlay.setHeight(dispH);

		// Window size as fraction of min dimension
		double base = Math.min(imgW, imgH);
		double winPix = base * VectorEngine.winSize; // size in source pixels
		// Compute top-left based on normalized offsets
		double maxX = Math.max(0, imgW - winPix);
		double maxY = Math.max(0, imgH - winPix);
		double srcX = VectorEngine.winX * maxX;
		double srcY = VectorEngine.winY * maxY;

		// Convert to display coords
		double dx = srcX * scale;
		double dy = srcY * scale;
		double dw = winPix * scale;
		double dh = winPix * scale;

		g.setStroke(Color.LIME);
		g.setLineWidth(2.0);
		g.strokeRect(dx, dy, dw, dh);
	}

	private void drawVectors() {
		GraphicsContext gc = vecGrid.getGraphicsContext2D();
		gc.clearRect(0, 0, vecGrid.getWidth(), vecGrid.getHeight());

		synchronized (this) {
			for (int i = 0; i < VectorEngine.vectors.size(); i++) {
				int x = (i % 8) * 16;
				int y = (i / 8) * 16;

				Vector v = eng.getVec(i);
				gc.setFill(Util.colorFor(v)); // <-- changed
				gc.fillRect(x, y, 14, 14);

			}
		}
		if (randomWall != null)
			randomWall.drawAll();
	}

	// Build 8×8 feature vectors from a sliding window of the image, scaled to W×H
	// (use W=H=64).

	// Keyed checkbox
	private javafx.scene.control.CheckBox makeCheck(String key, String label, boolean initial,
			java.util.function.Consumer<Boolean> onChange) {
		javafx.scene.control.CheckBox c = new javafx.scene.control.CheckBox(label);
		c.setSelected(initial);
		c.selectedProperty().addListener((o, ov, nv) -> onChange.accept(nv));
		registerCheck(key, c);
		return c;
	}

	// Keyed choice (for tempo rate/phase, waveform, etc.)
	private javafx.scene.layout.HBox makeChoice(String key, String label, String[] items, String initial,
			java.util.function.Consumer<String> onChange) {
		javafx.scene.control.Label lab = new javafx.scene.control.Label(label);
		javafx.scene.control.ChoiceBox<String> cb = new javafx.scene.control.ChoiceBox<>();
		cb.getItems().addAll(items);
		cb.setValue(initial);
		cb.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> onChange.accept(nv));
		registerChoice(key, cb);
		return new javafx.scene.layout.HBox(8, lab, cb);
	}

	private java.io.File ensurePropsExtension(java.io.File f) throws java.io.IOException {
		String name = f.getName();
		if (!name.toLowerCase().endsWith(".properties")) {
			f = new java.io.File(f.getParentFile(), name + ".properties");
		}
		java.io.File parent = f.getParentFile();
		if (parent != null && !parent.exists())
			parent.mkdirs();
		if (!f.exists())
			f.createNewFile();
		return f;
	}

	private void saveSettingsTo(java.io.File f) {
		try (java.io.FileOutputStream out = new java.io.FileOutputStream(ensurePropsExtension(f))) {
			java.util.Properties p = new java.util.Properties();

			int sc = 0, cc = 0, kc = 0; // counts
			for (var e : sliderRegistry.entrySet()) {
				p.setProperty(e.getKey(), Double.toString(e.getValue().getValue()));
				sc++;
			}
			for (var e : checkRegistry.entrySet()) {
				p.setProperty(e.getKey(), Boolean.toString(e.getValue().isSelected()));
				cc++;
			}
			for (var e : choiceRegistry.entrySet()) {
				String v = e.getValue().getValue();
				if (v != null) {
					p.setProperty(e.getKey(), v);
					kc++;
				}
			}
			p.setProperty("_format", "imagedubstep.v1");

			p.store(out, "ImageDubstep Settings");
//			System.out.printf("[Settings] Saved sliders=%d checks=%d choices=%d -> %s%n", sc, cc, kc,
//					f.getAbsolutePath());
		} catch (Exception ex) {
			ex.printStackTrace();
			new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
					"Save failed: " + ex.getMessage()).show();
		}
	}

	private void loadSettingsFrom(java.io.File f) {
		try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
			java.util.Properties p = new java.util.Properties();
			p.load(in);

			int sc = 0, cc = 0, kc = 0;
			for (var e : sliderRegistry.entrySet()) {
				String val = p.getProperty(e.getKey());
				if (val != null) {
					e.getValue().setValue(Double.parseDouble(val));
					sc++;
				}
			}
			for (var e : checkRegistry.entrySet()) {
				String val = p.getProperty(e.getKey());
				if (val != null) {
					e.getValue().setSelected(Boolean.parseBoolean(val));
					cc++;
				}
			}
			for (var e : choiceRegistry.entrySet()) {
				String val = p.getProperty(e.getKey());
				if (val != null) {
					e.getValue().setValue(val);
					kc++;
				}
			}
//			System.out.printf("[Settings] Loaded sliders=%d checks=%d choices=%d <- %s%n", sc, cc, kc,
//					f.getAbsolutePath());
		} catch (Exception ex) {
			ex.printStackTrace();
			new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,
					"Load failed: " + ex.getMessage()).show();
		}
	}

	/**
	 * Fills the six extra Vector features for an 8x8 grid, using your existing
	 * 64x64 luma + Sobel arrays. Assumes vectors are ordered row-major: row*8 +
	 * col.
	 */

	// Keyed slider maker (registers slider under 'key') with editable readout
	private javafx.scene.layout.Pane makeSlider(String key, String label, double min, double max, double initial,
			java.util.function.DoubleConsumer onChange) {

		javafx.scene.control.Label lab = new javafx.scene.control.Label(label);

		javafx.scene.control.Slider s = new javafx.scene.control.Slider(min, max, initial);
		s.setShowTickMarks(true);
		s.setShowTickLabels(true);
		s.setBlockIncrement(.1);
		s.setMajorTickUnit(.25);

		// Text field mirrors the slider and can edit it
		javafx.scene.control.TextField tf = new javafx.scene.control.TextField();
		tf.setPrefWidth(90);

		// Simple formatter: nice integers, otherwise a few decimals depending on range
		java.util.function.Function<Double, String> fmt = v -> {
			double av = Math.abs(v);
			if (Math.floor(v) == v)
				return String.format(java.util.Locale.US, "%.0f", v);
			if (av >= 1000)
				return String.format(java.util.Locale.US, "%.0f", v);
			if (av >= 100)
				return String.format(java.util.Locale.US, "%.1f", v);
			if (av >= 10)
				return String.format(java.util.Locale.US, "%.1f", v);
			return String.format(java.util.Locale.US, "%.3f", v);
		};

		// init text to the starting value
		tf.setText(fmt.apply(initial));

		// keep text in sync when the slider moves (also fires your onChange)
		s.valueProperty().addListener((o, ov, nv) -> {
			double val = nv.doubleValue();
			tf.setText(fmt.apply(val));
			onChange.accept(val);
		});

		// when user edits text, parse and clamp, then push into the slider
		Runnable commitFromText = () -> {
			try {
				String raw = tf.getText().trim();
				// allow typing with units like "200 Hz" or "0.25 s": strip non numeric chars
				String cleaned = raw.replaceAll("[^0-9eE+\\-\\.]", "");
				double v = Double.parseDouble(cleaned);
				if (v < min)
					v = min;
				if (v > max)
					v = max;
				s.setValue(v); // triggers listener above (updates tf + onChange)
			} catch (Exception ex) {
				// revert if parse fails
				tf.setText(fmt.apply(s.getValue()));
			}
		};

		tf.setOnAction(ev -> commitFromText.run());
		tf.focusedProperty().addListener((obs, was, is) -> {
			if (!is)
				commitFromText.run();
		});

		javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(8, lab, s, tf);
		row.setAlignment(Pos.CENTER_LEFT);
		javafx.scene.layout.HBox.setHgrow(s, Priority.ALWAYS);

		registerSlider(key, s);
		return row;
	}

	public static void main(String[] args) {
		launch();
	}
}
