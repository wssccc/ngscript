/*
 * Copyright 2021 wssccc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ngscript.examples;

import org.apache.commons.io.IOUtils;
import org.ngscript.Ngscript;

import java.nio.charset.StandardCharsets;

/**
 * @author wssccc
 */
public class RoseRender {

    public static void main(String[] args) throws Exception {
        long time = System.currentTimeMillis();
        new Ngscript().eval(IOUtils.toString(Thread.currentThread().getContextClassLoader().getResourceAsStream("RoseRender.ngs"), StandardCharsets.UTF_8));
        System.out.println("Time elapsed " + (System.currentTimeMillis() - time) + " ms");
    }

}
