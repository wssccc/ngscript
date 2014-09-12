/*
 *  wssccc all rights reserved
 */
package ngscript.compiler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import ngscript.common.Instruction;
import parseroid.parser.AstNode;

/**
 *
 * @author wssccc <wssccc@qq.com>
 */
public class WscCompiler {

    WscAssembler asm;
    Scanner sc;
    int printedLines = 0;

    Stack<String> continueStack = new Stack<String>();
    Stack<String> breakStack = new Stack<String>();
    Stack<String> finallyStack = new Stack<String>();

    public WscCompiler(String namespace) {
        asm = new WscAssembler(namespace);
    }

    public ArrayList<Instruction> getCompiledInstructions() {
        return asm.instructions;
    }

    public WscAssembler getAssembler() {
        return asm;
    }

    public void compileCode(AstNode ast, String referCode) {
        asm.instructions.clear();
        sc = new Scanner(referCode);
        printedLines = -1;
        compile(ast);
        while (sc.hasNextLine()) {
            asm.emit("//", sc.nextLine(), "" + printedLines);
        }
    }

    void printDebug(AstNode ast) {
        if (printedLines != ast.token.line) {
            while (printedLines < ast.token.line) {
                asm.emit("//", sc.hasNextLine() ? sc.nextLine() : "no line", "" + printedLines);
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
        } catch (NoSuchMethodException ex) {
            Logger.getLogger(WscCompiler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            Logger.getLogger(WscCompiler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IllegalArgumentException ex) {
            Logger.getLogger(WscCompiler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            Logger.getLogger(WscCompiler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(WscCompiler.class.getName()).log(Level.SEVERE, null, ex);
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

    private void compile_expr(AstNode ast) throws WscCompilerException {
        compile_expr(ast, false);
    }

    private void compile_expr(AstNode ast, boolean byref) throws WscCompilerException {
        printDebug(ast);
        String type = ast.token.type;
        if (ast.token.type.equals("expr")) {
            _compile_expr_opr(ast, byref);
        } else if (ast.token.type.equals("NULL")) {
        } else {
            throw new WscCompilerException(this, "manual dispatch error " + ast);
        }
    }

    public void _compile_expr_opr(AstNode ast, boolean byref) throws WscCompilerException {
        ArrayList<AstNode> child = ast.contents;
        String header = child.get(0).token.type;
        if (header.equals("assign")) {
            compile_expr(child.get(1), true);
            asm.emit("push", "%eax");
            compile_expr(child.get(2));
            asm.emit("assign");
        } else if (header.equals("integer") || header.equals("string")) {
            asm.emit(header, child.get(0).token.value);
        } else if (header.equals("double")) {
            asm.emit("double_", child.get(0).token.value);
        } else if (header.equals("dot")) {
            compile_expr(child.get(1));
            asm.emit("push", "%eax");
            asm.emit("string", child.get(2).token.value);
            asm.emit("member_ref");
            if (!byref) {
                asm.emit("mov", "%eax", "[%eax]");
            }
        } else if (header.equals("mul") || header.equals("mod") || header.equals("div") || header.equals("add") || header.equals("sub")) {
            makeParam2(child);
            asm.emit(header);
        } else if (header.equals("new")) {
            _compile_funcall(ast);
            asm.emit("new_op");

        } else if (header.equals("lambda")) {
            compile_lambda(ast);
        } else if (header.equals("funcall")) {
            //prepare env
            _compile_funcall(ast);
            asm.emit("pop", "%env");
            //asm.emit("mov", "env", "%eax");
            asm.emit("pop");//pop params
            asm.emit("pop");//pop function body
        } else if (header.equals("inc")) {
            compile_expr(child.get(1), true); //param1
            asm.emit("inc");
        } else if (header.equals("ident")) {
            if (!byref) {
                asm.emit("mov", "%eax", '[' + child.get(0).token.value + ']');
            } else {
                asm.emit("mov", "%eax", child.get(0).token.value);
            }
        } else if (header.equals("var")) {
            if (!child.get(1).token.type.equals("ident")) {
                throw new WscCompilerException(this, "var statement expect an ident");
            }
            asm.emit("clear", "%eax");
            asm.emit("set_var", child.get(1).token.value);
            if (!byref) {
                asm.emit("mov", "%eax", '[' + child.get(1).token.value + ']');
            } else {
                asm.emit("mov", "%eax", child.get(1).token.value);
            }
        } else if (header.equals("array")) {
            makeParam2(child);
            asm.emit("array_ref");
            if (!byref) {
                asm.emit("mov", "%eax", "[%eax]");
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
        } else if (header.equals("eq") || header.equals("neq") || header.equals("lt") || header.equals("gt") || header.equals("le") || header.equals("ge")) {
            makeParam2(child);
            asm.emit(header);
        } else {
            throw new WscCompilerException(this, "unknown expr " + child);
        }
    }

    void makeParam2(ArrayList<AstNode> child) throws WscCompilerException {
        compile_expr(child.get(1)); //param1
        asm.emit("push", "%eax");
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

        for (AstNode content : params.contents) {
            asm.emit("dequeue", "stack", "3");
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

    void _compile_nullable_expr(AstNode ast) throws WscCompilerException {
        if (ast.contents.size() == 1) {
            if (ast.contents.get(0).token.type.equals("NULL")) {
                asm.emit("integer", "1");
                return;
            }
        }
        compile_expr(ast);
    }

    private void compile_for_block(AstNode ast) throws WscCompilerException {
        String testLable = asm.label("test", ast);
        String continueLabel = asm.label("continue", ast);
        String breakLabel = asm.label("break", ast);
        breakStack.push(breakLabel);
        continueStack.push(continueLabel);
        _compile_nullable_expr(ast.contents.get(0));
        asm.emit("label", testLable);
        _compile_nullable_expr(ast.contents.get(1));
        asm.emit("jz", breakLabel);
        compile_statements(ast.contents.get(3));
        asm.emit("label", continueLabel);
        _compile_nullable_expr(ast.contents.get(2));
        asm.emit("jmp", testLable);
        asm.emit("label", breakLabel);
        breakStack.pop();
        continueStack.pop();
    }

    private void compile_while_block(AstNode ast) throws WscCompilerException {
        String testLable = asm.label("test", ast);
        String continueLabel = asm.label("continue", ast);
        String breakLabel = asm.label("break", ast);
        breakStack.push(breakLabel);
        continueStack.push(continueLabel);

        asm.emit("label", testLable);
        compile_expr(ast.contents.get(0));
        asm.emit("jz", breakLabel);
        compile_statements(ast.contents.get(1));
        asm.emit("label", continueLabel);
        asm.emit("jmp", testLable);
        asm.emit("label", breakLabel);
        breakStack.pop();
        continueStack.pop();
    }

    private void compile_switch_block(AstNode ast) throws WscCompilerException {
        String breakLabel = asm.label("break", ast);
        breakStack.push(breakLabel);
        compile_expr(ast.contents.get(0));
        asm.emit("push", "%eax");

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
        breakStack.pop();
        asm.emit("label", breakLabel);
    }

    private void compile_try_block(AstNode ast) {
        String catchLabel = asm.label("catch", ast);
        String finallyLabel = asm.label("finally", ast);
        String exitLabel = asm.label("exit", ast);
        finallyStack.push(finallyLabel);
        asm.emit("save_machine_state");
        asm.emit("test", "%exception");
        asm.emit("jnz", catchLabel);
        compile_statements(ast.contents.get(0));
        asm.emit("drop_machine_state");
        asm.emit("push", "%eip");
        asm.emit("jmp", finallyLabel);
        asm.emit("jmp", exitLabel);
        asm.emit("label", catchLabel);
        asm.emit("mov", "%eax", "%exception");
        asm.emit("set_var", ast.contents.get(1).token.value);
        asm.emit("clear", "%exception");
        compile_statements(ast.contents.get(2));
        asm.emit("push", "%eip");
        asm.emit("jmp", finallyLabel);
        asm.emit("jmp", exitLabel);
        asm.emit("label", finallyLabel);
        if (ast.contents.size() == 4) {
            compile_statements(ast.contents.get(3));
        }
        asm.emit("clear_call_stack");
        asm.emit("pop", "%eax");
        asm.emit("jmp", "offset", "1");
        asm.emit("label", exitLabel);
        finallyStack.pop();
    }

    private void compile_throw_exception(AstNode ast) throws WscCompilerException {
        compile_expr(ast.contents.get(0));
        asm.emit("mov", "%exception", "%eax");
        asm.emit("restore_machine_state");
    }

    private void compile_if_block(AstNode ast) throws WscCompilerException {
        String exitLable = asm.label("exit", ast);

        compile_expr(ast.contents.get(0));
        asm.emit("jz", exitLable);
        compile_statements(ast.contents.get(1));

        asm.emit("label", exitLable);
    }

    private void compile_if_else_block(AstNode ast) throws WscCompilerException {
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

    private void compile_break(AstNode ast) throws WscCompilerException {
        if (breakStack.isEmpty()) {
            throw new WscCompilerException(this, "break without loop");
        }
        asm.emit("jmp", breakStack.peek());
    }

    private void compile_continue(AstNode ast) throws WscCompilerException {
        if (breakStack.isEmpty()) {
            throw new WscCompilerException(this, "continue without loop");
        }
        asm.emit("jmp", continueStack.peek());
    }

    private void compile_hooked_break(AstNode ast) throws WscCompilerException {
        _compile_call_finally();
        compile_break(ast);
    }

    private void compile_hooked_continue(AstNode ast) throws WscCompilerException {
        _compile_call_finally();
        compile_continue(ast);
    }

    private void compile_NULL(AstNode ast) {

    }

    private void compile_return_val(AstNode ast) throws WscCompilerException {
        compile_expr(ast.contents.get(0));
        asm.emit("ret");
    }

    private void compile_return_void(AstNode ast) {
        asm.emit("clear", "%eax");
        asm.emit("ret");
    }

    void _compile_call_finally() {
        asm.emit("push", "%eip");
        asm.emit("jmp", finallyStack.peek());
    }

    private void compile_hooked_return_val(AstNode ast) throws WscCompilerException {
        _compile_call_finally();
        compile_return_val(ast);
    }

    private void compile_hooked_return_void(AstNode ast) {
        _compile_call_finally();
        compile_return_void(ast);
    }

    void _compile_funcall(AstNode ast) throws WscCompilerException {
        //params
        int param_n = 0;
        for (AstNode content : ast.contents.get(2).contents) {
            if (!content.token.type.equals("NULL")) {
                ++param_n;
                compile_expr(content);
                asm.emit("push", "%eax");
            }
        }
        asm.emit("new_queue", param_n + "");
        asm.emit("push", "%eax");
        //function body
        compile_expr(ast.contents.get(1));
        asm.emit("push", "%eax");

        asm.emit("push", "%env");
        asm.emit("call");
    }
}
