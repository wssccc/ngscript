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

package org.ngscript;

import org.junit.Test;
import org.ngscript.api.NgScriptEngine;

/**
 * Behavioral tests for the {@code typeof} operator after the explicit
 * type-registry rework (no more package-name heuristic).
 *
 * @author wssccc
 */
public class TypeOfTest {

    private void assertTypeOf(String code) throws Exception {
        new NgScriptEngine().eval(code);
    }

    @Test
    public void primitives() throws Exception {
        assertTypeOf("assert(typeof 1 == \"number\");");
        assertTypeOf("assert(typeof 1.5 == \"number\");");
        assertTypeOf("assert(typeof \"abc\" == \"string\");");
        // ES: booleans report "boolean"
        assertTypeOf("assert(typeof true == \"boolean\");");
        assertTypeOf("assert(typeof undefined == \"undefined\");");
        assertTypeOf("assert(typeof null == \"object\");");
    }

    @Test
    public void scriptFunctionReportsFunction() throws Exception {
        assertTypeOf("function f() { return 1; }\n"
                + "assert(typeof f == \"function\");");
        assertTypeOf("var g = function() { return 1; };\n"
                + "assert(typeof g == \"function\");");
    }

    @Test
    public void objectInstanceReportsObject() throws Exception {
        // ES: objects report "object"
        assertTypeOf("function Foo() { this.x = 1; }\n"
                + "assert(typeof new Foo() == \"object\");");
        assertTypeOf("assert(typeof {} == \"object\");");
        assertTypeOf("assert(typeof [] == \"object\");");
        assertTypeOf("assert(typeof /x/ == \"object\");");
    }

    @Test
    public void javaClassReportsQualifiedName() throws Exception {
        assertTypeOf("var l = new ArrayList();\n"
                + "assert(typeof l == \"java.util.ArrayList\");");
    }
}
