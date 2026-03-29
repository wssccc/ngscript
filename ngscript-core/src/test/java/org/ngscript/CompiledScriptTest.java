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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.ngscript.api.NgScriptEngine;
import org.ngscript.api.NgScriptException;
import org.ngscript.api.Script;

/**
 * Verifies the compile/execute split: repeatable execution, isolation from
 * the engine environment, and eval returning unwrapped values.
 *
 * @author wssccc
 */
public class CompiledScriptTest {

    @Test
    public void repeatedExecutionYieldsSameResult() throws Exception {
        Script script = new NgScriptEngine().compile("var a = 21; a * 2;");
        assertEquals(42, script.execute());
        assertEquals(42, script.execute());
        assertEquals(42, script.execute());
    }

    @Test
    public void executionIsIsolatedFromEngineState() throws Exception {
        NgScriptEngine engine = new NgScriptEngine();
        engine.eval("var leaked = 1;");
        Script script = engine.compile("var local = 2; local;");
        assertEquals(2, script.execute());
        // ES: top-level var declarations bind on the global object, so the
        // variable is visible to later evaluations (global code declares
        // vars globally); let/const bindings stay on the script frame
        assertEquals(Integer.valueOf(1), engine.eval("leaked;"));
        assertEquals(Integer.valueOf(3), engine.eval("var c = 1; let l = 2; c + l;"));
        assertEquals(Integer.valueOf(3), engine.eval("c + 2;"));
    }

    @Test
    public void topLevelAssignmentDoesNotLeakAcrossExecutions() throws Exception {
        Script local = new NgScriptEngine().compile("var n = 0; n = n + 1; n;");
        assertEquals(1, local.execute());
        assertEquals(1, local.execute());
        assertEquals(1, local.execute());
    }

    @Test
    public void evalReturnsUnwrappedValue() throws Exception {
        Object result = new NgScriptEngine().eval("1 + 1;");
        assertEquals(Integer.valueOf(2), result);
        assertFalse("must not return the MemRef wrapper", result.getClass().getName().contains("MemRef"));
    }

    @Test
    public void evalOfEmptyScriptReturnsNull() throws Exception {
        assertNull(new NgScriptEngine().eval(""));
    }

    @Test
    public void compileRejectsInvalidSyntax() throws Exception {
        try {
            new NgScriptEngine().compile("function ( {");
            fail("expected parse failure at compile time");
        } catch (NgScriptException expected) {
            assertTrue(expected.getMessage().contains("expecting"));
        }
    }

    @Test
    public void scriptSeesBuiltins() throws Exception {
        Script script = new NgScriptEngine().compile("println(\"builtin-visible\"); 7;");
        assertEquals(7, script.execute());
    }
}
