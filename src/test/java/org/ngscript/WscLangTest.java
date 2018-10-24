/*
 *  wssccc all rights reserved
 */
package org.ngscript;

import org.junit.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscLangTest {

    @Test
    public void testExamples() throws Exception {
        try {
            //
            File folder = new File("src/test/resources/examples");
            File[] files = folder.listFiles();
            for (File file : files) {
                if (file.isFile()) {
                    System.out.println("Testing " + file);
                    try {
                        WscLang.test(new FileInputStream(file));
                    } catch (Exception ex) {
                        System.out.println("Failed while testing " + file);
                        throw ex;
                    }
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(WscLangTest.class.getName()).log(Level.SEVERE, null, ex);
            throw ex;
        }
    }
}
