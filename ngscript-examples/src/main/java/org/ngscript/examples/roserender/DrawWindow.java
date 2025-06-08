/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.examples.roserender;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.WindowConstants;

import org.apache.commons.io.FilenameUtils;

/**
 * A window that renders into an off-screen buffer. In headless mode
 * (system property {@value #HEADLESS_PROPERTY} or the constructor flag)
 * no {@link JFrame} is created, so the rendering pipeline can run without
 * a display, e.g. for automated tests; the result can still be saved via
 * {@link #save(String)}.
 *
 * @author wssccc
 */
public class DrawWindow {

    private static final int WIDTH = 500;
    private static final int HEIGHT = 700;
    private static final int REFRESH_AFTER = 10000;

    /**
     * System property that switches the default constructor to headless mode.
     */
    public static final String HEADLESS_PROPERTY = "ngscript.headless";

    private static DrawWindow lastInstance;

    private final BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
    private final boolean headless;
    private final JFrame frame;
    private int drawCount = 0;
    private long ts;

    /**
     * Creates a draw window. Headless mode is enabled when the system property
     * {@value #HEADLESS_PROPERTY} is set to true.
     */
    public DrawWindow() {
        this(Boolean.parseBoolean(System.getProperty(HEADLESS_PROPERTY, "false")));
    }

    /**
     * @param headless true to render off-screen without creating a window
     */
    public DrawWindow(boolean headless) {
        this.headless = headless;
        lastInstance = this;
        if (headless) {
            this.frame = null;
        } else {
            this.frame = new JFrame();
            frame.setSize(WIDTH, HEIGHT);
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setVisible(true);
        }
        Graphics g = buffer.getGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, buffer.getWidth(), buffer.getHeight());
        g.dispose();
        ts = System.currentTimeMillis();
    }

    /**
     * @return the most recently created draw window
     */
    public static DrawWindow getLastInstance() {
        return lastInstance;
    }

    /**
     * Saves the buffer of the most recently created draw window.
     *
     * @param fileName output file name; the extension selects the image format
     * @throws IOException if the file cannot be written
     */
    public static void saveLast(String fileName) throws IOException {
        if (lastInstance != null) {
            lastInstance.save(fileName);
        }
    }

    /**
     * Writes the current buffer to a file.
     *
     * @param fileName output file name; the extension selects the image format
     * @throws IOException if the file cannot be written
     */
    public void save(String fileName) throws IOException {
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

                if (!headless) {
                    Graphics g2d = frame.getGraphics();
                    g2d.drawImage(buffer, 0, 0, null);
                    g2d.dispose();
                }
            }
            r = (r >= 0 && r <= 255) ? r : 0;
            g = (g >= 0 && g <= 255) ? g : 0;
            b = (b >= 0 && b <= 255) ? b : 0;
            int rgb = (r << 16) | (g << 8) | b;
            buffer.setRGB(x, y, rgb);
        }
    }
}
