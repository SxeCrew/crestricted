package crestricted.parser;

import crestricted.CompileError;
import crestricted.ast.Ast.*;
import crestricted.lexer.Token;
import crestricted.lexer.Token.Kind;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Parser {
    private static final Set<String> RESERVED = Set.of(
            "int", "if", "else", "while", "return", "printf", "scanf", "void", "char", "float", "double",
            "long", "short", "signed", "unsigned", "for", "do", "break", "continue", "switch", "case",
            "default", "struct", "union", "enum", "typedef", "sizeof", "const", "volatile", "static",
            "extern", "auto", "register", "goto", "inline", "restrict");
    private final List<Token> tokens;
    private int current;
    public Parser(List<Token> tokens) { this.tokens = tokens; }

    public Program parseProgram() {
        if (accept("#")) { expect("include"); expect("<"); expect("stdio"); expect("."); expect("h"); expect(">"); }
        List<Function> functions = new ArrayList<>();
        while (!at(Kind.EOF)) functions.add(parseFunction());
        if (functions.isEmpty()) error(peek(), "a program needs at least one function");
        return new Program(functions);
    }

    private Function parseFunction() {
        expect("int");
        String name = identifier();
        expect("(");
        List<String> params = new ArrayList<>();
        if (!at(")")) {
            do { expect("int"); params.add(identifier()); } while (accept(","));
        }
        expect(")");
        return new Function(name, params, block());
    }

    private Block block() {
        expect("{");
        List<Stmt> statements = new ArrayList<>();
        while (!at("}")) { if (at(Kind.EOF)) error(peek(), "expected '}'"); statements.add(statement()); }
        expect("}");
        return new Block(statements);
    }

    private Stmt statement() {
        if (at("{")) return block();
        if (accept(";")) return new Empty();
        if (accept("int")) {
            String name = identifier();
            Expr init = accept("=") ? expression() : null;
            expect(";"); return new Decl(name, init);
        }
        if (accept("if")) {
            expect("("); Expr test = expression(); expect(")"); Stmt thenPart = controlledStatement();
            return new If(test, thenPart, accept("else") ? controlledStatement() : null);
        }
        if (accept("while")) { expect("("); Expr test = expression(); expect(")"); return new While(test, controlledStatement()); }
        if (accept("return")) { Expr value = expression(); expect(";"); return new Return(value); }
        if (accept("printf")) {
            expect("("); String format = string();
            if (!"%d".equals(format) && !"%d\n".equals(format)) error(previous(), "printf supports only formats \"%d\" and \"%d\\n\"");
            expect(","); Expr value = expression(); expect(")"); expect(";"); return new Printf(value, format.endsWith("\n"));
        }
        if (accept("scanf")) {
            expect("("); String format = string(); if (!"%d".equals(format)) error(previous(), "scanf supports only format \"%d\"");
            expect(","); expect("&"); String name = identifier(); expect(")"); expect(";"); return new Scanf(name);
        }
        String name = identifier();
        expect("="); Expr value = expression(); expect(";"); return new Assign(name, value);
    }

    private Stmt controlledStatement() {
        if (at("int")) error(peek(), "a declaration in if/while requires a braced block");
        return statement();
    }

    private Expr expression() {
        Expr result = relational();
        while (accept("==")) result = new Binary("==", result, relational());
        return result;
    }
    private Expr relational() {
        Expr result = additive();
        while (at(">") || at("<") || at(">=") || at("<=")) {
            String op = take().text(); result = new Binary(op, result, additive());
        }
        return result;
    }
    private Expr additive() {
        Expr result = multiplicative();
        while (at("+") || at("-")) { String op = take().text(); result = new Binary(op, result, multiplicative()); }
        return result;
    }
    private Expr multiplicative() {
        Expr result = unary();
        while (accept("*")) result = new Binary("*", result, unary());
        return result;
    }
    private Expr unary() {
        if (!accept("-")) return primary();
        if (at(Kind.NUMBER) && new BigInteger(peek().text()).equals(BigInteger.valueOf(2147483648L))) {
            take(); return new IntLit(Integer.MIN_VALUE);
        }
        return new UnaryMinus(unary());
    }
    private Expr primary() {
        if (at(Kind.NUMBER)) {
            Token number = take();
            try { return new IntLit(Integer.parseInt(number.text())); }
            catch (NumberFormatException e) { error(number, "integer is outside the int range"); }
        }
        if (accept("(")) { Expr result = expression(); expect(")"); return result; }
        String id = identifier();
        if (!accept("(")) return new Var(id);
        List<Expr> args = new ArrayList<>();
        if (!at(")")) { do { args.add(expression()); } while (accept(",")); }
        expect(")"); return new Call(id, args);
    }

    private boolean at(String text) { return peek().text().equals(text); }
    private boolean at(Kind kind) { return peek().kind() == kind; }
    private boolean accept(String text) { if (!at(text)) return false; current++; return true; }
    private Token expect(String text) { if (!at(text)) error(peek(), "expected '" + text + "'"); return take(); }
    private Token expectKind(Kind kind, String what) { if (!at(kind)) error(peek(), "expected " + what); return take(); }
    private String identifier() {
        Token token = expectKind(Kind.ID, "identifier");
        if (RESERVED.contains(token.text())) error(token, "reserved name '" + token.text() + "' cannot be an identifier");
        return token.text();
    }
    private String string() { return expectKind(Kind.STRING, "string literal").text(); }
    private Token take() { return tokens.get(current++); }
    private Token peek() { return tokens.get(current); }
    private Token previous() { return tokens.get(current - 1); }
    private static void error(Token token, String message) { throw new CompileError(token.line() + ":" + token.column() + ": " + message); }
}
