/*
 *  wssccc all rights reserved
 */
package org.ngscript;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
@Slf4j
public class ConformanceTest {

    @Test
    public void doTest() throws Exception {
        Configuration.DEFAULT.setGenerateDebugInfo(false);
        Configuration.DEFAULT.setInteractive(false);
        testExamples();
        Configuration.DEFAULT.setInteractive(true);
        testExamples();
    }

    public void testExamples() throws Exception {
        try {
            //
            File folder = new File("src/test/ngscript/tc");
            File[] files = folder.listFiles();
            for (File file : files) {
                if (file.isFile()) {
                    System.out.println("Testing " + file);
                    try {
                        new Ngscript().eval(IOUtils.toString(new FileInputStream(file), StandardCharsets.UTF_8));
                    } catch (Exception ex) {
                        System.out.println("Failed while testing " + file);
                        throw ex;
                    }
                }
            }
        } catch (Exception ex) {
            log.error("error", ex);
            throw ex;
        }
    }
}
