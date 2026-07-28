import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Loads and saves the all-time high score to a small file in the user's
 * home directory ({@code ~/.snake_game/highscore.dat}). Because it lives
 * on disk rather than in a field, the high score survives both in-game
 * restarts and fully closing and reopening the application.
 */
public class HighScoreManager {

    private final File saveFile;
    private int highScore;

    public HighScoreManager() {
        File dir = new File(System.getProperty("user.home"), ".snake_game");
        this.saveFile = new File(dir, "highscore.dat");
        this.highScore = loadFromDisk();
    }

    public int getHighScore() {
        return highScore;
    }

    /**
     * Compares the given score against the stored high score. If it's a
     * new record, updates it in memory and writes it to disk immediately.
     *
     * @return true if this score set a new high score.
     */
    public boolean reportScore(int score) {
        if (score > highScore) {
            highScore = score;
            saveToDisk(highScore);
            return true;
        }
        return false;
    }

    private int loadFromDisk() {
        if (!saveFile.exists()) {
            return 0;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(saveFile))) {
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                return Integer.parseInt(line.trim());
            }
        } catch (IOException | NumberFormatException ex) {
            // A corrupt or unreadable save file shouldn't crash the game --
            // just start back at 0 as if no high score had been set yet.
            System.err.println("Could not read saved high score, starting from 0: " + ex.getMessage());
        }
        return 0;
    }

    private void saveToDisk(int score) {
        try {
            File dir = saveFile.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(saveFile))) {
                writer.write(Integer.toString(score));
            }
        } catch (IOException ex) {
            // If we can't persist it, the high score still works correctly
            // for the rest of this session -- it just won't survive the app
            // closing. A disk/permissions hiccup shouldn't crash the game.
            System.err.println("Could not save high score to disk: " + ex.getMessage());
        }
    }
}
