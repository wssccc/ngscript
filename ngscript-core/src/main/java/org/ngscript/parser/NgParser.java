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

package org.ngscript.parser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.ngscript.ast.ArrayLiteral;
import org.ngscript.ast.AssignExpr;
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
import org.ngscript.ast.NewTargetExpr;
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
import org.ngscript.compiler.CompilerException;
import org.ngscript.lexer.Lexer;

/**
 * A hand-written recursive descent parser for the ngscript language.
 *
 * <p>Two modes of operation are supported:
 * <ul>
 * <li>Batch mode: {@link #parse(List)} consumes a complete token list and returns a {@link Program}.</li>
 * <li>Interactive streaming mode: {@link #feedInteractive(List)} feeds token batches into a
 * {@link BlockingQueue} consumed by a dedicated parser thread. When a complete statement has been
 * parsed, the registered {@link StatementListener} is invoked on the parser thread; incomplete
 * statements simply block until more tokens arrive. Each batch is acknowledged synchronously so
 * callers know whether the batch produced a compilable statement.</li>
 * </ul>
 *
 * @author wssccc
 */
public class NgParser {

    /**
     * Receives each complete statement parsed in streaming mode.
     */
    public interface StatementListener {

        /**
         * Called on the parser thread whenever a complete statement has been parsed.
         *
         * @param stmt the parsed statement
         * @throws Exception any error (e.g. compile error) is reported back to the feeder
         */
        void onStatement(Stmt stmt) throws Exception;
    }

    /**
     * Sentinel put into the queue after each fed batch; never part of the token stream.
     */
    private static final Object BATCH_END = new Object();

    /**
     * A supplier that may throw {@link ParserException}.
     */
    private interface ExprSupplier {

        Expr get() throws ParserException;
    }

    private static final Map<String, BinaryExpr.Op> BINARY_OP_BY_TOKEN = new HashMap<>();

    static {
        BINARY_OP_BY_TOKEN.put("or", BinaryExpr.Op.OR);
        BINARY_OP_BY_TOKEN.put("and", BinaryExpr.Op.AND);
        BINARY_OP_BY_TOKEN.put("bit_or", BinaryExpr.Op.BIT_OR);
        BINARY_OP_BY_TOKEN.put("bit_xor", BinaryExpr.Op.BIT_XOR);
        BINARY_OP_BY_TOKEN.put("bit_and", BinaryExpr.Op.BIT_AND);
        BINARY_OP_BY_TOKEN.put("eq", BinaryExpr.Op.EQ);
        BINARY_OP_BY_TOKEN.put("veq", BinaryExpr.Op.VEQ);
        BINARY_OP_BY_TOKEN.put("neq", BinaryExpr.Op.NEQ);
        BINARY_OP_BY_TOKEN.put("vneq", BinaryExpr.Op.VNEQ);
        BINARY_OP_BY_TOKEN.put("gt", BinaryExpr.Op.GT);
        BINARY_OP_BY_TOKEN.put("lt", BinaryExpr.Op.LT);
        BINARY_OP_BY_TOKEN.put("ge", BinaryExpr.Op.GE);
        BINARY_OP_BY_TOKEN.put("le", BinaryExpr.Op.LE);
        BINARY_OP_BY_TOKEN.put("add", BinaryExpr.Op.ADD);
        BINARY_OP_BY_TOKEN.put("sub", BinaryExpr.Op.SUB);
        BINARY_OP_BY_TOKEN.put("mul", BinaryExpr.Op.MUL);
        BINARY_OP_BY_TOKEN.put("div", BinaryExpr.Op.DIV);
        BINARY_OP_BY_TOKEN.put("mod", BinaryExpr.Op.MOD);
        BINARY_OP_BY_TOKEN.put("exp", BinaryExpr.Op.EXP);
        BINARY_OP_BY_TOKEN.put("shl", BinaryExpr.Op.SHL);
        BINARY_OP_BY_TOKEN.put("shr", BinaryExpr.Op.SHR);
        BINARY_OP_BY_TOKEN.put("ushr", BinaryExpr.Op.USHR);
        BINARY_OP_BY_TOKEN.put("nullish", BinaryExpr.Op.NULLISH);
    }

    private static final java.util.Set<String> ASSIGN_OPS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "assign", "asgn_add", "asgn_sub", "asgn_mul", "asgn_div", "asgn_mod", "asgn_exp",
                    "asgn_shl", "asgn_shr", "asgn_ushr", "asgn_bit_and", "asgn_bit_or", "asgn_bit_xor",
                    "asgn_and", "asgn_or", "asgn_nullish")));

    //----------------------------------------------------------------------
    // batch mode state
    //----------------------------------------------------------------------

    private List<Token> tokens;
    private int index;

    /**
     * Line of the most recently consumed token; used by automatic semicolon
     * insertion to detect line terminators.
     */
    private int lastTokenLine = 1;

    /**
     * Number of enclosing loops (while/for/do) and switches; used for the
     * early-error check that break/continue appear in a valid context.
     */
    private int loopDepth;
    private int switchDepth;

    /**
     * Depth of enclosing function bodies; a return statement outside a
     * function is an early error.
     */
    private int functionDepth;

    /**
     * Whether super is legal here: >0 inside a method definition (class
     * methods, object literal methods, getters/setters). Plain functions
     * and arrows reset it to 0 (super is only allowed directly inside a
     * method body).
     */
    private int superDepth;

    /**
     * Strict mode of the current script/function (ES directive prologue:
     * a leading {@code "use strict";} string-literal statement). Strict
     * semantics: no implicit globals, with() is illegal, plain-call this
     * is undefined, and several early errors apply.
     */
    /** ES strict mode of the current directive prologue (script or function). */
    public boolean strictMode;

    /**
     * Whether the current statement list position is still the directive
     * prologue (only leading string-literal expression statements count);
     * reset at the start of every function body.
     */
    private boolean directivePrologue = true;

    /**
     * Depth of enclosing generator (function*) bodies; {@code yield} is a
     * contextual keyword valid only here (elsewhere it stays an ordinary
     * identifier, e.g. the coroutine extension's yield function).
     */
    private int generatorDepth;

    /**
     * Depth of enclosing async function bodies; {@code await} is a
     * contextual keyword valid only here (elsewhere it stays an ordinary
     * identifier).
     */
    private int asyncDepth;

    /**
     * Lexer-driven mode state: when {@link #parse(String)} is used the lexer
     * produces tokens lazily so it can apply the ECMAScript regex/division
     * lexical goal rule based on the previous token.
     */
    private Lexer lexer;
    private final List<Token> lexerBuf = new ArrayList<>();

    //----------------------------------------------------------------------
    // streaming mode state
    //----------------------------------------------------------------------

    private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
    private final LinkedList<Token> pending = new LinkedList<>();
    private Thread parserThread;
    private StatementListener statementListener;
    private final Object batchLock = new Object();
    private boolean batchDone;
    private boolean batchCompiled;
    private Exception batchError;

    public void setStatementListener(StatementListener listener) {
        this.statementListener = listener;
    }

    /**
     * Parses a complete token list into a program. This is the batch mode entry point.
     *
     * @param tokens tokens produced by the lexer (without a trailing EOF token)
     * @return the parsed program
     * @throws ParserException on syntax errors, including unexpected end of input
     */
    public Program parse(List<Token> tokens) throws ParserException {
        this.lexer = null;
        this.tokens = tokens;
        this.index = 0;
        List<Stmt> stmts = new ArrayList<>();
        while (peek() != null) {
            Stmt stmt = parseStatement();
            if (stmt != null) {
                stmts.add(stmt);
            }
        }
        return new Program(stmts, 1);
    }

    /**
     * Parses source code into a program, letting the lexer apply the
     * ECMAScript regex/division lexical goal rule. This is the entry point
     * used by the engine for batch compilation.
     *
     * @param code the source code
     * @return the parsed program
     * @throws ParserException on syntax errors
     */
    public Program parse(String code) throws ParserException {
        this.lexer = new Lexer(code);
        this.lexerBuf.clear();
        this.tokens = null;
        this.index = 0;
        // a fresh script: reset the directive prologue and strictness (the
        // parser instance is shared, so a previous parse must not leak)
        this.directivePrologue = true;
        this.strictMode = false;
        List<Stmt> stmts = new ArrayList<>();
        while (peek() != null) {
            Stmt stmt = parseStatement();
            if (stmt != null) {
                stmts.add(stmt);
            }
        }
        return new Program(stmts, 1);
    }

    /**
     * Parses direct-eval source: the top level may use super (ES: a direct
     * eval inside a method inherits the caller's method environment, so
     * {@code eval('super.x')} in a class field initializer is legal; the
     * caller-context check happens at runtime in evalIn). Nested plain
     * functions still reset the super context.
     */
    public Program parseEval(String code) throws ParserException {
        this.lexer = new Lexer(code);
        this.lexerBuf.clear();
        this.tokens = null;
        this.index = 0;
        this.directivePrologue = true;
        this.strictMode = false;
        int savedSuper = superDepth;
        superDepth = 1;
        List<Stmt> stmts = new ArrayList<>();
        try {
            while (peek() != null) {
                Stmt stmt = parseStatement();
                if (stmt != null) {
                    stmts.add(stmt);
                }
            }
        } finally {
            superDepth = savedSuper;
        }
        return new Program(stmts, 1);
    }

    /**
     * Feeds a batch of tokens into the streaming parser and blocks until the batch has been
     * fully consumed. Returns true when the batch completed at least one statement
     * (which has already been dispatched to the statement listener).
     *
     * @param tokens the token batch (may be empty)
     * @return true if at least one complete statement was produced by this batch
     * @throws ParserException on syntax errors detected in the batch
     * @throws CompilerException on errors thrown by the statement listener
     */
    public boolean feedInteractive(List<Token> tokens) throws ParserException, CompilerException {
        ensureParserThread();
        for (Token t : tokens) {
            queue.add(t);
        }
        queue.add(BATCH_END);
        synchronized (batchLock) {
            while (!batchDone) {
                try {
                    batchLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            batchDone = false;
            Exception error = batchError;
            batchError = null;
            boolean compiled = batchCompiled;
            batchCompiled = false;
            if (error != null) {
                if (error instanceof ParserException) {
                    throw (ParserException) error;
                }
                if (error instanceof CompilerException) {
                    throw (CompilerException) error;
                }
                throw new RuntimeException(error);
            }
            return compiled;
        }
    }

    private synchronized void ensureParserThread() {
        if (parserThread == null) {
            parserThread = new Thread(this::parserLoop, "ngscript-parser");
            parserThread.setDaemon(true);
            parserThread.start();
        }
    }

    /**
     * The streaming parser loop, running on the dedicated parser thread. It blocks on the
     * queue whenever more input is needed, so partially parsed statements are preserved
     * across batches by the call stack. On errors the remaining tokens of the current batch
     * are discarded, the error is reported to the feeder and parsing restarts at the
     * statement boundary.
     */
    private void parserLoop() {
        while (true) {
            try {
                while (true) {
                    Stmt stmt = parseStatement();
                    if (stmt != null) {
                        if (statementListener != null) {
                            statementListener.onStatement(stmt);
                        }
                        synchronized (batchLock) {
                            batchCompiled = true;
                        }
                    }
                }
            } catch (Exception e) {
                // discard the rest of the current batch
                while (true) {
                    try {
                        Object item = queue.take();
                        if (item == BATCH_END) {
                            break;
                        }
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                synchronized (batchLock) {
                    if (batchError == null) {
                        batchError = e;
                    }
                    batchDone = true;
                    batchLock.notifyAll();
                }
            }
        }
    }

    //----------------------------------------------------------------------
    // token access (shared by batch and streaming modes)
    //----------------------------------------------------------------------

    private boolean isStreaming() {
        return parserThread != null;
    }

    private Token peek() {
        return peekN(1);
    }

    private Token peekN(int n) {
        if (lexer != null) {
            while (lexerBuf.size() < n) {
                Token t = lexer.nextToken();
                if (t != null && t.type.equals("comment")) {
                    continue;
                }
                lexerBuf.add(t);
            }
            return lexerBuf.get(n - 1);
        }
        if (isStreaming()) {
            while (pending.size() < n) {
                try {
                    Object item = queue.take();
                    if (item == BATCH_END) {
                        synchronized (batchLock) {
                            batchDone = true;
                            batchLock.notifyAll();
                        }
                        continue;
                    }
                    pending.addLast((Token) item);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return pending.get(n - 1);
        }
        return index + n - 1 < tokens.size() ? tokens.get(index + n - 1) : null;
    }

    private Token next() {
        Token t = peek();
        if (t != null) {
            lastTokenLine = t.line;
        }
        if (lexer != null) {
            lexerBuf.remove(0);
        } else if (isStreaming()) {
            pending.removeFirst();
        } else {
            ++index;
        }
        return t;
    }

    private boolean peekIs(String type) {
        Token t = peek();
        return t != null && t.type.equals(type);
    }

    private Token expect(String type) throws ParserException {
        Token t = next();
        if (t == null || !t.type.equals(type)) {
            throw new ParserException("expecting " + type + " but got " + (t == null ? "end of input" : t),
                    t == null ? null : new org.ngscript.api.Source(null, t.line));
        }
        return t;
    }

    /**
     * Consumes a semicolon, or applies the ECMAScript automatic semicolon
     * insertion rules when one is missing: a semicolon is inserted at the end
     * of input, before a closing brace, or before a token that starts on a
     * later line than the previous token.
     */
    private void consumeSemicolon() throws ParserException {
        if (peekIs("semicolon")) {
            next();
            return;
        }
        Token t = peek();
        if (t == null) {
            return; // end of input
        }
        if (t.type.equals("rcurly")) {
            return; // before a closing brace
        }
        if (t.line > lastTokenLine) {
            return; // line terminator before the offending token
        }
        throw new ParserException("expecting semicolon but got " + t,
                new org.ngscript.api.Source(null, t.line));
    }

    /**
     * True when the next token is on a later line than {@code refLine}, is a
     * closing brace, or the input has ended; used by restricted productions
     * (return/break/continue/throw) to decide whether a semicolon is implied.
     */
    private boolean atLineBreakAfter(int refLine) {
        Token t = peek();
        if (t == null) {
            return true;
        }
        if (t.type.equals("rcurly")) {
            return true;
        }
        return t.line > refLine;
    }

    //----------------------------------------------------------------------
    // statements
    //----------------------------------------------------------------------

    private Stmt parseStatement() throws ParserException {
        // any statement that is not a leading string-literal expression
        // ends the directive prologue (ES: only ExpressionStatements of
        // string literals form the prologue; an empty statement or a
        // non-string statement terminates it)
        if (directivePrologue) {
            Token t0 = peek();
            if (t0 == null || !t0.type.equals("string")) {
                directivePrologue = false;
            }
        }
        Token t = peek();
        if (t == null) {
            return null;
        }
        switch (t.type) {
            case "semicolon":
                next();
                return null;
            case "function": {
                // function name(...) / function* name(...) is a declaration,
                // function(...) is a lambda expression
                Token second = peekN(2);
                if (second != null && (second.type.equals("ident")
                        || (second.type.equals("mul") && peekN(3) != null
                            && peekN(3).type.equals("ident")))) {
                    return parseFunctionDecl();
                }
                return parseExprStmt();
            }
            case "ident": {
                // async function name(...) is an async function declaration
                // (async must be followed by function on the same line)
                Token second = peekN(2);
                if (second != null && second.type.equals("function")
                        && second.line == t.line
                        && t.value.equals("async")) {
                    Token third = peekN(3);
                    if (third != null && (third.type.equals("ident")
                            || (third.type.equals("mul") && peekN(4) != null
                                && peekN(4).type.equals("ident")))) {
                        return parseAsyncFunctionDecl(t);
                    }
                }
                // let as a declaration keyword is contextual: "let x" declares,
                // "let = 1" / "let.x" / "let()" treat it as a plain identifier
                if (t.value.equals("let") && second != null
                        && (second.type.equals("ident")
                            || second.type.equals("lsqr") || second.type.equals("lcurly"))) {
                    return parseLexicalDecl(false);
                }
                return parseExprStmt();
            }
            case "if":
                return parseIf();
            case "for":
                return parseFor();
            case "while":
                return parseWhile();
            case "do":
                return parseDo();
            case "switch":
                return parseSwitch();
            case "with": {
                if (strictMode) {
                    // ES early error: with is illegal in strict mode
                    throw new ParserException("with statement is not allowed in strict mode",
                            new org.ngscript.api.Source(null, t.line));
                }
                next();
                expect("lparen");
                Expr object = parseExpr();
                expect("rparen");
                Stmt body = parseStatementOrBlock();
                return new WithStmt(object, body, t.line);
            }
            case "debugger":
                // a no-op statement in ES
                next();
                consumeSemicolon();
                return null;
            case "try":
                return parseTry();
            case "const":
                return parseLexicalDecl(true);
            case "class": {
                Token kw = next();
                Token name = expect("ident");
                return new ExprStmt(parseClassTail(name.value, kw), kw.line);
            }
            case "lcurly":
                // a bare block used as a statement
                next();
                return new BlockStmt(parseBlockRest(), t.line);
            case "throw": {
                next();
                if (atLineBreakAfter(t.line)) {
                    throw new ParserException("unexpected line break after throw",
                            new org.ngscript.api.Source(null, t.line));
                }
                Expr expr = parseExpr();
                consumeSemicolon();
                return new ThrowStmt(expr, t.line);
            }
            case "return": {
                next();
                if (functionDepth == 0) {
                    throw new ParserException("Illegal return statement",
                            new org.ngscript.api.Source(null, t.line));
                }
                if (peekIs("semicolon")) {
                    next();
                    return new ReturnStmt(null, t.line);
                }
                if (atLineBreakAfter(t.line)) {
                    // restricted production: return followed by a line terminator
                    return new ReturnStmt(null, t.line);
                }
                Expr expr = parseExpr();
                consumeSemicolon();
                return new ReturnStmt(expr, t.line);
            }
            case "break": {
                next();
                if (peekIs("ident") && !atLineBreakAfter(t.line)) {
                    Token label = next();
                    consumeSemicolon();
                    if (!currentLabels.contains(label.value)) {
                        throw new ParserException("Undefined label '" + label.value + "'",
                                new org.ngscript.api.Source(null, t.line));
                    }
                    return new BreakStmt(label.value, t.line);
                }
                consumeSemicolon();
                if (loopDepth == 0 && switchDepth == 0 && currentLabels.isEmpty()) {
                    throw new ParserException("Illegal break statement",
                            new org.ngscript.api.Source(null, t.line));
                }
                return new BreakStmt(t.line);
            }
            case "continue": {
                next();
                if (peekIs("ident") && !atLineBreakAfter(t.line)) {
                    Token label = next();
                    consumeSemicolon();
                    if (!currentLabels.contains(label.value)) {
                        throw new ParserException("Undefined label '" + label.value + "'",
                                new org.ngscript.api.Source(null, t.line));
                    }
                    return new ContinueStmt(label.value, t.line);
                }
                consumeSemicolon();
                if (loopDepth == 0 && currentLabels.isEmpty()) {
                    throw new ParserException("Illegal continue statement",
                            new org.ngscript.api.Source(null, t.line));
                }
                return new ContinueStmt(t.line);
            }
            case "import":
                // `import(` on the same line is a dynamic import expression
                // (statement position); otherwise the statement-level
                // `import Java包` extension applies
                {
                    Token it = peek();
                    Token nx2 = peekN(2);
                    if (nx2 != null && nx2.type.equals("lparen") && nx2.line == it.line) {
                        return parseExprStmt();
                    }
                }
                return parseImport();
            case "go":
                return parseGo();
            default:
                // let as a declaration keyword is contextual: "let x" declares,
                // "let = 1" / "let.x" / "let()" treat it as a plain identifier
                if (t.type.equals("ident") && t.value.equals("let")) {
                    Token second = peekN(2);
                    if (second != null && (second.type.equals("ident")
                            || second.type.equals("lsqr") || second.type.equals("lcurly"))) {
                        return parseLexicalDecl(false);
                    }
                }
                // a label statement: ident followed by a colon
                if (t.type.equals("ident")) {
                    Token second = peekN(2);
                    if (second != null && second.type.equals("colon")) {
                        next();
                        next();
                        currentLabels.push(t.value);
                        try {
                            Stmt inner = parseStatement();
                            return new LabeledStmt(t.value, inner, t.line);
                        } finally {
                            currentLabels.pop();
                        }
                    }
                }
                return parseExprStmt();
        }
    }

    /**
     * Parses a let/const declaration statement; the leading keyword token
     * must still be current.
     */
    private Stmt parseLexicalDecl(boolean isConst) throws ParserException {
        LetDeclExpr decl = parseLexicalDeclTail(isConst, next());
        consumeSemicolon();
        return new ExprStmt(decl, decl.line);
    }

    /**
     * Parses the rest of a class body after the class keyword (and the name
     * when present): an optional extends clause and the method definitions.
     */
    private Expr parseClassTail(String name, Token kw) throws ParserException {
        Expr superClass = null;
        if (peekIs("extends")) {
            next();
            superClass = parseExprClass();
        }
        expect("lcurly");
        List<ClassExpr.MethodDef> methods = new ArrayList<>();
        List<ClassExpr.FieldDef> fields = new ArrayList<>();
        while (!peekIs("rcurly")) {
            if (peekIs("semicolon")) {
                next();
                continue;
            }
            Token t = peek();
            boolean isStatic = false;
            if (t != null && t.type.equals("ident") && t.value.equals("static")) {
                Token nx = peekN(2);
                // `static` followed by `=`, `;` or `}` is a field named static
                if (nx != null && !nx.type.equals("lparen")
                        && !nx.type.equals("assign") && !nx.type.equals("semicolon")
                        && !nx.type.equals("rcurly")) {
                    isStatic = true;
                    next();
                    t = peek();
                }
            }
            boolean isGetter = false;
            boolean isSetter = false;
            if (t != null && t.type.equals("ident")
                    && (t.value.equals("get") || t.value.equals("set"))) {
                Token nx = peekN(2);
                if (nx != null && (nx.type.equals("ident")
                        || nx.type.equals("private_ident")
                        || nx.type.equals("lsqr")
                        || nx.type.equals("string")
                        || nx.type.equals("integer")
                        || nx.type.equals("double"))) {
                    isGetter = t.value.equals("get");
                    isSetter = t.value.equals("set");
                    next();
                    next();
                    String mname = null;
                    Expr computedKey = null;
                    if (nx.type.equals("lsqr")) {
                        computedKey = parseExpr();
                        expect("rsqr");
                    } else {
                        mname = tokenName(nx);
                    }
                    expect("lparen");
                    Params accParams;
                    if (isSetter) {
                        accParams = parseParamList();
                    } else {
                        // getters take no parameters
                        accParams = new Params(Collections.emptyList());
                    }
                    expect("rparen");
                    ++functionDepth;
                    try {
                        Block body = parseFunctionBody();
                        ClassExpr.MethodDef md = new ClassExpr.MethodDef(
                                mname, accParams, body,
                                isStatic, isGetter, isSetter, false,
                                false, computedKey);
                        md.strict = pendingFunctionStrict;
                        methods.add(md);
                    } finally {
                        --functionDepth;
                    }
                    if (peekIs("semicolon")) {
                        next();
                    }
                    continue;
                }
            }
            Token mname = peek();
            boolean isGenerator = false;
            boolean isAsync = false;
            Expr computedKey = null;
            if (mname != null && mname.type.equals("ident") && mname.value.equals("async")) {
                // async method: async m() / async *m() / async [expr]()
                Token nx = peekN(2);
                if (nx != null && (nx.type.equals("ident")
                        || nx.type.equals("private_ident")
                        || nx.type.equals("mul") || nx.type.equals("lsqr"))) {
                    isAsync = true;
                    next();
                    mname = peek();
                    if (mname != null && mname.type.equals("mul")) {
                        isGenerator = true;
                        next();
                        mname = peek();
                    }
                }
            }
            if (mname != null && mname.type.equals("mul")) {
                // generator method: *m() {}
                isGenerator = true;
                next();
                mname = peek();
                if (mname != null && mname.type.equals("lsqr")) {
                    next();
                    computedKey = parseExpr();
                    expect("rsqr");
                    mname = null;
                }
            } else if (mname != null && mname.type.equals("lsqr")) {
                // computed method name: [expr]() {}
                next();
                computedKey = parseExpr();
                expect("rsqr");
                mname = null;
            }
            if (mname == null && computedKey == null) {
                throw new ParserException("expecting a method name but got " + peek(),
                        new org.ngscript.api.Source(null, kw.line));
            }
            if (mname != null && !(isKeyName(mname.type)
                    || mname.type.equals("string") || mname.type.equals("integer")
                    || mname.type.equals("double"))) {
                throw new ParserException("expecting a method name but got " + mname,
                        new org.ngscript.api.Source(null, kw.line));
            }
            if (mname != null) {
                next();
            }
            // a field declaration: `name = init`, `name;` or the class body
            // closes right after the name ([expr] = init for computed keys);
            // ASI also terminates the field when the next token is on a new
            // line (e.g. `m() {} a` then `b = 1` on the next line), unless
            // the next token starts a method (`name(`)
            Token after = peek();
            boolean methodFollows = after != null && after.type.equals("lparen");
            if (after != null && !methodFollows
                    && (after.type.equals("assign")
                            || after.type.equals("semicolon") || after.type.equals("rcurly")
                            || atLineBreakAfter(kw.line))) {
                if (isGenerator || isAsync) {
                    throw new ParserException("generator/async fields are not allowed",
                            new org.ngscript.api.Source(null, kw.line));
                }
                Expr init = null;
                if (after.type.equals("assign")) {
                    next();
                    // field initializers run in a method context: super is
                    // available (ES: the initializer's environment is a
                    // method environment with a HomeObject)
                    int savedSuper = superDepth;
                    ++superDepth;
                    try {
                        init = parseAssignment();
                    } finally {
                        superDepth = savedSuper;
                    }
                }
                fields.add(new ClassExpr.FieldDef(
                        mname == null ? null : tokenName(mname),
                        isStatic, computedKey, init));
                if (peekIs("semicolon")) {
                    next();
                }
                continue;
            }
            expect("lparen");
            Params params = parseParamList();
            expect("rparen");
            ++functionDepth;
            int savedGen = generatorDepth;
            int savedAsync = asyncDepth;
            int savedSuper = superDepth;
            if (isGenerator) {
                ++generatorDepth;
            } else {
                generatorDepth = 0;
            }
            asyncDepth = isAsync ? 1 : 0;
            // class methods allow super
            ++superDepth;
            try {
                Block body = parseFunctionBody();
                // keyword tokens (break/if/...) have a null value: only a
                // real ident token can be `constructor`
                boolean isCtor = mname != null && mname.value != null
                        && mname.value.equals("constructor")
                        && !isStatic && !isGenerator && !isAsync;
                ClassExpr.MethodDef md2 = new ClassExpr.MethodDef(
                        mname == null ? null : tokenName(mname), params, body,
                        isStatic, false, false, isCtor, isGenerator, isAsync, computedKey);
                md2.strict = pendingFunctionStrict;
                methods.add(md2);
            } finally {
                --functionDepth;
                generatorDepth = savedGen;
                asyncDepth = savedAsync;
                superDepth = savedSuper;
            }
            if (peekIs("semicolon")) {
                next();
            }
        }
        expect("rcurly");
        return new ClassExpr(name, superClass, methods, fields, kw.line);
    }

    /**
     * Parses the declarator list of a let/const declaration after the leading
     * keyword token has been consumed ({@code kw} is that token, used for the
     * line number).
     */
    private LetDeclExpr parseLexicalDeclTail(boolean isConst, Token kw) throws ParserException {
        List<String> names = new ArrayList<>();
        List<Expr> inits = new ArrayList<>();
        List<Pattern> patterns = null;
        while (true) {
            Token t = peek();
            if (t != null && (t.type.equals("lsqr") || t.type.equals("lcurly"))) {
                // destructuring declarator: let [a, b] = arr;
                if (patterns == null) {
                    patterns = new ArrayList<>();
                }
                patterns.add(parsePattern(false));
                names.add(null);
            } else {
                if (patterns != null) {
                    throw new ParserException("mixed destructuring and plain declarators",
                            new org.ngscript.api.Source(null, t == null ? -1 : t.line));
                }
                Token name = expect("ident");
                names.add(name.value);
            }
            if (peekIs("assign")) {
                next();
                inits.add(parseAssignment());
            } else {
                inits.add(null);
                if (isConst) {
                    throw new ParserException("Missing initializer in const declaration",
                            new org.ngscript.api.Source(null, t == null ? -1 : t.line));
                }
            }
            if (!peekIs("comma")) {
                break;
            }
            next();
        }
        return new LetDeclExpr(names, inits, patterns, isConst, kw.line);
    }

    /**
     * Parses a destructuring pattern: {@code x}, {@code [a, , ...rest]} or
     * {@code {p, q: r}} with optional {@code = default} initializers.
     * {@code allowDefault} is false for the top level of a declaration
     * pattern (there the following {@code =} is the declarator's own
     * initializer, per the ES grammar).
     */
    private Pattern parsePattern(boolean allowDefault) throws ParserException {
        Token t = peek();
        if (t == null) {
            throw new ParserException("unexpected end of input");
        }
        Pattern pattern;
        if (t.type.equals("lsqr")) {
            next();
            List<Pattern> items = new ArrayList<>();
            while (!peekIs("rsqr")) {
                if (peekIs("comma")) {
                    next();
                    items.add(null); // hole
                    continue;
                }
                if (peekIs("spread")) {
                    next();
                    Pattern.ArrayP arr = new Pattern.ArrayP(items, t.line);
                    arr.rest = parsePattern(true);
                    expect("rsqr");
                    return allowDefault ? applyPatternDefault(arr) : arr;
                }
                items.add(parsePattern(true));
                if (!peekIs("comma")) {
                    break;
                }
                next();
            }
            expect("rsqr");
            pattern = new Pattern.ArrayP(items, t.line);
        } else if (t.type.equals("lcurly")) {
            next();
            List<Pattern.Prop> props = new ArrayList<>();
            while (!peekIs("rcurly")) {
                if (peekIs("spread")) {
                    // object rest: {a, ...rest}
                    next();
                    Pattern.ObjectP op = new Pattern.ObjectP(props, t.line);
                    op.rest = parsePattern(true);
                    expect("rcurly");
                    return allowDefault ? applyPatternDefault(op) : op;
                }
                if (peekIs("lsqr")) {
                    // computed key: {[expr]: target}
                    next();
                    Expr keyExpr = parseExpr();
                    expect("rsqr");
                    if (!peekIs("colon")) {
                        throw new ParserException("expecting colon after computed key in pattern",
                                new org.ngscript.api.Source(null, t.line));
                    }
                    next();
                    props.add(new Pattern.Prop(keyExpr, parsePattern(true)));
                    if (!peekIs("comma")) {
                        break;
                    }
                    next();
                    continue;
                }
                Token key = peek();
                if (key == null || !(isKeyName(key.type) || key.type.equals("string")
                        || key.type.equals("integer") || key.type.equals("double"))) {
                    throw new ParserException("expecting object key but got "
                            + (key == null ? "end of input" : key),
                            new org.ngscript.api.Source(null, t.line));
                }
                next();
                String keyName = tokenName(key);
                Pattern inner;
                if (peekIs("colon")) {
                    next();
                    inner = parsePattern(true);
                } else {
                    // shorthand: {x} means {x: x} ({x = 1} takes a default)
                    inner = applyPatternDefault(new Pattern.IdentP(keyName, key.line));
                }
                props.add(new Pattern.Prop(keyName, inner));
                if (!peekIs("comma")) {
                    break;
                }
                next();
            }
            expect("rcurly");
            pattern = new Pattern.ObjectP(props, t.line);
        } else {
            Token name = expect("ident");
            pattern = new Pattern.IdentP(name.value, name.line);
        }
        return allowDefault ? applyPatternDefault(pattern) : pattern;
    }

    private Pattern applyPatternDefault(Pattern pattern) throws ParserException {
        if (peekIs("assign")) {
            next();
            pattern.defaultValue = parseAssignment();
        }
        return pattern;
    }

    /**
     * Parses a statement or a block, used for the bodies of if/while/for/do
     * which may be a single statement (ES: if (x) doThing();).
     */
    private Stmt parseStatementOrBlock() throws ParserException {
        if (peekIs("lcurly")) {
            Token lcurly = next();
            return new BlockStmt(parseBlockRest(), lcurly.line);
        }
        return parseStatement();
    }

    /**
     * Parses the statements of a block, assuming the opening brace has been
     * consumed, and consumes the closing brace.
     */
    private Block parseBlockRest() throws ParserException {
        List<Stmt> stmts = new ArrayList<>();
        while (true) {
            Token t = peek();
            if (t == null) {
                throw new ParserException("unexpected end of input, expecting }");
            }
            if (t.type.equals("rcurly")) {
                next();
                break;
            }
            Stmt stmt = parseStatement();
            if (stmt != null) {
                stmts.add(stmt);
            }
        }
        return new Block(stmts, 0);
    }

    /**
     * Labels declared in enclosing labelled statements, for the break/continue
     * early-error check (a bare break inside a labelled block is illegal).
     */
    private final Deque<String> currentLabels = new ArrayDeque<>();

    private Stmt parseFunctionDecl() throws ParserException {
        Token fn = next();
        boolean generator = false;
        if (peekIs("mul")) {
            // function* name(...)
            next();
            generator = true;
        }
        Token name = expect("ident");
        expect("lparen");
        Params params = parseParamList();
        expect("rparen");
        ++functionDepth;
        int savedGen = generatorDepth;
        int savedAsync = asyncDepth;
        int savedSuper = superDepth;
        if (generator) {
            ++generatorDepth;
        } else {
            // a nested plain function has no yield context of its own
            generatorDepth = 0;
        }
        asyncDepth = 0;
        superDepth = 0;
        try {
            Block body = parseFunctionBody();
            FunctionDecl decl = new FunctionDecl(name.value, params, body, generator, fn.line);
            decl.strict = pendingFunctionStrict;
            return decl;
        } finally {
            --functionDepth;
            generatorDepth = savedGen;
            asyncDepth = savedAsync;
            superDepth = savedSuper;
        }
    }

    /** Parses an async function declaration (the 'async' token is current). */
    private Stmt parseAsyncFunctionDecl(Token asyncTok) throws ParserException {
        next(); // async
        expect("function");
        boolean generator = false;
        if (peekIs("mul")) {
            next();
            generator = true;
        }
        Token name = expect("ident");
        expect("lparen");
        Params params = parseParamList();
        expect("rparen");
        ++functionDepth;
        int savedGen = generatorDepth;
        int savedAsync = asyncDepth;
        int savedSuper = superDepth;
        generatorDepth = 0;
        ++asyncDepth;
        superDepth = 0;
        try {
            Block body = parseFunctionBody();
            FunctionDecl decl = new FunctionDecl(name.value, params, body, generator, true, asyncTok.line);
            decl.strict = pendingFunctionStrict;
            return decl;
        } finally {
            --functionDepth;
            generatorDepth = savedGen;
            asyncDepth = savedAsync;
            superDepth = savedSuper;
        }
    }

    /**
     * Parses the rest of a for-of loop: the iterable expression, the closing
     * paren and the body. The {@code of} keyword has been consumed.
     */
    private Stmt parseForOf(Expr target, Token forTok) throws ParserException {
        Expr object = parseExpr();
        expect("rparen");
        ++loopDepth;
        try {
            return new ForOfStmt(target, object, parseStatementOrBlock(), forTok.line);
        } finally {
            --loopDepth;
        }
    }

    private Stmt parseIf() throws ParserException {
        Token t = next();
        expect("lparen");
        Expr condition = parseExpr();
        expect("rparen");
        Stmt thenBody = parseStatementOrBlock();
        Stmt elseBody = null;
        if (peekIs("else")) {
            next();
            elseBody = parseStatementOrBlock();
        }
        return new IfStmt(condition, thenBody, elseBody, t.line);
    }

    private Stmt parseFor() throws ParserException {
        Token t = next();
        expect("lparen");
        // for-in forms: for (var x in obj) and for (x in obj)
        Token first = peek();
        if (first != null && first.type.equals("var")) {
            Token second = peekN(2);
            Token third = peekN(3);
            if (second != null && second.type.equals("ident") && third != null && third.type.equals("in")) {
                next();
                next();
                next();
                Expr object = parseExpr();
                expect("rparen");
                ++loopDepth;
                try {
                    return new ForInStmt(new VarDeclExpr(second.value, false, second.line), object,
                            parseStatementOrBlock(), t.line);
                } finally {
                    --loopDepth;
                }
            }
            if (second != null && second.type.equals("ident") && third != null
                    && third.type.equals("ident") && third.value.equals("of")) {
                next();
                next();
                next();
                return parseForOf(new VarDeclExpr(second.value, false, second.line), t);
            }
        } else if (first != null && first.type.equals("ident")) {
            Token second = peekN(2);
            if (second != null && second.type.equals("in")) {
                next();
                next();
                Expr object = parseExpr();
                expect("rparen");
                ++loopDepth;
                try {
                    return new ForInStmt(new IdentExpr(first.value, first.line), object,
                            parseStatementOrBlock(), t.line);
                } finally {
                    --loopDepth;
                }
            }
            if (second != null && second.type.equals("ident") && second.value.equals("of")) {
                next();
                next();
                return parseForOf(new IdentExpr(first.value, first.line), t);
            }
            // let as a declaration in for: for (let x in obj) / for (let i = 0;;)
            if (first.value.equals("let") && second != null && second.type.equals("ident")) {
                Token third = peekN(3);
                if (third != null && third.type.equals("in")) {
                    next();
                    next();
                    next();
                    Expr object = parseExpr();
                    expect("rparen");
                    ++loopDepth;
                    try {
                        return new ForInStmt(new LetDeclExpr(
                                Collections.singletonList(second.value),
                                Collections.singletonList(null), false, second.line), object,
                                parseStatementOrBlock(), t.line);
                    } finally {
                        --loopDepth;
                    }
                }
                if (third != null && third.type.equals("ident") && third.value.equals("of")) {
                    next();
                    next();
                    next();
                    return parseForOf(new LetDeclExpr(
                            Collections.singletonList(second.value),
                            Collections.singletonList(null), false, second.line), t);
                }
                // C-style for with a let init: for (let i = 0; test; step)
                LetDeclExpr init = parseLexicalDeclTail(false, next());
                expect("semicolon");
                Expr test = parseNullableExpr();
                expect("semicolon");
                Expr step = parseNullableExpr();
                expect("rparen");
                ++loopDepth;
                try {
                    return new ForStmt(init, test, step, parseStatementOrBlock(), t.line);
                } finally {
                    --loopDepth;
                }
            }
        } else if (first != null && first.type.equals("const")) {
            Token second = peekN(2);
            Token third = peekN(3);
            if (second != null && second.type.equals("ident") && third != null && third.type.equals("in")) {
                next();
                next();
                next();
                Expr object = parseExpr();
                expect("rparen");
                ++loopDepth;
                try {
                    return new ForInStmt(new LetDeclExpr(
                            Collections.singletonList(second.value),
                            Collections.singletonList(null), true, second.line), object,
                            parseStatementOrBlock(), t.line);
                } finally {
                    --loopDepth;
                }
            }
            if (second != null && second.type.equals("ident") && third != null
                    && third.type.equals("ident") && third.value.equals("of")) {
                next();
                next();
                next();
                return parseForOf(new LetDeclExpr(
                        Collections.singletonList(second.value),
                        Collections.singletonList(null), true, second.line), t);
            }
            // C-style for with a const init: for (const i = 0; test; step)
            LetDeclExpr init = parseLexicalDeclTail(true, next());
            expect("semicolon");
            Expr test = parseNullableExpr();
            expect("semicolon");
            Expr step = parseNullableExpr();
            expect("rparen");
            ++loopDepth;
            try {
                return new ForStmt(init, test, step, parseStatementOrBlock(), t.line);
            } finally {
                --loopDepth;
            }
        }
        // C-style for
        Expr init = parseNullableExpr();
        expect("semicolon");
        Expr test = parseNullableExpr();
        expect("semicolon");
        Expr step = parseNullableExpr();
        expect("rparen");
        ++loopDepth;
        try {
            Stmt body = parseStatementOrBlock();
            return new ForStmt(init, test, step, body, t.line);
        } finally {
            --loopDepth;
        }
    }

    private Stmt parseWhile() throws ParserException {
        Token t = next();
        expect("lparen");
        Expr condition = parseExpr();
        expect("rparen");
        ++loopDepth;
        try {
            Stmt body = parseStatementOrBlock();
            return new WhileStmt(condition, body, t.line);
        } finally {
            --loopDepth;
        }
    }

    private Stmt parseDo() throws ParserException {
        Token t = next();
        ++loopDepth;
        try {
            Stmt body = parseStatementOrBlock();
            expect("while");
            expect("lparen");
            Expr condition = parseExpr();
            expect("rparen");
            consumeSemicolon();
            return new DoWhileStmt(body, condition, t.line);
        } finally {
            --loopDepth;
        }
    }

    private Stmt parseSwitch() throws ParserException {
        Token t = next();
        expect("lparen");
        Expr value = parseExpr();
        expect("rparen");
        expect("lcurly");
        ++switchDepth;
        try {
            List<SwitchCase> cases = new ArrayList<>();
            Block defaultBody = null;
            while (true) {
                Token clause = peek();
                if (clause == null) {
                    throw new ParserException("unexpected end of input, expecting case, default or }");
                }
                if (clause.type.equals("rcurly")) {
                    next();
                    break;
                }
                if (clause.type.equals("case")) {
                    next();
                    Expr caseValue = parseExpr();
                    expect("colon");
                    Block body = parseStatementsUntilSwitchClause();
                    cases.add(new SwitchCase(caseValue, body, clause.line));
                } else if (clause.type.equals("default")) {
                    next();
                    expect("colon");
                    defaultBody = parseStatementsUntilSwitchClause();
                } else {
                    throw new ParserException("expecting case, default or } but got " + clause);
                }
            }
            return new SwitchStmt(value, cases, defaultBody, t.line);
        } finally {
            --switchDepth;
        }
    }

    private Block parseStatementsUntilSwitchClause() throws ParserException {
        List<Stmt> stmts = new ArrayList<>();
        while (true) {
            Token t = peek();
            if (t == null) {
                throw new ParserException("unexpected end of input, expecting case, default or }");
            }
            if (t.type.equals("case") || t.type.equals("default") || t.type.equals("rcurly")) {
                break;
            }
            Stmt stmt = parseStatement();
            if (stmt != null) {
                stmts.add(stmt);
            }
        }
        return new Block(stmts, 0);
    }

    private Stmt parseTry() throws ParserException {
        Token t = next();
        Block tryBody = parseBlock();
        String catchVar = null;
        Block catchBody = null;
        Block finallyBody = null;
        if (peekIs("catch")) {
            next();
            if (peekIs("lparen")) {
                next();
                Token v = expect("ident");
                expect("rparen");
                catchVar = v.value;
            }
            catchBody = parseBlock();
        }
        if (peekIs("finally")) {
            next();
            finallyBody = parseBlock();
        }
        if (catchBody == null && finallyBody == null) {
            throw new ParserException("expecting catch or finally", new org.ngscript.api.Source(null, t.line));
        }
        return new TryStmt(tryBody, catchVar, catchBody, finallyBody, t.line);
    }

    private Stmt parseImport() throws ParserException {
        Token t = next();
        StringBuilder sb = new StringBuilder(expect("ident").value);
        while (peekIs("dot")) {
            next();
            sb.append('.').append(expect("ident").value);
        }
        consumeSemicolon();
        return new ImportStmt(sb.toString(), t.line);
    }

    private Stmt parseGo() throws ParserException {
        Token t = next();
        Expr callee = parsePostfix(false);
        expect("lparen");
        List<Expr> args = parseParams();
        expect("rparen");
        consumeSemicolon();
        return new GoStmt(callee, args, t.line);
    }

    private Stmt parseExprStmt() throws ParserException {
        Expr expr = parseExpr();
        consumeSemicolon();
        if (directivePrologue && expr instanceof LiteralExpr
                && ((LiteralExpr) expr).kind == LiteralExpr.Kind.STRING) {
            // ES directive prologue: every leading string-literal statement
            // stays in the prologue; "use strict" (written without escapes)
            // switches the script or function to strict mode
            if ("use strict".equals(((LiteralExpr) expr).value)
                    && !((LiteralExpr) expr).hasEscape) {
                strictMode = true;
            }
        } else {
            directivePrologue = false;
        }
        return new ExprStmt(expr, expr.line);
    }

    /**
     * Parses a function body block, resetting the directive prologue so a
     * leading {@code "use strict";} inside the function takes effect for
     * the function (the caller saves/restores {@link #strictMode} around
     * the whole function definition and records the result on the AST).
     */
    private Block parseFunctionBody() throws ParserException {
        boolean savedPrologue = directivePrologue;
        directivePrologue = true;
        boolean savedStrict = strictMode;
        try {
            Block body = parseBlock();
            // the function's own strictness (its directive prologue, or
            // inherited from the enclosing scope); the caller reads it to
            // stamp the function AST node
            pendingFunctionStrict = strictMode;
            return body;
        } finally {
            strictMode = savedStrict;
            directivePrologue = savedPrologue;
        }
    }

    /** The strictness of the most recently parsed function body. */
    private boolean pendingFunctionStrict;

    private Block parseBlock() throws ParserException {
        Token lcurly = expect("lcurly");
        List<Stmt> stmts = new ArrayList<>();
        while (true) {
            Token t = peek();
            if (t == null) {
                throw new ParserException("unexpected end of input, expecting }");
            }
            if (t.type.equals("rcurly")) {
                next();
                break;
            }
            Stmt stmt = parseStatement();
            if (stmt != null) {
                stmts.add(stmt);
            }
        }
        return new Block(stmts, lcurly.line);
    }

    private Params parseParamList() throws ParserException {
        List<String> names = new ArrayList<>();
        List<Expr> defaults = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>();
        boolean rest = false;
        if (!peekIs("rparen")) {
            while (true) {
                if (peekIs("spread")) {
                    next();
                    rest = true;
                    // a rest parameter may be a binding pattern: function f(...[a, b])
                    Token t = peek();
                    if (t != null && (t.type.equals("lsqr") || t.type.equals("lcurly"))) {
                        patterns.add(parsePattern(true));
                        names.add(null);
                    } else {
                        Token name = expect("ident");
                        names.add(name.value);
                        patterns.add(null);
                    }
                    defaults.add(null);
                    break; // a rest parameter must be the last one
                }
                Token t = peek();
                if (t != null && (t.type.equals("lsqr") || t.type.equals("lcurly"))) {
                    // destructuring parameter: function f({a}, [b] = [1])
                    patterns.add(parsePattern(true));
                    names.add(null);
                    defaults.add(null); // the pattern carries its own default
                } else {
                    Token name = expect("ident");
                    names.add(name.value);
                    patterns.add(null);
                    if (peekIs("assign")) {
                        next();
                        defaults.add(parseAssignment());
                    } else {
                        defaults.add(null);
                    }
                }
                if (!peekIs("comma")) {
                    break;
                }
                next();
                if (peekIs("rparen")) {
                    break; // trailing comma
                }
            }
        }
        return new Params(names, defaults, rest, patterns);
    }

    private List<Expr> parseParams() throws ParserException {
        List<Expr> args = new ArrayList<>();
        if (!peekIs("rparen")) {
            while (true) {
                if (peekIs("spread")) {
                    Token t = next();
                    args.add(new SpreadExpr(parseAssignment(), t.line));
                } else {
                    args.add(parseAssignment());
                }
                if (!peekIs("comma")) {
                    break;
                }
                next();
                if (peekIs("rparen")) {
                    break; // trailing comma
                }
            }
        }
        return args;
    }

    private Expr parseNullableExpr() throws ParserException {
        Token t = peek();
        if (t != null && (t.type.equals("semicolon") || t.type.equals("rparen"))) {
            return null;
        }
        return parseExpr();
    }

    //----------------------------------------------------------------------
    // expressions (precedence climbing)
    //----------------------------------------------------------------------

    /**
     * The comma operator: {@code Expression := Assignment (',' Assignment)*}.
     * A leading {@code var} declaration turns every operand into a variable
     * declaration ({@code var a, b = 1, c}).
     */
    private Expr parseExpr() throws ParserException {
        Expr first = parseAssignment();
        if (!peekIs("comma")) {
            return first;
        }
        List<Expr> operands = new ArrayList<>();
        operands.add(first);
        int line = first.line;
        while (peekIs("comma")) {
            next();
            operands.add(parseAssignment());
        }
        if (first instanceof VarDeclExpr
                || (first instanceof AssignExpr
                    && ((AssignExpr) first).target instanceof VarDeclExpr)) {
            for (int i = 1; i < operands.size(); i++) {
                Expr o = operands.get(i);
                if (o instanceof VarDeclExpr) {
                    continue;
                }
                if (o instanceof AssignExpr && ((AssignExpr) o).target instanceof VarDeclExpr) {
                    continue;
                }
                if (o instanceof IdentExpr) {
                    operands.set(i, new VarDeclExpr(((IdentExpr) o).name, false, o.line));
                    continue;
                }
                if (o instanceof AssignExpr && ((AssignExpr) o).target instanceof IdentExpr) {
                    AssignExpr a = (AssignExpr) o;
                    operands.set(i, new AssignExpr(
                            new VarDeclExpr(((IdentExpr) a.target).name, false, a.target.line),
                            a.value, a.line, a.op));
                    continue;
                }
                throw new ParserException("invalid variable declaration " + o,
                        new org.ngscript.api.Source(null, o.line));
            }
        }
        return new CommaExpr(operands, line);
    }

    private Expr parseAssignment() throws ParserException {
        Expr left = parseCond();
        Token t = peek();
        if (t != null && ASSIGN_OPS.contains(t.type)) {
            // destructuring assignment: [a, b] = ... / ({x} = ...)
            if (left instanceof ArrayLiteral || left instanceof ObjectLiteral) {
                Pattern pattern = patternFromLiteral(left);
                next();
                // right associative: a = b = c parses as a = (b = c)
                Expr right = parseAssignment();
                return new AssignExpr(new DestructureExpr(pattern, left.line), right, t.line, t.type);
            }
            if (!isAssignableTarget(left)) {
                // ES early error: the assignment target must be a reference
                // (42 = 42 / true = x / f() = y are SyntaxErrors)
                throw new ParserException("invalid assignment target " + left,
                        new org.ngscript.api.Source(null, t.line));
            }
            next();
            // right associative: a = b = c parses as a = (b = c)
            Expr right = parseAssignment();
            return new AssignExpr(left, right, t.line, t.type);
        }
        return left;
    }

    /**
     * Converts an array/object literal used as an assignment target into a
     * destructuring pattern ([a, b] = ... / ({x} = ...)).
     */
    private Pattern patternFromLiteral(Expr literal) throws ParserException {
        if (literal instanceof ArrayLiteral) {
            ArrayLiteral arr = (ArrayLiteral) literal;
            List<Pattern> items = new ArrayList<>();
            Pattern.ArrayP p = new Pattern.ArrayP(items, arr.line);
            for (Expr item : arr.items) {
                if (item instanceof SpreadExpr) {
                    p.rest = patternFromExpr(((SpreadExpr) item).expr);
                } else if (item instanceof LiteralExpr
                        && ((LiteralExpr) item).kind == LiteralExpr.Kind.UNDEFINED) {
                    items.add(null); // hole
                } else {
                    items.add(patternFromExpr(item));
                }
            }
            return p;
        }
        if (literal instanceof ObjectLiteral) {
            ObjectLiteral obj = (ObjectLiteral) literal;
            List<Pattern.Prop> props = new ArrayList<>();
            Pattern.ObjectP op = new Pattern.ObjectP(props, obj.line);
            for (Object item : obj.items) {
                if (item instanceof Kvp) {
                    Kvp kvp = (Kvp) item;
                    if (kvp.keyExpr != null) {
                        // computed key: {[expr]: target}
                        props.add(new Pattern.Prop(kvp.keyExpr, patternFromExpr(kvp.value)));
                    } else {
                        props.add(new Pattern.Prop(kvp.name, patternFromExpr(kvp.value)));
                    }
                } else if (item instanceof SpreadExpr) {
                    // object rest: {a, ...rest} = value
                    op.rest = patternFromExpr(((SpreadExpr) item).expr);
                }
            }
            return op;
        }
        throw new ParserException("invalid destructuring target " + literal,
                new org.ngscript.api.Source(null, literal.line));
    }

    private Pattern patternFromExpr(Expr e) throws ParserException {
        if (e instanceof IdentExpr) {
            return new Pattern.IdentP(((IdentExpr) e).name, e.line);
        }
        if (e instanceof MemberExpr || e instanceof IndexExpr) {
            // [obj.x, arr[0]] = vals: member references are valid
            // assignment-pattern targets
            return new Pattern.RefP(e, e.line);
        }
        if (e instanceof ArrayLiteral || e instanceof ObjectLiteral) {
            return patternFromLiteral(e);
        }
        if (e instanceof AssignExpr) {
            // [a = 1] pattern with a default initializer
            Pattern inner = patternFromExpr(((AssignExpr) e).target);
            inner.defaultValue = ((AssignExpr) e).value;
            return inner;
        }
        throw new ParserException("invalid destructuring target " + e,
                new org.ngscript.api.Source(null, e.line));
    }

    private Expr parseCond() throws ParserException {
        Expr cond = parseNullish();
        if (peekIs("question_mark")) {
            Token t = next();
            Expr thenExpr = parseAssignment();
            expect("colon");
            // right associative: the else branch may be another conditional
            Expr elseExpr = parseAssignment();
            return new CondExpr(cond, thenExpr, elseExpr, t.line);
        }
        return cond;
    }

    private Expr parseNullish() throws ParserException {
        return parseLeftAssoc(Arrays.asList("nullish"), this::parseOr);
    }

    private Expr parseOr() throws ParserException {
        return parseLeftAssoc(Arrays.asList("or"), this::parseAnd);
    }

    private Expr parseAnd() throws ParserException {
        return parseLeftAssoc(Arrays.asList("and"), this::parseBitOr);
    }

    private Expr parseBitOr() throws ParserException {
        return parseLeftAssoc(Arrays.asList("bit_or"), this::parseBitXor);
    }

    private Expr parseBitXor() throws ParserException {
        return parseLeftAssoc(Arrays.asList("bit_xor"), this::parseBitAnd);
    }

    private Expr parseBitAnd() throws ParserException {
        return parseLeftAssoc(Arrays.asList("bit_and"), this::parseEq);
    }

    private Expr parseEq() throws ParserException {
        return parseLeftAssoc(Arrays.asList("eq", "veq", "neq", "vneq"), this::parseRel);
    }

    private Expr parseRel() throws ParserException {
        Expr left = parseShift();
        while (true) {
            Token t = peek();
            if (t == null) {
                return left;
            }
            BinaryExpr.Op op;
            switch (t.type) {
                case "gt": op = BinaryExpr.Op.GT; break;
                case "lt": op = BinaryExpr.Op.LT; break;
                case "ge": op = BinaryExpr.Op.GE; break;
                case "le": op = BinaryExpr.Op.LE; break;
                case "instanceof": op = BinaryExpr.Op.INSTANCEOF; break;
                case "in": op = BinaryExpr.Op.IN; break;
                default:
                    return left;
            }
            next();
            Expr right = parseShift();
            left = new BinaryExpr(op, left, right, t.line);
        }
    }

    private Expr parseShift() throws ParserException {
        return parseLeftAssoc(Arrays.asList("shl", "shr", "ushr"), this::parseAdd);
    }

    private Expr parseAdd() throws ParserException {
        return parseLeftAssoc(Arrays.asList("add", "sub"), this::parseMul);
    }

    private Expr parseMul() throws ParserException {
        return parseLeftAssoc(Arrays.asList("mul", "div", "mod"), this::parseExp);
    }

    /**
     * Exponentiation, right associative: {@code a ** b ** c} is {@code a ** (b ** c)}.
     */
    private Expr parseExp() throws ParserException {
        // ES: the exponentiation operator's left operand must not start with
        // a unary operator (-2 ** 2 is a SyntaxError; 2 ** -2 and (-2) ** 2
        // are fine)
        Token t0 = peek();
        boolean unaryStart = t0 != null && (t0.type.equals("sub") || t0.type.equals("add")
                || t0.type.equals("not") || t0.type.equals("bit_not")
                || t0.type.equals("typeof") || t0.type.equals("void")
                || t0.type.equals("delete") || t0.type.equals("inc") || t0.type.equals("dec"));
        Expr base = parseUnary();
        if (peekIs("exp")) {
            if (unaryStart) {
                throw new ParserException(
                        "unary expression cannot be the left operand of **",
                        new org.ngscript.api.Source(null, t0.line));
            }
            Token t = next();
            Expr right = parseExp();
            return new BinaryExpr(BinaryExpr.Op.EXP, base, right, t.line);
        }
        return base;
    }

    private Expr parseLeftAssoc(List<String> operatorTypes, ExprSupplier operand)
            throws ParserException {
        Expr left = operand.get();
        while (true) {
            Token t = peek();
            if (t == null || !operatorTypes.contains(t.type)) {
                return left;
            }
            next();
            Expr right = operand.get();
            left = new BinaryExpr(BINARY_OP_BY_TOKEN.get(t.type), left, right, t.line);
        }
    }

    private Expr parseUnary() throws ParserException {
        Token t = peek();
        if (t == null) {
            throw new ParserException("unexpected end of input");
        }
        switch (t.type) {
            case "sub":
                next();
                return new UnaryExpr(UnaryExpr.Op.NEG, parseUnary(), t.line);
            case "add":
                next();
                return new UnaryExpr(UnaryExpr.Op.PLUS, parseUnary(), t.line);
            case "not":
                next();
                return new UnaryExpr(UnaryExpr.Op.NOT, parseUnary(), t.line);
            case "bit_not":
                next();
                return new UnaryExpr(UnaryExpr.Op.BIT_NOT, parseUnary(), t.line);
            case "typeof":
                next();
                return new UnaryExpr(UnaryExpr.Op.TYPEOF, parseUnary(), t.line);
            case "delete":
                next();
                return new UnaryExpr(UnaryExpr.Op.DELETE, parseUnary(), t.line);
            case "void":
                next();
                return new UnaryExpr(UnaryExpr.Op.VOID, parseUnary(), t.line);
            case "inc":
                next();
                return prefixUpdate(UnaryExpr.Op.INC, t);
            case "dec":
                next();
                return prefixUpdate(UnaryExpr.Op.DEC, t);
            default: {
                Expr operand = parsePostfix(true);
                Token post = peek();
                // postfix ++/-- is a restricted production: a line terminator
                // before the operator makes it a prefix operator of the next
                // statement instead (a\n++b is a; ++b)
                if (post != null && post.type.equals("inc") && post.line <= lastTokenLine) {
                    next();
                    return postfixUpdate(UnaryExpr.Op.POST_INC, operand, post);
                }
                if (post != null && post.type.equals("dec") && post.line <= lastTokenLine) {
                    next();
                    return postfixUpdate(UnaryExpr.Op.POST_DEC, operand, post);
                }
                return operand;
            }
        }
    }

    private Expr prefixUpdate(UnaryExpr.Op op, Token t) throws ParserException {
        Expr operand = parseUnary();
        if (!isAssignableTarget(operand)) {
            throw new ParserException("invalid target for " + op + " operator: " + operand,
                    new org.ngscript.api.Source(null, t.line));
        }
        return new UnaryExpr(op, operand, t.line);
    }

    private Expr postfixUpdate(UnaryExpr.Op op, Expr operand, Token t) throws ParserException {
        if (!isAssignableTarget(operand)) {
            throw new ParserException("invalid target for " + op + " operator: " + operand,
                    new org.ngscript.api.Source(null, t.line));
        }
        return new UnaryExpr(op, operand, t.line);
    }

    /**
     * Whether an expression may be the target of an assignment or update
     * (an identifier, a member access or an index access).
     */
    private static boolean isAssignableTarget(Expr e) {
        return e instanceof IdentExpr || e instanceof MemberExpr || e instanceof IndexExpr
                || e instanceof SuperMemberExpr || e instanceof VarDeclExpr;
    }

    /**
     * Parses a postfix chain (member access, call, index). When {@code allowCall} is false,
     * call parentheses are not consumed (used by the {@code go} statement where the call
     * argument list is parsed by the statement itself).
     */
    private Expr parsePostfix(boolean allowCall) throws ParserException {
        Expr expr = parsePrimary();
        while (true) {
            Token t = peek();
            if (t == null) {
                return expr;
            }
            switch (t.type) {
                case "dot": {
                    next();
                    // ES allows keywords as property names: o.if, o.class ...
                    Token name = peek();
                    if (name == null || !isKeyName(name.type)) {
                        throw new ParserException("expecting a member name but got "
                                + (name == null ? "end of input" : name),
                                new org.ngscript.api.Source(null, t.line));
                    }
                    next();
                    expr = new MemberExpr(expr, tokenName(name), t.line);
                    break;
                }
                case "optional_chain": {
                    // optional chaining: a?.b, a?.[i], a?.(args). The chain
                    // keeps ordinary Member/Call/Index wrappers; the
                    // compiler flattens everything into one short-circuit
                    // chain (a?.b.c skips the whole tail when a is nullish)
                    next();
                    Token nx = peek();
                    if (nx == null) {
                        throw new ParserException("unexpected end of input after ?.",
                                new org.ngscript.api.Source(null, t.line));
                    }
                    if (nx.type.equals("lsqr")) {
                        next();
                        Expr index = parseExpr();
                        expect("rsqr");
                        expr = new OptionalChainExpr.Index(expr, index, t.line);
                        break;
                    }
                    if (nx.type.equals("lparen")) {
                        next();
                        List<Expr> args = parseParams();
                        expect("rparen");
                        expr = new OptionalChainExpr.Call(expr, args, t.line);
                        break;
                    }
                    if (!isKeyName(nx.type)) {
                        throw new ParserException("expecting a member name after ?. but got "
                                + nx, new org.ngscript.api.Source(null, t.line));
                    }
                    next();
                    expr = new OptionalChainExpr.Member(expr, tokenName(nx), t.line);
                    break;
                }
                case "lparen":
                    if (!allowCall) {
                        return expr;
                    }
                    next();
                    List<Expr> args = parseParams();
                    expect("rparen");
                    expr = new CallExpr(expr, args, t.line);
                    break;
                case "template_start":
                case "template_start_end": {
                    // a tagged template: tag`...` (start_end = no substitutions)
                    next();
                    TemplateExpr tpl = parseTemplateRest(t);
                    expr = new TaggedTemplateExpr(expr, tpl, t.line);
                    break;
                }
                case "lsqr": {
                    next();
                    Expr index = parseExpr();
                    expect("rsqr");
                    expr = new IndexExpr(expr, index, t.line);
                    break;
                }
                default:
                    return expr;
            }
        }
    }

    private Expr parsePrimary() throws ParserException {
        Token t = peek();
        if (t == null) {
            throw new ParserException("unexpected end of input");
        }
        if (t.type.equals("ident") && t.value.equals("async")) {
            // async function expressions and async arrows: async function
            // name? (...) / async (...) => / async x =>
            Token second = peekN(2);
            if (second != null && second.type.equals("function") && second.line == t.line) {
                return parseAsyncFunctionExpr(t);
            }
            if (second != null && second.type.equals("lparen") && second.line == t.line
                    && probeArrow(3)) {
                next(); // async
                next(); // lparen
                Params params = parseParamList();
                expect("rparen");
                expect("arrow_fn");
                return parseAsyncArrowRest(params, t.line);
            }
            if (second != null && second.type.equals("ident") && second.line == t.line) {
                Token third = peekN(3);
                if (third != null && third.type.equals("arrow_fn") && third.line == second.line) {
                    next(); // async
                    next(); // ident
                    next(); // =>
                    return parseAsyncArrowRest(
                            new Params(Collections.singletonList(second.value)), t.line);
                }
            }
        }
        if (t.type.equals("new")) {
            return parseNew(t);
        }
        if (t.type.equals("lparen")) {
            return parseParenOrArrow(t);
        }
        if (t.type.equals("template_start") || t.type.equals("template_start_end")) {
            // an untagged template literal (template_start_end = no substitutions)
            next();
            return parseTemplateRest(t);
        }
        return parseExpr9();
    }

    /**
     * Parses the rest of a template literal after the first chunk token:
     * substitution expressions interleaved with template chunk tokens.
     */
    private TemplateExpr parseTemplateRest(Token first) throws ParserException {
        List<String> chunks = new ArrayList<>();
        List<String> raws = new ArrayList<>();
        List<Expr> exprs = new ArrayList<>();
        chunks.add(first.value);
        raws.add(first.raw);
        if (first.type.equals("template_start_end")) {
            // a template with no substitutions
            return new TemplateExpr(chunks, raws, exprs, first.line);
        }
        while (true) {
            Expr e = parseExpr();
            exprs.add(e);
            Token t = peek();
            if (t == null) {
                throw new ParserException("unexpected end of input in template literal");
            }
            if (t.type.equals("template_end")) {
                next();
                chunks.add(t.value);
                raws.add(t.raw);
                break;
            }
            if (t.type.equals("template")) {
                next();
                chunks.add(t.value);
                raws.add(t.raw);
                continue;
            }
            throw new ParserException("expecting template continuation but got " + t,
                    new org.ngscript.api.Source(null, t.line));
        }
        return new TemplateExpr(chunks, raws, exprs, first.line);
    }

    private Expr parseNew(Token newTok) throws ParserException {
        next();
        if (peekIs("dot")) {
            // new.target: the constructor of the innermost constructor call
            // (undefined in plain calls, field initializers, and top level)
            Token dot = peekN(2);
            if (dot != null && dot.type.equals("ident") && "target".equals(dot.value)) {
                next(); // .
                next(); // target
                return new NewTargetExpr(newTok.line);
            }
        }
        Expr cls;
        if (peekIs("lparen")) {
            // new (expr) / new (() => {}): parseParenOrArrow expects the
            // lparen to still be the current token
            cls = parseParenOrArrow(peek());
        } else {
            cls = parseExprClass();
        }
        List<Expr> args = Collections.emptyList();
        if (peekIs("lparen")) {
            next();
            args = parseParams();
            expect("rparen");
        }
        return new NewExpr(cls, args, newTok.line);
    }

    private Expr parseExprClass() throws ParserException {
        Expr expr = parseExpr9();
        while (peekIs("dot")) {
            Token dot = next();
            Token name = expect("ident");
            expr = new MemberExpr(expr, name.value, dot.line);
        }
        return expr;
    }

    private Expr parseParenOrArrow(Token lparen) throws ParserException {
        Token second = peekN(2);
        if (second == null) {
            throw new ParserException("unexpected end of input");
        }
        if (second.type.equals("rparen") || second.type.equals("ident")
                || second.type.equals("lsqr") || second.type.equals("lcurly")) {
            // probe for an arrow function parameter list: scan to the
            // matching closing paren (tracking nested parens so default
            // parameter expressions are skipped) and check for =>
            // (pattern parameters: ({a}) => a, ([a, b]) => a + b)
            if (probeArrow(2)) {
                next();
                Params params = parseParamList();
                expect("rparen");
                expect("arrow_fn");
                return parseArrowRest(params, lparen.line);
            }
        }
        // plain parenthesized expression
        next();
        Expr expr = parseExpr();
        expect("rparen");
        return expr;
    }

    /**
     * Looks ahead from the current lparen position for a matching rparen
     * followed by an arrow token on the same line. {@code n} is the lookahead
     * offset of the first token inside the parens.
     */
    private boolean probeArrow(int n) {
        int depth = 1;
        while (true) {
            Token t = peekN(n);
            if (t == null) {
                return false;
            }
            if (t.type.equals("lparen")) {
                ++depth;
            } else if (t.type.equals("rparen")) {
                --depth;
                if (depth == 0) {
                    Token nx = peekN(n + 1);
                    return nx != null && nx.type.equals("arrow_fn") && nx.line == t.line;
                }
            }
            ++n;
        }
    }

    /**
     * Parses the body of an arrow function after the arrow token: either a
     * block body or a single assignment expression. ES disallows a line
     * terminator between the arrow and its body; that check happens where
     * the arrow token is matched.
     */
    private Expr parseArrowRest(Params params, int line) throws ParserException {
        ++functionDepth;
        // arrows lexically inherit the enclosing super context (ES: an arrow
        // inside a method may use super); only plain functions reset it
        try {
            if (peekIs("lcurly")) {
                Block body = parseFunctionBody();
                LambdaExpr a = new LambdaExpr(params, null, body, line, true);
                a.strict = pendingFunctionStrict;
                return a;
            }
            Expr body = parseAssignment();
            LambdaExpr ae = new LambdaExpr(params, body, null, line, true);
            ae.strict = strictMode;
            return ae;
        } finally {
            --functionDepth;
        }
    }

    /** Parses an async function expression: async function name?(...). */
    private Expr parseAsyncFunctionExpr(Token asyncTok) throws ParserException {
        next(); // async
        expect("function");
        boolean generator = false;
        if (peekIs("mul")) {
            next();
            generator = true;
        }
        String name = null;
        if (peekIs("ident")) {
            name = next().value;
        }
        expect("lparen");
        Params params = parseParamList();
        expect("rparen");
        ++functionDepth;
        int savedGen = generatorDepth;
        int savedAsync = asyncDepth;
        int savedSuper = superDepth;
        generatorDepth = 0;
        ++asyncDepth;
        superDepth = 0;
        try {
            Block body = parseFunctionBody();
            LambdaExpr lambda = new LambdaExpr(name, params, body, asyncTok.line);
            lambda.generator = generator;
            lambda.async = true;
            lambda.strict = pendingFunctionStrict;
            return lambda;
        } finally {
            --functionDepth;
            generatorDepth = savedGen;
            asyncDepth = savedAsync;
            superDepth = savedSuper;
        }
    }

    /** Parses the body of an async arrow function after the arrow token. */
    private Expr parseAsyncArrowRest(Params params, int line) throws ParserException {
        ++functionDepth;
        int savedGen = generatorDepth;
        int savedAsync = asyncDepth;
        // async arrows lexically inherit the enclosing super context
        generatorDepth = 0;
        ++asyncDepth;
        try {
            LambdaExpr lambda;
            if (peekIs("lcurly")) {
                Block body = parseFunctionBody();
                lambda = new LambdaExpr(params, null, body, line, true);
            } else {
                Expr body = parseAssignment();
                lambda = new LambdaExpr(params, body, null, line, true);
            }
            lambda.async = true;
            return lambda;
        } finally {
            --functionDepth;
            generatorDepth = savedGen;
            asyncDepth = savedAsync;
        }
    }

    private Expr parseExpr9() throws ParserException {
        Token t = peek();
        if (t == null) {
            throw new ParserException("unexpected end of input");
        }
        switch (t.type) {
            case "new":
                // nested member expressions like "new new String"
                return parseNew(t);
            case "var": {
                next();
                Token t2 = peek();
                if (t2 != null && (t2.type.equals("lsqr") || t2.type.equals("lcurly"))) {
                    // destructuring declaration: var [a, b] = arr;
                    return new VarDeclExpr(null, parsePattern(false), false, t2.line);
                }
                Token name = expect("ident");
                return new VarDeclExpr(name.value, false, name.line);
            }
            case "ident": {
                // single-parameter arrow function without parens: ident =>
                // (a line terminator between them turns it back into a plain
                // identifier, leaving => as an error further up)
                Token second = peekN(2);
                if (second != null && second.type.equals("arrow_fn") && second.line == t.line) {
                    next();
                    next();
                    return parseArrowRest(
                            new Params(Collections.singletonList(t.value)), t.line);
                }
                if (t.value.equals("yield") && generatorDepth > 0) {
                    // contextual yield (generators): the operand is optional
                    // after a line terminator or before a token that cannot
                    // start an expression
                    Token nx = peekN(2);
                    boolean none = nx == null || atLineBreakAfter(t.line)
                            || nx.type.equals("semicolon") || nx.type.equals("rcurly")
                            || nx.type.equals("rparen") || nx.type.equals("rsqr")
                            || nx.type.equals("comma") || nx.type.equals("colon");
                    if (none) {
                        next();
                        return new YieldExpr(null, false, t.line);
                    }
                    boolean delegate = nx.type.equals("mul");
                    next();
                    if (delegate) {
                        next();
                        return new YieldExpr(parseAssignment(), true, t.line);
                    }
                    return new YieldExpr(parseAssignment(), false, t.line);
                }
                if (t.value.equals("await") && asyncDepth > 0) {
                    // contextual await (async functions): await expr
                    next();
                    return new AwaitExpr(parseAssignment(), t.line);
                }
                next();
                if (t.value.equals("val") && peek() != null && peek().type.equals("ident")) {
                    // the ngscript read-only declaration extension: "val x"
                    // (val is a plain identifier in ES, so this only applies
                    // when followed by a name)
                    Token name = next();
                    return new VarDeclExpr(name.value, true, t.line);
                }
                return new IdentExpr(t.value, t.line);
            }
            case "undefined":
                next();
                return new LiteralExpr(LiteralExpr.Kind.UNDEFINED, null, t.line);
            case "double":
                next();
                return new LiteralExpr(LiteralExpr.Kind.DOUBLE, t.value, t.line);
            case "null":
                next();
                return new LiteralExpr(LiteralExpr.Kind.NULL, null, t.line);
            case "true":
                next();
                return new LiteralExpr(LiteralExpr.Kind.TRUE, null, t.line);
            case "false":
                next();
                return new LiteralExpr(LiteralExpr.Kind.FALSE, null, t.line);
            case "integer":
                next();
                return new LiteralExpr(LiteralExpr.Kind.INTEGER, t.value, t.line);
            case "bigint":
                next();
                return new LiteralExpr(LiteralExpr.Kind.BIGINT, t.value, t.line);
            case "string":
                next();
                LiteralExpr str = new LiteralExpr(LiteralExpr.Kind.STRING, t.value, t.line);
                str.hasEscape = t.hasEscape;
                return str;
            case "regex":
                next();
                return new RegExpLiteral(t.value, t.line);
            case "function": {
                next();
                boolean generator = false;
                if (peekIs("mul")) {
                    next();
                    generator = true;
                }
                String name = null;
                if (peekIs("ident")) {
                    name = next().value;
                }
                expect("lparen");
                Params params = parseParamList();
                expect("rparen");
                ++functionDepth;
                int savedGen = generatorDepth;
                int savedSuper = superDepth;
                if (generator) {
                    ++generatorDepth;
                } else {
                    generatorDepth = 0;
                }
                superDepth = 0;
                try {
                    Block body = parseFunctionBody();
                    LambdaExpr lambda = new LambdaExpr(name, params, body, t.line);
                    lambda.generator = generator;
                    lambda.strict = pendingFunctionStrict;
                    return lambda;
                } finally {
                    --functionDepth;
                    generatorDepth = savedGen;
                    superDepth = savedSuper;
                }
            }
            case "class": {
                // a class expression: anonymous or named
                next();
                String name = null;
                if (peekIs("ident")) {
                    name = next().value;
                }
                return parseClassTail(name, t);
            }
            case "super": {
                next();
                if (superDepth == 0) {
                    // super is only allowed directly inside a method body
                    // (class or object literal); anywhere else is an early
                    // SyntaxError, including nested plain functions/arrows
                    throw new ParserException("invalid use of super",
                            new org.ngscript.api.Source(null, t.line));
                }
                if (peekIs("lparen")) {
                    next();
                    List<Expr> args = parseParams();
                    expect("rparen");
                    return new SuperCallExpr(args, t.line);
                }
                if (peekIs("dot")) {
                    next();
                    Token name = expect("ident");
                    return new SuperMemberExpr(name.value, t.line);
                }
                if (peekIs("lsqr")) {
                    // super[expr]: the computed-key form of super.member
                    next();
                    Expr key = parseExpr();
                    expect("rsqr");
                    return new SuperMemberExpr(null, key, t.line);
                }
                throw new ParserException("invalid use of super",
                        new org.ngscript.api.Source(null, t.line));
            }
            case "import": {
                // dynamic import: import( AssignmentExpression ,opt ) at an
                // expression position (the statement-level `import Java包`
                // extension is handled in parseStatement); the optional
                // second argument is the ES import-attributes proposal
                Token it = peek();
                Token nx = peekN(2);
                if (nx != null && nx.type.equals("lparen") && nx.line == it.line) {
                    next(); // import
                    next(); // (
                    java.util.List<Expr> args = new java.util.ArrayList<>();
                    args.add(parseAssignment());
                    if (peekIs("comma")) {
                        next();
                        if (!peekIs("rparen")) {
                            args.add(parseAssignment());
                        }
                    }
                    expect("rparen");
                    return new ImportCallExpr(args, t.line);
                }
                throw new ParserException(
                        "import is only supported as import(...) or a statement",
                        new org.ngscript.api.Source(null, t.line));
            }
            case "lsqr":
                return parseArrayLiteral(t);
            case "lcurly":
                return parseObjectLiteral(t);
            default:
                throw new ParserException("unexpected token " + t);
        }
    }

    private Expr parseArrayLiteral(Token lsqr) throws ParserException {
        next();
        List<Expr> items = new ArrayList<>();
        while (!peekIs("rsqr")) {
            if (peekIs("comma")) {
                // an elision: the hole is approximated by undefined
                items.add(new LiteralExpr(LiteralExpr.Kind.UNDEFINED, null, lsqr.line));
            } else if (peekIs("spread")) {
                Token t = next();
                items.add(new SpreadExpr(parseAssignment(), t.line));
            } else {
                items.add(parseAssignment());
            }
            if (peekIs("comma")) {
                next();
            } else {
                break;
            }
        }
        expect("rsqr");
        return new ArrayLiteral(items, lsqr.line);
    }

    private Expr parseObjectLiteral(Token lcurly) throws ParserException {
        next();
        List<Object> items = new ArrayList<>();
        while (!peekIs("rcurly")) {
            if (peekIs("spread")) {
                // object spread: {...expr}
                Token s = next();
                items.add(new SpreadExpr(parseAssignment(), s.line));
            } else {
                items.add(parseKvp());
            }
            if (peekIs("comma")) {
                next();
            } else {
                break;
            }
        }
        expect("rcurly");
        return new ObjectLiteral(items, lcurly.line);
    }

    private Kvp parseKvp() throws ParserException {
        Token name = peek();
        // async methods: { async m() {} } / { async *m() {} } (the async
        // keyword is only a method modifier when a method follows)
        if (name != null && name.type.equals("ident") && "async".equals(name.value)) {
            Token second = peekN(2);
            boolean star = second != null && second.type.equals("mul");
            Token mname = star ? peekN(3) : second;
            boolean mnameOk = mname != null
                    && (isKeyName(mname.type) || mname.type.equals("string")
                            || mname.type.equals("integer") || mname.type.equals("double"));
            Token after = mnameOk ? peekN(star ? 4 : 3) : null;
            if (after != null && after.type.equals("lparen")) {
                // { async m() {} } / { async *m() {} }
                next(); // async
                if (star) {
                    next(); // *
                }
                Token key = next(); // method name
                expect("lparen");
                Params params = parseParamList();
                expect("rparen");
                ++functionDepth;
                int savedGen = generatorDepth;
                int savedAsync = asyncDepth;
                int savedSuper = superDepth;
                if (star) {
                    ++generatorDepth;
                }
                asyncDepth = 1;
                ++superDepth;
                try {
                    Block body = parseFunctionBody();
                    LambdaExpr lambda = new LambdaExpr(
                            key == null ? null : tokenName(key), params, body, name.line);
                    lambda.async = true;
                    lambda.generator = star;
                    lambda.strict = pendingFunctionStrict;
                    return new Kvp(tokenName(key), lambda, name.line);
                } finally {
                    --functionDepth;
                    generatorDepth = savedGen;
                    asyncDepth = savedAsync;
                    superDepth = savedSuper;
                }
            }
        }
        // computed property names: { [expr]: value } / { [expr]() {} }
        if (name != null && name.type.equals("lsqr")) {
            next();
            Expr key = parseExpr();
            expect("rsqr");
            if (peekIs("lparen")) {
                // computed-key method shorthand
                expect("lparen");
                Params params = parseParamList();
                expect("rparen");
                ++functionDepth;
                int savedSuper = superDepth;
                ++superDepth;
                try {
                    Block body = parseFunctionBody();
                    LambdaExpr cm = new LambdaExpr(null, params, body, name.line);
                    cm.strict = pendingFunctionStrict;
                    return new Kvp(key, cm, name.line);
                } finally {
                    --functionDepth;
                    superDepth = savedSuper;
                }
            }
            expect("colon");
            Expr value = parseAssignment();
            return new Kvp(key, value, name.line);
        }
        // generator method shorthand: { *m() {} }
        if (name != null && name.type.equals("mul")) {
            Token star = next();
            Token mname = peek();
            if (mname == null || !isKeyName(mname.type)) {
                throw new ParserException("expecting a method name but got "
                        + (mname == null ? "end of input" : mname),
                        new org.ngscript.api.Source(null, star.line));
            }
            next();
            expect("lparen");
            Params params = parseParamList();
            expect("rparen");
            ++functionDepth;
            int savedGen = generatorDepth;
            int savedSuper = superDepth;
            ++generatorDepth;
            ++superDepth;
            try {
                Block body = parseFunctionBody();
                LambdaExpr lambda = new LambdaExpr(tokenName(mname), params, body, star.line);
                lambda.generator = true;
                lambda.strict = pendingFunctionStrict;
                return new Kvp(tokenName(mname), lambda, star.line);
            } finally {
                --functionDepth;
                generatorDepth = savedGen;
                superDepth = savedSuper;
            }
        }
        // shorthand and method shorthand: { a } / { a, } / { m() {} } / { m(x) {} }
        if (name != null && isKeyName(name.type)) {
            Token second = peekN(2);
            if (second != null && second.type.equals("lparen")) {
                // method shorthand
                next();
                expect("lparen");
                Params params = parseParamList();
                expect("rparen");
                ++functionDepth;
                int savedSuper = superDepth;
                ++superDepth;
                try {
                    Block body = parseFunctionBody();
                    String mname = tokenName(name);
                    LambdaExpr ml = new LambdaExpr(mname, params, body, name.line);
                    ml.strict = pendingFunctionStrict;
                    return new Kvp(mname, ml, name.line);
                } finally {
                    --functionDepth;
                    superDepth = savedSuper;
                }
            }
            if (second != null && (second.type.equals("comma") || second.type.equals("rcurly")
                    || second.type.equals("assign"))) {
                // shorthand property: { a } means { a: a }; { a = 1 } is a
                // shorthand with a destructuring default (valid only when
                // the literal becomes an assignment pattern, but parsed
                // here and converted by patternFromLiteral)
                next();
                String sname = tokenName(name);
                if (peekIs("assign")) {
                    next();
                    Expr init = parseAssignment();
                    return new Kvp(sname,
                            new AssignExpr(new IdentExpr(sname, name.line),
                                    init, name.line, "="),
                            name.line);
                }
                return new Kvp(sname, new IdentExpr(sname, name.line), name.line);
            }
        }
        // accessor properties: { get x() {...} } / { set x(v) {...} }
        if (name != null && name.type.equals("ident")
                && (name.value.equals("get") || name.value.equals("set"))) {
            Token second = peekN(2);
            Token third = peekN(3);
            if (second != null && (isKeyName(second.type)
                    || second.type.equals("integer") || second.type.equals("double"))
                    && third != null && third.type.equals("lparen")) {
                next(); // get/set
                Token key = next();
                expect("lparen");
                Params params = parseParamList();
                expect("rparen");
                ++functionDepth;
                int savedSuper = superDepth;
                ++superDepth;
                try {
                    Block body = parseFunctionBody();
                    LambdaExpr gv = new LambdaExpr(name.value, params, body, name.line);
                    gv.strict = pendingFunctionStrict;
                    return new Kvp(tokenName(key), gv,
                            name.line, name.value);
                } finally {
                    --functionDepth;
                    superDepth = savedSuper;
                }
            }
        }
        if (name == null || !(isKeyName(name.type) || name.type.equals("string")
                || name.type.equals("integer") || name.type.equals("double"))) {
            throw new ParserException("expecting object key but got " + (name == null ? "end of input" : name));
        }
        next();
        expect("colon");
        Expr value = parseAssignment();
        return new Kvp(tokenName(name), value, name.line);
    }

    /**
     * The name text of a token usable as a property/method name: identifiers
     * carry their text in {@code value}, keyword tokens use their type (the
     * keyword itself, e.g. "if").
     */
    private static String tokenName(Token t) {
        if (t.type.equals("double")) {
            // ToPropertyKey canonicalizes numeric keys: 1E+9 -> "1000000000",
            // 0.5e1 -> "5" (class accessor/method/field names, object keys)
            return org.ngscript.runtime.OpUtils.toStringValue(Double.parseDouble(t.value));
        }
        return t.value != null ? t.value : t.type;
    }

    /**
     * Whether a token type may be an object property name (identifier or any
     * keyword, per ES IdentifierName).
     */
    private static boolean isKeyName(String type) {
        switch (type) {
            case "ident":
            case "private_ident":
            case "true": case "false": case "null": case "undefined":
            case "var": case "function": case "new": case "if": case "return":
            case "break": case "continue": case "while": case "switch": case "case":
            case "default": case "typeof": case "try": case "catch": case "finally":
            case "go": case "throw": case "for": case "else": case "do":
            case "in": case "instanceof": case "delete": case "void": case "with":
            case "class": case "const": case "enum": case "export": case "extends":
            case "super": case "import":
                return true;
            default:
                return false;
        }
    }
}
