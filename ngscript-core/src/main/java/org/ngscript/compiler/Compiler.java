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
import java.util.Deque;
import java.util.List;
import java.util.Scanner;

import org.ngscript.bytecode.Assembler;
import org.ngscript.bytecode.Instruction;
import org.ngscript.api.Configuration;
import org.ngscript.ast.ArrayLiteral;
import org.ngscript.ast.AssignExpr;
import org.ngscript.ast.AstNode;
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
     * True while compiling the try or catch body of a try statement: break, continue and
     * return inside those bodies must run the finally branch first.
     */
    boolean inHookedBlock;

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
        for (Stmt stmt : block.statements) {
            compileStmt(stmt);
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
        } else if (stmt instanceof SwitchStmt) {
            compileSwitch((SwitchStmt) stmt);
        } else if (stmt instanceof TryStmt) {
            compileTry((TryStmt) stmt);
        } else if (stmt instanceof ThrowStmt) {
            compileThrow((ThrowStmt) stmt);
        } else if (stmt instanceof ReturnStmt) {
            compileReturn((ReturnStmt) stmt);
        } else if (stmt instanceof BreakStmt) {
            compileBreak();
        } else if (stmt instanceof ContinueStmt) {
            compileContinue();
        } else if (stmt instanceof ImportStmt) {
            assembler.emit("import_", ((ImportStmt) stmt).packagePath);
        } else if (stmt instanceof GoStmt) {
            compileGo((GoStmt) stmt);
        } else {
            throw compileError("Unknown statement " + stmt);
        }
    }

    private void compileFunctionDecl(FunctionDecl decl) throws CompilerException {
        String enter = _compile_function_body(decl.params, decl.body);
        assembler.instructions.add(0, new Instruction("static_func", decl.name, enter));
    }

    private String _compile_function_body(Params params, Block body) throws CompilerException {
        String exit = assembler.label("func_exit", body);
        String enter = assembler.label("func_enter", body);
        assembler.emit("jmp", exit);
        assembler.emit("label", enter);

        for (int i = 0; i < params.names.size(); i++) {
            assembler.emit("pickarg", i + "");
            assembler.emit("set", params.names.get(i), "%eax");
        }
        compileBlock(body);
        //default return void
        assembler.emit("clear", "%eax");
        assembler.emit("ret");
        assembler.emit("label", exit);
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
        } else if (expr instanceof AssignExpr) {
            AssignExpr assign = (AssignExpr) expr;
            compileExpr(assign.target, true);
            assembler.emit("push_eax");
            compileExpr(assign.value);
            assembler.emit("assign");
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
            assembler.emit("jmp", exitLabel);
            assembler.emit("label", falseLabel);
            compileExpr(cond.elseExpr);
            assembler.emit("label", exitLabel);
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
        } else if (expr instanceof NewExpr) {
            NewExpr newExpr = (NewExpr) expr;
            _compile_funcall(newExpr.callee, newExpr.args);
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
            for (int i = array.items.size() - 1; i >= 0; i--) {
                compileExpr(array.items.get(i));
                assembler.emit("push_eax");
            }
            assembler.emit("array_new", array.items.size() + "");
        } else if (expr instanceof ObjectLiteral) {
            ObjectLiteral object = (ObjectLiteral) expr;
            for (Kvp kvp : object.kvps) {
                assembler.emit("string", kvp.name);
                assembler.emit("push_eax");
                compileExpr(kvp.value);
                assembler.emit("push_eax");
            }
            assembler.emit("object_new", object.kvps.size() + "");
        } else if (expr instanceof LambdaExpr) {
            LambdaExpr lambda = (LambdaExpr) expr;
            String enter = _compile_function_body(lambda.params, lambda.body);
            assembler.emit("new_closure", enter);
        } else {
            throw compileError("Unknown expression " + expr);
        }
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
            case NOT:
                throw compileError("not operator is not supported");
        }
    }

    private void compileBinary(BinaryExpr bin) throws CompilerException {
        if (bin.op == BinaryExpr.Op.OR) {
            String exit = assembler.label("exit_or", bin);
            compileExpr(bin.left);
            assembler.emit("jnz", exit);
            compileExpr(bin.right);
            assembler.emit("label", exit);
            return;
        }
        if (bin.op == BinaryExpr.Op.AND) {
            String exit = assembler.label("exit_and", bin);
            compileExpr(bin.left);
            assembler.emit("jz", exit);
            compileExpr(bin.right);
            assembler.emit("label", exit);
            return;
        }
        compileExpr(bin.left);
        assembler.emit("push_eax");
        compileExpr(bin.right);
        assembler.emit(bin.op.name().toLowerCase());
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
            compileBlock(ifStmt.thenBody);
            assembler.emit("label", exitLabel);
        } else {
            String exitLabel = assembler.label("exit", ifStmt);
            String elseLabel = assembler.label("else", ifStmt);
            compileExpr(ifStmt.condition);
            assembler.emit("jz", elseLabel);
            compileBlock(ifStmt.thenBody);
            assembler.emit("jmp", exitLabel);
            assembler.emit("label", elseLabel);
            compileBlock(ifStmt.elseBody);
            assembler.emit("label", exitLabel);
        }
    }

    private void compileFor(ForStmt forStmt) throws CompilerException {
        String testLabel = assembler.label("test", forStmt);
        String continueLabel = assembler.label("continue", forStmt);
        String breakLabel = assembler.label("break", forStmt);
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        _compile_nullable_expr(forStmt.init);
        assembler.emit("label", testLabel);
        _compile_nullable_expr(forStmt.test);
        assembler.emit("jz", breakLabel);
        compileBlock(forStmt.body);
        assembler.emit("label", continueLabel);
        _compile_nullable_expr(forStmt.step);
        assembler.emit("jmp", testLabel);
        assembler.emit("label", breakLabel);
        breakLabels.pop();
        continueLabels.pop();
    }

    private void compileWhile(WhileStmt whileStmt) throws CompilerException {
        String testLabel = assembler.label("test", whileStmt);
        String continueLabel = assembler.label("continue", whileStmt);
        String breakLabel = assembler.label("break", whileStmt);
        breakLabels.push(breakLabel);
        continueLabels.push(continueLabel);
        assembler.emit("label", testLabel);
        compileExpr(whileStmt.condition);
        assembler.emit("jz", breakLabel);
        compileBlock(whileStmt.body);
        assembler.emit("label", continueLabel);
        assembler.emit("jmp", testLabel);
        assembler.emit("label", breakLabel);
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
            assembler.emit("jmp", breakLabel);
            assembler.emit("label", nextLabel);
        }
        if (switchStmt.defaultBody != null) {
            compileBlock(switchStmt.defaultBody);
        }
        breakLabels.pop();
        assembler.emit("label", breakLabel);
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
        assembler.emit("jmp", finallyLabel);
        assembler.emit("jmp", exitLabel);
        assembler.emit("label", catchLabel);
        assembler.emit("mov_eax_exception");
        assembler.emit("set", tryStmt.catchVar);
        assembler.emit("clear_null", "%exception");
        compileBlock(tryStmt.catchBody);
        assembler.emit("push_eip");
        assembler.emit("jmp", finallyLabel);
        assembler.emit("jmp", exitLabel);
        assembler.emit("label", finallyLabel);
        inHookedBlock = savedHooked;
        if (tryStmt.finallyBody != null) {
            compileBlock(tryStmt.finallyBody);
        }
        assembler.emit("clear_call_stack");
        assembler.emit("pop_eax");
        assembler.emit("jmp", "offset", "1");
        assembler.emit("label", exitLabel);
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
            compileExpr(stmt.expr);
            assembler.emit("ret");
        } else {
            assembler.emit("clear", "%eax");
            assembler.emit("ret");
        }
    }

    private void compileBreak() throws CompilerException {
        if (breakLabels.isEmpty()) {
            throw compileError("break without loop");
        }
        if (inHookedBlock) {
            _compile_call_finally();
        }
        assembler.emit("jmp", breakLabels.peek());
    }

    private void compileContinue() throws CompilerException {
        if (breakLabels.isEmpty()) {
            throw compileError("continue without loop");
        }
        if (inHookedBlock) {
            _compile_call_finally();
        }
        assembler.emit("jmp", continueLabels.peek());
    }

    private void _compile_call_finally() {
        assembler.emit("push_eip");
        assembler.emit("jmp", finallyLabels.peek());
    }

    private void compileGo(GoStmt goStmt) throws CompilerException {
        String entryLabel = assembler.label("entry", goStmt);
        String goLabel = assembler.label("go", goStmt);
        assembler.emit("jmp", goLabel);
        assembler.emit("label", entryLabel);
        _compile_funcall(goStmt.callee, goStmt.args);
        assembler.emit("pop_env");
        assembler.emit("pop");//pop params
        assembler.emit("pop");//pop function body
        assembler.emit("halt");
        assembler.emit("label", goLabel);
        assembler.emit("go", entryLabel);
    }

    private void _compile_funcall(Expr callee, List<Expr> args) throws CompilerException {
        //params
        for (int i = args.size() - 1; i >= 0; i--) {
            compileExpr(args.get(i));
            assembler.emit("push_eax");
        }
        assembler.emit("packargs", args.size() + "");
        assembler.emit("push_eax");
        //function body
        compileExpr(callee);
        assembler.emit("push_eax");

        assembler.emit("push_env");
        assembler.emit("call");
    }
}
