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

package org.ngscript.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.ngscript.api.Configuration;
import org.ngscript.ast.ArrayLiteral;
import org.ngscript.ast.AssignExpr;
import org.ngscript.ast.AstNode;
import org.ngscript.ast.AwaitExpr;
import org.ngscript.ast.BinaryExpr;
import org.ngscript.ast.Block;
import org.ngscript.ast.BlockStmt;
import org.ngscript.ast.BreakStmt;
import org.ngscript.ast.CallExpr;
import org.ngscript.ast.ClassExpr;
import org.ngscript.ast.CommaExpr;
import org.ngscript.ast.CondExpr;
import org.ngscript.ast.ContinueStmt;
import org.ngscript.ast.DefinePropExpr;
import org.ngscript.ast.DestructureExpr;
import org.ngscript.ast.DoWhileStmt;
import org.ngscript.ast.Expr;
import org.ngscript.ast.ExprStmt;
import org.ngscript.ast.ForInStmt;
import org.ngscript.ast.ForOfStmt;
import org.ngscript.ast.ForStmt;
import org.ngscript.ast.FunctionDecl;
import org.ngscript.ast.GoStmt;
import org.ngscript.ast.IdentExpr;
import org.ngscript.ast.IfStmt;
import org.ngscript.ast.ImportCallExpr;
import org.ngscript.ast.ImportStmt;
import org.ngscript.ast.IndexExpr;
import org.ngscript.ast.Kvp;
import org.ngscript.ast.LabeledStmt;
import org.ngscript.ast.LambdaExpr;
import org.ngscript.ast.LetDeclExpr;
import org.ngscript.ast.LiteralExpr;
import org.ngscript.ast.MemberExpr;
import org.ngscript.ast.NewExpr;
import org.ngscript.ast.ObjectLiteral;
import org.ngscript.ast.OptionalChainExpr;
import org.ngscript.ast.Params;
import org.ngscript.ast.Pattern;
import org.ngscript.ast.Program;
import org.ngscript.ast.RegExpLiteral;
import org.ngscript.ast.ReturnStmt;
import org.ngscript.ast.SpreadExpr;
import org.ngscript.ast.Stmt;
import org.ngscript.ast.SuperCallExpr;
import org.ngscript.ast.SuperMemberExpr;
import org.ngscript.ast.SwitchCase;
import org.ngscript.ast.SwitchStmt;
import org.ngscript.ast.TaggedTemplateExpr;
import org.ngscript.ast.TemplateExpr;
import org.ngscript.ast.ThrowStmt;
import org.ngscript.ast.TryStmt;
import org.ngscript.ast.UnaryExpr;
import org.ngscript.ast.VarDeclExpr;
import org.ngscript.ast.WhileStmt;
import org.ngscript.ast.WithStmt;
import org.ngscript.ast.YieldExpr;
import org.ngscript.bytecode.Assembler;
import org.ngscript.bytecode.Instruction;

/**
 * Compiles a typed {@link Program} AST into a list of {@link Instruction}s for the
 * {@link org.ngscript.runtime.Machine}.
 *
 * @author wssccc
 */
public class Compiler {

    Configuration configuration;

    Assembler assembler = new Assembler();
    Scanner scanner;
    int printedLines;

    String sourceCode;

    Deque<String> continueLabels = new ArrayDeque<>();
    Deque<String> breakLabels = new ArrayDeque<>();
    Deque<String> finallyLabels = new ArrayDeque<>();

    /**
     * Label targets for labelled break/continue. A labelled break jumps past
     * the labelled statement; a labelled continue jumps to the continue point
     * of the (innermost) loop directly inside the labelled statement.
     */
    private final Map<String, String> breakLabelTargets = new HashMap<>();
    private final Map<String, String> continueLabelTargets = new HashMap<>();
    private String currentLabel;

    /**
     * True while compiling the try or catch body of a try statement: break, continue and
     * return inside those bodies must run the finally branch first.
     */
    boolean inHookedBlock;

    /**
     * The number of block frames (let/const scopes) open at the current
     * compilation point; jumps must pop any frames above the target's depth.
     */
    private int blockDepth;

    /**
     * The block depth at which each label was emitted, used to pop block
     * frames when jumping out of a lexical scope.
     */
    private final Map<String, Integer> labelDepths = new HashMap<>();

    /**
     * The number of try contexts (save_machine_state entries on the
     * machine's exception-state stack) open at the current compilation
     * point. Abrupt exits (return/break/continue) must drop the contexts of
     * every try they leave, mirroring the block-frame bookkeeping: otherwise
     * a later exception would restore a stale context of a finished function
     * frame (wild jumps / opaque NgsObject@hash failures).
     */
    private int tryDepth;

    /**
     * Stack of per-class private-name scopes: the source private name
     * "#m" maps to a unique per-class runtime key ("#m@N") so nested
     * classes with same-named private members do not alias each other (ES
     * private names are unique per class definition: accessing B's #m on
     * a C instance where C has its own #m is still a TypeError).
     */
    private final Deque<Map<String, String>> privateNameScopes = new ArrayDeque<>();
    private int privateNameSeq;

    /** The unique runtime key for a private name inside the current class. */
    private String privateKey(String name) {
        return privateKey(name, true);
    }

    /**
     * Resolves a private name to its unique runtime key. {@code define}
     * binds the name in the innermost class scope (a field/method
     * declaration always creates the current class's own private name);
     * a reference walks the class-scope stack outward, so a nested class
     * reads an enclosing class's same-named private member (ES lexical
     * private-name scoping).
     */
    private String privateKey(String name, boolean define) {
        if (name == null || !name.startsWith("#")) {
            return name;
        }
        if (!define) {
            for (java.util.Iterator<Map<String, String>> it = privateNameScopes.iterator();
                    it.hasNext(); ) {
                Map<String, String> scope = it.next();
                String key = scope.get(name);
                if (key != null) {
                    return key;
                }
            }
        }
        Map<String, String> scope = privateNameScopes.peek();
        if (scope == null) {
            // outside a class body the literal name stays (reading it on
            // an object without the key throws the runtime TypeError)
            return name;
        }
        String key = scope.get(name);
        if (key == null) {
            key = name + "@" + (++privateNameSeq);
            scope.put(name, key);
        }
        return key;
    }

    /**
     * The try-context depth at which each label was emitted, used to emit
     * drop_machine_state when jumping out of a try region.
     */
    private final Map<String, Integer> labelTryDepths = new HashMap<>();

    /**
     * Strict mode of the function being compiled: disables implicit globals
     * ({@code x = 1} on an undeclared x throws a ReferenceError). Set per
     * function body from the parsed strict flag; the script root defaults
     * to sloppy.
     */
    private boolean currentStrict;

    /** Emits a label op, recording the current block depth for jump fixups. */
    private void emitLabel(String name) {
        labelDepths.put(name, blockDepth);
        labelTryDepths.put(name, tryDepth);
        assembler.emit("label", name);
    }

    /** Emits a jmp, first popping any block frames above the target depth. */
    private void emitJump(String name) {
        Integer targetDepth = labelDepths.get(name);
        while (targetDepth != null && blockDepth > targetDepth) {
            assembler.emit("leave_block");
            --blockDepth;
        }
        Integer targetTryDepth = labelTryDepths.get(name);
        while (targetTryDepth != null && tryDepth > targetTryDepth) {
            assembler.emit("drop_machine_state");
            --tryDepth;
        }
        assembler.emit("jmp", name);
    }

    /** Pushes a try exception-state context (compile-time depth tracking). */
    private void trySave() {
        assembler.emit("save_machine_state");
        ++tryDepth;
    }

    /** Pops a try exception-state context (compile-time depth tracking). */
    private void tryDrop() {
        --tryDepth;
        assembler.emit("drop_machine_state");
    }

    /** Pushes a block scope frame onto the environment chain. */
    private void enterBlock() {
        assembler.emit("enter_block");
        ++blockDepth;
    }

    /** Pops the active block scope frame off the environment chain. */
    private void leaveBlock() {
        assembler.emit("leave_block");
        --blockDepth;
    }

    /**
     * Assigns the current eax value to an existing (or implicitly declared)
     * name through the normal assignment path: the reference is pushed, the
     * value restored to eax, then assign runs (TDZ/const checks apply). The
     * saved value is popped afterwards to keep the stack balanced. An
     * undeclared name creates an implicit global (sloppy-mode semantics);
     * strict mode throws a ReferenceError instead.
     */
    private void emitAssignToName(String name) {
        assembler.emit("push_eax");
        assembler.emit(currentStrict ? "mov_eax" : "mov_eax_or_create", name);
        assembler.emit("push_eax");
        assembler.emit("peek", "1");
        assembler.emit("assign");
        assembler.emit("pop");
    }

    private void compileLetDecl(LetDeclExpr decl) throws CompilerException {
        for (int i = 0; i < decl.names.size(); i++) {
            if (decl.patterns != null && decl.patterns.get(i) != null) {
                // destructuring declarator: let [a, b] = arr;
                Pattern pat = decl.patterns.get(i);
                declarePatternNames(pat, false);
                if (decl.inits.get(i) != null) {
                    compileExpr(decl.inits.get(i));
                    compilePattern(pat);
                }
                continue;
            }
            assembler.emit("decl_let", decl.names.get(i), decl.isConst ? "1" : "0");
            if (decl.inits.get(i) != null) {
                assembler.emit("mov_eax", decl.names.get(i));
                assembler.emit("push_eax");
                compileExpr(decl.inits.get(i));
                assembler.emit("assign");
            } else {
                // let x; initializes the binding to undefined
                assembler.emit("undefined");
                emitAssignToName(decl.names.get(i));
            }
        }
    }

    /** Declares every name bound by a pattern (let: TDZ bindings, var: set). */
    private void declarePatternNames(Pattern p, boolean isVar) throws CompilerException {
        if (p == null || p instanceof Pattern.RefP) {
            // a member reference target binds nothing
            return;
        }
        if (p instanceof Pattern.IdentP) {
            String name = ((Pattern.IdentP) p).name;
            if (isVar) {
                assembler.emit("clear", "%eax");
                assembler.emit("set", name);
            } else {
                assembler.emit("decl_let", name, "0");
            }
            return;
        }
        if (p instanceof Pattern.ArrayP) {
            Pattern.ArrayP arr = (Pattern.ArrayP) p;
            for (Pattern item : arr.items) {
                declarePatternNames(item, isVar);
            }
            declarePatternNames(arr.rest, isVar);
            return;
        }
        if (p instanceof Pattern.ObjectP) {
            Pattern.ObjectP obj = (Pattern.ObjectP) p;
            for (Pattern.Prop prop : obj.props) {
                declarePatternNames(prop.pattern, isVar);
            }
            declarePatternNames(obj.rest, isVar);
        }
    }

    /**
     * Compiles a destructuring pattern match: the value to destructure must
     * be in eax. Array patterns iterate via the ES iterator protocol;
     * object patterns read members; defaults apply when a value is exactly
     * undefined.
     */
    private void compilePattern(Pattern p) throws CompilerException {
        compilePattern(p, null);
    }

    /**
     * Compiles a destructuring pattern match: the value to destructure must
     * be in eax. {@code inferredName} is the binding name for ES
     * SetFunctionName: an anonymous function expression used as the
     * pattern's default ({@code [gen = function* () {}] = vals}) takes the
     * bound name.
     */
    private void compilePattern(Pattern p, String inferredName) throws CompilerException {
        if (p == null) {
            return; // hole
        }
        if (p.defaultValue != null) {
            String skip = assembler.label("pat_default", p);
            assembler.emit("jnundef", skip);
            compileExpr(p.defaultValue);
            if (inferredName != null && (p.defaultValue instanceof LambdaExpr
                    ? ((LambdaExpr) p.defaultValue).name == null
                    : p.defaultValue instanceof ClassExpr)) {
                // ES SetFunctionName: an anonymous function/class expression
                // used as the default takes the pattern's binding name
                assembler.emit("set_func_name", inferredName);
            }
            emitLabel(skip);
        }
        if (p instanceof Pattern.IdentP) {
            emitAssignToName(((Pattern.IdentP) p).name);
        } else if (p instanceof Pattern.RefP) {
            // a member reference target ([obj.x, arr[0]] = vals): the
            // reference is resolved (not read) and the matched value is
            // stored through it, ES PutValue semantics
            assembler.emit("push_eax"); // keep the matched value
            compileExpr(((Pattern.RefP) p).target, true);
            assembler.emit("push_eax"); // [value, ref]
            assembler.emit("peek", "1"); // eax = value
            assembler.emit("assign");
            assembler.emit("pop"); // the saved value
        } else if (p instanceof Pattern.ArrayP) {
            Pattern.ArrayP arr = (Pattern.ArrayP) p;
            assembler.emit("push_eax");
            assembler.emit("destr_iter_init");
            for (int i = 0; i < arr.items.size(); i++) {
                assembler.emit("destr_iter_next");
                compilePattern(arr.items.get(i), arr.items.get(i) instanceof Pattern.IdentP
                        ? ((Pattern.IdentP) arr.items.get(i)).name : null);
            }
            if (arr.rest != null) {
                assembler.emit("destr_iter_rest");
                compilePattern(arr.rest, arr.rest instanceof Pattern.IdentP
                        ? ((Pattern.IdentP) arr.rest).name : null);
            } else {
                assembler.emit("destr_iter_done");
            }
            // the pushed array itself (destr_iter_* only drops the iterator)
            assembler.emit("pop");
        } else if (p instanceof Pattern.ObjectP) {
            Pattern.ObjectP obj = (Pattern.ObjectP) p;
            assembler.emit("push_eax");
            StringBuilder consumed = new StringBuilder();
            int computedKeys = 0;
            for (Pattern.Prop prop : obj.props) {
                if (prop.keyExpr != null) {
                    // computed key: evaluate at runtime; the key string
                    // stays on the stack for destr_obj_rest, and the
                    // object is re-pushed above it for member_ref2
                    compileExpr(prop.keyExpr);
                    assembler.emit("to_string");
                    assembler.emit("push_eax"); // [.., obj, key]
                    assembler.emit("peek", "1"); // eax = obj
                    assembler.emit("push_eax"); // [.., obj, key, obj]
                    ++computedKeys;
                } else {
                    assembler.emit("peek", "0");
                    assembler.emit("push_eax");
                    assembler.emit("string", prop.key);
                }
                if (prop.keyExpr != null) {
                    assembler.emit("member_ref2");
                } else {
                    assembler.emit("member_ref");
                }
                assembler.emit("deref", "%eax");
                compilePattern(prop.pattern, prop.pattern instanceof Pattern.IdentP
                        ? ((Pattern.IdentP) prop.pattern).name : null);
                if (prop.keyExpr == null) {
                    // only static keys travel in the param list; computed
                    // keys are pushed on the stack for destr_obj_rest
                    if (consumed.length() > 0) {
                        consumed.append(',');
                    }
                    consumed.append(prop.key);
                }
            }
            if (obj.rest != null) {
                // collect the remaining own keys (minus the destructured
                // ones) into a fresh object for the rest pattern; the
                // computed keys sit above the object and are popped by
                // destr_obj_rest (static keys travel in param)
                assembler.emit("destr_obj_rest", consumed.toString() + ";" + computedKeys);
                compilePattern(obj.rest, obj.rest instanceof Pattern.IdentP
                        ? ((Pattern.IdentP) obj.rest).name : null);
                // only the object itself remains (destr_obj_rest popped
                // the computed key strings)
                assembler.emit("pop");
            } else {
                // no rest: drop the computed key strings and the object
                for (int i = 0; i < computedKeys; i++) {
                    assembler.emit("pop");
                }
                assembler.emit("pop");
            }
        }
    }

    /** Whether a block directly contains let/const declarations. */
    private boolean blockHasLexical(Block block) {
        for (Stmt stmt : block.statements) {
            if (stmt instanceof ExprStmt && ((ExprStmt) stmt).expr instanceof LetDeclExpr) {
                return true;
            }
            if (stmt instanceof FunctionDecl) {
                // block-scoped function declarations need a block frame to
                // hold their binding ({ function f() {} } does not leak f)
                return true;
            }
        }
        return false;
    }

    /**
     * Stack depths of the for-in/for-of loops currently being compiled (2
     * for for-in keys+index, 1 for the for-of iterator); return statements
     * pop them so the return address is not corrupted.
     */
    private final Deque<Integer> loopPopDepths = new ArrayDeque<>();

    public Compiler(Configuration configuration) {
        this.configuration = configuration;
    }

    public List<Instruction> compileCode(Program program, String sourceCode) throws CompilerException {
        assembler.instructions.clear();
        this.sourceCode = sourceCode;
        try (Scanner sc = new Scanner(sourceCode == null ? "" : sourceCode)) {
            this.scanner = sc;
            printedLines = 0;
            compileProgram(program);
            if (configuration.isGenerateDebugInfo()) {
                while (sc.hasNextLine()) {
                    assembler.emit("//", sc.nextLine(), "" + printedLines);
                }
            }
        }
        return assembler.instructions;
    }

    private void generateDebugInfo(AstNode node) {
        if (configuration.isGenerateDebugInfo()) {
            if (printedLines != node.line) {
                while (printedLines < node.line) {
                    String line = scanner.hasNextLine() ? scanner.nextLine() : "no line";
                    assembler.emit("//", line, "" + printedLines);
                    ++printedLines;
                }
            }
        }
    }

    private CompilerException compileError(String message) {
        return new CompilerException(message, new org.ngscript.api.Source(sourceCode, printedLines));
    }

    private void compileProgram(Program program) throws CompilerException {
        for (Stmt stmt : program.statements) {
            compileStmt(stmt);
        }
    }

    private void compileBlock(Block block) throws CompilerException {
        boolean scoped = blockHasLexical(block);
        if (scoped) {
            enterBlock();
        }
        for (Stmt stmt : block.statements) {
            compileStmt(stmt);
        }
        if (scoped) {
            leaveBlock();
        }
    }

    private void compileStmt(Stmt stmt) throws CompilerException {
        if (stmt == null) {
            return;
        }
        generateDebugInfo(stmt);
        if (stmt instanceof ExprStmt) {
            compileExpr(((ExprStmt) stmt).expr);
        } else if (stmt instanceof FunctionDecl) {
            compileFunctionDecl((FunctionDecl) stmt);
        } else if (stmt instanceof IfStmt) {
            compileIf((IfStmt) stmt);
        } else if (stmt instanceof ForStmt) {
            compileFor((ForStmt) stmt);
        } else if (stmt instanceof WhileStmt) {
            compileWhile((WhileStmt) stmt);
        } else if (stmt instanceof DoWhileStmt) {
            compileDo((DoWhileStmt) stmt);
        } else if (stmt instanceof ForInStmt) {
            compileForIn((ForInStmt) stmt);
        } else if (stmt instanceof ForOfStmt) {
            compileForOf((ForOfStmt) stmt);
        } else if (stmt instanceof LabeledStmt) {
            compileLabeled((LabeledStmt) stmt);
        } else if (stmt instanceof BlockStmt) {
            compileBlock(((BlockStmt) stmt).block);
        } else if (stmt instanceof SwitchStmt) {
            compileSwitch((SwitchStmt) stmt);
        } else if (stmt instanceof TryStmt) {
            compileTry((TryStmt) stmt);
        } else if (stmt instanceof ThrowStmt) {
            compileThrow((ThrowStmt) stmt);
        } else if (stmt instanceof ReturnStmt) {
            compileReturn((ReturnStmt) stmt);
        } else if (stmt instanceof BreakStmt) {
            compileBreak((BreakStmt) stmt);
        } else if (stmt instanceof ContinueStmt) {
            compileContinue((ContinueStmt) stmt);
        } else if (stmt instanceof ImportStmt) {
            assembler.emit("import_", ((ImportStmt) stmt).packagePath);
        } else if (stmt instanceof GoStmt) {
            compileGo((GoStmt) stmt);
        } else if (stmt instanceof WithStmt) {
            // with (obj) { body }: the object's properties shadow the outer
            // scope for lookups; writes to present properties land on the
            // object, absent ones fall through to the outer chain
            WithStmt with = (WithStmt) stmt;
            compileExpr(with.object);
            assembler.emit("enter_with");
            if (with.body instanceof Block) {
                // a block body is compiled with its own block-frame
                // bookkeeping; leave_block below pops the with frame
                compileStmt(with.body);
            } else {
                compileStmt(with.body);
            }
            assembler.emit("leave_block");
        } else {
            throw compileError("Unknown statement " + stmt);
        }
    }

    private void compileFunctionDecl(FunctionDecl decl) throws CompilerException {
        boolean savedStrict = currentStrict;
        currentStrict = decl.strict;
        String enter = _compile_function_body(decl.params, decl.body, decl.generator || decl.async);
        currentStrict = savedStrict;
        String exitLabel = exitLabelFor(enter);
        if (blockDepth > 0) {
            // block-scoped function declaration ({ function f() {} }): the
            // binding belongs to the block frame, not the function scope
            // (ES2015+ semantics; strict and sloppy both block-scope it)
            String marker = decl.generator ? "2" : (decl.async ? "3" : "0");
            assembler.emit("new_closure", enter, marker + "|exit:" + exitLabel);
            assembler.emit("set_func_meta", functionLength(decl.params) + "", decl.name);
            if (decl.strict) {
                assembler.emit("set_func_strict");
            }
            assembler.emit("decl_let", decl.name, "0");
            emitAssignToName(decl.name);
            return;
        }
        if (decl.generator) {
            assembler.instructions.add(0, new Instruction("static_func_gen", decl.name, enter + "|exit:" + exitLabel));
        } else if (decl.async) {
            assembler.instructions.add(0, new Instruction("static_func_async", decl.name, enter + "|exit:" + exitLabel));
        } else {
            assembler.instructions.add(0, new Instruction("static_func", decl.name, enter + "|exit:" + exitLabel));
        }
        assembler.instructions.add(1,
                new Instruction("set_func_meta", functionLength(decl.params) + "", decl.name));
        if (decl.strict) {
            assembler.instructions.add(2, new Instruction("set_func_strict", null, null));
        }
    }

    /**
     * The function exit label paired with an enter label: the enter label is
     * {@code Block_$N_func_enter} (or {@code ..._enter1}, {@code ..._enter2}
     * for nested functions); the exit label emitted by
     * {@link #_compile_function_body} is its {@code _func_exit} twin.
     */
    private static String exitLabelFor(String enter) {
        if (enter == null) {
            return null;
        }
        int idx = enter.lastIndexOf("_func_enter");
        if (idx >= 0) {
            return enter.substring(0, idx) + "_func_exit" + enter.substring(idx + "_func_enter".length());
        }
        return enter;
    }

    /**
     * The ES Function.length: the parameter count, stopping before the first
     * parameter with a default (including a pattern carrying one) and
     * excluding a rest parameter.
     */
    private int functionLength(Params params) {
        int length = params.names.size();
        if (params.rest) {
            length = params.names.size() - 1;
        }
        if (params.defaults != null) {
            for (int i = 0; i < params.defaults.size(); i++) {
                if (params.defaults.get(i) != null) {
                    length = Math.min(length, i);
                    break;
                }
            }
        }
        if (params.patterns != null) {
            for (int i = 0; i < params.patterns.size(); i++) {
                Pattern p = params.patterns.get(i);
                if (p != null && p.defaultValue != null) {
                    length = Math.min(length, i);
                    break;
                }
            }
        }
        return length;
    }

    private boolean hasNonNull(List<?> list) {
        if (list == null) {
            return false;
        }
        for (Object o : list) {
            if (o != null) {
                return true;
            }
        }
        return false;
    }

    private String _compile_function_body(Params params, Block body) throws CompilerException {
        return _compile_function_body(params, body, false, false);
    }

    private String _compile_function_body(Params params, Block body, boolean generatorBody) throws CompilerException {
        return _compile_function_body(params, body, generatorBody, false);
    }

    private String _compile_function_body(Params params, Block body, boolean generatorBody, boolean arrow) throws CompilerException {
        String exit = assembler.label("func_exit", body);
        String enter = assembler.label("func_enter", body);
        emitJump(exit);
        emitLabel(enter);

        // nested functions/arrows have their own block-depth bookkeeping
        int savedBlockDepth = blockDepth;
        blockDepth = 0;
        int savedTryDepth = tryDepth;
        tryDepth = 0;
        // mapped arguments require a simple parameter list: no defaults,
        // rest, or destructuring (ES unmaps the arguments object otherwise);
        // defaults/patterns are parallel lists whose entries are null when
        // the parameter has no default / is not a pattern
        boolean simpleParams = !params.rest
                && !hasNonNull(params.defaults)
                && !hasNonNull(params.patterns);
        try {
            // the arguments object: a snapshot of the actual arguments (ES
            // gives it the parameter names as accessor properties; the
            // snapshot approximation covers length/index/callee reads). It is
            // bound before the parameters: a default parameter expression (or
            // a direct eval inside it) must see the arguments binding, and
            // declaring "arguments" in such an eval is a SyntaxError.
            // Arrows never create an arguments object: the identifier
            // resolves through the caller's chain instead.
            if (!arrow) {
                assembler.emit("rest_args", "0");
                assembler.emit("bind_callee");
                assembler.emit("set", "arguments", "%eax");
            }
            // ES: with a non-simple parameter list, the parameter environment
            // binds every parameter name (as an uninitialized binding)
            // before any default value runs — a default expression (or a
            // direct eval inside one) that references a later parameter gets
            // a ReferenceError, and declaring "arguments" in such an eval is
            // a SyntaxError when the parameter list contains "arguments"
            // (even before that parameter is bound).
            if (!simpleParams) {
                for (int i = 0; i < params.names.size(); i++) {
                    String n = params.names.get(i);
                    if (n != null) {
                        assembler.emit("decl_let", n, "0");
                    }
                }
            }
            for (int i = 0; i < params.names.size(); i++) {
                Pattern pat = params.patterns != null ? params.patterns.get(i) : null;
                if (pat != null) {
                    // destructuring parameter: function f({a}, [b, c]) — the
                    // bound names become var bindings of the function scope,
                    // then the argument (or undefined) is matched against the
                    // pattern, with the pattern's own default applied on
                    // undefined.
                    declarePatternNames(pat, true);
                    if (params.rest && i == params.names.size() - 1) {
                        // rest pattern: function f(...[a, b]) matches the
                        // remaining arguments collected into an array
                        assembler.emit("rest_args", i + "");
                    } else {
                        assembler.emit("pickarg", i + "");
                    }
                    compilePattern(pat);
                    continue;
                }
                if (simpleParams) {
                    // shared reference so arguments[i] aliases the parameter
                    assembler.emit("pickarg_ref", i + "");
                    // the arguments snapshot was created before the
                    // parameters (a default expression or a direct eval in
                    // one must see the arguments binding): rewire its entry
                    // to the shared reference (ES mapped arguments)
                    assembler.emit("map_args", i + "");
                } else {
                    assembler.emit("pickarg", i + "");
                }
                assembler.emit("set", params.names.get(i), "%eax");
                if (params.rest && i == params.names.size() - 1) {
                    // rest parameter: bind the remaining arguments as an array
                    assembler.emit("rest_args", i + "");
                    assembler.emit("set", params.names.get(i), "%eax");
                }
                if (params.defaults != null && params.defaults.get(i) != null) {
                    // default parameter: apply when the argument is undefined
                    String skip = assembler.label("param_default", body);
                    assembler.emit("deref", params.names.get(i));
                    assembler.emit("jnundef", skip);
                    compileExpr(params.defaults.get(i));
                    emitAssignToName(params.names.get(i));
                    emitLabel(skip);
                }
            }
            if (generatorBody) {
                // parameter binding (including destructuring/defaults) runs
                // synchronously at generator call time; the body starts only
                // on the first next(). This marker lets the Generator bind
                // parameters without running the body.
                assembler.emit("gen_body_enter");
            }
            compileBlock(body);
            //default return void (pop any open block frames first)
            while (blockDepth > 0) {
                leaveBlock();
            }
            assembler.emit("clear", "%eax");
            assembler.emit("ret");
        } finally {
            blockDepth = savedBlockDepth;
            tryDepth = savedTryDepth;
        }
        emitLabel(exit);
        return enter;
    }

    private void compileExpr(Expr expr) throws CompilerException {
        compileExpr(expr, false);
    }

    private void compileExpr(Expr expr, boolean byref) throws CompilerException {
        if (expr == null) {
            return;
        }
        generateDebugInfo(expr);
        if (expr instanceof LiteralExpr) {
            compileLiteral((LiteralExpr) expr);
        } else if (expr instanceof IdentExpr) {
            IdentExpr ident = (IdentExpr) expr;
            if (!byref) {
                assembler.emit("deref", ident.name);
            } else {
                assembler.emit("mov_eax", ident.name);
            }
        } else if (expr instanceof VarDeclExpr) {
            VarDeclExpr decl = (VarDeclExpr) expr;
            if (decl.pattern != null) {
                // a bare destructuring declaration (no initializer)
                declarePatternNames(decl.pattern, true);
                assembler.emit("clear", "%eax");
                return;
            }
            assembler.emit("clear", "%eax");
            if (decl.isVal) {
                assembler.emit("val", decl.name);
            } else {
                assembler.emit("set", decl.name);
            }
            if (!byref) {
                assembler.emit("deref", decl.name);
            } else {
                assembler.emit("mov_eax", decl.name);
            }
        } else if (expr instanceof DestructureExpr) {
            throw compileError("destructuring patterns are only valid as assignment targets");
        } else if (expr instanceof LetDeclExpr) {
            // a let/const declaration used in statement or for-init position
            compileLetDecl((LetDeclExpr) expr);
            assembler.emit("undefined");
        } else if (expr instanceof AssignExpr) {
            AssignExpr assign = (AssignExpr) expr;
            if (!assign.op.equals("assign")) {
                compileCompoundAssign(assign);
            } else if (assign.target instanceof DestructureExpr) {
                // destructuring assignment: [a, b] = value (expression value = RHS)
                compileExpr(assign.value);
                assembler.emit("push_eax");
                compilePattern(((DestructureExpr) assign.target).pattern);
                assembler.emit("pop_eax");
            } else if (assign.target instanceof VarDeclExpr
                    && ((VarDeclExpr) assign.target).pattern != null) {
                // var destructuring declaration: var [a, b] = value
                Pattern pat = ((VarDeclExpr) assign.target).pattern;
                declarePatternNames(pat, true);
                compileExpr(assign.value);
                assembler.emit("push_eax");
                compilePattern(pat);
                assembler.emit("pop_eax");
            } else if (assign.target instanceof IdentExpr) {
                // plain identifier assignment: the target reference is
                // resolved (not read) first, then the right-hand side
                // evaluates, then the value is stored, creating an implicit
                // global for an undeclared name (sloppy-mode semantics);
                // strict mode throws a ReferenceError (x = x on an
                // undeclared x throws from the RHS read either way)
                assembler.emit(currentStrict ? "mov_eax" : "resolve_ref",
                        ((IdentExpr) assign.target).name);
                assembler.emit("push_eax");
                compileExpr(assign.value);
                assembler.emit(currentStrict ? "assign" : "assign_ref",
                        ((IdentExpr) assign.target).name);
                assembler.emit("pop");
            } else {
                compileExpr(assign.target, true);
                assembler.emit("push_eax");
                compileExpr(assign.value);
                assembler.emit("assign");
            }
        } else if (expr instanceof DefinePropExpr) {
            // class field initializer: define an own data property on the
            // object (CreateDataProperty), skipping the prototype chain
            DefinePropExpr d = (DefinePropExpr) expr;
            compileExpr(d.object);
            assembler.emit("push_eax");
            if (d.key != null) {
                compileExpr(d.key);
                assembler.emit("to_string");
            } else {
                assembler.emit("string", privateKey(d.name));
            }
            assembler.emit("push_eax");
            compileExpr(d.value);
            assembler.emit("define_prop");
        } else if (expr instanceof CommaExpr) {
            // each operand evaluates with a balanced stack; the value of the
            // last operand is left in eax and earlier values are simply
            // overwritten by the next evaluation
            CommaExpr comma = (CommaExpr) expr;
            for (int i = 0; i < comma.operands.size(); i++) {
                compileExpr(comma.operands.get(i));
            }
        } else if (expr instanceof UnaryExpr) {
            compileUnary((UnaryExpr) expr);
        } else if (expr instanceof BinaryExpr) {
            compileBinary((BinaryExpr) expr);
        } else if (expr instanceof CondExpr) {
            CondExpr cond = (CondExpr) expr;
            String falseLabel = assembler.label("falsePart", cond);
            String exitLabel = assembler.label("exitPart", cond);
            compileExpr(cond.condition);
            assembler.emit("jz", falseLabel);
            compileExpr(cond.thenExpr);
            emitJump(exitLabel);
            emitLabel(falseLabel);
            compileExpr(cond.elseExpr);
            emitLabel(exitLabel);
        } else if (expr instanceof MemberExpr) {
            MemberExpr member = (MemberExpr) expr;
            if (hasOptionalChain(member.object)) {
                // the member is part of an optional chain (a?.b.c): flatten
                compileOptionalChain(expr);
            } else {
                compileExpr(member.object);
                assembler.emit("push_eax");
                assembler.emit("string", privateKey(member.name, false));
                assembler.emit("member_ref");
                if (!byref) {
                    assembler.emit("deref", "%eax");
                }
            }
        } else if (expr instanceof OptionalChainExpr) {
            // a?.b / a?.[i] / a?.(...) and longer chains: flatten the chain
            // and compile it as one short-circuit unit
            compileOptionalChain(expr);
        } else if (expr instanceof CallExpr) {
            CallExpr call = (CallExpr) expr;
            if (hasOptionalChain(call.callee)) {
                // a?.b(...) / a?.()(...): the call is part of the chain and
                // must be skipped when the chain short-circuits
                compileOptionalChain(expr);
            } else {
                _compile_funcall(call.callee, call.args);
                assembler.emit("pop_env");
                assembler.emit("pop");//pop params
                assembler.emit("pop");//pop function body
                assembler.emit("pop");//pop receiver
            }
        } else if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            _compile_funcall(newExpr.callee, newExpr.args, true);
            assembler.emit("new_op");
        } else if (expr instanceof IndexExpr) {
            IndexExpr indexExpr = (IndexExpr) expr;
            if (hasOptionalChain(indexExpr.object)) {
                compileOptionalChain(expr);
            } else {
                compileExpr(indexExpr.object);
                assembler.emit("push_eax");
                compileExpr(indexExpr.index);
                assembler.emit("array_ref");
                if (!byref) {
                    assembler.emit("deref", "%eax");
                }
            }
        } else if (expr instanceof ArrayLiteral) {
            ArrayLiteral array = (ArrayLiteral) expr;
            boolean hasSpread = false;
            for (Expr item : array.items) {
                if (item instanceof SpreadExpr) {
                    hasSpread = true;
                    break;
                }
            }
            if (!hasSpread) {
                for (int i = array.items.size() - 1; i >= 0; i--) {
                    compileExpr(array.items.get(i));
                    assembler.emit("push_eax");
                }
                assembler.emit("array_new", array.items.size() + "");
            } else {
                assembler.emit("args_array_new");
                for (Expr item : array.items) {
                    if (item instanceof SpreadExpr) {
                        compileExpr(((SpreadExpr) item).expr);
                        assembler.emit("args_spread_add");
                    } else {
                        compileExpr(item);
                        assembler.emit("args_add");
                    }
                }
                assembler.emit("args_to_array");
            }
        } else if (expr instanceof ObjectLiteral) {
            ObjectLiteral object = (ObjectLiteral) expr;
            assembler.emit("obj_start");
            for (Object item : object.items) {
                if (item instanceof Kvp) {
                    Kvp kvp = (Kvp) item;
                    if (kvp.kind != null) {
                        // accessor property: { get v() {} } / { set v(x) {} }
                        // stack [obj.., key, fn] -> obj_accessor consumes
                        // key+fn and pushes one ObjAccessor marker
                        if (kvp.keyExpr != null) {
                            compileExpr(kvp.keyExpr);
                            assembler.emit("to_string");
                            assembler.emit("push_eax");
                        } else {
                            assembler.emit("string", kvp.name);
                            assembler.emit("push_eax");
                        }
                        compileExpr(kvp.value);
                        assembler.emit("obj_accessor", kvp.kind);
                    } else {
                        if (kvp.keyExpr != null) {
                            // computed property name: the key is a runtime value
                            compileExpr(kvp.keyExpr);
                            assembler.emit("push_eax");
                        } else {
                            assembler.emit("string", kvp.name);
                            assembler.emit("push_eax");
                        }
                        compileExpr(kvp.value);
                        assembler.emit("push_eax");
                    }
                } else if (item instanceof SpreadExpr) {
                    compileExpr(((SpreadExpr) item).expr);
                    assembler.emit("obj_spread");
                } else {
                    throw compileError("Unknown object literal element " + item);
                }
            }
            assembler.emit("object_new", object.items.size() + "");
        } else if (expr instanceof RegExpLiteral) {
            RegExpLiteral re = (RegExpLiteral) expr;
            // value like /ab+c/gi
            int lastSlash = re.value.lastIndexOf('/');
            if (lastSlash < 1) {
                throw compileError("invalid regular expression literal " + re.value);
            }
            assembler.emit("regex_new", re.value.substring(1, lastSlash), re.value.substring(lastSlash + 1));
        } else if (expr instanceof ClassExpr) {
            compileClass((ClassExpr) expr);
        } else if (expr instanceof SuperCallExpr) {
            SuperCallExpr sc = (SuperCallExpr) expr;
            // receiver = the current instance, callee = the superclass
            // constructor (bound as %superclass by the class definition)
            assembler.emit("deref", "this");
            assembler.emit("push_eax");
            assembler.emit("deref", "%superclass");
            assembler.emit("push_eax");
            emitArgs(sc.args);
            assembler.emit("push_env");
            assembler.emit("call");
            assembler.emit("pop_env");
            assembler.emit("pop");//pop params
            assembler.emit("pop");//pop function body
            assembler.emit("pop");//pop receiver
        } else if (expr instanceof SuperMemberExpr) {
            SuperMemberExpr sm = (SuperMemberExpr) expr;
            // member resolved on the superclass prototype (bound as
            // %superproto by class definitions); object literal methods
            // have no such binding, so super_ref falls back to the dynamic
            // prototype of `this` (the object's [[Prototype]] chain)
            assembler.emit("super_proto");
            assembler.emit("super_ref", sm.name);
            if (!byref) {
                assembler.emit("deref", "%eax");
            }
        } else if (expr instanceof YieldExpr) {
            YieldExpr y = (YieldExpr) expr;
            if (y.operand != null) {
                compileExpr(y.operand);
                assembler.emit(y.delegate ? "yield_delegate" : "yield_op");
            } else {
                assembler.emit("undefined");
                assembler.emit("yield_op");
            }
        } else if (expr instanceof AwaitExpr) {
            // await operand: evaluate the operand, then suspend until the
            // awaited promise settles (await_op handles the suspension)
            AwaitExpr a = (AwaitExpr) expr;
            compileExpr(a.operand);
            assembler.emit("await_op");
        } else if (expr instanceof SpreadExpr) {
            throw compileError("spread is only valid in call arguments and array literals");
        } else if (expr instanceof TemplateExpr) {
            // chunks and substitution values interleaved on the stack;
            // template_join concatenates with ES ToString coercion
            TemplateExpr tpl = (TemplateExpr) expr;
            assembler.emit("string", tpl.chunks.get(0));
            assembler.emit("push_eax");
            for (int i = 0; i < tpl.exprs.size(); i++) {
                compileExpr(tpl.exprs.get(i));
                assembler.emit("push_eax");
                assembler.emit("string", tpl.chunks.get(i + 1));
                assembler.emit("push_eax");
            }
            assembler.emit("template_join", tpl.exprs.size() * 2 + 1 + "");
        } else if (expr instanceof TaggedTemplateExpr) {
            compileTaggedTemplate((TaggedTemplateExpr) expr);
        } else if (expr instanceof ImportCallExpr) {
            ImportCallExpr imp = (ImportCallExpr) expr;
            // ES: import(specifier) evaluates the specifier and returns a
            // Promise; the host has no module loader, so the result is a
            // rejected promise (the specifier string is still produced)
            if (!imp.args.isEmpty()) {
                compileExpr(imp.args.get(0));
            } else {
                assembler.emit("undefined");
            }
            assembler.emit("import_call");
        } else if (expr instanceof LambdaExpr) {
            LambdaExpr lambda = (LambdaExpr) expr;
            Block body = lambda.body;
            if (body == null && lambda.bodyExpr != null) {
                // arrow expression body: return the expression value
                body = new Block(
                        Collections.singletonList(new ReturnStmt(lambda.bodyExpr, lambda.line)),
                        lambda.line);
            }
            boolean savedStrict = currentStrict;
            currentStrict = lambda.strict;
            String enter = _compile_function_body(lambda.params, body, lambda.generator, lambda.isArrow);
            currentStrict = savedStrict;
            String marker = lambda.async
                    ? (lambda.generator ? "5" : (lambda.isArrow ? "4" : "3"))
                    : (lambda.isArrow ? "1" : (lambda.generator ? "2" : "0"));
            String exitLabel = exitLabelFor(enter);
            assembler.emit("new_closure", enter, marker + "|exit:" + exitLabel);
            assembler.emit("set_func_meta", functionLength(lambda.params) + "",
                    lambda.name == null ? "" : lambda.name);
            if (lambda.strict) {
                assembler.emit("set_func_strict");
            }
        } else {
            throw compileError("Unknown expression " + expr);
        }
    }

    /**
     * Compiles {@code tag`...`}: the tag (with its base object as receiver
     * for member tags) is called with the cooked chunks array (carrying the
     * raw chunks as its {@code raw} property) followed by the substitution
     * values (ES tagged template call convention).
     */
    private void compileTaggedTemplate(TaggedTemplateExpr t) throws CompilerException {
        if (t.tag instanceof MemberExpr) {
            MemberExpr member = (MemberExpr) t.tag;
            compileExpr(member.object);
            assembler.emit("push_eax");
            // duplicate the receiver: one copy stays as the this binding,
            // the other feeds member_ref
            assembler.emit("peek", "0");
            assembler.emit("push_eax");
            assembler.emit("string", privateKey(member.name, false));
            assembler.emit("member_ref");
            assembler.emit("deref", "%eax");
            assembler.emit("push_eax");
        } else {
            assembler.emit("clear_null", "%eax");
            assembler.emit("push_eax");
            compileExpr(t.tag);
            assembler.emit("push_eax");
        }
        // substitution values pushed in reverse so the cooked array (pushed
        // last) becomes the first argument
        for (int i = t.template.exprs.size() - 1; i >= 0; i--) {
            compileExpr(t.template.exprs.get(i));
            assembler.emit("push_eax");
        }
        // cooked chunks array with the raw chunks as its raw property
        buildTemplateArray(t.template.chunks);
        assembler.emit("push_eax");
        assembler.emit("peek", "0");
        assembler.emit("push_eax");
        assembler.emit("string", "raw");
        assembler.emit("member_ref");
        assembler.emit("push_eax");
        buildTemplateArray(t.template.rawChunks);
        assembler.emit("assign");
        assembler.emit("packargs", t.template.exprs.size() + 1 + "");
        assembler.emit("push_eax");
        assembler.emit("push_env");
        assembler.emit("call");
        assembler.emit("pop_env");
        assembler.emit("pop");//pop params
        assembler.emit("pop");//pop function body
        assembler.emit("pop");//pop receiver
    }

    /** Builds an array of string parts on the value stack. */
    private void buildTemplateArray(List<String> parts) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            assembler.emit("string", parts.get(i));
            assembler.emit("push_eax");
        }
        assembler.emit("array_new", parts.size() + "");
    }

    private void compileLiteral(LiteralExpr lit) {
        switch (lit.kind) {
            case INTEGER:
                assembler.emit("integer", lit.value);
                break;
            case BIGINT:
                assembler.emit("bigint", lit.value);
                break;
            case STRING:
                assembler.emit("string", lit.value);
                break;
            case DOUBLE:
                assembler.emit("double_", lit.value);
                break;
            case TRUE:
                assembler.emit("bool", "true");
                break;
            case FALSE:
                assembler.emit("bool", "false");
                break;
            case NULL:
                assembler.emit("clear_null", "%eax");
                break;
            case UNDEFINED:
                assembler.emit("undefined");
                break;
        }
    }

    /** Whether an expression contains an optional-chaining segment. */
    private static boolean hasOptionalChain(Expr e) {
        if (e instanceof OptionalChainExpr) {
            return true;
        }
        if (e instanceof MemberExpr) {
            return hasOptionalChain(((MemberExpr) e).object);
        }
        if (e instanceof IndexExpr) {
            return hasOptionalChain(((IndexExpr) e).object);
        }
        if (e instanceof CallExpr) {
            return hasOptionalChain(((CallExpr) e).callee);
        }
        return false;
    }

    /**
     * Compiles an optional chaining expression ({@code a?.b}, {@code a?.[i]},
     * {@code a?.(...)}, and longer chains like {@code a?.b.c()}) as one
     * flattened short-circuit unit: when any optional segment's base value
     * is null or undefined the whole chain evaluates to undefined without
     * touching the remaining segments.
     */
    private void compileOptionalChain(Expr expr) throws CompilerException {
        // collect the chain segments outward, then reverse into source order
        List<Object> segments = new java.util.ArrayList<>();
        Expr base = expr;
        while (true) {
            if (base instanceof MemberExpr) {
                segments.add(new Seg.Member(((MemberExpr) base).name, false));
                base = ((MemberExpr) base).object;
            } else if (base instanceof IndexExpr) {
                segments.add(new Seg.Index(((IndexExpr) base).index, false));
                base = ((IndexExpr) base).object;
            } else if (base instanceof CallExpr) {
                segments.add(new Seg.Call(((CallExpr) base).args, false));
                base = ((CallExpr) base).callee;
            } else if (base instanceof OptionalChainExpr.Member) {
                segments.add(new Seg.Member(((OptionalChainExpr.Member) base).name, true));
                base = ((OptionalChainExpr.Member) base).object;
            } else if (base instanceof OptionalChainExpr.Index) {
                segments.add(new Seg.Index(((OptionalChainExpr.Index) base).index, true));
                base = ((OptionalChainExpr.Index) base).object;
            } else if (base instanceof OptionalChainExpr.Call) {
                segments.add(new Seg.Call(((OptionalChainExpr.Call) base).args, true));
                base = ((OptionalChainExpr.Call) base).object;
            } else {
                break;
            }
        }
        Collections.reverse(segments);
        int size = segments.size();
        // stack residue at the chain end: base + one value per non-final
        // segment, minus the receiver (and func when not duplicated) consumed
        // by each call segment
        int residue = 1;
        for (int i = 0; i < size; i++) {
            boolean isCall = segments.get(i) instanceof Seg.Call;
            if (isCall) {
                // a first-segment call duplicates its callee (pops 1 from
                // the accumulation); later calls consume receiver + func (2)
                residue -= (i == 0) ? 1 : 2;
                if (i < size - 1) {
                    ++residue;
                }
            } else if (i < size - 1) {
                ++residue;
            }
        }
        // short-circuit labels, one per optional segment: each pops exactly
        // what has been pushed so far (base + i segment results) and joins
        // the chain end (the normal path pops the full residue there)
        String[] shortLabels = new String[size];
        boolean anyOptional = false;
        for (int i = 0; i < size; i++) {
            if (((Seg) segments.get(i)).optional) {
                shortLabels[i] = assembler.label("opt_short_" + i, expr);
                anyOptional = true;
            }
        }
        String done = assembler.label("opt_done", expr);
        compileExpr(base);
        assembler.emit("push_eax");
        for (int i = 0; i < size; i++) {
            Seg seg = (Seg) segments.get(i);
            if (seg.optional) {
                // short-circuit when the segment's base value is nullish
                assembler.emit("peek", "0");
                assembler.emit("jnullish", shortLabels[i]);
            }
            if (seg instanceof Seg.Member) {
                assembler.emit("peek", "0");
                assembler.emit("push_eax");
                assembler.emit("string", ((Seg.Member) seg).name);
                assembler.emit("member_ref");
                assembler.emit("deref", "%eax");
            } else if (seg instanceof Seg.Index) {
                assembler.emit("peek", "0");
                assembler.emit("push_eax");
                compileExpr(((Seg.Index) seg).index);
                assembler.emit("array_ref");
                assembler.emit("deref", "%eax");
            } else {
                // call segment: the callee is the stack top. When this is
                // the first segment the base is both receiver and callee, so
                // duplicate it to build [receiver, func, args, env];
                // otherwise receiver and func are already on the stack
                Seg.Call c = (Seg.Call) seg;
                if (i == 0) {
                    assembler.emit("peek", "0");
                    assembler.emit("push_eax");
                }
                emitArgs(c.args);
                assembler.emit("push_env");
                assembler.emit("call");
                assembler.emit("pop_env");
                assembler.emit("pop");
                assembler.emit("pop");
                assembler.emit("pop");
            }
            if (i < size - 1) {
                assembler.emit("push_eax");
            }
        }
        // normal path: drop the accumulated chain objects and finish
        for (int i = 0; i < residue; i++) {
            assembler.emit("pop");
        }
        if (anyOptional) {
            assembler.emit("jmp", done);
            // short-circuit paths: undefined is already in eax; drop what
            // was pushed up to (and including) the checked segment
            for (int i = 0; i < size; i++) {
                if (shortLabels[i] != null) {
                    assembler.emit("label", shortLabels[i]);
                    for (int j = 0; j <= i; j++) {
                        assembler.emit("pop");
                    }
                    assembler.emit("jmp", done);
                }
            }
        }
        assembler.emit("label", done);
    }

    /** One segment of an optional chain. */
    private abstract static class Seg {
        final boolean optional;

        Seg(boolean optional) {
            this.optional = optional;
        }

        static class Member extends Seg {
            final String name;

            Member(String name, boolean optional) {
                super(optional);
                this.name = name;
            }
        }

        static class Index extends Seg {
            final Expr index;

            Index(Expr index, boolean optional) {
                super(optional);
                this.index = index;
            }
        }

        static class Call extends Seg {
            final List<Expr> args;

            Call(List<Expr> args, boolean optional) {
                super(optional);
                this.args = args;
            }
        }
    }

    /**
     * Compiles a compound assignment like {@code a += b} as: load the current
     * value of the target, apply the binary operator with the right operand,
     * then write the result back to the target. The target reference is
     * evaluated exactly once.
     */
    private void compileCompoundAssign(AssignExpr assign) throws CompilerException {
        String op = assign.op;
        if (op.equals("asgn_and") || op.equals("asgn_or") || op.equals("asgn_nullish")) {
            // short-circuit semantics: a &&= b is a = a ? b : a
            String exit = assembler.label("asgn_exit", assign);
            compileExpr(assign.target, true);
            assembler.emit("push_eax");
            assembler.emit("peek", "0");
            assembler.emit("deref", "%eax");
            if (op.equals("asgn_and")) {
                assembler.emit("jz", exit);
            } else if (op.equals("asgn_or")) {
                assembler.emit("jnz", exit);
            } else {
                // nullish: skip when the current value is not null/undefined
                assembler.emit("jnn", exit);
            }
            compileExpr(assign.value);
            emitLabel(exit);
            assembler.emit("assign");
            return;
        }
        compileExpr(assign.target, true);
        assembler.emit("push_eax");
        assembler.emit("peek", "0");
        assembler.emit("deref", "%eax");
        assembler.emit("push_eax");
        compileExpr(assign.value);
        assembler.emit(binaryOpcodeForAssign(op));
        assembler.emit("assign");
    }

    private static String binaryOpcodeForAssign(String op) {
        switch (op) {
            case "asgn_add": return "add";
            case "asgn_sub": return "sub";
            case "asgn_mul": return "mul";
            case "asgn_div": return "div";
            case "asgn_mod": return "mod";
            case "asgn_exp": return "exp";
            case "asgn_shl": return "shl";
            case "asgn_shr": return "shr";
            case "asgn_ushr": return "ushr";
            case "asgn_bit_and": return "bit_and";
            case "asgn_bit_or": return "bit_or";
            case "asgn_bit_xor": return "bit_xor";
            default:
                throw new IllegalStateException("unknown compound operator " + op);
        }
    }

    private void compileUnary(UnaryExpr unary) throws CompilerException {
        switch (unary.op) {
            case INC:
                compileExpr(unary.operand, true);
                assembler.emit("inc");
                break;
            case POST_INC:
                compileExpr(unary.operand, true);
                assembler.emit("post_inc");
                break;
            case DEC:
                compileExpr(unary.operand, true);
                assembler.emit("dec");
                break;
            case POST_DEC:
                compileExpr(unary.operand, true);
                assembler.emit("post_dec");
                break;
            case TYPEOF:
                if (unary.operand instanceof IdentExpr) {
                    // typeof on an undeclared name is "undefined", not a
                    // ReferenceError (TDZ bindings still throw)
                    assembler.emit("typeof_name", ((IdentExpr) unary.operand).name);
                    assembler.emit("typeof");
                } else {
                    compileExpr(unary.operand);
                    assembler.emit("typeof");
                }
                break;
            case NEG:
                compileExpr(unary.operand);
                assembler.emit("neg");
                break;
            case PLUS:
                compileExpr(unary.operand);
                assembler.emit("unary_plus");
                break;
            case BIT_NOT:
                compileExpr(unary.operand);
                assembler.emit("bit_not");
                break;
            case VOID:
                compileExpr(unary.operand);
                assembler.emit("void_op");
                break;
            case DELETE:
                if (unary.operand instanceof MemberExpr) {
                    MemberExpr member = (MemberExpr) unary.operand;
                    compileExpr(member.object);
                    assembler.emit("push_eax");
                    assembler.emit("string", privateKey(member.name, false));
                    assembler.emit(currentStrict ? "delete_op_strict" : "delete_op");
                } else if (unary.operand instanceof IndexExpr) {
                    // delete obj[key]: compile the key expression too
                    IndexExpr index = (IndexExpr) unary.operand;
                    compileExpr(index.object);
                    assembler.emit("push_eax");
                    compileExpr(index.index);
                    assembler.emit(currentStrict ? "delete_op_strict" : "delete_op");
                } else if (unary.operand instanceof IdentExpr) {
                    if (currentStrict) {
                        // ES early error: deleting an unqualified identifier
                        // is illegal in strict mode
                        throw compileError("Delete of an unqualified identifier in strict mode");
                    }
                    // deleting a plain variable binding yields false in ES;
                    // the variable is not read (delete x with an undeclared
                    // x is true in sloppy mode, but the binding read would
                    // throw)
                    assembler.emit("bool", "false");
                } else {
                    // delete of any other expression (call results, literals)
                    // evaluates the expression, then yields true (ES5
                    // semantics; modern ES makes this a SyntaxError)
                    compileExpr(unary.operand);
                    assembler.emit("bool", "true");
                }
                break;
            case NOT:
                compileExpr(unary.operand);
                assembler.emit("not");
                break;
        }
    }

    private void compileBinary(BinaryExpr bin) throws CompilerException {
        if (bin.op == BinaryExpr.Op.OR) {
            String exit = assembler.label("exit_or", bin);
            compileExpr(bin.left);
            assembler.emit("jnz", exit);
            compileExpr(bin.right);
            emitLabel(exit);
            return;
        }
        if (bin.op == BinaryExpr.Op.AND) {
            String exit = assembler.label("exit_and", bin);
            compileExpr(bin.left);
            assembler.emit("jz", exit);
            compileExpr(bin.right);
            emitLabel(exit);
            return;
        }
        compileExpr(bin.left);
        assembler.emit("push_eax");
        compileExpr(bin.right);
        assembler.emit(binaryOpcode(bin.op));
    }

    private static String binaryOpcode(BinaryExpr.Op op) {
        if (op == BinaryExpr.Op.INSTANCEOF) {
            return "instanceof_op";
        }
        return op.name().toLowerCase();
    }

    private void _compile_nullable_expr(Expr expr) throws CompilerException {
        if (expr == null) {
            assembler.emit("integer", "1");
            return;
        }
        compileExpr(expr);
    }

    private void compileIf(IfStmt ifStmt) throws CompilerException {
        if (ifStmt.elseBody == null) {
            String exitLabel = assembler.label("exit", ifStmt);
            compileExpr(ifStmt.condition);
            assembler.emit("jz", exitLabel);
            compileStmt(ifStmt.thenBody);
            emitLabel(exitLabel);
        } else {
            String exitLabel = assembler.label("exit", ifStmt);
            String elseLabel = assembler.label("else", ifStmt);
            compileExpr(ifStmt.condition);
            assembler.emit("jz", elseLabel);
            compileStmt(ifStmt.thenBody);
            emitJump(exitLabel);
            emitLabel(elseLabel);
            compileStmt(ifStmt.elseBody);
            emitLabel(exitLabel);
        }
    }

    private void compileLabeled(LabeledStmt labeled) throws CompilerException {
        String exit = assembler.label("label_exit", labeled);
        breakLabelTargets.put(labeled.name, exit);
        String savedLabel = currentLabel;
        currentLabel = labeled.name;
        compileStmt(labeled.statement);
        currentLabel = savedLabel;
        emitLabel(exit);
        breakLabelTargets.remove(labeled.name);
    }

    private void compileFor(ForStmt forStmt) throws CompilerException {
        String testLabel = assembler.label("test", forStmt);
        String continueLabel = assembler.label("continue", forStmt);
        String breakLabel = assembler.label("break", forStmt);
        String afterLabel = null;
        boolean letInit = forStmt.init instanceof LetDeclExpr;
        if (letInit) {
            // the init declaration lives in a block frame wrapping the whole
            // loop; the frame is popped after the break label so breaks and
            // normal exits both leave it
            afterLabel = assembler.label("after", forStmt);
            enterBlock();
        }
        if (currentLabel != null && !continueLabelTargets.containsKey(currentLabel)) {
            continueLabelTargets.put(currentLabel, continueLabel);
        }
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        _compile_nullable_expr(forStmt.init);
        emitLabel(testLabel);
        _compile_nullable_expr(forStmt.test);
        assembler.emit("jz", breakLabel);
        compileStmt(forStmt.body);
        emitLabel(continueLabel);
        _compile_nullable_expr(forStmt.step);
        emitJump(testLabel);
        emitLabel(breakLabel);
        if (letInit) {
            leaveBlock();
            emitLabel(afterLabel);
        }
        breakLabels.pop();
        continueLabels.pop();
    }

    private void compileDo(DoWhileStmt stmt) throws CompilerException {
        String testLabel = assembler.label("test", stmt);
        String continueLabel = assembler.label("continue", stmt);
        String breakLabel = assembler.label("break", stmt);
        if (currentLabel != null && !continueLabelTargets.containsKey(currentLabel)) {
            continueLabelTargets.put(currentLabel, continueLabel);
        }
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        emitLabel(testLabel);
        compileStmt(stmt.body);
        emitLabel(continueLabel);
        compileExpr(stmt.condition);
        assembler.emit("jnz", testLabel);
        emitLabel(breakLabel);
        breakLabels.pop();
        continueLabels.pop();
    }

    private void compileForIn(ForInStmt stmt) throws CompilerException {
        String testLabel = assembler.label("test", stmt);
        String continueLabel = assembler.label("continue", stmt);
        String breakLabel = assembler.label("break", stmt);
        String doneLabel = assembler.label("done", stmt);
        String afterLabel = null;
        boolean letTarget = stmt.target instanceof LetDeclExpr;
        if (letTarget) {
            afterLabel = assembler.label("after", stmt);
            enterBlock();
        }
        if (currentLabel != null && !continueLabelTargets.containsKey(currentLabel)) {
            continueLabelTargets.put(currentLabel, continueLabel);
        }
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        loopPopDepths.push(2); // keys + index left on the stack
        compileExpr(stmt.object);
        assembler.emit("forin_init");
        emitLabel(testLabel);
        assembler.emit("forin_next");
        assembler.emit("jz", doneLabel);
        if (letTarget) {
            // for (let x in obj): a fresh TDZ binding per scope, assigned the key
            LetDeclExpr decl = (LetDeclExpr) stmt.target;
            assembler.emit("decl_let", decl.names.get(0), decl.isConst ? "1" : "0");
            emitAssignToName(decl.names.get(0));
        } else if (stmt.target instanceof VarDeclExpr) {
            assembler.emit("set", ((VarDeclExpr) stmt.target).name);
        } else if (stmt.target instanceof IdentExpr) {
            emitAssignToName(((IdentExpr) stmt.target).name);
        } else {
            throw compileError("unsupported for-in target " + stmt.target);
        }
        compileStmt(stmt.body);
        emitLabel(continueLabel);
        emitJump(testLabel);
        emitLabel(breakLabel);
        // an explicit break leaves the keys + index on the stack
        assembler.emit("pop");
        assembler.emit("pop");
        emitLabel(doneLabel);
        loopPopDepths.pop();
        if (letTarget) {
            leaveBlock();
            emitLabel(afterLabel);
        }
        breakLabels.pop();
        continueLabels.pop();
    }

    private void compileForOf(ForOfStmt stmt) throws CompilerException {
        String testLabel = assembler.label("test", stmt);
        String continueLabel = assembler.label("continue", stmt);
        String breakLabel = assembler.label("break", stmt);
        String doneLabel = assembler.label("done", stmt);
        String afterLabel = null;
        boolean letTarget = stmt.target instanceof LetDeclExpr;
        if (letTarget) {
            afterLabel = assembler.label("after", stmt);
            enterBlock();
        }
        if (currentLabel != null && !continueLabelTargets.containsKey(currentLabel)) {
            continueLabelTargets.put(currentLabel, continueLabel);
        }
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        loopPopDepths.push(1); // the iterator left on the stack
        compileExpr(stmt.object);
        assembler.emit("forof_init");
        emitLabel(testLabel);
        assembler.emit("forof_next");
        assembler.emit("jdone", doneLabel);
        if (letTarget) {
            LetDeclExpr decl = (LetDeclExpr) stmt.target;
            assembler.emit("decl_let", decl.names.get(0), decl.isConst ? "1" : "0");
            emitAssignToName(decl.names.get(0));
        } else if (stmt.target instanceof VarDeclExpr) {
            assembler.emit("set", ((VarDeclExpr) stmt.target).name);
        } else if (stmt.target instanceof IdentExpr) {
            emitAssignToName(((IdentExpr) stmt.target).name);
        } else {
            throw compileError("unsupported for-of target " + stmt.target);
        }
        compileStmt(stmt.body);
        emitLabel(continueLabel);
        emitJump(testLabel);
        emitLabel(breakLabel);
        // an explicit break leaves the iterator on the stack
        assembler.emit("pop");
        emitLabel(doneLabel);
        loopPopDepths.pop();
        if (letTarget) {
            leaveBlock();
            emitLabel(afterLabel);
        }
        breakLabels.pop();
        continueLabels.pop();
    }

    private void compileWhile(WhileStmt whileStmt) throws CompilerException {
        String testLabel = assembler.label("test", whileStmt);
        String continueLabel = assembler.label("continue", whileStmt);
        String breakLabel = assembler.label("break", whileStmt);
        if (currentLabel != null && !continueLabelTargets.containsKey(currentLabel)) {
            continueLabelTargets.put(currentLabel, continueLabel);
        }
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        emitLabel(testLabel);
        compileExpr(whileStmt.condition);
        assembler.emit("jz", breakLabel);
        compileStmt(whileStmt.body);
        emitLabel(continueLabel);
        emitJump(testLabel);
        emitLabel(breakLabel);
        breakLabels.pop();
        continueLabels.pop();
    }

    private void compileSwitch(SwitchStmt switchStmt) throws CompilerException {
        String breakLabel = assembler.label("break", switchStmt);
        breakLabels.push(breakLabel);
        // the switch value stays on the stack: every case comparison reads
        // a copy via peek (eq pops its operand), and the matching case (or
        // the default) pops the value before running its body
        compileExpr(switchStmt.value);
        assembler.emit("push_eax");
        for (int i = 0; i < switchStmt.cases.size(); i++) {
            SwitchCase c = switchStmt.cases.get(i);
            String nextLabel = assembler.label("case_" + i, switchStmt);
            compileExpr(c.value);
            assembler.emit("peek", "0");
            assembler.emit("push_eax");
            assembler.emit("eq");
            assembler.emit("jz", nextLabel);
            assembler.emit("pop"); // drop the switch value
            compileBlock(c.body);
            emitJump(breakLabel);
            emitLabel(nextLabel);
        }
        if (switchStmt.defaultBody != null) {
            assembler.emit("pop"); // drop the switch value
            compileBlock(switchStmt.defaultBody);
        }
        breakLabels.pop();
        emitLabel(breakLabel);
    }

    private void compileTry(TryStmt tryStmt) throws CompilerException {
        String catchLabel = assembler.label("catch", tryStmt);
        String finallyLabel = assembler.label("finally", tryStmt);
        String exitLabel = assembler.label("exit", tryStmt);
        finallyLabels.push(finallyLabel);
        boolean savedHooked = inHookedBlock;
        // return/break/continue inside a try need the finally hook only when
        // a finally clause exists: without one, the catch clause does not
        // intercept control flow, and routing through the (empty) finally
        // block would pop the return address off the stack and break ret
        inHookedBlock = tryStmt.finallyBody != null;
        trySave();
        assembler.emit("test", "%exception");
        assembler.emit("jnz", catchLabel);
        compileBlock(tryStmt.tryBody);
        tryDrop();
        assembler.emit("push_eip");
        emitJump(finallyLabel);
        emitJump(exitLabel);
        emitLabel(catchLabel);
        assembler.emit("mov_eax_exception");
        boolean catchScoped = tryStmt.catchVar != null;
        if (catchScoped) {
            // the catch binding is block scoped: a dedicated frame with a
            // let binding (ES catch (e) creates a new lexical scope)
            enterBlock();
            assembler.emit("decl_let", tryStmt.catchVar, "0");
            emitAssignToName(tryStmt.catchVar);
        }
        assembler.emit("clear_null", "%exception");
        String rethrowLabel = null;
        if (tryStmt.catchBody != null) {
            compileBlock(tryStmt.catchBody);
        } else {
            // no catch clause: remember the exception, run finally, then rethrow
            assembler.emit("push_eax");
            rethrowLabel = assembler.label("rethrow", tryStmt);
        }
        if (catchScoped) {
            leaveBlock();
        }
        assembler.emit("push_eip");
        emitJump(finallyLabel);
        if (rethrowLabel != null) {
            // the finally return lands here (return address + 1): rethrow
            emitJump(rethrowLabel);
            emitLabel(rethrowLabel);
            assembler.emit("pop_eax");
            assembler.emit("mov_exception_eax");
            assembler.emit("restore_machine_state");
        }
        emitJump(exitLabel);
        emitLabel(finallyLabel);
        inHookedBlock = savedHooked;
        if (tryStmt.finallyBody != null) {
            compileBlock(tryStmt.finallyBody);
        }
        assembler.emit("clear_call_stack");
        assembler.emit("pop_eax");
        assembler.emit("jmp", "offset", "1");
        emitLabel(exitLabel);
        finallyLabels.pop();
    }

    private void compileThrow(ThrowStmt stmt) throws CompilerException {
        compileExpr(stmt.expr);
        assembler.emit("mov_exception_eax");
        assembler.emit("restore_machine_state");
    }

    private void compileReturn(ReturnStmt stmt) throws CompilerException {
        if (inHookedBlock) {
            // return inside a try with finally: evaluate the return value
            // first, then route through the finally block. The finally's
            // "jmp offset 1" resumes at the return address + 1, so a nop
            // placeholder is emitted after the jump to make the resumed
            // position the value handling below.
            if (stmt.expr != null) {
                compileExpr(stmt.expr);
                assembler.emit("push_eax");
            } else {
                assembler.emit("clear", "%eax");
            }
            _compile_call_finally();
            emitLabel(assembler.label("ret_hook", stmt));
            // the return exits every enclosing try: drop their saved
            // exception-state contexts (they would otherwise be restored
            // by a later exception, replaying a dead frame)
            while (tryDepth > 0) {
                tryDrop();
            }
            if (stmt.expr != null) {
                assembler.emit("pop_eax");
            }
            assembler.emit("ret");
            return;
        }
        // returning out of open for-in/for-of loops drops their iteration
        // state (keys + index / iterator) left on the stack
        for (int depth : loopPopDepths) {
            for (int i = 0; i < depth; i++) {
                assembler.emit("pop");
            }
        }
        if (stmt.expr != null) {
            // evaluate the expression before popping block frames: the
            // bindings (let/const) may live in a block frame
            compileExpr(stmt.expr);
            assembler.emit("push_eax");
        } else {
            assembler.emit("clear", "%eax");
        }
        // returning pops any block frames the body may have opened
        while (blockDepth > 0) {
            leaveBlock();
        }
        // ... and any try contexts still open (try bodies exited by the
        // return must not leave stale exception states behind)
        while (tryDepth > 0) {
            tryDrop();
        }
        if (stmt.expr != null) {
            assembler.emit("pop_eax");
        }
        assembler.emit("ret");
    }

    private void compileBreak(BreakStmt stmt) throws CompilerException {
        String target;
        if (stmt.label != null) {
            target = breakLabelTargets.get(stmt.label);
            if (target == null) {
                throw compileError("undefined label '" + stmt.label + "'");
            }
        } else {
            if (breakLabels.isEmpty()) {
                throw compileError("break without loop");
            }
            target = breakLabels.peek();
        }
        if (inHookedBlock) {
            _compile_call_finally();
        }
        emitJump(target);
    }

    private void compileContinue(ContinueStmt stmt) throws CompilerException {
        String target;
        if (stmt.label != null) {
            target = continueLabelTargets.get(stmt.label);
            if (target == null) {
                throw compileError("undefined label '" + stmt.label + "'");
            }
        } else {
            if (breakLabels.isEmpty()) {
                throw compileError("continue without loop");
            }
            target = continueLabels.peek();
        }
        if (inHookedBlock) {
            _compile_call_finally();
        }
        emitJump(target);
    }

    private void _compile_call_finally() {
        assembler.emit("push_eip");
        // a raw jump: the finally's "jmp offset 1" resumes exactly one
        // instruction past this jump, so no fixup instructions may sit
        // between push_eip and the jump (try-context drops for the abrupt
        // exit are emitted at the continuation point instead)
        assembler.emit("jmp", finallyLabels.peek());
    }

    private void compileGo(GoStmt goStmt) throws CompilerException {
        String entryLabel = assembler.label("entry", goStmt);
        String goLabel = assembler.label("go", goStmt);
        emitJump(goLabel);
        emitLabel(entryLabel);
        _compile_funcall(goStmt.callee, goStmt.args);
        assembler.emit("pop_env");
        assembler.emit("pop");//pop params
        assembler.emit("pop");//pop function body
        assembler.emit("pop");//pop receiver
        assembler.emit("halt");
        emitLabel(goLabel);
        assembler.emit("go", entryLabel);
    }

    private void _compile_funcall(Expr callee, List<Expr> args) throws CompilerException {
        _compile_funcall(callee, args, false);
    }

    /**
     * Compiles a function call with the stack layout {@code [receiver, func,
     * args[], env]}. When the callee is a member access the receiver is its
     * base object (bound to {@code this}); new-expressions push a marker
     * instead (new_op binds this to the instance).
     */
    private void _compile_funcall(Expr callee, List<Expr> args, boolean isNew) throws CompilerException {
        //receiver (bottom of the call frame)
        if (callee instanceof SuperMemberExpr) {
            // super.m(...): push the receiver (the current this), then
            // resolve the method on the superclass prototype
            assembler.emit("deref", "this");
            assembler.emit("push_eax");
            compileExpr(callee);
            assembler.emit("push_eax");
        } else if (callee instanceof MemberExpr && !isNew) {
            MemberExpr member = (MemberExpr) callee;
            compileExpr(member.object);
            assembler.emit("push_eax");
            // duplicate the receiver: one copy stays as the this binding,
            // the other feeds member_ref
            assembler.emit("peek", "0");
            assembler.emit("push_eax");
            assembler.emit("string", privateKey(member.name, false));
            assembler.emit("member_ref");
            assembler.emit("deref", "%eax");
            assembler.emit("push_eax");
        } else if (callee instanceof IndexExpr && !isNew) {
            // obj[key](...): the base object is the receiver (this), a
            // duplicate feeds array_ref
            IndexExpr ix = (IndexExpr) callee;
            compileExpr(ix.object);
            assembler.emit("push_eax");
            assembler.emit("peek", "0");
            assembler.emit("push_eax");
            compileExpr(ix.index);
            assembler.emit("array_ref");
            assembler.emit("deref", "%eax");
            assembler.emit("push_eax");
        } else {
            if (isNew) {
                assembler.emit("new_marker");
            } else {
                assembler.emit("clear_null", "%eax");
            }
            assembler.emit("push_eax");
            compileExpr(callee);
            assembler.emit("push_eax");
        }
        //params
        emitArgs(args);

        if (callee instanceof IdentExpr && "eval".equals(((IdentExpr) callee).name)) {
            // ES direct eval: the callee is the IdentifierReference `eval` —
            // the call shares the caller's scope (and super context). The
            // marker is emitted after the arguments (no nested calls can
            // interfere) and consumed by the eval builtin; every native
            // call clears it afterwards so a reassigned `eval` cannot leak
            // the flag into a later call.
            assembler.emit("eval_direct_mark");
        }
        assembler.emit("push_env");
        assembler.emit("call");
    }

    /**
     * Emits call argument evaluation: arguments are pushed in reverse order
     * followed by packargs; spread elements use the dynamic argument
     * collector instead.
     */
    private void emitArgs(List<Expr> args) throws CompilerException {
        boolean hasSpread = false;
        for (Expr a : args) {
            if (a instanceof SpreadExpr) {
                hasSpread = true;
                break;
            }
        }
        if (!hasSpread) {
            for (int i = args.size() - 1; i >= 0; i--) {
                compileExpr(args.get(i));
                assembler.emit("push_eax");
            }
            assembler.emit("packargs", args.size() + "");
            assembler.emit("push_eax");
        } else {
            // spread elements: collect the arguments dynamically
            assembler.emit("args_array_new");
            for (Expr a : args) {
                if (a instanceof SpreadExpr) {
                    compileExpr(((SpreadExpr) a).expr);
                    assembler.emit("args_spread_add");
                } else {
                    compileExpr(a);
                    assembler.emit("args_add");
                }
            }
            assembler.emit("args_pack");
            assembler.emit("push_eax");
        }
    }

    /**
     * Compiles a class declaration/expression: a TDZ binding for the name,
     * the constructor (synthesized when absent), the prototype chain for
     * extends, and the method definitions (instance methods on the
     * prototype, static methods on the constructor).
     */
    private void compileClass(ClassExpr cls) throws CompilerException {
        Map<String, String> privateScope = new HashMap<>();
        privateNameScopes.push(privateScope);
        // pre-register every private name of this class: a nested class
        // compiled later (inside a field initializer) resolves a private
        // reference lexically to this class's key even before the member
        // itself is compiled (ES private-name scoping is static)
        for (ClassExpr.MethodDef m : cls.methods) {
            if (m.name != null && m.name.startsWith("#") && !privateScope.containsKey(m.name)) {
                privateScope.put(m.name, m.name + "@" + (++privateNameSeq));
            }
        }
        for (ClassExpr.FieldDef f : cls.fields) {
            if (f.name != null && f.name.startsWith("#") && !privateScope.containsKey(f.name)) {
                privateScope.put(f.name, f.name + "@" + (++privateNameSeq));
            }
        }
        if (cls.name != null) {
            // TDZ binding so `class C extends C` throws like ES
            assembler.emit("decl_let", cls.name, "0");
        }
        if (cls.superClass != null) {
            compileExpr(cls.superClass);
            assembler.emit("push_eax");
            assembler.emit("class_bind_super");
        }
        // constructor (synthesized when absent: an empty one, or
        // constructor(...args) { super(...args); } for derived classes)
        ClassExpr.MethodDef ctor = null;
        for (ClassExpr.MethodDef m : cls.methods) {
            if (m.isCtor) {
                ctor = m;
                break;
            }
        }
        // instance field initializers run inside the constructor, after the
        // leading super() call for derived classes (at the top for base
        // classes), in declaration order, with this bound to the instance
        List<ClassExpr.FieldDef> instFields = new ArrayList<>();
        for (ClassExpr.FieldDef f : cls.fields) {
            if (!f.isStatic) {
                instFields.add(f);
            }
        }
        List<Stmt> fieldStmts = new ArrayList<>();
        for (ClassExpr.FieldDef f : instFields) {
            // ES: a field initializer defines the field on the instance
            // (CreateDataProperty) — the prototype chain is not consulted,
            // so a same-named prototype accessor is shadowed, not invoked
            Expr target = new IdentExpr("this", cls.line);
            Expr value = f.initializer != null ? f.initializer
                    : new LiteralExpr(LiteralExpr.Kind.UNDEFINED, "undefined", cls.line);
            fieldStmts.add(new ExprStmt(f.computedKey != null
                    ? new DefinePropExpr(target, f.computedKey, null, value, cls.line)
                    : new DefinePropExpr(target, null, f.name, value, cls.line), cls.line));
        }
        Params ctorParams;
        Block ctorBody;
        if (ctor != null) {
            ctorParams = ctor.params;
            List<Stmt> stmts = new ArrayList<>(ctor.body.statements);
            int insertAt = 0;
            if (cls.superClass != null && !stmts.isEmpty()
                    && stmts.get(0) instanceof ExprStmt
                    && ((ExprStmt) stmts.get(0)).expr instanceof SuperCallExpr) {
                insertAt = 1;
            }
            stmts.addAll(insertAt, fieldStmts);
            ctorBody = new Block(stmts, cls.line);
        } else if (cls.superClass != null) {
            ctorParams = new Params(Collections.singletonList("args"), null, true);
            List<Stmt> stmts = new ArrayList<>();
            stmts.add(new ExprStmt(new SuperCallExpr(Collections.singletonList(new SpreadExpr(
                    new IdentExpr("args", cls.line), cls.line)), cls.line), cls.line));
            stmts.addAll(fieldStmts);
            ctorBody = new Block(stmts, cls.line);
        } else {
            ctorParams = new Params(Collections.emptyList());
            ctorBody = new Block(fieldStmts, cls.line);
        }
        String enter = _compile_function_body(ctorParams, ctorBody);
        assembler.emit("new_closure", enter, "0");
        assembler.emit("set_func_meta", functionLength(ctorParams) + "",
                cls.name == null ? "" : cls.name);
        if (cls.superClass != null) {
            // [super, ctor]; proto_extend pops both and chains the prototypes
            assembler.emit("push_eax");
            assembler.emit("proto_extend");
        }
        assembler.emit("push_eax"); // keep the constructor on the stack
        // instance methods live on the prototype
        assembler.emit("func_proto");
        assembler.emit("push_eax"); // [ctor, proto]
        for (ClassExpr.MethodDef m : cls.methods) {
            if (m.isCtor || m.isStatic) {
                continue;
            }
            emitMethodDefine(m);
        }
        assembler.emit("pop"); // [ctor]
        // static methods live on the constructor itself
        for (ClassExpr.MethodDef m : cls.methods) {
            if (!m.isStatic || m.isCtor) {
                continue;
            }
            emitMethodDefine(m);
        }
        // static field initializers run after all methods are defined, in
        // declaration order, with the constructor bound as this; the
        // surrounding this binding is restored afterwards
        boolean hasStaticFields = false;
        for (ClassExpr.FieldDef f : cls.fields) {
            if (f.isStatic) {
                hasStaticFields = true;
                break;
            }
        }
        if (hasStaticFields) {
            assembler.emit("push_this");
            for (ClassExpr.FieldDef f : cls.fields) {
                if (!f.isStatic) {
                    continue;
                }
                if (f.name != null && "constructor".equals(f.name) && f.computedKey == null) {
                    // ES early error: a static field may not be named
                    // constructor (the computed-key form is allowed)
                    throw compileError("Class static fields may not be named 'constructor'");
                }
                assembler.emit("peek", "0");
                assembler.emit("push_eax");
                assembler.emit("set_this");
                if (f.computedKey != null) {
                    compileExpr(f.computedKey);
                    assembler.emit("to_string");
                } else {
                    assembler.emit("string", privateKey(f.name));
                }
                assembler.emit("own_member_ref");
                assembler.emit("push_eax");
                if (f.initializer != null) {
                    compileExpr(f.initializer);
                } else {
                    assembler.emit("undefined");
                }
                assembler.emit("assign");
            }
            assembler.emit("pop_this");
        }
        assembler.emit("pop_eax"); // eax = ctor, stack clean
        if (cls.name != null) {
            emitAssignToName(cls.name);
        }
        privateNameScopes.pop();
    }

    /**
     * Defines one method on the object at the stack top (the class prototype
     * or the constructor): duplicates the object, resolves (or creates) an
     * own member reference and assigns the compiled method closure.
     */
    private void emitMethodDefine(ClassExpr.MethodDef m) throws CompilerException {
        if (m.isStatic && m.name != null && "prototype".equals(m.name)) {
            // ES early error: static members may not be named prototype
            // (the computed-key form is allowed); static methods named
            // "constructor" are legal ({ static constructor() {} })
            throw compileError("Class static members may not be named 'prototype'");
        }
        assembler.emit("peek", "0");
        assembler.emit("push_eax");
        if (m.computedKey != null) {
            compileExpr(m.computedKey);
            assembler.emit("to_string");
        } else {
            assembler.emit("string", privateKey(m.name));
        }
        String marker = m.async ? (m.generator ? "5" : "3")
                : (m.generator ? "2" : "0");
        if (m.isStatic) {
            // ES: static members may not be named prototype/constructor
            assembler.emit("static_name_check");
        }
        if (m.isGetter || m.isSetter) {
            // accessor methods: [proto, key]; the key string stays on the
            // stack for accessor_define (no member reference is needed);
            // class accessors are non-enumerable like class methods
            assembler.emit("push_eax");
            String enter = _compile_function_body(m.params, m.body, m.generator);
            assembler.emit("new_closure", enter, marker);
            assembler.emit("set_func_meta", functionLength(m.params) + "",
                    m.name == null ? "" : m.name);
            if (m.strict) {
                assembler.emit("set_func_strict");
            }
            assembler.emit("accessor_define", m.isGetter ? "get" : "set", "ne");
            return;
        }
        // class methods are non-enumerable: own_member_ref_ne marks the
        // defined key so Object.keys / for-in / descriptor checks agree
        assembler.emit("own_member_ref_ne");
        assembler.emit("push_eax");
        String enter = _compile_function_body(m.params, m.body, m.generator);
        assembler.emit("new_closure", enter, marker);
        assembler.emit("set_func_meta", functionLength(m.params) + "",
                m.name == null ? "" : m.name);
        if (m.strict) {
            assembler.emit("set_func_strict");
        }
        if (m.isGetter) {
            // accessor: define the getter on the class prototype (the
            // receiver is the prototype object held in eax's stack slot)
            assembler.emit("accessor_define", "get");
        } else if (m.isSetter) {
            assembler.emit("accessor_define", "set");
        } else {
            assembler.emit("assign");
        }
    }
}
