/*
 *  wssccc all rights reserved
 */
package org.ngscript.examples;

import org.ngscript.Ngs;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class RoseRenderer {

    public void run() throws Exception {
        long time = System.currentTimeMillis();
        new Ngs().run(Thread.currentThread().getContextClassLoader().getResourceAsStream("rose.txt"));
        System.out.println("execute time " + (System.currentTimeMillis() - time) + " ms");
    }

}
