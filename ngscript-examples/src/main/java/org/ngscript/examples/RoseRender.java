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

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.ngscript.api.NgScriptEngine;
import org.ngscript.examples.roserender.DrawWindow;

/**
 * @author wssccc
 */
public class RoseRender {

    public static void main(String[] args) throws Exception {
        String output = null;
        for (int i = 0; i < args.length; i++) {
            if ("--headless".equals(args[i])) {
                // no window is created; the rendered buffer can be saved via --output
                System.setProperty(DrawWindow.HEADLESS_PROPERTY, "true");
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                output = args[++i];
            }
        }
        long time = System.currentTimeMillis();
        new NgScriptEngine().eval(IOUtils.toString(Thread.currentThread().getContextClassLoader().getResourceAsStream("RoseRender.ngs"), StandardCharsets.UTF_8));
        System.out.println("Time elapsed " + (System.currentTimeMillis() - time) + " ms");
        if (output != null) {
            DrawWindow.saveLast(output);
        }
    }

}
