/*
 *  wssccc all rights reserved
 */
package org.ngscript.examples;

import org.apache.commons.io.IOUtils;
import org.ngscript.Ngscript;

import java.nio.charset.StandardCharsets;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class RoseRenderer {

    public void run() throws Exception {
        long time = System.currentTimeMillis();
        new Ngscript().eval(IOUtils.toString(Thread.currentThread().getContextClassLoader().getResourceAsStream("rose.txt"), StandardCharsets.UTF_8));
        System.out.println("execute time " + (System.currentTimeMillis() - time) + " ms");
    }

}
