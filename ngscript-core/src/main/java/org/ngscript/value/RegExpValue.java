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

package org.ngscript.value;

import java.util.regex.Pattern;

import org.ngscript.runtime.NgScriptRuntimeException;

/**
 * A regular expression value produced by a regex literal or the RegExp
 * builtin. Minimal ES surface: {@code test} and {@code toString}; the full
 * RegExp semantics (exec, lastIndex, groups) arrive with the builtins phase.
 *
 * @author wssccc
 */
public class RegExpValue {

    public final String pattern;
    public final String flags;
    private final Pattern compiled;

    public RegExpValue(String pattern, String flags) {
        this.pattern = pattern;
        this.flags = flags == null ? "" : flags;
        int f = 0;
        if (this.flags.indexOf('i') >= 0) {
            f |= Pattern.CASE_INSENSITIVE;
        }
        if (this.flags.indexOf('m') >= 0) {
            f |= Pattern.MULTILINE;
        }
        if (this.flags.indexOf('s') >= 0) {
            f |= Pattern.DOTALL;
        }
        try {
            this.compiled = Pattern.compile(pattern, f);
        } catch (Exception e) {
            throw new NgScriptRuntimeException("invalid regular expression: /" + pattern + "/" + this.flags
                    + " (" + e.getMessage() + ")");
        }
    }

    public boolean test(String input) {
        return compiled.matcher(input).find();
    }

    @Override
    public String toString() {
        return "/" + pattern + "/" + flags;
    }
}
