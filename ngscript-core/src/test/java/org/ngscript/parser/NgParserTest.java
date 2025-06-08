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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ngscript.parser.ast.ArrayLiteral;
import org.ngscript.parser.ast.AssignExpr;
import org.ngscript.parser.ast.BinaryExpr;
import org.ngscript.parser.ast.BreakStmt;
import org.ngscript.parser.ast.CallExpr;
import org.ngscript.parser.ast.CondExpr;
import org.ngscript.parser.ast.ContinueStmt;
import org.ngscript.parser.ast.Expr;
import org.ngscript.parser.ast.ExprStmt;
import org.ngscript.parser.ast.ForStmt;
import org.ngscript.parser.ast.FunctionDecl;
import org.ngscript.parser.ast.GoStmt;
import org.ngscript.parser.ast.IdentExpr;
import org.ngscript.parser.ast.IfStmt;
import org.ngscript.parser.ast.ImportStmt;
import org.ngscript.parser.ast.IndexExpr;
import org.ngscript.parser.ast.Kvp;
import org.ngscript.parser.ast.LambdaExpr;
import org.ngscript.parser.ast.LiteralExpr;
import org.ngscript.parser.ast.MemberExpr;
import org.ngscript.parser.ast.NewExpr;
import org.ngscript.parser.ast.ObjectLiteral;
import org.ngscript.parser.ast.Program;
import org.ngscript.parser.ast.ReturnStmt;
import org.ngscript.parser.ast.Stmt;
import org.ngscript.parser.ast.SwitchCase;
import org.ngscript.parser.ast.SwitchStmt;
import org.ngscript.parser.ast.ThrowStmt;
import org.ngscript.parser.ast.TryStmt;
import org.ngscript.parser.ast.UnaryExpr;
import org.ngscript.parser.ast.VarDeclExpr;
import org.ngscript.parser.ast.WhileStmt;
import org.ngscript.parser.lexer.Lexer;

/**
 * Unit tests for the recursive descent {@link NgParser}.
 *
 * @author wssccc
 */
public class NgParserTest {

    private Program parse(String code) throws Exception {
        return new NgParser().parse(Lexer.scan(code));
    }

    private Expr exprOf(String code) throws Exception {
        Program program = parse(code);
        assertEquals(1, program.statements.size());
        return ((ExprStmt) program.statements.get(0)).expr;
    }

    private static IdentExpr ident(Expr expr, String name) {
        assertTrue(expr instanceof IdentExpr);
        IdentExpr ident = (IdentExpr) expr;
        assertEquals(name, ident.name);
        return ident;
    }

    private static LiteralExpr lit(Expr expr, String value) {
        assertTrue(expr instanceof LiteralExpr);
        LiteralExpr literal = (LiteralExpr) expr;
        assertEquals(value, literal.value);
        return literal;
    }

    //----------------------------------------------------------------------
    // expressions
    //----------------------------------------------------------------------

    @Test
    public void testPrecedence() throws Exception {
        // 1 + 2 * 3 must parse as 1 + (2 * 3)
        BinaryExpr add = (BinaryExpr) exprOf("1 + 2 * 3;");
        assertEquals(BinaryExpr.Op.ADD, add.op);
        lit(add.left, "1");
        BinaryExpr mul = (BinaryExpr) add.right;
        assertEquals(BinaryExpr.Op.MUL, mul.op);
        lit(mul.left, "2");
        lit(mul.right, "3");
    }

    @Test
    public void testAssignmentIsLeftAssociative() throws Exception {
        // a = b = c parses as (a = b) = c, matching the original grammar
        AssignExpr outer = (AssignExpr) exprOf("a = b = c;");
        AssignExpr inner = (AssignExpr) outer.target;
        ident(inner.target, "a");
        ident(inner.value, "b");
        ident(outer.value, "c");
    }

    @Test
    public void testConditional() throws Exception {
        CondExpr cond = (CondExpr) exprOf("a ? b : c;");
        ident(cond.condition, "a");
        ident(cond.thenExpr, "b");
        ident(cond.elseExpr, "c");
    }

    @Test
    public void testBitXorOperator() throws Exception {
        // the lexer now emits bit_xor for ^ so this must parse
        BinaryExpr bitXor = (BinaryExpr) exprOf("a ^ b;");
        assertEquals(BinaryExpr.Op.BIT_XOR, bitXor.op);
    }

    @Test
    public void testUnaryAndPostfix() throws Exception {
        assertEquals(UnaryExpr.Op.NEG, ((UnaryExpr) exprOf("-a;")).op);
        assertEquals(UnaryExpr.Op.NOT, ((UnaryExpr) exprOf("!a;")).op);
        assertEquals(UnaryExpr.Op.TYPEOF, ((UnaryExpr) exprOf("typeof a;")).op);
        assertEquals(UnaryExpr.Op.INC, ((UnaryExpr) exprOf("++a;")).op);
        assertEquals(UnaryExpr.Op.DEC, ((UnaryExpr) exprOf("--a;")).op);
        assertEquals(UnaryExpr.Op.POST_INC, ((UnaryExpr) exprOf("a++;")).op);
        assertEquals(UnaryExpr.Op.POST_DEC, ((UnaryExpr) exprOf("a--;")).op);
    }

    @Test
    public void testMemberCallIndexChain() throws Exception {
        // a.b[0].c(1) parses as Call(Member(Index(Member(a, b), 0), c), [1])
        CallExpr call = (CallExpr) exprOf("a.b[0].c(1);");
        MemberExpr member = (MemberExpr) call.callee;
        assertEquals("c", member.name);
        IndexExpr index = (IndexExpr) member.object;
        lit(index.index, "0");
        MemberExpr innerMember = (MemberExpr) index.object;
        assertEquals("b", innerMember.name);
        ident(innerMember.object, "a");
        assertEquals(1, call.args.size());
        lit(call.args.get(0), "1");
    }

    @Test
    public void testNewAndCall() throws Exception {
        // new Foo(1).bar() parses as Call(Member(New(Foo, [1]), bar), [])
        CallExpr call = (CallExpr) exprOf("new Foo(1).bar();");
        MemberExpr member = (MemberExpr) call.callee;
        assertEquals("bar", member.name);
        NewExpr newExpr = (NewExpr) member.object;
        ident(newExpr.callee, "Foo");
        assertEquals(1, newExpr.args.size());
    }

    @Test
    public void testArrayAndObjectLiteral() throws Exception {
        ArrayLiteral array = (ArrayLiteral) exprOf("[1, 2, 3];");
        assertEquals(3, array.items.size());

        ObjectLiteral object = (ObjectLiteral) exprOf("{a: 1, \"b\": 2};");
        assertEquals(2, object.kvps.size());
        Kvp kvp = object.kvps.get(0);
        assertEquals("a", kvp.name);
        lit(kvp.value, "1");
    }

    @Test
    public void testVarValAndIdent() throws Exception {
        VarDeclExpr decl = (VarDeclExpr) exprOf("var x;");
        assertEquals("x", decl.name);
        assertTrue(!decl.isVal);
        assertTrue(((VarDeclExpr) exprOf("val y;")).isVal);
        ident(exprOf("z;"), "z");
    }

    @Test
    public void testLambdaAndArrowFunction() throws Exception {
        LambdaExpr lambda = (LambdaExpr) exprOf("function(a, b) { return a; };");
        assertEquals(2, lambda.params.names.size());
        assertEquals("a", lambda.params.names.get(0));

        AssignExpr assign = (AssignExpr) exprOf("val f = (x, y) => { x + y; };");
        assertTrue(assign.target instanceof VarDeclExpr);
        LambdaExpr arrow = (LambdaExpr) assign.value;
        assertEquals(2, arrow.params.names.size());
        assertEquals(1, arrow.body.statements.size());
    }

    @Test
    public void testParenthesizedExpression() throws Exception {
        BinaryExpr add = (BinaryExpr) exprOf("(1 + 2) * 3;");
        assertEquals(BinaryExpr.Op.MUL, add.op);
        assertEquals(BinaryExpr.Op.ADD, ((BinaryExpr) add.left).op);
    }

    //----------------------------------------------------------------------
    // statements
    //----------------------------------------------------------------------

    @Test
    public void testFunctionDecl() throws Exception {
        Program program = parse("function calculateFibonacci(position) { if (position <= 1) { return position; } }");
        FunctionDecl decl = (FunctionDecl) program.statements.get(0);
        assertEquals("calculateFibonacci", decl.name);
        assertEquals(1, decl.params.names.size());
        assertEquals(1, decl.body.statements.size());
        IfStmt ifStmt = (IfStmt) decl.body.statements.get(0);
        assertEquals(1, ifStmt.thenBody.statements.size());
        assertTrue(ifStmt.thenBody.statements.get(0) instanceof ReturnStmt);
        assertNull(ifStmt.elseBody);
    }

    @Test
    public void testIfElse() throws Exception {
        IfStmt ifStmt = (IfStmt) parse("if (a) { b; } else { c; }").statements.get(0);
        assertNotNull(ifStmt.elseBody);
        assertEquals(1, ifStmt.elseBody.statements.size());
    }

    @Test
    public void testForLoopWithEmptyClauses() throws Exception {
        // for(;;) must parse with null clauses instead of crashing
        ForStmt forStmt = (ForStmt) parse("for (;;) { }").statements.get(0);
        assertNull(forStmt.init);
        assertNull(forStmt.test);
        assertNull(forStmt.step);
        assertEquals(0, forStmt.body.statements.size());
    }

    @Test
    public void testForLoopWithClauses() throws Exception {
        ForStmt forStmt = (ForStmt) parse("for (var i = 0; i < 10; i++) { break; }").statements.get(0);
        assertTrue(forStmt.init instanceof AssignExpr);
        assertTrue(forStmt.test instanceof BinaryExpr);
        assertTrue(forStmt.step instanceof UnaryExpr);
        assertTrue(forStmt.body.statements.get(0) instanceof BreakStmt);
    }

    @Test
    public void testWhileLoop() throws Exception {
        WhileStmt whileStmt = (WhileStmt) parse("while (a) { continue; }").statements.get(0);
        ident(whileStmt.condition, "a");
        assertTrue(whileStmt.body.statements.get(0) instanceof ContinueStmt);
    }

    @Test
    public void testSwitch() throws Exception {
        SwitchStmt switchStmt = (SwitchStmt) parse("switch (x) { case 1: a; break; default: b; }").statements.get(0);
        ident(switchStmt.value, "x");
        assertEquals(1, switchStmt.cases.size());
        SwitchCase c = switchStmt.cases.get(0);
        lit(c.value, "1");
        assertEquals(2, c.body.statements.size());
        assertNotNull(switchStmt.defaultBody);
        assertEquals(1, switchStmt.defaultBody.statements.size());
    }

    @Test
    public void testTryCatchFinally() throws Exception {
        TryStmt tryStmt = (TryStmt) parse("try { a; } catch (e) { b; } finally { c; }").statements.get(0);
        assertEquals("e", tryStmt.catchVar);
        assertEquals(1, tryStmt.tryBody.statements.size());
        assertEquals(1, tryStmt.catchBody.statements.size());
        assertNotNull(tryStmt.finallyBody);
    }

    @Test
    public void testThrowReturnBreakContinue() throws Exception {
        ThrowStmt throwStmt = (ThrowStmt) parse("throw a;").statements.get(0);
        ident(throwStmt.expr, "a");
        ReturnStmt returnStmt = (ReturnStmt) parse("return a;").statements.get(0);
        ident(returnStmt.expr, "a");
        assertNull(((ReturnStmt) parse("return;").statements.get(0)).expr);
        assertTrue(parse("break;").statements.get(0) instanceof BreakStmt);
        assertTrue(parse("continue;").statements.get(0) instanceof ContinueStmt);
    }

    @Test
    public void testImport() throws Exception {
        ImportStmt importStmt = (ImportStmt) parse("import org.ngscript.foo.Bar;").statements.get(0);
        assertEquals("org.ngscript.foo.Bar", importStmt.packagePath);
    }

    @Test
    public void testGoStatement() throws Exception {
        GoStmt goStmt = (GoStmt) parse("go callbackFunction(123);").statements.get(0);
        ident(goStmt.callee, "callbackFunction");
        assertEquals(1, goStmt.args.size());
        lit(goStmt.args.get(0), "123");
    }

    //----------------------------------------------------------------------
    // errors
    //----------------------------------------------------------------------

    @Test
    public void testSyntaxError() throws Exception {
        try {
            parse("var x = ;");
            fail("expected ParserException");
        } catch (ParserException expected) {
            assertTrue(expected.getMessage().contains("semicolon"));
        }
    }

    @Test
    public void testUnexpectedEndOfInput() throws Exception {
        try {
            parse("var x = 1");
            fail("expected ParserException");
        } catch (ParserException expected) {
            assertTrue(expected.getMessage().contains("end of input"));
        }
    }

    @Test
    public void testLineNumberInError() throws Exception {
        try {
            parse("1;\n2 + ;");
            fail("expected ParserException");
        } catch (ParserException expected) {
            assertTrue(expected.getMessage().contains("line:2"));
        }
    }

    //----------------------------------------------------------------------
    // interactive streaming mode
    //----------------------------------------------------------------------

    @Test
    public void testInteractiveStatementAcrossBatches() throws Exception {
        NgParser parser = new NgParser();
        List<Stmt> statements = new ArrayList<>();
        parser.setStatementListener(statements::add);

        // incomplete statement: no compile, wait for more input
        assertTrue(!parser.feedInteractive(Lexer.scan("var x = 1")));
        assertEquals(0, statements.size());

        // completion arrives in the next batch
        assertTrue(parser.feedInteractive(Lexer.scan(" + 2;")));
        assertEquals(1, statements.size());

        ExprStmt exprStmt = (ExprStmt) statements.get(0);
        AssignExpr assign = (AssignExpr) exprStmt.expr;
        assertTrue(assign.target instanceof VarDeclExpr);
        assertEquals(BinaryExpr.Op.ADD, ((BinaryExpr) assign.value).op);
    }

    @Test
    public void testInteractiveEmptyBatch() throws Exception {
        NgParser parser = new NgParser();
        List<Stmt> statements = new ArrayList<>();
        parser.setStatementListener(statements::add);
        assertTrue(!parser.feedInteractive(new ArrayList<>()));
        assertTrue(!parser.feedInteractive(Lexer.scan(";")));
        assertEquals(0, statements.size());
        assertTrue(parser.feedInteractive(Lexer.scan("1 + 1;")));
        assertEquals(1, statements.size());
    }

    @Test
    public void testInteractiveMultipleStatementsInOneBatch() throws Exception {
        NgParser parser = new NgParser();
        List<Stmt> statements = new ArrayList<>();
        parser.setStatementListener(statements::add);
        assertTrue(parser.feedInteractive(Lexer.scan("1 + 1; 2 + 2;")));
        assertEquals(2, statements.size());
    }

    @Test
    public void testInteractiveErrorAndRecovery() throws Exception {
        NgParser parser = new NgParser();
        List<Stmt> statements = new ArrayList<>();
        parser.setStatementListener(statements::add);

        try {
            parser.feedInteractive(Lexer.scan("1 + ;"));
            fail("expected ParserException");
        } catch (ParserException expected) {
            // expected
        }
        assertEquals(0, statements.size());

        // the parser must recover at the statement boundary for the next batch
        assertTrue(parser.feedInteractive(Lexer.scan("2 + 3;")));
        assertEquals(1, statements.size());
    }
}
