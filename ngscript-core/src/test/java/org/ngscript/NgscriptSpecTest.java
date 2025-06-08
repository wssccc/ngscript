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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
        File[] files = dir.listFiles(File::isFile);
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
            new Ngscript().eval(script);
            // Add specific assertions based on expected behavior
            assertTrue("Script should execute without errors", true);
        } catch (Exception e) {
            fail("Failed to execute script " + name + ": " + e.getMessage());
        }
    }

    private void testInteractive(String name) throws Exception {
        // multi-line block comments cannot be lexed in line-by-line mode, strip them
        String script = IOUtils.toString(openResource(name), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "");
        try (Scanner sc = new Scanner(script)) {
            Configuration configuration = new Configuration();
            configuration.setInteractive(true);
            Ngscript ngscript = new Ngscript(configuration);

            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                ngscript.eval(line);
            }
            // Add specific assertions based on expected behavior
            assertTrue("Interactive script should execute without errors", true);
        } catch (Exception e) {
            fail("Failed to execute interactive script " + name + ": " + e.getMessage());
        }
    }

    private InputStream openResource(String name) throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(TEST_CASES_DIR + "/" + name);
        assertNotNull("Test case should exist on classpath: " + name, in);
        return in;
    }
}
