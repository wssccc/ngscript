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
import org.ngscript.ast.BinaryExpr;
import org.ngscript.ast.Block;
import org.ngscript.ast.BlockStmt;
import org.ngscript.ast.BreakStmt;
import org.ngscript.ast.CallExpr;
import org.ngscript.ast.CommaExpr;
import org.ngscript.ast.CondExpr;
import org.ngscript.ast.ContinueStmt;
import org.ngscript.ast.DoWhileStmt;
import org.ngscript.ast.Expr;
import org.ngscript.ast.ExprStmt;
import org.ngscript.ast.ForInStmt;
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
import org.ngscript.ast.LiteralExpr;
import org.ngscript.ast.MemberExpr;
import org.ngscript.ast.NewExpr;
import org.ngscript.ast.ObjectLiteral;
import org.ngscript.ast.Params;
import org.ngscript.ast.Program;
import org.ngscript.ast.RegExpLiteral;
import org.ngscript.ast.ReturnStmt;
import org.ngscript.ast.Stmt;
import org.ngscript.ast.SwitchCase;
import org.ngscript.ast.SwitchStmt;
import org.ngscript.ast.ThrowStmt;
import org.ngscript.ast.TryStmt;
import org.ngscript.ast.UnaryExpr;
import org.ngscript.ast.VarDeclExpr;
import org.ngscript.ast.WhileStmt;
import org.ngscript.ast.WithStmt;
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
        Token t = peek();
        if (t == null) {
            return null;
        }
        switch (t.type) {
            case "semicolon":
                next();
                return null;
            case "function": {
                // function name(...) is a declaration, function(...) is a lambda expression
                Token second = peekN(2);
                if (second != null && second.type.equals("ident")) {
                    return parseFunctionDecl();
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
                return parseImport();
            case "go":
                return parseGo();
            default:
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
        Token name = expect("ident");
        expect("lparen");
        Params params = parseParamList();
        expect("rparen");
        ++functionDepth;
        try {
            Block body = parseBlock();
            return new FunctionDecl(name.value, params, body, fn.line);
        } finally {
            --functionDepth;
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
        return new ExprStmt(expr, expr.line);
    }

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
        if (!peekIs("rparen")) {
            names.add(expect("ident").value);
            while (peekIs("comma")) {
                next();
                if (peekIs("rparen")) {
                    break; // trailing comma
                }
                names.add(expect("ident").value);
            }
        }
        return new Params(names);
    }

    private List<Expr> parseParams() throws ParserException {
        List<Expr> args = new ArrayList<>();
        if (!peekIs("rparen")) {
            args.add(parseAssignment());
            while (peekIs("comma")) {
                next();
                if (peekIs("rparen")) {
                    break; // trailing comma
                }
                args.add(parseAssignment());
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
        if (first instanceof VarDeclExpr) {
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
            next();
            // right associative: a = b = c parses as a = (b = c)
            Expr right = parseAssignment();
            return new AssignExpr(left, right, t.line, t.type);
        }
        return left;
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
        Expr base = parseUnary();
        if (peekIs("exp")) {
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
        return e instanceof IdentExpr || e instanceof MemberExpr || e instanceof IndexExpr;
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
                    Token name = expect("ident");
                    expr = new MemberExpr(expr, name.value, t.line);
                    break;
                }
                case "optional_chain":
                    throw new ParserException("optional chaining is not supported yet",
                            new org.ngscript.api.Source(null, t.line));
                case "lparen":
                    if (!allowCall) {
                        return expr;
                    }
                    next();
                    List<Expr> args = parseParams();
                    expect("rparen");
                    expr = new CallExpr(expr, args, t.line);
                    break;
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
        if (t.type.equals("new")) {
            return parseNew(t);
        }
        if (t.type.equals("lparen")) {
            return parseParenOrArrow(t);
        }
        return parseExpr9();
    }

    private Expr parseNew(Token newTok) throws ParserException {
        next();
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
        if (second.type.equals("rparen")) {
            // () => ...
            Token third = peekN(3);
            if (third != null && third.type.equals("arrow_fn") && third.line == second.line) {
                next();
                next();
                next();
                return parseArrowRest(new Params(Collections.emptyList()), lparen.line);
            }
        } else if (second.type.equals("ident")) {
            Token third = peekN(3);
            if (third != null && third.type.equals("rparen")) {
                // (x) => ...
                Token fourth = peekN(4);
                if (fourth != null && fourth.type.equals("arrow_fn") && fourth.line == third.line) {
                    next();
                    next();
                    next();
                    next();
                    return parseArrowRest(
                            new Params(Collections.singletonList(second.value)), lparen.line);
                }
            }
            if (third != null && third.type.equals("comma")) {
                // probe for the arrow form (x, y, ...) =>: only treat the
                // parenthesised list as parameters when the closing paren is
                // followed by => (otherwise it is a comma expression)
                boolean arrow = false;
                int n = 3;
                while (true) {
                    Token tt = peekN(n);
                    if (tt == null) {
                        break;
                    }
                    if (tt.type.equals("comma")) {
                        n += 2;
                        continue;
                    }
                    if (tt.type.equals("rparen")) {
                        Token nx = peekN(n + 1);
                        if (nx != null && nx.type.equals("arrow_fn") && nx.line == tt.line) {
                            arrow = true;
                        }
                    }
                    break;
                }
                if (arrow) {
                    next();
                    Params params = parseParamList();
                    expect("rparen");
                    expect("arrow_fn");
                    return parseArrowRest(params, lparen.line);
                }
            }
        }
        // plain parenthesized expression
        next();
        Expr expr = parseExpr();
        expect("rparen");
        return expr;
    }

    /**
     * Parses the body of an arrow function after the arrow token: either a
     * block body or a single assignment expression. ES disallows a line
     * terminator between the arrow and its body; that check happens where
     * the arrow token is matched.
     */
    private Expr parseArrowRest(Params params, int line) throws ParserException {
        ++functionDepth;
        try {
            if (peekIs("lcurly")) {
                Block body = parseBlock();
                return new LambdaExpr(params, null, body, line, true);
            }
            Expr body = parseAssignment();
            return new LambdaExpr(params, body, null, line, true);
        } finally {
            --functionDepth;
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
            case "string":
                next();
                return new LiteralExpr(LiteralExpr.Kind.STRING, t.value, t.line);
            case "regex":
                next();
                return new RegExpLiteral(t.value, t.line);
            case "function": {
                next();
                String name = null;
                if (peekIs("ident")) {
                    name = next().value;
                }
                expect("lparen");
                Params params = parseParamList();
                expect("rparen");
                ++functionDepth;
                try {
                    Block body = parseBlock();
                    return new LambdaExpr(name, params, body, t.line);
                } finally {
                    --functionDepth;
                }
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
        List<Kvp> kvps = new ArrayList<>();
        while (!peekIs("rcurly")) {
            kvps.add(parseKvp());
            if (peekIs("comma")) {
                next();
            } else {
                break;
            }
        }
        expect("rcurly");
        return new ObjectLiteral(kvps, lcurly.line);
    }

    private Kvp parseKvp() throws ParserException {
        Token name = peek();
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
                try {
                    Block body = parseBlock();
                    return new Kvp(key.value, new LambdaExpr(name.value, params, body, name.line),
                            name.line, name.value);
                } finally {
                    --functionDepth;
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
        return new Kvp(name.value, value, name.line);
    }

    /**
     * Whether a token type may be an object property name (identifier or any
     * keyword, per ES IdentifierName).
     */
    private static boolean isKeyName(String type) {
        switch (type) {
            case "ident":
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
