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
import org.ngscript.ast.Pattern;
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
import org.ngscript.lexer.Lexer;

/**
 * Unit tests for the recursive descent {@link NgParser}.
 *
 * @author wssccc
 */
public class NgParserTest {

    private Program parse(String code) throws Exception {
        return new NgParser().parse(code);
    }

    private static Block blockOf(Stmt stmt) {
        assertTrue(stmt instanceof BlockStmt);
        return ((BlockStmt) stmt).block;
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
    public void testAssignmentIsRightAssociative() throws Exception {
        // a = b = c parses as a = (b = c), matching ECMAScript
        AssignExpr outer = (AssignExpr) exprOf("a = b = c;");
        ident(outer.target, "a");
        AssignExpr inner = (AssignExpr) outer.value;
        ident(inner.target, "b");
        ident(inner.value, "c");
    }

    @Test
    public void testCompoundAssignment() throws Exception {
        AssignExpr add = (AssignExpr) exprOf("a += 1;");
        assertEquals("asgn_add", add.op);
        ident(add.target, "a");
        lit(add.value, "1");
    }

    @Test
    public void testCommaOperator() throws Exception {
        CommaExpr comma = (CommaExpr) exprOf("a, b, c;");
        assertEquals(3, comma.operands.size());
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

        AssignExpr assign = (AssignExpr) exprOf("var o = {a: 1, \"b\": 2};");
        ObjectLiteral object = (ObjectLiteral) assign.value;
        assertEquals(2, object.kvps.size());
        Kvp kvp = object.kvps.get(0);
        assertEquals("a", kvp.name);
        lit(kvp.value, "1");
    }

    @Test
    public void testBareBlockStatement() throws Exception {
        // a brace at statement position is a block statement in ES, not an object literal
        Program program = parse("{ a; b; }");
        assertEquals(1, program.statements.size());
        assertTrue(program.statements.get(0) instanceof BlockStmt);
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
    public void testParameterDestructuring() throws Exception {
        // object pattern parameter with shorthand and defaulted inner binding
        FunctionDecl decl = (FunctionDecl) parse("function f({a, b = 1}) {}").statements.get(0);
        assertEquals(1, decl.params.names.size());
        Pattern.ObjectP obj = (Pattern.ObjectP) decl.params.patterns.get(0);
        assertEquals(2, obj.props.size());
        assertEquals("a", obj.props.get(0).key);
        assertEquals("a", ((Pattern.IdentP) obj.props.get(0).pattern).name);
        assertNotNull(obj.props.get(1).pattern.defaultValue);

        // array pattern parameter with a whole-pattern default
        decl = (FunctionDecl) parse("function g([x, , ...rest] = []) {}").statements.get(0);
        Pattern.ArrayP arr = (Pattern.ArrayP) decl.params.patterns.get(0);
        assertEquals(2, arr.items.size());
        assertNull(arr.items.get(1)); // hole
        assertNotNull(arr.rest);
        assertNotNull(arr.defaultValue);

        // mixed plain and pattern parameters
        decl = (FunctionDecl) parse("function h(a, {b}, [c]) {}").statements.get(0);
        assertEquals(3, decl.params.names.size());
        assertNull(decl.params.patterns.get(0));
        assertTrue(decl.params.patterns.get(1) instanceof Pattern.ObjectP);
        assertTrue(decl.params.patterns.get(2) instanceof Pattern.ArrayP);

        // rest binding pattern
        decl = (FunctionDecl) parse("function k(...[a, b]) {}").statements.get(0);
        assertTrue(decl.params.rest);
        assertTrue(decl.params.patterns.get(0) instanceof Pattern.ArrayP);

        // arrow with a pattern parameter
        LambdaExpr arrow = (LambdaExpr) exprOf("(({x}) => x);");
        assertEquals(1, arrow.params.names.size());
        assertTrue(arrow.params.patterns.get(0) instanceof Pattern.ObjectP);
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
        assertEquals(1, blockOf(ifStmt.thenBody).statements.size());
        assertTrue(blockOf(ifStmt.thenBody).statements.get(0) instanceof ReturnStmt);
        assertNull(ifStmt.elseBody);
    }

    @Test
    public void testIfElse() throws Exception {
        IfStmt ifStmt = (IfStmt) parse("if (a) { b; } else { c; }").statements.get(0);
        assertNotNull(ifStmt.elseBody);
        assertEquals(1, blockOf(ifStmt.elseBody).statements.size());
    }

    @Test
    public void testForLoopWithEmptyClauses() throws Exception {
        // for(;;) must parse with null clauses instead of crashing
        ForStmt forStmt = (ForStmt) parse("for (;;) { }").statements.get(0);
        assertNull(forStmt.init);
        assertNull(forStmt.test);
        assertNull(forStmt.step);
        assertEquals(0, blockOf(forStmt.body).statements.size());
    }

    @Test
    public void testForLoopWithClauses() throws Exception {
        ForStmt forStmt = (ForStmt) parse("for (var i = 0; i < 10; i++) { break; }").statements.get(0);
        assertTrue(forStmt.init instanceof AssignExpr);
        assertTrue(forStmt.test instanceof BinaryExpr);
        assertTrue(forStmt.step instanceof UnaryExpr);
        assertTrue(blockOf(forStmt.body).statements.get(0) instanceof BreakStmt);
    }

    @Test
    public void testWhileLoop() throws Exception {
        WhileStmt whileStmt = (WhileStmt) parse("while (a) { continue; }").statements.get(0);
        ident(whileStmt.condition, "a");
        assertTrue(blockOf(whileStmt.body).statements.get(0) instanceof ContinueStmt);
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
        // return/break/continue need a function or loop context (early errors)
        FunctionDecl fn = (FunctionDecl) parse("function f() { return a; }").statements.get(0);
        ReturnStmt returnStmt = (ReturnStmt) fn.body.statements.get(0);
        ident(returnStmt.expr, "a");
        assertNull(((ReturnStmt) ((FunctionDecl) parse("function f() { return; }")
                .statements.get(0)).body.statements.get(0)).expr);
        assertTrue(parse("while (a) { break; }").statements.get(0) instanceof WhileStmt);
        assertTrue(parse("while (a) { continue; }").statements.get(0) instanceof WhileStmt);
        try {
            parse("return a;");
            fail("expected ParserException");
        } catch (ParserException expected) {
            assertTrue(expected.getMessage().contains("return"));
        }
        try {
            parse("break;");
            fail("expected ParserException");
        } catch (ParserException expected) {
            assertTrue(expected.getMessage().contains("break"));
        }
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
            parse("var x = 1; var");
            fail("expected ParserException");
        } catch (ParserException expected) {
            assertTrue(expected.getMessage().contains("end of input"));
        }
    }

    @Test
    public void testAsiAtEndOfInput() throws Exception {
        // automatic semicolon insertion: a missing semicolon before EOF is fine
        Program program = parse("var x = 1");
        assertEquals(1, program.statements.size());
    }

    @Test
    public void testAsiBeforeClosingBrace() throws Exception {
        Program program = parse("function f() { return 1 }");
        FunctionDecl decl = (FunctionDecl) program.statements.get(0);
        ReturnStmt ret = (ReturnStmt) decl.body.statements.get(0);
        lit(ret.expr, "1");
    }

    @Test
    public void testAsiOnLineBreak() throws Exception {
        Program program = parse("var a = 1\nvar b = 2");
        assertEquals(2, program.statements.size());
    }

    @Test
    public void testAsiRejectedOnSameLine() throws Exception {
        try {
            parse("var a = 1 var b = 2");
            fail("expected ParserException");
        } catch (ParserException expected) {
            assertTrue(expected.getMessage().contains("semicolon"));
        }
    }

    @Test
    public void testRestrictedProductionReturn() throws Exception {
        // return followed by a line terminator: ASI applies, no expression
        Program program = parse("function f() { return\n1; }");
        FunctionDecl decl = (FunctionDecl) program.statements.get(0);
        ReturnStmt ret = (ReturnStmt) decl.body.statements.get(0);
        assertNull(ret.expr);
        // same line: the expression is part of the return statement
        Program program2 = parse("function g() { return 1; }");
        FunctionDecl decl2 = (FunctionDecl) program2.statements.get(0);
        ReturnStmt ret2 = (ReturnStmt) decl2.body.statements.get(0);
        assertNotNull(ret2.expr);
    }

    @Test
    public void testThrowRejectsLineBreak() throws Exception {
        try {
            parse("throw\n1;");
            fail("expected ParserException");
        } catch (ParserException expected) {
            assertTrue(expected.getMessage().contains("line break after throw"));
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
