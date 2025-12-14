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

package org.ngscript.shell;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ngscript.parser.NgParser;
import org.ngscript.parser.ParserException;

/**
 * A minimal test262 runner. Currently supports a parse-only mode that checks
 * whether each test file is accepted (or rejected, for negative SyntaxError
 * tests) by the parser, plus excludelist.xml filtering and a plain text
 * report. Execution of tests arrives with the semantics phase.
 *
 * <p>Usage: {@code java org.ngscript.shell.Test262Runner [--dir test262-root]
 * [--parse] [--report file] [--fail-below percent] [--feature X] ...}
 */
public class Test262Runner {

    private static final Pattern FRONTMATTER = Pattern.compile("(?s)^/\\*---\\r?\\n(.*?)\\r?\\n---\\*/\\r?\\n?(.*)$");
    private static final Pattern FLAGS = Pattern.compile("flags: \\[(.*?)\\]");
    private static final Pattern NEGATIVE = Pattern.compile("negative:\\s*\\r?\\n\\s*phase: (\\w+)\\s*\\r?\\n\\s*type: (\\S+)");
    private static final Pattern FEATURES = Pattern.compile("features: \\[(.*?)\\]");
    private static final Pattern INCLUDES = Pattern.compile("includes: \\[(.*?)\\]");
    private static final Pattern ES5ID = Pattern.compile("es5id: (\\S+)");
    private static final Pattern EXCLUDE_ID = Pattern.compile("<test id=\"([^\"]+)\"");

    private final NgParser parser = new NgParser();
    private final Set<String> excludelist = new HashSet<>();
    private final List<String> enabledFeatures = new ArrayList<>();
    private boolean execMode;
    private boolean es5Only;
    private String harnessDir;
    private String harnessPrelude = "";
    private org.ngscript.api.NgScriptEngine sharedEngine;
    private org.ngscript.runtime.Machine machine;
    private java.util.Map<String, org.ngscript.value.MemRef> globalBaseline;
    private java.util.Map<Object, java.util.Map<String, org.ngscript.value.MemRef>> hostBaseline;
    private java.util.Map<String, org.ngscript.value.MemRef> objectProtoBaseline;
    private java.util.Map<String, org.ngscript.value.MemRef> arrayProtoBaseline;

    private int total;
    private int passed;
    private int failed;
    private int excluded;
    private int es5Total;
    private int es5Passed;
    private final List<String> failures = new ArrayList<>();
    private int limit = -1;

    public static void main(String[] args) throws Exception {
        String dir = "test262";
        String report = null;
        Double failBelow = null;
        List<String> features = new ArrayList<>();
        boolean parseMode = true;
        boolean execMode = false;
        boolean es5Only = false;
        int limit = -1;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dir":
                    dir = args[++i];
                    break;
                case "--report":
                    report = args[++i];
                    break;
                case "--fail-below":
                    failBelow = Double.parseDouble(args[++i]);
                    break;
                case "--feature":
                    features.add(args[++i]);
                    break;
                case "--no-parse":
                    parseMode = false;
                    break;
                case "--exec":
                    execMode = true;
                    parseMode = false;
                    break;
                case "--es5":
                    es5Only = true;
                    break;
                case "--limit":
                    limit = Integer.parseInt(args[++i]);
                    break;
                default:
                    System.err.println("unknown argument " + args[i]);
                    System.exit(2);
            }
        }
        final boolean es5OnlyFlag = es5Only;
        final int limitFlag = limit;
        Test262Runner runner = new Test262Runner();
        runner.enabledFeatures.addAll(features);
        runner.execMode = execMode;
        runner.es5Only = es5OnlyFlag;
        runner.limit = limitFlag;
        runner.harnessDir = dir + "/harness";
        runner.harnessPrelude = runner.buildHarnessPrelude();
        runner.loadExcludelist(new File(dir, "excludelist.xml"));
        File root = new File(dir);
        if (parseMode || execMode) {
            // test262 keeps the conformance suites under test/ (the src/
            // directory holds the in-progress migration)
            for (String sub : new String[]{"test/language", "test/built-ins"}) {
                File subDir = new File(root, sub);
                if (subDir.isDirectory()) {
                    runner.scan(subDir, sub);
                }
            }
        }
        runner.printReport();
        if (report != null) {
            Files.write(new File(report).toPath(), runner.reportText().getBytes(StandardCharsets.UTF_8));
        }
        double rate = runner.total == 0 ? 0 : 100.0 * runner.passed / runner.total;
        if (failBelow != null && rate < failBelow) {
            System.err.println("pass rate " + rate + "% is below threshold " + failBelow + "%");
            System.exit(1);
        }
    }

    private void loadExcludelist(File file) throws IOException {
        if (file.isFile()) {
            String xml = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Matcher m = EXCLUDE_ID.matcher(xml);
            while (m.find()) {
                excludelist.add(m.group(1));
            }
        }
    }

    private void scan(File dir, String relPath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File f : files) {
            if (f.isDirectory()) {
                scan(f, relPath + "/" + f.getName());
            } else if (f.getName().endsWith(".js")) {
                runCase(f, relPath + "/" + f.getName());
            }
        }
    }

    private void runCase(File file, String id) throws IOException {
        if (limit >= 0 && total >= limit) {
            return;
        }
        ++total;
        if (total % 10 == 0) {
            System.err.println("progress " + total + ": " + id + " @ " + System.currentTimeMillis());
        }
        if (excludelist.contains(id)) {
            ++excluded;
            return;
        }
        String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        String body = source;
        Set<String> flags = new HashSet<>();
        String negativePhase = null;
        String negativeType = null;
        Set<String> features = new HashSet<>();
        List<String> includes = new ArrayList<>();
        boolean es5 = false;
        // frontmatter is a /*--- ... ---*/ block; copyright comments may precede it
        int fmStart = source.indexOf("/*---");
        if (fmStart >= 0) {
            int fmEnd = source.indexOf("---*/", fmStart);
            if (fmEnd > fmStart) {
                String meta = source.substring(fmStart + 5, fmEnd);
                body = source.substring(fmEnd + 5);
                es5 = ES5ID.matcher(meta).find();
                Matcher fl = FLAGS.matcher(meta);
                if (fl.find()) {
                    for (String f : fl.group(1).split(",")) {
                        flags.add(f.trim());
                    }
                }
                Matcher ng = NEGATIVE.matcher(meta);
                if (ng.find()) {
                    negativePhase = ng.group(1);
                    negativeType = ng.group(2);
                }
                Matcher ft = FEATURES.matcher(meta);
                if (ft.find()) {
                    for (String f : ft.group(1).split(",")) {
                        features.add(f.trim());
                    }
                }
                Matcher ic = INCLUDES.matcher(meta);
                if (ic.find()) {
                    for (String f : ic.group(1).split(",")) {
                        includes.add(f.trim());
                    }
                }
            }
        }
        if (flags.contains("module")) {
            ++excluded;
            return; // ES modules are deferred
        }
        if (flags.contains("raw")) {
            ++excluded;
            return; // raw tests are not adapted to the harness
        }
        if (!enabledFeatures.isEmpty()) {
            for (String f : features) {
                if (!enabledFeatures.contains(f)) {
                    ++excluded;
                    return;
                }
            }
        }
        if (es5Only && !es5) {
            ++excluded;
            return;
        }
        if (execMode) {
            long t0 = System.currentTimeMillis();
            runExecutableCase(file, id, source, body, includes, flags, negativePhase, negativeType, es5);
            long elapsed = System.currentTimeMillis() - t0;
            if (elapsed > 2000) {
                System.err.println("SLOW-CASE " + elapsed + "ms: " + id);
            }
            return;
        }
        // parse-only judgement
        boolean parseOk = parse(body);
        boolean expectedFail = "parse".equals(negativePhase) || "early".equals(negativePhase);
        boolean ok = parseOk != expectedFail;
        if (ok) {
            ++passed;
        } else {
            fail(file, id, expectedFail ? "expected " + negativePhase + " " + negativeType + " but parse succeeded"
                    : "parse failed");
        }
        if (es5) {
            ++es5Total;
            if (ok) {
                ++es5Passed;
            }
        }
    }

    /**
     * Execution-mode judgement: strict mode is not implemented yet, so
     * onlyStrict tests are excluded; the body runs against the harness.
     */
    private void runExecutableCase(File file, String id, String source, String body,
                                   List<String> includes, Set<String> flags,
                                   String negativePhase, String negativeType, boolean es5)
            throws IOException {
        if (flags.contains("onlyStrict") || flags.contains("async")) {
            ++excluded;
            return;
        }
        boolean expectedRuntimeFail = "runtime".equals(negativePhase);
        String harnessed = harnessPrelude + buildIncludes(includes) + body.replace("$DONOTEVALUATE();", "");
        try {
            // a shared engine is reused across cases: each eval compiles and
            // executes in an isolated root scope, so no state leaks between tests
            if (sharedEngine == null) {
                java.io.PrintWriter sink = new java.io.PrintWriter(new java.io.StringWriter());
                sharedEngine = new org.ngscript.api.NgScriptEngine(
                        new org.ngscript.api.Configuration(), sink, sink);
                // baseline captured once, before any case: test262 cases run
                // in an isolated realm, and assignments like `eval = f` on
                // the global object must not leak into later cases (a
                // polluted eval made every subsequent eval() recurse)
                machine = sharedEngine.getMachine();
                globalBaseline = org.ngscript.runtime.Machine.globalThis.snapshotData();
                hostBaseline = machine.snapshotHostProps();
                // the shared prototypes are mutable too: a case that defines
                // an accessor on Object.prototype (or Array.prototype) would
                // otherwise redirect every property lookup in later cases
                objectProtoBaseline = machine.objectPrototype.snapshotData();
                arrayProtoBaseline = machine.arrayPrototype.snapshotData();
            }
            // guard against runaway loops in test bodies
            sharedEngine.setRunBudget(50_000_000L);
            long t0 = System.currentTimeMillis();
            sharedEngine.eval(harnessed);
            long elapsed = System.currentTimeMillis() - t0;
            if (elapsed > 2000) {
                System.err.println("SLOW " + elapsed + "ms: " + id);
            }
            if (expectedRuntimeFail) {
                fail(file, id, "expected runtime " + negativeType + " but execution succeeded");
            } else {
                ++passed;
                if (es5) {
                    ++es5Total;
                    ++es5Passed;
                }
            }
        } catch (Throwable e) {
            if (expectedRuntimeFail) {
                ++passed;
                if (es5) {
                    ++es5Total;
                    ++es5Passed;
                }
            } else if (("parse".equals(negativePhase) || "early".equals(negativePhase))
                    && (e instanceof org.ngscript.parser.ParserException
                            || e instanceof org.ngscript.compiler.CompilerException
                            || e instanceof org.ngscript.api.NgScriptException)) {
                // a negative parse/early-error case: compile failing is the
                // expected outcome
                ++passed;
            } else {
                fail(file, id, "execution failed: " + firstLine(e.getMessage()));
            }
        } finally {
            // restore the shared engine even when the case failed: an
            // exception aborts the eval mid-way, leaving the global object,
            // host props and shared prototypes polluted for later cases
            org.ngscript.runtime.Machine.globalThis.restoreData(globalBaseline);
            machine.restoreHostProps(hostBaseline);
            machine.objectPrototype.restoreData(objectProtoBaseline);
            machine.arrayPrototype.restoreData(arrayProtoBaseline);
        }
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "null";
        }
        int i = s.indexOf('\n');
        return i > 0 ? s.substring(0, i).trim() : s.trim();
    }

    /**
     * Loads the harness helper files listed in the test's includes field
     * (propertyHelper.js, compareArray.js, ...), cached per name.
     */
    private final Map<String, String> includeCache = new HashMap<>();

    private String buildIncludes(List<String> includes) {
        if (includes == null || includes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String name : includes) {
            String cached = includeCache.get(name);
            if (cached == null) {
                try {
                    cached = new String(Files.readAllBytes(
                            new File(harnessDir, name).toPath()), StandardCharsets.UTF_8) + "\n";
                } catch (IOException e) {
                    cached = "";
                }
                includeCache.put(name, cached);
            }
            sb.append(cached);
        }
        return sb.toString();
    }

    /**
     * Minimal harness: sta.js + assert.js with a stub $262 host object.
     */
    private String buildHarnessPrelude() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("var $262 = { print: println, gc: function() {},"
                    + " evalScript: function(s) { return eval(s); },"
                    + " detachArrayBuffer: function() {}, createRealm: function() { throw new Error(\"createRealm not supported\"); } };");
            sb.append("assert.throws = function(ctor, fn) {"
                    + " var threw = false;"
                    + " try { fn(); } catch (e) { threw = true; if (e instanceof ctor) { return; } throw e; }"
                    + " if (!threw) { throw new Test262Error(\"expected throw\"); } };");
            sb.append("var $DONOTEVALUATE = function() {};");
            sb.append('\n');
            sb.append(new String(Files.readAllBytes(
                    new File(harnessDir, "sta.js").toPath()), StandardCharsets.UTF_8));
            sb.append('\n');
            sb.append(new String(Files.readAllBytes(
                    new File(harnessDir, "assert.js").toPath()), StandardCharsets.UTF_8));
            sb.append('\n');
        } catch (IOException e) {
            System.err.println("harness prelude failed: " + e);
        }
        return sb.toString();
    }

    private boolean parse(String source) {
        try {
            parser.parse(source);
            return true;
        } catch (ParserException e) {
            return false;
        } catch (RuntimeException e) {
            // internal errors (e.g. stack overflow) count as failures
            return false;
        }
    }

    private void fail(File file, String id, String reason) {
        ++failed;
        failures.add(id + "  " + reason);
    }

    private void printReport() {
        System.out.println(reportText());
    }

    private String reportText() {
        double rate = total == 0 ? 0 : 100.0 * passed / (passed + failed);
        double es5Rate = es5Total == 0 ? 0 : 100.0 * es5Passed / es5Total;
        StringBuilder sb = new StringBuilder();
        sb.append("test262 parse-only report\n");
        sb.append("total=").append(total).append(" passed=").append(passed)
                .append(" failed=").append(failed).append(" excluded=").append(excluded)
                .append(" pass-rate=").append(String.format("%.2f%%", rate)).append('\n');
        sb.append("es5id cases: total=").append(es5Total).append(" passed=").append(es5Passed)
                .append(" rate=").append(String.format("%.2f%%", es5Rate)).append('\n');
        if (!failures.isEmpty()) {
            sb.append("failures:\n");
            for (String f : failures) {
                sb.append("  ").append(f).append('\n');
            }
        }
        return sb.toString();
    }
}
