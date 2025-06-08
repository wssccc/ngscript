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

package org.ngscript.examples;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import org.junit.Test;

/**
 * Runs the RoseRender example end-to-end in headless mode and verifies that
 * the script renders pixels into the saved image.
 *
 * @author wssccc
 */
public class RoseRenderHeadlessTest {

    @Test
    public void testHeadlessRender() throws Exception {
        File output = new File("target/rose.png");
        if (output.exists()) {
            assertTrue(output.delete());
        }

        RoseRender.main(new String[]{"--headless", "--output", output.getAbsolutePath()});

        assertTrue("output image should exist: " + output, output.isFile());
        BufferedImage image = ImageIO.read(output);
        assertNotNull("output image should be readable", image);
        assertEquals(500, image.getWidth());
        assertEquals(700, image.getHeight());

        // the buffer starts filled with gray (0xFF808080); at least one pixel must differ
        boolean drawn = false;
        outer:
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != 0xFF808080) {
                    drawn = true;
                    break outer;
                }
            }
        }
        assertTrue("at least one pixel should be drawn", drawn);
    }
}
