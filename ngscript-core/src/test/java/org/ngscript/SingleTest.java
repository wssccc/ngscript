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

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * @author wssccc
 */
@Slf4j
public class SingleTest {
    public static void main(String[] args) throws Exception {
        test1Pass();
    }

    static void test1Pass() throws Exception {
        try (FileInputStream fis = new FileInputStream("ngscript-core/src/test/ngscript/tc/example9.txt")) {
            new Ngscript().eval(IOUtils.toString(fis, StandardCharsets.UTF_8));
        }
    }

    static void testInteractive() throws Exception {
        try (FileInputStream fis = new FileInputStream("ngscript-core/src/test/ngscript/tc/example1.txt");
             Scanner sc = new Scanner(fis)) {
            Configuration configuration = new Configuration();
            configuration.setGenerateDebugInfo(true);
            configuration.setInteractive(true);
            Ngscript ngscript = new Ngscript(configuration);
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                System.out.println("feed " + line);
                ngscript.eval(line);
            }
        }
    }
}
