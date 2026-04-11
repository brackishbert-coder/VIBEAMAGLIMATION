package com.imagedubstep.visualizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.imagedubstep.engine.EngineInterface;
import com.imagedubstep.engine.VectorEngine;
import com.imagedubstep.som.SOM;
import com.imagedubstep.som.SOMRuntime;
import com.imagedubstep.som.SOMView;

import javafx.animation.AnimationTimer;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.stage.Stage;

/**
 * A wall of SOMView tiles shown in its own window.
 */
public final class RandomSOMWall extends BorderPane {

    private final VectorEngine eng;
    private final int cols, rows;
    private final GridPane grid = new GridPane();
    private final List<SOMView> tiles = new ArrayList<>();
    private final Random rng = new Random();

    private SOM som;
    private SOMRuntime somRt;

    private AnimationTimer timer;
    private long lastNs = 0;
    private int bmuDrainPerTick = 6;   // how many BMU events to visualize per frame
    private int fpsLimitNs = 33_000_000; // ~30 fps

    private Stage stage;

    public RandomSOMWall(VectorEngine eng2,SOM som,SOMRuntime somRt, int cols, int rows) {
        this.eng = eng2;
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);

        // Ensure SOM + runtime exist

        this.som = som;

		somRt.setMaxBatchPerTick(64);  // was 16/32
		somRt.setIdleSleepMs(1);       // was 3–6
		somRt.setBmuPostEvery(4);
     
 somRt.start();
       
        this.somRt = somRt;

        buildGrid();
        setCenter(grid);
        setPadding(Insets.EMPTY);

        // Setup window
        stage = new Stage();
        stage.setTitle("Random SOM Wall");
        stage.setScene(new Scene(this, 800, 800));
        

        // Start heartbeat
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (now - lastNs < fpsLimitNs) return;
                lastNs = now;

                for (int i = 0; i < bmuDrainPerTick; i++) {
                    int[] b = (RandomSOMWall.this.somRt != null)
                              ? RandomSOMWall.this.somRt.pollBMU() : null;
                    if (b == null) break;
                    for (SOMView v : tiles) v.pingBMU(b[0], b[1]);
                }
                for (SOMView v : tiles) v.redraw();
            }
        };
        timer.start();
    }

    /** Re-randomize the modes/dimensions across the wall. */
    public void shuffle() {
        int dim = com.imagedubstep.som.BeatFeatureExtractor.DIM;
        for (SOMView v : tiles) {
            if (rng.nextBoolean()) {
                v.setMode(SOMView.Mode.U_MATRIX);
            } else {
                v.setMode(SOMView.Mode.WEIGHT_DIM);
                v.setDimIndex(rng.nextInt(dim));
            }
        }
    }

    /** Close the window and stop the animation timer. */
    public void close() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (stage != null) {
            stage.close();
            stage = null;
        }
    }

    // ---------------- internal ----------------

    private void buildGrid() {
        grid.setPadding(Insets.EMPTY);
        grid.setHgap(0);
        grid.setVgap(0);

        // Stretch equally
        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            cc.setHalignment(HPos.CENTER);
            cc.setPercentWidth(100.0 / cols);
            grid.getColumnConstraints().add(cc);
        }
        for (int r = 0; r < rows; r++) {
            RowConstraints rc = new RowConstraints();
            rc.setVgrow(Priority.ALWAYS);
            rc.setValignment(VPos.CENTER);
            rc.setPercentHeight(100.0 / rows);
            grid.getRowConstraints().add(rc);
        }

        tiles.clear();
        grid.getChildren().clear();

        int dim = com.imagedubstep.som.BeatFeatureExtractor.DIM;

        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                SOMView view = new SOMView(som, 100, 100); // initial size
                if (rng.nextBoolean()) {
                    view.setMode(SOMView.Mode.U_MATRIX);
                } else {
                    view.setMode(SOMView.Mode.WEIGHT_DIM);
                    view.setDimIndex(rng.nextInt(dim));
                }
                tiles.add(view);
                grid.add(view, x, y);
            }
        }

        shuffle();
    }
    public void show() {  stage.show(); }
   
}
