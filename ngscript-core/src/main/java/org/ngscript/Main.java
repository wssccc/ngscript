/*
 *  wssccc all rights reserved
 */
package org.ngscript;

import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;

/**
 * @author wssccc <wssccc@qq.com>
 */
@Slf4j
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            log.error("no input file specified");
            return;
        }
        String scriptFile = args[0];
        new Ngs().run(new FileInputStream(scriptFile));
    }
}
