package com.racer.app.racer;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.media.AudioClip;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Racer extends Application {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 720;

    private static final int ROAD_LEFT = 90;
    private static final int ROAD_RIGHT = WIDTH - 90;
    private static final int LANES = 3;
    private static final double LANE_WIDTH = (ROAD_RIGHT - ROAD_LEFT) / (double) LANES;

    private static final double CAR_W = 50;
    private static final double CAR_H = 90;

    private static final double DAY_NIGHT_CYCLE_SECONDS = 45.0;

    private enum GameState { MENU, COUNTDOWN, PLAYING, GAME_OVER }

    private enum CarVariant { SEDAN, SPORTS, TRUCK }

    private static final Color[] CAR_COLORS = {
            Color.LIMEGREEN, Color.CRIMSON, Color.DODGERBLUE, Color.GOLD,
            Color.WHITE, Color.DARKORANGE, Color.MEDIUMPURPLE
    };
    private static final CarVariant[] CAR_VARIANTS = CarVariant.values();
    private int colorIndex = 0;
    private int variantIndex = 0;

    private static final double MENU_COLOR_ROW_Y = HEIGHT / 2.0 + 10;
    private static final double MENU_VARIANT_ROW_Y = HEIGHT / 2.0 + 60;
    private static final double MENU_COLOR_ARROW_OFFSET = 75;
    private static final double MENU_VARIANT_ARROW_OFFSET = 95;
    private static final double MENU_ARROW_HIT_RADIUS = 18;

    private final Random random = new Random();
    private final List<Obstacle> obstacles = new ArrayList<>();

    private GameState state = GameState.MENU;

    private double playerX;
    private double playerY;
    private boolean movingLeft = false;
    private boolean movingRight = false;

    private double speed;
    private double distance;
    private double spawnTimer;
    private double spawnInterval;
    private double roadLineOffset;

    private double countdownElapsed;
    private static final double LIGHT_STEP_SECONDS = 1.0;
    private static final int LIGHT_STAGES = 3;
    private int lastLightStage = -1;

    private double elapsedTime;

    private long lastNanoTime = -1;

    private MediaPlayer menuMusicPlayer;
    private MediaPlayer musicPlayer;
    private MediaPlayer engineLoopPlayer;
    private AudioClip beepClip;
    private AudioClip goClip;
    private AudioClip crashClip;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        StackPane root = new StackPane(canvas);
        Scene scene = new Scene(root, WIDTH, HEIGHT);

        loadAudio();
        resetGame();

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.A) movingLeft = true;
            if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.D) movingRight = true;
            if ((e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE) && state == GameState.MENU) {
                startCountdown();
            }
            if (e.getCode() == KeyCode.R && state == GameState.GAME_OVER) goToMenu();
        });
        scene.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.A) movingLeft = false;
            if (e.getCode() == KeyCode.RIGHT || e.getCode() == KeyCode.D) movingRight = false;
        });
        scene.setOnMouseClicked(e -> {
            if (state == GameState.MENU) startCountdown();
        });

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanoTime < 0) lastNanoTime = now;
                double dt = Math.min(0.05, (now - lastNanoTime) / 1_000_000_000.0);
                lastNanoTime = now;

                elapsedTime += dt;
                update(dt);
                render(gc);
            }
        };
        timer.start();

        stage.setTitle("JavaFX Car Racer");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    private void loadAudio() {
        menuMusicPlayer = loadLoopingMedia("/com/racer/app/racer/audio/menu.mp3", 0.5);
        musicPlayer = loadLoopingMedia("/com/racer/app/racer/audio/music.mp3", 0.35);
        engineLoopPlayer = loadLoopingMedia("/com/racer/app/racer/audio/engine.mp3", 0.5);
        beepClip = loadClip("/com/racer/app/racer/audio/beep.mp3");
        goClip = loadClip("/com/racer/app/racer/audio/go.mp3");
        crashClip = loadClip("/com/racer/app/racer/audio/crash.mp3");
    }

    private MediaPlayer loadLoopingMedia(String resourcePath, double volume) {
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) {
                System.out.println("Audio not found (skipping): " + resourcePath);
                return null;
            }
            MediaPlayer player = new MediaPlayer(new Media(url.toExternalForm()));
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setVolume(volume);
            return player;
        } catch (Exception e) {
            System.out.println("Could not load audio " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    private AudioClip loadClip(String resourcePath) {
        try {
            URL url = getClass().getResource(resourcePath);
            if (url == null) {
                System.out.println("Audio not found (skipping): " + resourcePath);
                return null;
            }
            return new AudioClip(url.toExternalForm());
        } catch (Exception e) {
            System.out.println("Could not load audio " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    private void playClip(AudioClip clip) {
        if (clip != null) clip.play();
    }


    private void resetGame() {
        obstacles.clear();
        playerX = laneCenter(1) - CAR_W / 2;
        playerY = HEIGHT - CAR_H - 30;

        speed = 2.5;
        distance = 0;
        spawnTimer = 0;
        spawnInterval = 90;
        roadLineOffset = 0;

        countdownElapsed = 0;
        lastLightStage = -1;
        lastNanoTime = -1;

        if (engineLoopPlayer != null) engineLoopPlayer.pause();
        if (musicPlayer != null) musicPlayer.pause();

        state = GameState.MENU;
        if (menuMusicPlayer != null) {
            menuMusicPlayer.seek(menuMusicPlayer.getStartTime());
            menuMusicPlayer.play();
        }
    }

    private void goToMenu() {
        resetGame();
    }

    private void startCountdown() {
        if (menuMusicPlayer != null) menuMusicPlayer.pause();
        countdownElapsed = 0;
        lastLightStage = -1;
        state = GameState.COUNTDOWN;
        if (musicPlayer != null) musicPlayer.play();
    }

    private double laneCenter(int lane) {
        return ROAD_LEFT + LANE_WIDTH * lane + LANE_WIDTH / 2;
    }

    private void update(double dt) {
        switch (state) {
            case MENU -> {

                roadLineOffset += 1.0;
                if (roadLineOffset > 40) roadLineOffset = 0;
            }
            case COUNTDOWN -> updateCountdown(dt);
            case PLAYING -> updatePlaying(dt);
            case GAME_OVER -> {  }
        }
    }

    private void updateCountdown(double dt) {
        countdownElapsed += dt;
        int stage = (int) (countdownElapsed / LIGHT_STEP_SECONDS);

        if (stage != lastLightStage) {
            lastLightStage = stage;
            if (stage >= 1 && stage <= LIGHT_STAGES) {
                playClip(beepClip);
            } else if (stage > LIGHT_STAGES) {
                playClip(goClip);
            }
        }

        if (countdownElapsed >= LIGHT_STEP_SECONDS * (LIGHT_STAGES + 1)) {
            state = GameState.PLAYING;
            lastLightStage = -1;
            if (engineLoopPlayer != null) engineLoopPlayer.play();
        }

        roadLineOffset += 1.0;
        if (roadLineOffset > 40) roadLineOffset = 0;
    }

    private void updatePlaying(double dt) {
        double moveSpeed = 320;
        if (movingLeft) playerX -= moveSpeed * dt;
        if (movingRight) playerX += moveSpeed * dt;
        playerX = Math.max(ROAD_LEFT + 5, Math.min(ROAD_RIGHT - CAR_W - 5, playerX));

        distance += speed * dt * 10;
        speed += dt * 0.08;
        spawnInterval = Math.max(20, 90 - distance / 25.0);

        roadLineOffset += speed * 2;
        if (roadLineOffset > 40) roadLineOffset = 0;

        spawnTimer += 1;
        if (spawnTimer >= spawnInterval) {
            spawnTimer = 0;
            spawnObstacle();
        }

        List<Obstacle> toRemove = new ArrayList<>();
        for (Obstacle o : obstacles) {
            o.y += speed * o.speedFactor;
            if (o.y > HEIGHT) {
                toRemove.add(o);
            } else if (rectsOverlap(playerX, playerY, CAR_W, CAR_H, o.x, o.y, CAR_W, CAR_H)) {
                state = GameState.GAME_OVER;
                playClip(crashClip);
                if (engineLoopPlayer != null) engineLoopPlayer.pause();
                if (musicPlayer != null) musicPlayer.pause();
            }
        }
        obstacles.removeAll(toRemove);
    }

    private void spawnObstacle() {
        int lane = random.nextInt(LANES);
        double x = laneCenter(lane) - CAR_W / 2;
        double speedFactor = 0.8 + random.nextDouble() * 0.6;
        Color color = randomCarColor();
        obstacles.add(new Obstacle(x, -CAR_H, speedFactor, color));
    }

    private Color randomCarColor() {
        Color[] palette = { Color.CRIMSON, Color.DODGERBLUE, Color.GOLD, Color.MEDIUMPURPLE, Color.DARKORANGE };
        return palette[random.nextInt(palette.length)];
    }

    private boolean rectsOverlap(double x1, double y1, double w1, double h1,
                                 double x2, double y2, double w2, double h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    private double nightFactor() {
        double phase = (elapsedTime % DAY_NIGHT_CYCLE_SECONDS) / DAY_NIGHT_CYCLE_SECONDS;
        return (1 - Math.cos(phase * 2 * Math.PI)) / 2.0;
    }

    private void render(GraphicsContext gc) {
        double night = nightFactor();

        Color grassDay = Color.rgb(34, 120, 60);
        Color grassNight = Color.rgb(10, 30, 20);
        Color roadDay = Color.rgb(50, 50, 55);
        Color roadNight = Color.rgb(18, 18, 22);

        gc.setFill(lerp(grassDay, grassNight, night));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.setFill(lerp(roadDay, roadNight, night));
        gc.fillRect(ROAD_LEFT, 0, ROAD_RIGHT - ROAD_LEFT, HEIGHT);

        gc.setFill(Color.WHITE.deriveColor(0, 1, 1 - night * 0.4, 1));
        for (int lane = 1; lane < LANES; lane++) {
            double x = ROAD_LEFT + LANE_WIDTH * lane - 2;
            for (double y = -40 + roadLineOffset; y < HEIGHT; y += 40) {
                gc.fillRect(x, y, 4, 24);
            }
        }
        gc.fillRect(ROAD_LEFT - 4, 0, 4, HEIGHT);
        gc.fillRect(ROAD_RIGHT, 0, 4, HEIGHT);

        double phase = (elapsedTime % DAY_NIGHT_CYCLE_SECONDS) / DAY_NIGHT_CYCLE_SECONDS;
        Color orb = night > 0.5 ? Color.rgb(230, 230, 210) : Color.rgb(255, 214, 100);
        gc.setFill(orb);
        gc.fillOval(WIDTH - 50, 20 + Math.sin(phase * 2 * Math.PI) * 5, 26, 26);

        for (Obstacle o : obstacles) {
            drawCar(gc, o.x, o.y, o.color, night);
        }
        drawCar(gc, playerX, playerY, Color.LIMEGREEN, night);


        if (night > 0.02) {
            gc.setFill(Color.rgb(10, 10, 40, night * 0.35));
            gc.fillRect(0, 0, WIDTH, HEIGHT);
        }

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font(20));
        gc.fillText("Score: " + (int) distance, 12, 28);

        if (state == GameState.MENU) {
            drawMenu(gc);
        }

        if (state == GameState.COUNTDOWN) {
            drawStartLights(gc);
        }

        if (state == GameState.GAME_OVER) {
            gc.setFill(Color.rgb(0, 0, 0, 0.6));
            gc.fillRect(0, HEIGHT / 2.0 - 60, WIDTH, 120);
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(28));
            gc.fillText("GAME OVER", WIDTH / 2.0 - 90, HEIGHT / 2.0 - 10);
            gc.setFont(Font.font(18));
            gc.fillText("Score: " + (int) distance + "   Press R to restart", WIDTH / 2.0 - 130, HEIGHT / 2.0 + 25);
        }
    }

    private void drawMenu(GraphicsContext gc) {
        gc.setFill(Color.rgb(0, 0, 0, 0.55));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);

        gc.setFill(Color.LIMEGREEN);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 48));
        gc.fillText("CAR RACER", WIDTH / 2.0, HEIGHT / 2.0 - 90);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.NORMAL, 18));
        gc.fillText("Dodge traffic. Survive as long as you can.", WIDTH / 2.0, HEIGHT / 2.0 - 50);
        gc.fillText("Steer with \u2190 \u2192 or A / D", WIDTH / 2.0, HEIGHT / 2.0 - 20);


        double blink = (Math.sin(elapsedTime * 4) + 1) / 2.0;
        gc.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.4 + 0.6 * blink));
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        gc.fillText("Press ENTER or Click to Start", WIDTH / 2.0, HEIGHT / 2.0 + 40);

        gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
    }

    private void drawStartLights(GraphicsContext gc) {
        int stage = (int) (countdownElapsed / LIGHT_STEP_SECONDS);

        double boxW = 140, boxH = 70;
        double boxX = WIDTH / 2.0 - boxW / 2, boxY = 60;
        gc.setFill(Color.rgb(20, 20, 20, 0.85));
        gc.fillRoundRect(boxX, boxY, boxW, boxH, 14, 14);

        double r = 16, gap = 8;
        double totalW = LIGHT_STAGES * (r * 2) + (LIGHT_STAGES - 1) * gap;
        double startX = boxX + (boxW - totalW) / 2;
        double cy = boxY + boxH / 2;

        for (int i = 0; i < LIGHT_STAGES; i++) {
            boolean lit = stage > i;
            boolean allGreen = stage > LIGHT_STAGES;
            Color c = allGreen ? Color.LIMEGREEN : (lit ? Color.RED : Color.rgb(60, 60, 60));
            gc.setFill(c);
            double cx = startX + i * (r * 2 + gap) + r;
            gc.fillOval(cx - r, cy - r, r * 2, r * 2);
        }

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 26));
        if (stage <= LIGHT_STAGES) {
            String num = String.valueOf(LIGHT_STAGES + 1 - stage);
            gc.fillText(num, WIDTH / 2.0 - 8, boxY + boxH + 40);
        } else {
            gc.setFill(Color.LIMEGREEN);
            gc.fillText("GO!", WIDTH / 2.0 - 24, boxY + boxH + 40);
        }
    }

    private Color lerp(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                a.getRed() + (b.getRed() - a.getRed()) * t,
                a.getGreen() + (b.getGreen() - a.getGreen()) * t,
                a.getBlue() + (b.getBlue() - a.getBlue()) * t,
                1.0
        );
    }

    private void drawCar(GraphicsContext gc, double x, double y, Color body, double night) {
        gc.setFill(body);
        gc.fillRoundRect(x, y, CAR_W, CAR_H, 12, 12);
        gc.setFill(Color.rgb(180, 220, 255, 0.85));
        gc.fillRoundRect(x + 8, y + 12, CAR_W - 16, 22, 6, 6);
        gc.setFill(Color.BLACK);
        gc.fillRect(x - 3, y + 10, 6, 18);
        gc.fillRect(x + CAR_W - 3, y + 10, 6, 18);
        gc.fillRect(x - 3, y + CAR_H - 28, 6, 18);
        gc.fillRect(x + CAR_W - 3, y + CAR_H - 28, 6, 18);

        if (night > 0.35) {
            gc.setFill(Color.rgb(255, 245, 180, Math.min(0.9, night)));
            gc.fillOval(x + 4, y - 4, 10, 8);
            gc.fillOval(x + CAR_W - 14, y - 4, 10, 8);
        }
    }

    private static class Obstacle {
        double x, y;
        double speedFactor;
        Color color;

        Obstacle(double x, double y, double speedFactor, Color color) {
            this.x = x;
            this.y = y;
            this.speedFactor = speedFactor;
            this.color = color;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}