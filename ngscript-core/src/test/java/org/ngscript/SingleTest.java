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
public class SingleTest {
    public static void main(String[] args) throws Exception {
        new Ngs().run(new FileInputStream("src/test/ngscript/tc/example1.txt"));
    }
}
