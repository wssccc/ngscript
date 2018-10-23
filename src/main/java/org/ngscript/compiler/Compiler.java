/*
 *  wssccc all rights reserved
 */
package org.ngscript.compiler;

import org.ngscript.parseroid.parser.AstNode;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author wssccc <wssccc@qq.com>
 */
public class Compiler {

    Assembler asm;
    Scanner sc;
    int printedLines = 0;

    Deque<String> continues = new ArrayDeque<>();
    Deque<String> breaks = new ArrayDeque<String>();
    Deque<String> finallys = new ArrayDeque<String>();
    final HashSet<String> binaryOp;

    public Compiler() {
        asm = new Assembler();
        binaryOp = new HashSet<String>();
        binaryOp.add("bit_xor");
        binaryOp.add("bit_or");
        binaryOp.add("bit_and");
        binaryOp.add("eq");
        binaryOp.add("neq");
        binaryOp.add("lt");
        binaryOp.add("gt");
        binaryOp.add("le");
        binaryOp.add("ge");
        binaryOp.add("veq");
        binaryOp.add("vneq");
        binaryOp.add("mul");
        binaryOp.add("mod");
        binaryOp.add("div");
        binaryOp.add("add");
        binaryOp.add("sub");
    }

    public List<Instruction> getCompiledInstructions() {
        return asm.instructions;
    }

    public Assembler getAssembler() {
        return asm;
    }

    public void compileCode(AstNode ast, String sourceCode) {
        asm.instructions.clear();
        sc = new Scanner(sourceCode);
        printedLines = 0;
        compile(ast);
        while (sc.hasNextLine()) {
            asm.emit("//", sc.nextLine(), "" + printedLines);
        }
    }

    void printDebug(AstNode ast) {
        if (printedLines != ast.token.line_no) {
            while (printedLines < ast.token.line_no) {
                String line = sc.hasNextLine() ? sc.nextLine() : "no line";
                asm.emit("//", line, "" + printedLines);
                ++printedLines;
            }
        }
    }

    private void compile(AstNode ast) {
        if (ast == null) {
            return;
        }
        printDebug(ast);
        try {
            Method m = this.getClass().getDeclaredMethod("compile_" + ast.token.type, AstNode.class);
            m.invoke(this, ast);
        } catch (Exception ex) {
            Logger.getLogger(Compiler.class.getName()).log(Level.SEVERE, null, ex.getCause());
        }
    }

    private void compile_program(AstNode ast) {
        for (AstNode content : ast.contents) {
            compile(content);
        }
    }

    private void compile_import_statement(AstNode ast) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < ast.contents.size(); i++) {
            sb.append(ast.contents.get(i).token.value);
        }
        asm.emit("import_", sb.toString());
    }

    private void compile_expr(AstNode ast) throws CompilerException {
        compile_expr(ast, false);
    }

    private void compile_expr(AstNode ast, boolean byref) throws CompilerException {
        printDebug(ast);
        String type = ast.token.type;
        if (ast.token.type.equals("expr")) {
            _compile_expr_opr(ast, byref);
        } else if (ast.token.type.equals("NULL")) {
        } else {
            throw new CompilerException(this, "manual dispatch error " + ast);
        }
    }

    public void _compile_expr_opr(AstNode ast, boolean byref) throws CompilerException {
        ArrayList<AstNode> child = ast.contents;
        String header = child.get(0).token.type;
        if (header.equals("assign")) {
            compile_expr(child.get(1), true);
            asm.emit("push_eax");
            compile_expr(child.get(2));
            asm.emit("assign");
        } else if (header.equals("integer") || header.equals("string")) {
            asm.emit(header, child.get(0).token.value);
        } else if (header.equals("double")) {
            asm.emit("double_", child.get(0).token.value);
        } else if (header.equals("dot")) {
            compile_expr(child.get(1));
            asm.emit("push_eax");
            asm.emit("string", child.get(2).token.value);
            asm.emit("member_ref");
            if (!byref) {
                asm.emit("deref", "%eax");
            }
        } else if (header.equals("new")) {
            _compile_funcall(ast);
            asm.emit("new_op");
        } else if (header.equals("lambda")) {
            compile_lambda(ast);
        } else if (header.equals("funcall")) {
            //prepare env
            _compile_funcall(ast);
            asm.emit("pop_env");
            asm.emit("pop");//pop params
            asm.emit("pop");//pop function body
        } else if (header.equals("inc")) {
            compile_expr(child.get(1), true); //param1
            asm.emit("inc");
        } else if (header.equals("post_inc")) {
            compile_expr(child.get(1), true); //param1
            asm.emit("post_inc");
        } else if (header.equals("dec")) {
            compile_expr(child.get(1), true); //param1
            asm.emit("dec");
        } else if (header.equals("post_dec")) {
            compile_expr(child.get(1), true); //param1
            asm.emit("post_dec");
        } else if (header.equals("undefined")) {
            asm.emit("undefined");
        } else if (header.equals("ident")) {
            if (!byref) {
                asm.emit("deref", child.get(0).token.value);
            } else {
                asm.emit("mov_eax", child.get(0).token.value);
            }
        } else if (header.equals("var")) {
            if (!child.get(1).token.type.equals("ident")) {
                throw new CompilerException(this, "var statement expect an ident");
            }
            asm.emit("clear", "%eax");
            asm.emit("set_var", child.get(1).token.value);
            if (!byref) {
                asm.emit("deref", child.get(1).token.value);
            } else {
                asm.emit("mov_eax", child.get(1).token.value);
            }
        } else if (header.equals("array")) {
            makeParam2(child);
            asm.emit("array_ref");
            if (!byref) {
                asm.emit("deref", "%eax");
            }
        } else if (header.equals("typeof")) {
            compile_expr(child.get(1)); //param1
            asm.emit("typeof");
        } else if (header.equals("null")) {
            //null is real null
            asm.emit("clear_null", "%eax");
        } else if (header.equals("true")) {
            asm.emit("integer", "1");
        } else if (header.equals("false")) {
            asm.emit("integer", "0");
        } else if (header.equals("typeof")) {
            compile_expr(child.get(1)); //param1
            asm.emit("typeof");
        } else if (header.equals("neg")) {
            compile_expr(child.get(1)); //param1
            asm.emit("neg");
        } else if (header.equals("cond")) {
            String falseLabel = asm.label("falsePart", ast);
            String exitLabel = asm.label("exitPart", ast);

            compile_expr(child.get(1));
            asm.emit("jz", falseLabel);
            compile_expr(child.get(2));
            asm.emit("jmp", exitLabel);
            asm.emit("label", falseLabel);
            compile_expr(child.get(3));
            asm.emit("label", exitLabel);
        } else if (binaryOp.contains(header)) {
            makeParam2(child);
            asm.emit(header);
        } else if (header.equals("or")) {
            String exit = asm.label("exit_or", ast);
            compile_expr(child.get(1));
            asm.emit("jnz", exit);
            compile_expr(child.get(2));
            asm.emit("label", exit);
        } else if (header.equals("and")) {
            String exit = asm.label("exit_and", ast);
            compile_expr(child.get(1));
            asm.emit("jz", exit);
            compile_expr(child.get(2));
            asm.emit("label", exit);
        } else if (header.equals("array_new")) {
            for (int i = 0; i < child.get(1).contents.size(); i++) {
                compile_expr(child.get(1).contents.get(i));
                asm.emit("push_eax");
            }
            asm.emit("array_new", child.get(1).contents.size() + "");
        } else if (header.equals("object_new")) {
            for (int i = 0; i < child.get(1).contents.size(); i++) {
                asm.emit("string", child.get(1).contents.get(i).contents.get(0).token.value);
                asm.emit("push_eax");
                compile_expr(child.get(1).contents.get(i).contents.get(1));
                asm.emit("push_eax");
            }
            asm.emit("object_new", child.get(1).contents.size() + "");
        } else {
            throw new CompilerException(this, "unknown expr \r\n" + child);
        }
    }

    void makeParam2(ArrayList<AstNode> child) throws CompilerException {
        compile_expr(child.get(1)); //param1
        asm.emit("push_eax");
        compile_expr(child.get(2)); //param2
    }

    private void compile_function_decl(AstNode ast) {
        String enter = _compile_function_body(ast);
        asm.instructions.add(0, new Instruction("static_func", ast.contents.get(0).token.value, enter));
    }

    String _compile_function_body(AstNode ast) {
        String exit = asm.label("func_exit", ast);
        String enter = asm.label("func_enter", ast);
        asm.emit("jmp", exit);
        asm.emit("label", enter);

        AstNode params = ast.getNode("param_list");

        ArrayList<AstNode> contents = params.contents;
        for (int i = 0; i < contents.size(); i++) {
            AstNode content = contents.get(i);
            asm.emit("pickarg", i + "");
            asm.emit("set_var", content.token.value, "%eax");
        }

        compile(ast.getNode("statements"));
        compile(ast.getNode("statement"));
        //default return void
        asm.emit("clear", "%eax");
        asm.emit("ret");
        asm.emit("label", exit);
        return enter;
    }

    private void compile_lambda(AstNode ast) {
        String enter = _compile_function_body(ast);
        asm.emit("new_closure", enter);
    }

    private void compile_statements(AstNode ast) {
        compile_program(ast);
    }

    private void compile_statement(AstNode ast) {
        compile_program(ast);
    }

    void _compile_nullable_expr(AstNode ast) throws CompilerException {
        if (ast.contents.size() == 1) {
            if (ast.contents.get(0).token.type.equals("NULL")) {
                asm.emit("integer", "1");
                return;
            }
        }
        compile_expr(ast);
    }

    private void compile_for_block(AstNode ast) throws CompilerException {
        String testLable = asm.label("test", ast);
        String continueLabel = asm.label("continue", ast);
        String breakLabel = asm.label("break", ast);
        breaks.push(breakLabel);
        continues.push(continueLabel);
        _compile_nullable_expr(ast.contents.get(0));
        asm.emit("label", testLable);
        _compile_nullable_expr(ast.contents.get(1));
        asm.emit("jz", breakLabel);
        compile_statements(ast.contents.get(3));
        asm.emit("label", continueLabel);
        _compile_nullable_expr(ast.contents.get(2));
        asm.emit("jmp", testLable);
        asm.emit("label", breakLabel);
        breaks.pop();
        continues.pop();
    }

    private void compile_while_block(AstNode ast) throws CompilerException {
        String testLable = asm.label("test", ast);
        String continueLabel = asm.label("continue", ast);
        String breakLabel = asm.label("break", ast);
        breaks.push(breakLabel);
        continues.push(continueLabel);

        asm.emit("label", testLable);
        compile_expr(ast.contents.get(0));
        asm.emit("jz", breakLabel);
        compile_statements(ast.contents.get(1));
        asm.emit("label", continueLabel);
        asm.emit("jmp", testLable);
        asm.emit("label", breakLabel);
        breaks.pop();
        continues.pop();
    }

    private void compile_switch_block(AstNode ast) throws CompilerException {
        String breakLabel = asm.label("break", ast);
        breaks.push(breakLabel);
        compile_expr(ast.contents.get(0));
        asm.emit("push_eax");

        String nextBody = null;
        for (int i = 0; i < ast.contents.get(1).contents.size() - 1; i++) {
            String nextLabel = asm.label("case_" + i, ast);

            compile_expr(ast.contents.get(1).contents.get(i).contents.get(0));
            asm.emit("eq");
            asm.emit("jz", nextLabel);
            if (nextBody != null) {
                asm.emit("label", nextBody);
            }
            nextBody = asm.label("body_" + i, ast);
            compile_statements(ast.contents.get(1).contents.get(i).contents.get(1));
            asm.emit("jmp", nextBody);
            asm.emit("label", nextLabel);
        }
        if (ast.contents.size() == 3) {
            //has default block
            if (nextBody != null) {
                asm.emit("label", nextBody);
            }
            compile_statements(ast.contents.get(2).contents.get(0));
        }
        breaks.pop();
        asm.emit("label", breakLabel);
    }

    private void compile_try_block(AstNode ast) {
        String catchLabel = asm.label("catch", ast);
        String finallyLabel = asm.label("finally", ast);
        String exitLabel = asm.label("exit", ast);
        finallys.push(finallyLabel);
        asm.emit("save_machine_state");
        asm.emit("test", "%exception");
        asm.emit("jnz", catchLabel);
        compile_statements(ast.contents.get(0));
        asm.emit("drop_machine_state");
        asm.emit("push_eip");
        asm.emit("jmp", finallyLabel);
        asm.emit("jmp", exitLabel);
        asm.emit("label", catchLabel);
        asm.emit("mov_eax_exception");
        asm.emit("set_var", ast.contents.get(1).token.value);
        asm.emit("clear", "%exception");
        compile_statements(ast.contents.get(2));
        asm.emit("push_eip");
        asm.emit("jmp", finallyLabel);
        asm.emit("jmp", exitLabel);
        asm.emit("label", finallyLabel);
        if (ast.contents.size() == 4) {
            compile_statements(ast.contents.get(3));
        }
        asm.emit("clear_call_stack");
        asm.emit("pop_eax");
        asm.emit("jmp", "offset", "1");
        asm.emit("label", exitLabel);
        finallys.pop();
    }

    private void compile_throw_exception(AstNode ast) throws CompilerException {
        compile_expr(ast.contents.get(0));
        asm.emit("mov_exception_eax");
        asm.emit("restore_machine_state");
    }

    private void compile_if_block(AstNode ast) throws CompilerException {
        String exitLable = asm.label("exit", ast);

        compile_expr(ast.contents.get(0));
        asm.emit("jz", exitLable);
        compile_statements(ast.contents.get(1));

        asm.emit("label", exitLable);
    }

    private void compile_if_else_block(AstNode ast) throws CompilerException {
        String exitLabel = asm.label("exit", ast);
        String elseLabel = asm.label("else", ast);
        compile_expr(ast.contents.get(0));
        asm.emit("jz", elseLabel);
        compile_statements(ast.contents.get(1));
        asm.emit("jmp", exitLabel);
        asm.emit("label", elseLabel);
        compile_statements(ast.contents.get(2));
        asm.emit("label", exitLabel);
    }

    private void compile_break(AstNode ast) throws CompilerException {
        if (breaks.isEmpty()) {
            throw new CompilerException(this, "break without loop");
        }
        asm.emit("jmp", breaks.peek());
    }

    private void compile_continue(AstNode ast) throws CompilerException {
        if (breaks.isEmpty()) {
            throw new CompilerException(this, "continue without loop");
        }
        asm.emit("jmp", continues.peek());
    }

    private void compile_hooked_break(AstNode ast) throws CompilerException {
        _compile_call_finally();
        compile_break(ast);
    }

    private void compile_hooked_continue(AstNode ast) throws CompilerException {
        _compile_call_finally();
        compile_continue(ast);
    }

    private void compile_NULL(AstNode ast) {

    }

    private void compile_return_val(AstNode ast) throws CompilerException {
        compile_expr(ast.contents.get(0));
        asm.emit("ret");
    }

    private void compile_return_void(AstNode ast) {
        asm.emit("clear", "%eax");
        asm.emit("ret");
    }

    void _compile_call_finally() {
        asm.emit("push_eip");
        asm.emit("jmp", finallys.peek());
    }

    private void compile_hooked_return_val(AstNode ast) throws CompilerException {
        _compile_call_finally();
        compile_return_val(ast);
    }

    private void compile_hooked_return_void(AstNode ast) {
        _compile_call_finally();
        compile_return_void(ast);
    }

    void _compile_funcall(AstNode ast) throws CompilerException {
        //params
        int param_n = 0;
        ArrayList<AstNode> contents = ast.contents.get(2).contents;
        for (int i = contents.size() - 1; i >= 0; i--) {
            AstNode content = contents.get(i);
            if (!content.token.type.equals("NULL")) {
                ++param_n;
                compile_expr(content);
                asm.emit("push_eax");
            }
        }
        asm.emit("packargs", param_n + "");
        asm.emit("push_eax");
        //function body
        compile_expr(ast.contents.get(1));
        asm.emit("push_eax");

        asm.emit("push_env");
        asm.emit("call");
    }
}
