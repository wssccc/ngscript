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

package org.ngscript.api;

/**
 * Identifies the origin of a script: an optional file name and the line
 * number. Diagnostics ({@link NgScriptException}) carry a {@code Source}
 * when the position is known.
 *
 * @author wssccc
 */
public class Source {

    private final String fileName;
    private final int line;

    public Source(String fileName) {
        this(fileName, -1);
    }

    public Source(String fileName, int line) {
        this.fileName = fileName;
        this.line = line;
    }

    public String getFileName() {
        return fileName;
    }

    public int getLine() {
        return line;
    }

    @Override
    public String toString() {
        if (fileName == null && line <= 0) {
            return "(unknown)";
        }
        String base = fileName == null ? "(eval)" : fileName;
        return line > 0 ? base + ":" + line : base;
    }
}
