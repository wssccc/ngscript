/*
 *  wssccc all rights reserved
 */
package org.ngscript;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * @author wssccc <wssccc@qq.com>
 */
@Slf4j
public class SingleTest {
    public static void main(String[] args) throws Exception {
        testInteractive();
    }

    static void test1Pass() throws Exception {
        new Ngscript().eval(IOUtils.toString(new FileInputStream("src/test/ngscript/tc/example1.txt"), StandardCharsets.UTF_8));
    }

    static void testInteractive() throws Exception {
        Scanner sc = new Scanner(new FileInputStream("src/test/ngscript/tc/example1.txt"));
        Configuration configuration = new Configuration();
        configuration.setGenerateDebugInfo(true);
        configuration.setInteractive(true);
        Ngscript ngscript = new Ngscript(configuration);
        while (sc.hasNextLine()) {
            String line = sc.nextLine();
            System.out.println("feed " + line);
            ngscript.eval(line);
        }

    }
}
