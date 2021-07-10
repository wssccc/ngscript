package org.ngscript.examples.roserender;

import org.apache.commons.io.FilenameUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * @author wssccc
 */
public class DrawWindow extends JFrame {

    private static final int WIDTH = 500;
    private static final int HEIGHT = 700;
    private static final int REFRESH_AFTER = 10000;

    private final BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
    private int drawCount = 0;
    private long ts;

    public DrawWindow() {
        init();
    }

    private void init() {
        Graphics g = buffer.getGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());
        g.dispose();

        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setVisible(true);

        ts = System.currentTimeMillis();
    }

    void save(String fileName) throws IOException {
        ImageIO.write(buffer, FilenameUtils.getExtension(fileName), new File(fileName));
    }

    public void drawPoint(int x, int y, int r, int g, int b) {
        if (x < buffer.getWidth() && x >= 0 && y >= 0 && y < buffer.getHeight()) {
            ++drawCount;
            if (drawCount > REFRESH_AFTER) {
                long now = System.currentTimeMillis();
                System.out.println("dps = " + (REFRESH_AFTER / ((now - ts) / 1000.0)));
                ts = now;
                drawCount = 0;

                Graphics g2d = getGraphics();
                g2d.drawImage(buffer, 0, 0, null);
                g2d.dispose();
            }
            r = (r >= 0 && r <= 255) ? r : 0;
            g = (g >= 0 && g <= 255) ? g : 0;
            b = (b >= 0 && b <= 255) ? b : 0;
            int rgb = (r << 16) | (g << 8) | b;
            buffer.setRGB(x, y, rgb);
        }
    }
}
