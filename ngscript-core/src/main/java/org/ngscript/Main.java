/*
 *  wssccc all rights reserved
 */
package org.ngscript;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

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
        new Ngscript().eval(IOUtils.toString(new FileInputStream(scriptFile), StandardCharsets.UTF_8));
    }
}
