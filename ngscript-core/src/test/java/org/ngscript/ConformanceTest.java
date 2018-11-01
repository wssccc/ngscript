/*
 *  wssccc all rights reserved
 */
package org.ngscript;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
@Slf4j
public class ConformanceTest {

    @Test
    public void testExamples() throws Exception {
        try {
            //
            File folder = new File("src/test/ngscript/tc");
            File[] files = folder.listFiles();
            for (File file : files) {
                if (file.isFile()) {
                    System.out.println("Testing " + file);
                    try {
                        new Ngs().run(new FileInputStream(file));
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
