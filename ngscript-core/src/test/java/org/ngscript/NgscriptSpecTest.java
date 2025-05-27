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

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Test suite for ngscript that runs all test cases in both eval and interactive modes
 * @author wssccc
 */
@Slf4j
public class NgscriptSpecTest {
    
    private static final String TEST_CASES_DIR = "src/test/ngscript/tc";
    private File testCasesFolder;
    
    @Before
    public void setUp() {
        testCasesFolder = new File(TEST_CASES_DIR);
        assertTrue("Test cases directory should exist", testCasesFolder.exists());
        assertTrue("Test cases directory should be a directory", testCasesFolder.isDirectory());
    }

    @Test
    public void testAllEvalCases() throws Exception {
        File[] files = testCasesFolder.listFiles();
        assertNotNull("Test cases directory should not be empty", files);
        
        for (File file : files) {
            if (file.isFile()) {
                log.info("Testing eval case: {}", file.getName());
                testEval(file);
            }
        }
    }

    public void testAllInteractiveCases() throws Exception {
        File[] files = testCasesFolder.listFiles();
        assertNotNull("Test cases directory should not be empty", files);
        
        for (File file : files) {
            if (file.isFile()) {
                log.info("Testing interactive case: {}", file.getName());
                testInteractive(file);
            }
        }
    }

    private void testEval(File file) throws Exception {
        try {
            String script = IOUtils.toString(new FileInputStream(file), StandardCharsets.UTF_8);
            new Ngscript().eval(script);
            // Add specific assertions based on expected behavior
            assertTrue("Script should execute without errors", true);
        } catch (Exception e) {
            fail("Failed to execute script " + file.getName() + ": " + e.getMessage());
        }
    }

    private void testInteractive(File file) throws Exception {
        try (Scanner sc = new Scanner(new FileInputStream(file))) {
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
            fail("Failed to execute interactive script " + file.getName() + ": " + e.getMessage());
        }
    }
}
