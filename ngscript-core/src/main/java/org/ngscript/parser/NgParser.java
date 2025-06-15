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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.ngscript.compiler.CompilerException;
import org.ngscript.ast.ArrayLiteral;
import org.ngscript.ast.AssignExpr;
import org.ngscript.ast.BinaryExpr;
import org.ngscript.ast.Block;
import org.ngscript.ast.BreakStmt;
import org.ngscript.ast.CallExpr;
import org.ngscript.ast.CondExpr;
import org.ngscript.ast.ContinueStmt;
import org.ngscript.ast.Expr;
import org.ngscript.ast.ExprStmt;
import org.ngscript.ast.ForStmt;
import org.ngscript.ast.FunctionDecl;
import org.ngscript.ast.GoStmt;
import org.ngscript.ast.IdentExpr;
import org.ngscript.ast.IfStmt;
import org.ngscript.ast.ImportStmt;
import org.ngscript.ast.IndexExpr;
import org.ngscript.ast.Kvp;
import org.ngscript.ast.LambdaExpr;
import org.ngscript.ast.LiteralExpr;
import org.ngscript.ast.MemberExpr;
import org.ngscript.ast.NewExpr;
import org.ngscript.ast.ObjectLiteral;
import org.ngscript.ast.Params;
import org.ngscript.ast.Program;
import org.ngscript.ast.ReturnStmt;
import org.ngscript.ast.Stmt;
import org.ngscript.ast.SwitchCase;
import org.ngscript.ast.SwitchStmt;
import org.ngscript.ast.ThrowStmt;
import org.ngscript.ast.TryStmt;
import org.ngscript.ast.UnaryExpr;
import org.ngscript.ast.VarDeclExpr;
import org.ngscript.ast.WhileStmt;

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
    }

    //----------------------------------------------------------------------
    // batch mode state
    //----------------------------------------------------------------------

    private List<Token> tokens;
    private int index;

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
        if (isStreaming()) {
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
            throw new ParserException("expecting " + type + " but got " + (t == null ? "end of input" : t));
        }
        return t;
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
            case "switch":
                return parseSwitch();
            case "try":
                return parseTry();
            case "throw": {
                next();
                Expr expr = parseExpr();
                expect("semicolon");
                return new ThrowStmt(expr, t.line);
            }
            case "return": {
                next();
                if (peekIs("semicolon")) {
                    next();
                    return new ReturnStmt(null, t.line);
                }
                Expr expr = parseExpr();
                expect("semicolon");
                return new ReturnStmt(expr, t.line);
            }
            case "break": {
                next();
                expect("semicolon");
                return new BreakStmt(t.line);
            }
            case "continue": {
                next();
                expect("semicolon");
                return new ContinueStmt(t.line);
            }
            case "import":
                return parseImport();
            case "go":
                return parseGo();
            default:
                return parseExprStmt();
        }
    }

    private Stmt parseFunctionDecl() throws ParserException {
        Token fn = next();
        Token name = expect("ident");
        expect("lparen");
        Params params = parseParamList();
        expect("rparen");
        Block body = parseBlock();
        return new FunctionDecl(name.value, params, body, fn.line);
    }

    private Stmt parseIf() throws ParserException {
        Token t = next();
        expect("lparen");
        Expr condition = parseExpr();
        expect("rparen");
        Block thenBody = parseBlock();
        Block elseBody = null;
        if (peekIs("else")) {
            next();
            elseBody = parseBlock();
        }
        return new IfStmt(condition, thenBody, elseBody, t.line);
    }

    private Stmt parseFor() throws ParserException {
        Token t = next();
        expect("lparen");
        Expr init = parseNullableExpr();
        expect("semicolon");
        Expr test = parseNullableExpr();
        expect("semicolon");
        Expr step = parseNullableExpr();
        expect("rparen");
        Block body = parseBlock();
        return new ForStmt(init, test, step, body, t.line);
    }

    private Stmt parseWhile() throws ParserException {
        Token t = next();
        expect("lparen");
        Expr condition = parseExpr();
        expect("rparen");
        Block body = parseBlock();
        return new WhileStmt(condition, body, t.line);
    }

    private Stmt parseSwitch() throws ParserException {
        Token t = next();
        expect("lparen");
        Expr value = parseExpr();
        expect("rparen");
        expect("lcurly");
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
        expect("catch");
        expect("lparen");
        Token catchVar = expect("ident");
        expect("rparen");
        Block catchBody = parseBlock();
        Block finallyBody = null;
        if (peekIs("finally")) {
            next();
            finallyBody = parseBlock();
        }
        return new TryStmt(tryBody, catchVar.value, catchBody, finallyBody, t.line);
    }

    private Stmt parseImport() throws ParserException {
        Token t = next();
        StringBuilder sb = new StringBuilder(expect("ident").value);
        while (peekIs("dot")) {
            next();
            sb.append('.').append(expect("ident").value);
        }
        expect("semicolon");
        return new ImportStmt(sb.toString(), t.line);
    }

    private Stmt parseGo() throws ParserException {
        Token t = next();
        Expr callee = parsePostfix(false);
        expect("lparen");
        List<Expr> args = parseParams();
        expect("rparen");
        expect("semicolon");
        return new GoStmt(callee, args, t.line);
    }

    private Stmt parseExprStmt() throws ParserException {
        Expr expr = parseExpr();
        expect("semicolon");
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
                names.add(expect("ident").value);
            }
        }
        return new Params(names);
    }

    private List<Expr> parseParams() throws ParserException {
        List<Expr> args = new ArrayList<>();
        if (!peekIs("rparen")) {
            args.add(parseExpr());
            while (peekIs("comma")) {
                next();
                args.add(parseExpr());
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

    private Expr parseExpr() throws ParserException {
        return parseAssign();
    }

    private Expr parseAssign() throws ParserException {
        Expr left = parseCond();
        while (peekIs("assign")) {
            Token t = next();
            Expr right = parseCond();
            left = new AssignExpr(left, right, t.line);
        }
        return left;
    }

    private Expr parseCond() throws ParserException {
        Expr cond = parseOr();
        while (peekIs("question_mark")) {
            Token t = next();
            Expr thenExpr = parseOr();
            expect("colon");
            Expr elseExpr = parseOr();
            cond = new CondExpr(cond, thenExpr, elseExpr, t.line);
        }
        return cond;
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
        return parseLeftAssoc(Arrays.asList("gt", "lt", "ge", "le"), this::parseAdd);
    }

    private Expr parseAdd() throws ParserException {
        return parseLeftAssoc(Arrays.asList("add", "sub"), this::parseMul);
    }

    private Expr parseMul() throws ParserException {
        return parseLeftAssoc(Arrays.asList("mul", "div", "mod"), this::parseUnary);
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
                return new UnaryExpr(UnaryExpr.Op.NEG, parsePostfix(true), t.line);
            case "not":
                next();
                return new UnaryExpr(UnaryExpr.Op.NOT, parsePostfix(true), t.line);
            case "typeof":
                next();
                return new UnaryExpr(UnaryExpr.Op.TYPEOF, parsePostfix(true), t.line);
            case "inc":
                next();
                return new UnaryExpr(UnaryExpr.Op.INC, parsePostfix(true), t.line);
            case "dec":
                next();
                return new UnaryExpr(UnaryExpr.Op.DEC, parsePostfix(true), t.line);
            default: {
                Expr operand = parsePostfix(true);
                Token post = peek();
                if (post != null && post.type.equals("inc")) {
                    next();
                    return new UnaryExpr(UnaryExpr.Op.POST_INC, operand, post.line);
                }
                if (post != null && post.type.equals("dec")) {
                    next();
                    return new UnaryExpr(UnaryExpr.Op.POST_DEC, operand, post.line);
                }
                return operand;
            }
        }
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
        Expr cls = parseExprClass();
        expect("lparen");
        List<Expr> args = parseParams();
        expect("rparen");
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
        if (second.type.equals("rparen_arrow_fn")) {
            // () => { ... }
            next();
            next();
            Block body = parseBlock();
            return new LambdaExpr(new Params(Collections.emptyList()), body, lparen.line);
        }
        if (second.type.equals("ident")) {
            Token third = peekN(3);
            if (third != null && (third.type.equals("comma") || third.type.equals("rparen_arrow_fn"))) {
                // (x) => { ... } or (x, y) => { ... }
                next();
                Params params = parseParamList();
                expect("rparen_arrow_fn");
                Block body = parseBlock();
                return new LambdaExpr(params, body, lparen.line);
            }
        }
        // plain parenthesized expression
        next();
        Expr expr = parseExpr();
        expect("rparen");
        return expr;
    }

    private Expr parseExpr9() throws ParserException {
        Token t = peek();
        if (t == null) {
            throw new ParserException("unexpected end of input");
        }
        switch (t.type) {
            case "var": {
                next();
                Token name = expect("ident");
                return new VarDeclExpr(name.value, false, name.line);
            }
            case "val": {
                next();
                Token name = expect("ident");
                return new VarDeclExpr(name.value, true, name.line);
            }
            case "ident":
                next();
                return new IdentExpr(t.value, t.line);
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
            case "function": {
                next();
                expect("lparen");
                Params params = parseParamList();
                expect("rparen");
                Block body = parseBlock();
                return new LambdaExpr(params, body, t.line);
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
        if (!peekIs("rsqr")) {
            items.add(parseExpr());
            while (peekIs("comma")) {
                next();
                items.add(parseExpr());
            }
        }
        expect("rsqr");
        return new ArrayLiteral(items, lsqr.line);
    }

    private Expr parseObjectLiteral(Token lcurly) throws ParserException {
        next();
        List<Kvp> kvps = new ArrayList<>();
        if (!peekIs("rcurly")) {
            kvps.add(parseKvp());
            while (peekIs("comma")) {
                next();
                kvps.add(parseKvp());
            }
        }
        expect("rcurly");
        return new ObjectLiteral(kvps, lcurly.line);
    }

    private Kvp parseKvp() throws ParserException {
        Token name = peek();
        if (name == null || !(name.type.equals("ident") || name.type.equals("string"))) {
            throw new ParserException("expecting object key but got " + (name == null ? "end of input" : name));
        }
        next();
        expect("colon");
        Expr value = parseExpr();
        return new Kvp(name.value, value, name.line);
    }
}
