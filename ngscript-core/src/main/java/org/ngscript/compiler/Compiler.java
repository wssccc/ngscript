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
import org.ngscript.ast.BinaryExpr;
import org.ngscript.ast.Block;
import org.ngscript.ast.BlockStmt;
import org.ngscript.ast.BreakStmt;
import org.ngscript.ast.CallExpr;
import org.ngscript.ast.ClassExpr;
import org.ngscript.ast.CommaExpr;
import org.ngscript.ast.CondExpr;
import org.ngscript.ast.ContinueStmt;
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

    /** Emits a label op, recording the current block depth for jump fixups. */
    private void emitLabel(String name) {
        labelDepths.put(name, blockDepth);
        assembler.emit("label", name);
    }

    /** Emits a jmp, first popping any block frames above the target depth. */
    private void emitJump(String name) {
        Integer targetDepth = labelDepths.get(name);
        while (targetDepth != null && blockDepth > targetDepth) {
            assembler.emit("leave_block");
            --blockDepth;
        }
        assembler.emit("jmp", name);
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
     * saved value is popped afterwards to keep the stack balanced.
     */
    private void emitAssignToName(String name) {
        assembler.emit("push_eax");
        assembler.emit("mov_eax", name);
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
        if (p == null) {
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
            for (Pattern.Prop prop : ((Pattern.ObjectP) p).props) {
                declarePatternNames(prop.pattern, isVar);
            }
        }
    }

    /**
     * Compiles a destructuring pattern match: the value to destructure must
     * be in eax. Array patterns iterate via the ES iterator protocol;
     * object patterns read members; defaults apply when a value is exactly
     * undefined.
     */
    private void compilePattern(Pattern p) throws CompilerException {
        if (p == null) {
            return; // hole
        }
        if (p.defaultValue != null) {
            String skip = assembler.label("pat_default", p);
            assembler.emit("jnundef", skip);
            compileExpr(p.defaultValue);
            emitLabel(skip);
        }
        if (p instanceof Pattern.IdentP) {
            emitAssignToName(((Pattern.IdentP) p).name);
        } else if (p instanceof Pattern.ArrayP) {
            Pattern.ArrayP arr = (Pattern.ArrayP) p;
            assembler.emit("push_eax");
            assembler.emit("destr_iter_init");
            for (Pattern item : arr.items) {
                assembler.emit("destr_iter_next");
                compilePattern(item);
            }
            if (arr.rest != null) {
                assembler.emit("destr_iter_rest");
                compilePattern(arr.rest);
            } else {
                assembler.emit("destr_iter_done");
            }
            // the pushed array itself (destr_iter_* only drops the iterator)
            assembler.emit("pop");
        } else if (p instanceof Pattern.ObjectP) {
            Pattern.ObjectP obj = (Pattern.ObjectP) p;
            assembler.emit("push_eax");
            for (Pattern.Prop prop : obj.props) {
                assembler.emit("peek", "0");
                assembler.emit("push_eax");
                assembler.emit("string", prop.key);
                assembler.emit("member_ref");
                assembler.emit("deref", "%eax");
                compilePattern(prop.pattern);
            }
            assembler.emit("pop");
        }
    }

    /** Whether a block directly contains let/const declarations. */
    private boolean blockHasLexical(Block block) {
        for (Stmt stmt : block.statements) {
            if (stmt instanceof ExprStmt && ((ExprStmt) stmt).expr instanceof LetDeclExpr) {
                return true;
            }
        }
        return false;
    }

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
            // parsed for conformance; semantics arrive with the environment rework
            throw compileError("with statement is not supported yet");
        } else {
            throw compileError("Unknown statement " + stmt);
        }
    }

    private void compileFunctionDecl(FunctionDecl decl) throws CompilerException {
        String enter = _compile_function_body(decl.params, decl.body);
        if (decl.generator) {
            assembler.instructions.add(0, new Instruction("static_func_gen", decl.name, enter));
        } else {
            assembler.instructions.add(0, new Instruction("static_func", decl.name, enter));
        }
        assembler.instructions.add(1,
                new Instruction("set_func_meta", functionLength(decl.params) + "", decl.name));
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

    private String _compile_function_body(Params params, Block body) throws CompilerException {
        String exit = assembler.label("func_exit", body);
        String enter = assembler.label("func_enter", body);
        emitJump(exit);
        emitLabel(enter);

        // nested functions/arrows have their own block-depth bookkeeping
        int savedBlockDepth = blockDepth;
        blockDepth = 0;
        try {
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
                assembler.emit("pickarg", i + "");
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
            // the arguments object: a snapshot of the actual arguments
            // (ES gives it the parameter names as accessor properties; the
            // snapshot approximation covers length/index reads)
            assembler.emit("rest_args", "0");
            assembler.emit("set", "arguments", "%eax");
            compileBlock(body);
            //default return void (pop any open block frames first)
            while (blockDepth > 0) {
                leaveBlock();
            }
            assembler.emit("clear", "%eax");
            assembler.emit("ret");
        } finally {
            blockDepth = savedBlockDepth;
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
            } else {
                compileExpr(assign.target, true);
                assembler.emit("push_eax");
                compileExpr(assign.value);
                assembler.emit("assign");
            }
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
            compileExpr(member.object);
            assembler.emit("push_eax");
            assembler.emit("string", member.name);
            assembler.emit("member_ref");
            if (!byref) {
                assembler.emit("deref", "%eax");
            }
        } else if (expr instanceof CallExpr) {
            CallExpr call = (CallExpr) expr;
            _compile_funcall(call.callee, call.args);
            assembler.emit("pop_env");
            assembler.emit("pop");//pop params
            assembler.emit("pop");//pop function body
            assembler.emit("pop");//pop receiver
        } else if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            _compile_funcall(newExpr.callee, newExpr.args, true);
            assembler.emit("new_op");
        } else if (expr instanceof IndexExpr) {
            IndexExpr indexExpr = (IndexExpr) expr;
            compileExpr(indexExpr.object);
            assembler.emit("push_eax");
            compileExpr(indexExpr.index);
            assembler.emit("array_ref");
            if (!byref) {
                assembler.emit("deref", "%eax");
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
            // member resolved on the superclass prototype; the receiver (this)
            // is pushed by the call compiler, so nothing leaks here
            assembler.emit("deref", "%superproto");
            assembler.emit("push_eax");
            assembler.emit("string", sm.name);
            assembler.emit("member_ref");
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
        } else if (expr instanceof LambdaExpr) {
            LambdaExpr lambda = (LambdaExpr) expr;
            Block body = lambda.body;
            if (body == null && lambda.bodyExpr != null) {
                // arrow expression body: return the expression value
                body = new Block(
                        Collections.singletonList(new ReturnStmt(lambda.bodyExpr, lambda.line)),
                        lambda.line);
            }
            String enter = _compile_function_body(lambda.params, body);
            assembler.emit("new_closure", enter,
                    lambda.isArrow ? "1" : (lambda.generator ? "2" : "0"));
            assembler.emit("set_func_meta", functionLength(lambda.params) + "",
                    lambda.name == null ? "" : lambda.name);
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
            assembler.emit("string", member.name);
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
                compileExpr(unary.operand);
                assembler.emit("typeof");
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
                    assembler.emit("string", member.name);
                    assembler.emit("delete_op");
                } else {
                    // deleting a plain variable binding yields false in ES
                    compileExpr(unary.operand);
                    assembler.emit("pop");
                    assembler.emit("bool", "false");
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
        compileExpr(stmt.object);
        assembler.emit("forin_init");
        emitLabel(testLabel);
        assembler.emit("forin_next");
        assembler.emit("jz", breakLabel);
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
        compileExpr(stmt.object);
        assembler.emit("forof_init");
        emitLabel(testLabel);
        assembler.emit("forof_next");
        assembler.emit("jdone", breakLabel);
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
        compileExpr(switchStmt.value);
        assembler.emit("push_eax");
        for (int i = 0; i < switchStmt.cases.size(); i++) {
            SwitchCase c = switchStmt.cases.get(i);
            String nextLabel = assembler.label("case_" + i, switchStmt);
            compileExpr(c.value);
            assembler.emit("eq");
            assembler.emit("jz", nextLabel);
            compileBlock(c.body);
            emitJump(breakLabel);
            emitLabel(nextLabel);
        }
        if (switchStmt.defaultBody != null) {
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
        inHookedBlock = true;
        assembler.emit("save_machine_state");
        assembler.emit("test", "%exception");
        assembler.emit("jnz", catchLabel);
        compileBlock(tryStmt.tryBody);
        assembler.emit("drop_machine_state");
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
            _compile_call_finally();
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
        emitJump(finallyLabels.peek());
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
            assembler.emit("string", member.name);
            assembler.emit("member_ref");
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
        Params ctorParams;
        Block ctorBody;
        if (ctor != null) {
            ctorParams = ctor.params;
            ctorBody = ctor.body;
        } else if (cls.superClass != null) {
            ctorParams = new Params(Collections.singletonList("args"), null, true);
            ctorBody = new Block(Collections.singletonList(new ExprStmt(
                    new SuperCallExpr(Collections.singletonList(new SpreadExpr(
                            new IdentExpr("args", cls.line), cls.line)), cls.line), cls.line)), cls.line);
        } else {
            ctorParams = new Params(Collections.emptyList());
            ctorBody = new Block(Collections.emptyList(), cls.line);
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
        assembler.emit("pop_eax"); // eax = ctor, stack clean
        if (cls.name != null) {
            emitAssignToName(cls.name);
        }
    }

    /**
     * Defines one method on the object at the stack top (the class prototype
     * or the constructor): duplicates the object, resolves (or creates) an
     * own member reference and assigns the compiled method closure.
     */
    private void emitMethodDefine(ClassExpr.MethodDef m) throws CompilerException {
        assembler.emit("peek", "0");
        assembler.emit("push_eax");
        if (m.computedKey != null) {
            compileExpr(m.computedKey);
            assembler.emit("to_string");
        } else {
            assembler.emit("string", m.name);
        }
        assembler.emit("own_member_ref");
        assembler.emit("push_eax");
        String enter = _compile_function_body(m.params, m.body);
        assembler.emit("new_closure", enter, m.generator ? "2" : "0");
        assembler.emit("set_func_meta", functionLength(m.params) + "",
                m.name == null ? "" : m.name);
        assembler.emit("assign");
    }
}
