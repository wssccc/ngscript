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

package org.ngscript;

import static org.junit.Assert.fail;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.ngscript.api.Configuration;
import org.ngscript.api.NgScriptEngine;

/**
 * Regression tests for {@code generateDebugInfo=true}: previously
 * {@code Program.toString()} triggered a StackOverflowError.
 *
 * @author wssccc
 */
public class GenerateDebugInfoTest {

    @Test
    public void batchModeWithDebugInfo() throws Exception {
        try {
            String script = IOUtils.toString(
                    openResource("example9.txt"), StandardCharsets.UTF_8);
            Configuration configuration = new Configuration();
            configuration.setGenerateDebugInfo(true);
            new NgScriptEngine(configuration).eval(script);
        } catch (Exception e) {
            fail("batch generateDebugInfo failed: " + e);
        }
    }

    @Test
    public void interactiveModeWithDebugInfo() throws Exception {
        try {
            // multi-line block comments cannot be lexed in line-by-line mode, strip them
            String script = IOUtils.toString(
                    openResource("example1.txt"), StandardCharsets.UTF_8)
                    .replaceAll("(?s)/\\*.*?\\*/", "");
            try (Scanner sc = new Scanner(script)) {
                Configuration configuration = new Configuration();
                configuration.setGenerateDebugInfo(true);
                configuration.setInteractive(true);
                NgScriptEngine ngscript = new NgScriptEngine(configuration);
                while (sc.hasNextLine()) {
                    ngscript.eval(sc.nextLine());
                }
            }
        } catch (Exception e) {
            fail("interactive generateDebugInfo failed: " + e);
        }
    }

    private InputStream openResource(String name) {
        return getClass().getClassLoader().getResourceAsStream("ngscript/tc/" + name);
    }
}
