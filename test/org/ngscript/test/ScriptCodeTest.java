/*
 *  wssccc all rights reserved
 */
package org.ngscript.test;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.ngscript.WscLang;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class ScriptCodeTest {

    public ScriptCodeTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testExamples() throws Exception {
        try {
            WscLang.testExamples();
        } catch (Exception ex) {
            Logger.getLogger(ScriptCodeTest.class.getName()).log(Level.SEVERE, null, ex);
            throw ex;
        }
    }
}
