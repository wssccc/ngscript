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
public class RoseRender {

    public static void main(String[] args) throws Exception {
        long time = System.currentTimeMillis();
        new Ngscript().eval(IOUtils.toString(Thread.currentThread().getContextClassLoader().getResourceAsStream("RoseRender.ngs"), StandardCharsets.UTF_8));
        System.out.println("Time elapsed " + (System.currentTimeMillis() - time) + " ms");
    }

}
