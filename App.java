import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class App {
    public static void main(String[] args) {
        // All Swing UI construction should happen on the Event Dispatch
        // Thread, not directly in main().
        SwingUtilities.invokeLater(() -> {
            int boardWidth = 600;
            int boardHeight = 600;

            JFrame frame = new JFrame("Snake");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            SnakeGame snakeGame = new SnakeGame(boardWidth, boardHeight);
            frame.add(snakeGame);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            snakeGame.requestFocusInWindow();
        });
    }
}
