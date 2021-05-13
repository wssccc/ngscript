/*
 *  wssccc all rights reserved
 */
package org.ngscript;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
@Slf4j
public class ConformanceTest {

    @Test
    public void doTest() throws Exception {
        File folder = new File("src/test/ngscript/tc");
        File[] files = folder.listFiles();
        assert files != null;
        for (File file : files) {
            if (file.isFile()) {
                System.out.println("Testing " + file);
                testEval(file);
                testInteractive(file);
            }
        }
    }

    void testEval(File file) throws Exception {
        new Ngscript().eval(IOUtils.toString(new FileInputStream(file), StandardCharsets.UTF_8));
    }

    void testInteractive(File file) throws Exception {
        Scanner sc = new Scanner(new FileInputStream(file));
        Configuration configuration = new Configuration();
        configuration.setInteractive(true);
        Ngscript ngscript = new Ngscript(configuration);
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            ngscript.eval(line);
        }
    }
}
