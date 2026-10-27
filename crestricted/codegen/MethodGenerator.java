package crestricted.codegen;

import crestricted.CompileError;
import crestricted.ast.Ast.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MethodGenerator {
    private final Function function;
    private final String className;
    private final Map<String, Function> functions;
    private final StringBuilder code = new StringBuilder();
    private final Deque<Map<String, Integer>> scopes = new ArrayDeque<>();
    private Set<Integer> initialized = new HashSet<>();
    private int nextLocal, maxStack, label;

    MethodGenerator(Function function, String className, Map<String, Function> functions) {
        this.function = function;
        this.className = className;
        this.functions = functions;
    }

    String generate() {
        List<Stmt> statements = function.body().statements();
        if (statements.isEmpty() || !(statements.get(statements.size() - 1) instanceof Return)) {
            throw new CompileError("function '" + function.name() + "' must end with return expression");
        }
        enterScope();
        for (String param : function.params()) initialized.add(declare(param));
        for (int i = 0; i < statements.size() - 1; i++) statement(statements.get(i));
        expression(((Return) statements.get(statements.size() - 1)).value());
        emit("ireturn");
        leaveScope();
        return finish();
    }

    private String finish() {
        StringBuilder output = new StringBuilder();
        if (maxStack > 65535) throw new CompileError("function '" + function.name() + "' exceeds the JVM stack limit");
        output.append(".method public static ").append(function.name())
                .append('(').append("I".repeat(function.params().size())).append(")I\n")
                .append("  .limit stack ").append(Math.max(1, maxStack)).append('\n')
                .append("  .limit locals ").append(nextLocal).append('\n');
        output.append(code).append(".end method\n\n");
        return output.toString();
    }

    private void enterScope() { scopes.push(new HashMap<>()); }
    private void leaveScope() { initialized.removeAll(scopes.pop().values()); }
    private int declare(String name) {
        if (scopes.peek().containsKey(name)) throw new CompileError("duplicate local variable '" + name + "' in function '" + function.name() + "'");
        if (nextLocal >= 65535) throw new CompileError("function '" + function.name() + "' exceeds the JVM local variable limit");
        scopes.peek().put(name, nextLocal);
        return nextLocal++;
    }
    private int local(String name) {
        for (Map<String, Integer> scope : scopes) { Integer index = scope.get(name); if (index != null) return index; }
        throw new CompileError("unknown variable '" + name + "' in function '" + function.name() + "'");
    }

    private void emit(String line) { code.append("  ").append(line).append('\n'); }
    private void mark(String name) { code.append(name).append(":\n"); }
    private String fresh() { return "L" + label++; }

    private void statement(Stmt stmt) {
        if (stmt instanceof Block block) {
            enterScope();
            for (Stmt child : block.statements()) statement(child);
            leaveScope();
        } else if (stmt instanceof Decl decl) {
            int index = declare(decl.name());
            if (decl.initializer() != null) {
                expression(decl.initializer()); emit("istore " + index); initialized.add(index);
            }
        } else if (stmt instanceof Assign assign) {
            int index = local(assign.name());
            expression(assign.value()); emit("istore " + index); initialized.add(index);
        } else if (stmt instanceof Printf printf) {
            emit("getstatic java/lang/System/out Ljava/io/PrintStream;");
            maxStack = Math.max(maxStack, 1 + expression(printf.value()));
            emit("invokevirtual java/io/PrintStream/" + (printf.newline() ? "println" : "print") + "(I)V");
        } else if (stmt instanceof Scanf scanf) {
            int index = local(scanf.name());
            emit("getstatic " + className + "/_scanner Ljava/util/Scanner;");
            emit("invokevirtual java/util/Scanner/nextInt()I");
            emit("istore " + index); initialized.add(index);
            maxStack = Math.max(maxStack, 1);
        } else if (stmt instanceof Return) {
            throw new CompileError("return is allowed only as the final statement of function '" + function.name() + "'");
        } else if (stmt instanceof If iff) {
            String otherwise = fresh(), end = fresh();
            expression(iff.test()); emit("ifeq " + otherwise);
            Set<Integer> before = new HashSet<>(initialized);
            statement(iff.thenPart());
            Set<Integer> afterThen = new HashSet<>(initialized);
            initialized = new HashSet<>(before);
            if (iff.elsePart() != null) {
                emit("goto " + end); mark(otherwise); statement(iff.elsePart()); mark(end);
                initialized.retainAll(afterThen);
            } else mark(otherwise);
        } else if (stmt instanceof While loop) {
            String begin = fresh(), end = fresh(); mark(begin); expression(loop.test()); emit("ifeq " + end);
            Set<Integer> before = new HashSet<>(initialized);
            statement(loop.body()); emit("goto " + begin); mark(end);
            initialized = before;
        }
    }

    private int expression(Expr expr) {
        int depth = 1;
        if (expr instanceof IntLit number) integer(number.value());
        else if (expr instanceof Var variable) {
            int index = local(variable.name());
            if (!initialized.contains(index)) throw new CompileError("variable '" + variable.name() + "' may be uninitialized in function '" + function.name() + "'");
            emit("iload " + index);
        } else if (expr instanceof UnaryMinus minus) { depth = expression(minus.value()); emit("ineg"); }
        else if (expr instanceof Call call) {
            for (Map<String, Integer> scope : scopes) {
                if (scope.containsKey(call.name())) throw new CompileError("local variable '" + call.name() + "' is not a function");
            }
            Function target = functions.get(call.name());
            if (target == null) throw new CompileError("unknown function '" + call.name() + "'");
            if (target.params().size() != call.args().size()) throw new CompileError("function '" + call.name() + "' expects " + target.params().size() + " argument(s)");
            for (int i = 0; i < call.args().size(); i++) depth = Math.max(depth, i + expression(call.args().get(i)));
            emit("invokestatic " + className + "/" + call.name() + "(" + "I".repeat(call.args().size()) + ")I");
        } else if (expr instanceof Binary binary) {
            int leftDepth = expression(binary.left());
            depth = Math.max(leftDepth, 1 + expression(binary.right()));
            switch (binary.op()) {
                case "+" -> emit("iadd"); case "-" -> emit("isub"); case "*" -> emit("imul");
                default -> comparison(binary.op());
            }
        }
        maxStack = Math.max(maxStack, depth);
        return depth;
    }

    private void comparison(String op) {
        String trueLabel = fresh(), end = fresh();
        String instruction = switch (op) { case "==" -> "if_icmpeq"; case ">" -> "if_icmpgt"; case "<" -> "if_icmplt"; case ">=" -> "if_icmpge"; case "<=" -> "if_icmple"; default -> throw new CompileError("unknown operator " + op); };
        emit(instruction + " " + trueLabel); emit("iconst_0"); emit("goto " + end); mark(trueLabel); emit("iconst_1"); mark(end);
    }

    private void integer(int value) {
        if (value >= -1 && value <= 5) emit(value == -1 ? "iconst_m1" : "iconst_" + value);
        else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) emit("bipush " + value);
        else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) emit("sipush " + value);
        else emit("ldc " + value);
    }
}
