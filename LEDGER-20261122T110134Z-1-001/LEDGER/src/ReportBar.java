import java.awt.Color;
import java.awt.Dimension;
import javax.swing.JPanel;

    public class ReportBar extends JPanel {
        private final double fraction;
        private final Color color;

        ReportBar(double value, double max, Color color) {
            this.fraction = max <= 0 ? 0 : Math.max(0, Math.min(1, value / max));
            this.color = color;
            setBackground(new Color(238, 240, 246));
            setOpaque(true);
            setPreferredSize(new Dimension(120, 14));
        }

        @Override protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            g.setColor(color);
            if (fraction > 0) g.fillRect(0, 0, Math.max(1, (int) Math.round(getWidth() * fraction)), getHeight());
        }
    }
