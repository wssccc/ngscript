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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.ngscript.api.NgScriptEngine;
import org.ngscript.api.NgScriptException;
import org.ngscript.parser.ParserException;
import org.ngscript.runtime.NgScriptRuntimeException;

/**
 * Verifies the unified exception hierarchy: parse, compile and runtime
 * errors all derive from {@link NgScriptException} and carry source
 * positions where known.
 *
 * @author wssccc
 */
public class ExceptionHierarchyTest {

    @Test
    public void parseErrorIsNgScriptExceptionWithLine() {
        try {
            new NgScriptEngine().eval("function ( {");
            fail("expected ParserException");
        } catch (NgScriptException e) {
            assertTrue("should be a ParserException", e instanceof ParserException);
            assertNotNull("parse errors should carry source", e.getSource());
            assertTrue("line should be positive", e.getLine() > 0);
        }
    }

    @Test
    public void compileErrorIsNgScriptException() {
        try {
            // ES module imports (import x from 'm') are not supported: the
            // dynamic import expression import(...) now parses and yields
            // a rejected promise instead
            new NgScriptEngine().eval("import x from 'm';");
            fail("expected ParserException");
        } catch (NgScriptException e) {
            assertTrue("should be a ParserException", e instanceof ParserException);
        }
    }

    @Test
    public void runtimeErrorIsNgScriptException() {
        try {
            new NgScriptEngine().eval("println(undeclaredVariable);");
            fail("expected NgScriptRuntimeException");
        } catch (NgScriptException e) {
            assertTrue("should be a NgScriptRuntimeException", e instanceof NgScriptRuntimeException);
            assertEquals("message should mention the member", true,
                    e.getMessage().contains("undeclaredVariable"));
        }
    }
}
