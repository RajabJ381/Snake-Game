import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Core game panel: owns the snake/food model, the game loop, rendering,
 * and keyboard input. The panel moves through four states (see
 * {@link GameState}): a start screen, active play, a paused overlay, and
 * a game-over screen that lets the player restart without relaunching
 * the app.
 */
public class SnakeGame extends JPanel implements ActionListener, KeyListener {

    private class Tile {
        int x;
        int y;

        Tile(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    // ----- Board -----
    private final int boardWidth;
    private final int boardHeight;
    private final int tileSize = 25;

    // ----- Snake -----
    private Tile snakeHead;
    private ArrayList<Tile> snakeBody;

    // ----- Food -----
    private Tile food;
    private final Random random = new Random();

    // ----- Game loop -----
    private final Timer gameLoop;
    private static final int BASE_DELAY_MS = 120;
    private static final int MIN_DELAY_MS = 70;
    private static final int SPEEDUP_PER_FOOD_MS = 2;

    // ----- Start / game-over prompt blink -----
    private final Timer blinkTimer;
    private boolean blinkOn = true;

    // ----- Movement -----
    // velocityX/Y is the direction currently in effect for movement.
    // nextVelocityX/Y is the direction requested since the last tick.
    // Buffering the request and only applying it once per tick (in move())
    // prevents the classic bug where pressing two keys in the same frame
    // lets the snake reverse directly into its own neck.
    private int velocityX;
    private int velocityY;
    private int nextVelocityX;
    private int nextVelocityY;

    // ----- Game state -----
    private GameState state = GameState.START_SCREEN;
    private int score = 0;
    private boolean newHighScoreThisRun = false;
    private final HighScoreManager highScoreManager = new HighScoreManager();

    // ----- Palette -----
    private static final Color COLOR_BG = new Color(24, 26, 34);
    private static final Color COLOR_GRID = new Color(33, 36, 46);
    private static final Color COLOR_SNAKE_HEAD = new Color(102, 217, 130);
    private static final Color COLOR_SNAKE_BODY = new Color(58, 168, 100);
    private static final Color COLOR_FOOD = new Color(235, 87, 87);
    private static final Color COLOR_TEXT = new Color(235, 235, 240);
    private static final Color COLOR_ACCENT = new Color(242, 201, 76);
    private static final Color COLOR_OVERLAY = new Color(0, 0, 0, 175);

    SnakeGame(int boardWidth, int boardHeight) {
        this.boardWidth = boardWidth;
        this.boardHeight = boardHeight;

        setPreferredSize(new Dimension(this.boardWidth, this.boardHeight));
        setBackground(COLOR_BG);
        setFocusable(true);
        addKeyListener(this);

        gameLoop = new Timer(BASE_DELAY_MS, this);

        blinkTimer = new Timer(500, e -> {
            blinkOn = !blinkOn;
            if (state == GameState.START_SCREEN || state == GameState.GAME_OVER) {
                repaint();
            }
        });
        blinkTimer.start();

        resetGame();
    }

    // ================= Game flow control =================

    private void resetGame() {
        snakeHead = new Tile(5, 5);
        snakeBody = new ArrayList<>();

        velocityX = 1;
        velocityY = 0;
        nextVelocityX = 1;
        nextVelocityY = 0;

        score = 0;
        newHighScoreThisRun = false;

        gameLoop.setDelay(BASE_DELAY_MS);
        placeFood();
    }

    private void startGame() {
        resetGame();
        state = GameState.PLAYING;
        gameLoop.start();
        repaint();
    }

    private void pauseGame() {
        state = GameState.PAUSED;
        gameLoop.stop();
        repaint();
    }

    private void resumeGame() {
        state = GameState.PLAYING;
        gameLoop.start();
        repaint();
    }

    private void triggerGameOver() {
        state = GameState.GAME_OVER;
        gameLoop.stop();
        repaint();
    }

    // ================= Update logic =================

    private void move() {
        // Apply the buffered direction chosen since the last tick.
        velocityX = nextVelocityX;
        velocityY = nextVelocityY;

        boolean ateFood = collision(snakeHead, food);

        if (ateFood) {
            snakeBody.add(new Tile(food.x, food.y));
            score++;
            if (highScoreManager.reportScore(score)) {
                newHighScoreThisRun = true;
            }
            gameLoop.setDelay(Math.max(MIN_DELAY_MS, BASE_DELAY_MS - score * SPEEDUP_PER_FOOD_MS));
            placeFood();
        }

        for (int i = snakeBody.size() - 1; i >= 0; i--) {
            Tile part = snakeBody.get(i);
            if (i == 0) {
                part.x = snakeHead.x;
                part.y = snakeHead.y;
            } else {
                Tile prev = snakeBody.get(i - 1);
                part.x = prev.x;
                part.y = prev.y;
            }
        }

        snakeHead.x += velocityX;
        snakeHead.y += velocityY;

        if (snakeHead.x < 0 || snakeHead.y < 0
                || snakeHead.x >= boardWidth / tileSize
                || snakeHead.y >= boardHeight / tileSize) {
            triggerGameOver();
            return;
        }

        for (Tile part : snakeBody) {
            if (collision(snakeHead, part)) {
                triggerGameOver();
                return;
            }
        }
    }

    private void placeFood() {
        int maxX = boardWidth / tileSize;
        int maxY = boardHeight / tileSize;

        ArrayList<Tile> freeCells = new ArrayList<>();
        for (int x = 0; x < maxX; x++) {
            for (int y = 0; y < maxY; y++) {
                Tile candidate = new Tile(x, y);
                if (!isOccupiedBySnake(candidate)) {
                    freeCells.add(candidate);
                }
            }
        }

        if (!freeCells.isEmpty()) {
            food = freeCells.get(random.nextInt(freeCells.size()));
        }
        // If there are no free cells, the snake fills the entire board --
        // effectively a win condition. Just leave food where it was.
    }

    private boolean isOccupiedBySnake(Tile tile) {
        if (collision(tile, snakeHead)) {
            return true;
        }
        for (Tile part : snakeBody) {
            if (collision(tile, part)) {
                return true;
            }
        }
        return false;
    }

    private boolean collision(Tile a, Tile b) {
        return a.x == b.x && a.y == b.y;
    }

    // ================= Rendering =================

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawGrid(g2d);
        drawSnakeAndFood(g2d);

        switch (state) {
            case START_SCREEN:
                drawStartOverlay(g2d);
                break;
            case PLAYING:
                drawHud(g2d);
                break;
            case PAUSED:
                drawHud(g2d);
                drawPauseOverlay(g2d);
                break;
            case GAME_OVER:
                drawHud(g2d);
                drawGameOverOverlay(g2d);
                break;
        }
    }

    private void drawGrid(Graphics2D g2d) {
        g2d.setColor(COLOR_GRID);
        for (int x = 0; x < boardWidth; x += tileSize) {
            g2d.drawLine(x, 0, x, boardHeight);
        }
        for (int y = 0; y < boardHeight; y += tileSize) {
            g2d.drawLine(0, y, boardWidth, y);
        }
    }

    private void drawSnakeAndFood(Graphics2D g2d) {
        int pad = 3;
        g2d.setColor(COLOR_FOOD);
        g2d.fillOval(food.x * tileSize + pad, food.y * tileSize + pad,
                tileSize - pad * 2, tileSize - pad * 2);
        g2d.setColor(new Color(120, 200, 120));
        g2d.fillRect(food.x * tileSize + tileSize / 2 - 1, food.y * tileSize, 2, 5);

        g2d.setColor(COLOR_SNAKE_BODY);
        for (Tile part : snakeBody) {
            g2d.fillRoundRect(part.x * tileSize + 1, part.y * tileSize + 1,
                    tileSize - 2, tileSize - 2, 8, 8);
        }

        g2d.setColor(COLOR_SNAKE_HEAD);
        g2d.fillRoundRect(snakeHead.x * tileSize + 1, snakeHead.y * tileSize + 1,
                tileSize - 2, tileSize - 2, 8, 8);

        drawSnakeEyes(g2d);
    }

    private void drawSnakeEyes(Graphics2D g2d) {
        int cx = snakeHead.x * tileSize + tileSize / 2;
        int cy = snakeHead.y * tileSize + tileSize / 2;
        int offset = 6;
        int eyeSize = 4;

        int e1x = cx;
        int e1y = cy;
        int e2x = cx;
        int e2y = cy;

        if (velocityX == 1) {
            e1x = cx + offset; e1y = cy - offset / 2;
            e2x = cx + offset; e2y = cy + offset / 2;
        } else if (velocityX == -1) {
            e1x = cx - offset; e1y = cy - offset / 2;
            e2x = cx - offset; e2y = cy + offset / 2;
        } else if (velocityY == -1) {
            e1x = cx - offset / 2; e1y = cy - offset;
            e2x = cx + offset / 2; e2y = cy - offset;
        } else if (velocityY == 1) {
            e1x = cx - offset / 2; e1y = cy + offset;
            e2x = cx + offset / 2; e2y = cy + offset;
        }

        g2d.setColor(COLOR_BG);
        g2d.fillOval(e1x - eyeSize / 2, e1y - eyeSize / 2, eyeSize, eyeSize);
        g2d.fillOval(e2x - eyeSize / 2, e2y - eyeSize / 2, eyeSize, eyeSize);
    }

    private void drawHud(Graphics2D g2d) {
        g2d.setFont(new Font("SansSerif", Font.BOLD, 16));

        g2d.setColor(COLOR_TEXT);
        g2d.drawString("Score: " + score, 10, 22);

        String highScoreText = "High Score: " + highScoreManager.getHighScore();
        FontMetrics fm = g2d.getFontMetrics();
        g2d.setColor(COLOR_ACCENT);
        g2d.drawString(highScoreText, boardWidth - fm.stringWidth(highScoreText) - 10, 22);
    }

    private void drawStartOverlay(Graphics2D g2d) {
        g2d.setColor(COLOR_OVERLAY);
        g2d.fillRect(0, 0, boardWidth, boardHeight);

        g2d.setColor(COLOR_SNAKE_HEAD);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 46));
        drawCentered(g2d, "SNAKE", boardHeight / 2 - 70);

        g2d.setColor(COLOR_TEXT);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 15));
        drawCentered(g2d, "Arrow keys or WASD to move", boardHeight / 2 - 20);
        drawCentered(g2d, "P to pause", boardHeight / 2 + 2);

        if (blinkOn) {
            g2d.setColor(COLOR_ACCENT);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 17));
            drawCentered(g2d, "Press SPACE to Start", boardHeight / 2 + 45);
        }

        g2d.setColor(COLOR_TEXT);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        drawCentered(g2d, "High Score: " + highScoreManager.getHighScore(), boardHeight / 2 + 80);
    }

    private void drawPauseOverlay(Graphics2D g2d) {
        g2d.setColor(COLOR_OVERLAY);
        g2d.fillRect(0, 0, boardWidth, boardHeight);

        g2d.setColor(COLOR_TEXT);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 32));
        drawCentered(g2d, "PAUSED", boardHeight / 2 - 10);

        g2d.setFont(new Font("SansSerif", Font.PLAIN, 14));
        drawCentered(g2d, "Press P to Resume", boardHeight / 2 + 25);
    }

    private void drawGameOverOverlay(Graphics2D g2d) {
        g2d.setColor(COLOR_OVERLAY);
        g2d.fillRect(0, 0, boardWidth, boardHeight);

        g2d.setColor(COLOR_FOOD);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 36));
        drawCentered(g2d, "GAME OVER", boardHeight / 2 - 50);

        g2d.setColor(COLOR_TEXT);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 18));
        drawCentered(g2d, "Score: " + score, boardHeight / 2 - 10);

        if (newHighScoreThisRun) {
            g2d.setColor(COLOR_ACCENT);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 18));
            drawCentered(g2d, "New High Score!", boardHeight / 2 + 20);
        } else {
            g2d.setColor(COLOR_TEXT);
            g2d.setFont(new Font("SansSerif", Font.PLAIN, 16));
            drawCentered(g2d, "High Score: " + highScoreManager.getHighScore(), boardHeight / 2 + 20);
        }

        if (blinkOn) {
            g2d.setColor(COLOR_ACCENT);
            g2d.setFont(new Font("SansSerif", Font.BOLD, 16));
            drawCentered(g2d, "Press SPACE to Restart", boardHeight / 2 + 60);
        }
    }

    private void drawCentered(Graphics2D g2d, String text, int y) {
        FontMetrics fm = g2d.getFontMetrics();
        int x = (boardWidth - fm.stringWidth(text)) / 2;
        g2d.drawString(text, x, y);
    }

    // ================= Input handling =================

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();

        switch (state) {
            case START_SCREEN:
                if (code == KeyEvent.VK_SPACE) {
                    startGame();
                }
                break;

            case GAME_OVER:
                if (code == KeyEvent.VK_SPACE) {
                    startGame();
                }
                break;

            case PAUSED:
                if (code == KeyEvent.VK_P) {
                    resumeGame();
                }
                break;

            case PLAYING:
                if (code == KeyEvent.VK_P) {
                    pauseGame();
                } else if ((code == KeyEvent.VK_UP || code == KeyEvent.VK_W) && velocityY != 1) {
                    nextVelocityX = 0;
                    nextVelocityY = -1;
                } else if ((code == KeyEvent.VK_DOWN || code == KeyEvent.VK_S) && velocityY != -1) {
                    nextVelocityX = 0;
                    nextVelocityY = 1;
                } else if ((code == KeyEvent.VK_LEFT || code == KeyEvent.VK_A) && velocityX != 1) {
                    nextVelocityX = -1;
                    nextVelocityY = 0;
                } else if ((code == KeyEvent.VK_RIGHT || code == KeyEvent.VK_D) && velocityX != -1) {
                    nextVelocityX = 1;
                    nextVelocityY = 0;
                }
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    // ================= Timer callback =================

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == GameState.PLAYING) {
            move();
            repaint();
        }
    }
}
