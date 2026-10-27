package crestricted.ast;

import java.util.List;

public final class Ast {
    private Ast() { }

    public record Program(List<Function> functions) { }
    public record Function(String name, List<String> params, Block body) { }

    public sealed interface Stmt permits Block, Decl, Assign, If, While, Return, Printf, Scanf, Empty { }
    public record Block(List<Stmt> statements) implements Stmt { }
    public record Decl(String name, Expr initializer) implements Stmt { }
    public record Assign(String name, Expr value) implements Stmt { }
    public record If(Expr test, Stmt thenPart, Stmt elsePart) implements Stmt { }
    public record While(Expr test, Stmt body) implements Stmt { }
    public record Return(Expr value) implements Stmt { }
    public record Printf(Expr value, boolean newline) implements Stmt { }
    public record Scanf(String name) implements Stmt { }
    public record Empty() implements Stmt { }

    public sealed interface Expr permits IntLit, Var, UnaryMinus, Binary, Call { }
    public record IntLit(int value) implements Expr { }
    public record Var(String name) implements Expr { }
    public record UnaryMinus(Expr value) implements Expr { }
    public record Binary(String op, Expr left, Expr right) implements Expr { }
    public record Call(String name, List<Expr> args) implements Expr { }
}
