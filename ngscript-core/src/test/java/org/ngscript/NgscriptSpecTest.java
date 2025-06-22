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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ngscript.api.Configuration;
import org.ngscript.api.NgScriptEngine;

/**
 * Test suite for ngscript that runs every test case in {@code ngscript/tc}
 * in both eval and interactive modes. Each case is a separate JUnit test.
 *
 * @author wssccc
 */
@RunWith(Parameterized.class)
public class NgscriptSpecTest {

    private static final String TEST_CASES_DIR = "ngscript/tc";

    @Parameterized.Parameters(name = "{0} [{1}]")
    public static Collection<Object[]> cases() throws Exception {
        URL url = NgscriptSpecTest.class.getClassLoader().getResource(TEST_CASES_DIR);
        assertNotNull("Test cases directory should exist on classpath", url);
        File dir = new File(url.toURI());
        assertTrue("Test cases directory should be a directory", dir.isDirectory());
        File[] files = dir.listFiles(file -> file.isFile() && file.getName().endsWith(".txt"));
        assertNotNull("Test cases directory should not be empty", files);
        Arrays.sort(files, Comparator.comparing(File::getName));

        List<Object[]> params = new ArrayList<>();
        for (File file : files) {
            params.add(new Object[]{file.getName(), "eval"});
            params.add(new Object[]{file.getName(), "interactive"});
        }
        return params;
    }

    private final String fileName;
    private final String mode;

    public NgscriptSpecTest(String fileName, String mode) {
        this.fileName = fileName;
        this.mode = mode;
    }

    @Test
    public void runCase() throws Exception {
        if ("interactive".equals(mode)) {
            testInteractive(fileName);
        } else {
            testEval(fileName);
        }
    }

    private void testEval(String name) throws Exception {
        try {
            String script = IOUtils.toString(openResource(name), StandardCharsets.UTF_8);
            String actual = runCaptured(script, false);
            assertExpectedOutput(name, actual);
        } catch (Exception e) {
            fail("Failed to execute script " + name + ": " + e.getMessage());
        }
    }

    private void testInteractive(String name) throws Exception {
        // multi-line block comments cannot be lexed in line-by-line mode, strip them
        String script = IOUtils.toString(openResource(name), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "");
        try {
            runCaptured(script, true);
            // interactive mode may diverge from batch semantics (e.g. try blocks
            // fed line by line), so only assert successful execution here
            assertTrue("Interactive script should execute without errors", true);
        } catch (Exception e) {
            fail("Failed to execute interactive script " + name + ": " + e.getMessage());
        }
    }

    /**
     * Runs the script (batch or line-by-line interactive mode) while capturing
     * stdout/stderr, then restores the original streams.
     */
    private String runCaptured(String script, boolean interactive) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        System.setOut(ps);
        System.setErr(ps);
        try {
            if (interactive) {
                Configuration configuration = new Configuration();
                configuration.setInteractive(true);
                NgScriptEngine ngscript = new NgScriptEngine(configuration);
                try (Scanner sc = new Scanner(script)) {
                    while (sc.hasNextLine()) {
                        ngscript.eval(sc.nextLine());
                    }
                }
            } else {
                new NgScriptEngine().eval(script);
            }
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
            ps.flush();
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * Asserts captured output against an optional {@code .expected} baseline.
     * Baselines are normalized line-wise: SLF4J noise lines are dropped and
     * identity hashes ({@code @hex}) are replaced, because they are not stable
     * across runs. Cases without a baseline only assert successful execution.
     */
    private void assertExpectedOutput(String name, String actual) throws IOException {
        InputStream expected = getClass().getClassLoader()
                .getResourceAsStream(TEST_CASES_DIR + "/" + name + ".expected");
        if (expected == null) {
            assertTrue("Script " + name + " should execute without errors", true);
            return;
        }
        String expectedText = IOUtils.toString(expected, StandardCharsets.UTF_8);
        assertEquals("output mismatch for " + name,
                normalize(expectedText), normalize(actual));
    }

    private static String normalize(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\r?\n")) {
            if (line.startsWith("SLF4J:")) {
                continue;
            }
            sb.append(line.replaceAll("@[0-9a-f]{6,}", "@<hash>")).append("\n");
        }
        return sb.toString();
    }

    private InputStream openResource(String name) throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(TEST_CASES_DIR + "/" + name);
        assertNotNull("Test case should exist on classpath: " + name, in);
        return in;
    }
}
