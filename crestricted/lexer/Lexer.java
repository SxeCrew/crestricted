package crestricted.lexer;

import crestricted.CompileError;
import crestricted.lexer.Token.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class Lexer {
    private final String input;
    private int pos, line = 1, column = 1;
    public Lexer(String input) { this.input = input.startsWith("\uFEFF") ? input.substring(1) : input; }

    public List<Token> scan() {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (Character.isWhitespace(ch)) { advance(); continue; }
            if (ch == '/' && peek(1) == '/') { while (pos < input.length() && input.charAt(pos) != '\n') advance(); continue; }
            if (ch == '/' && peek(1) == '*') { advance(); advance(); skipComment(); continue; }
            int startLine = line, startColumn = column;
            if (Character.isLetter(ch) || ch == '_') {
                StringBuilder word = new StringBuilder();
                while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) word.append(take());
                tokens.add(new Token(Kind.ID, word.toString(), startLine, startColumn));
            } else if (Character.isDigit(ch)) {
                StringBuilder number = new StringBuilder();
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) number.append(take());
                tokens.add(new Token(Kind.NUMBER, number.toString(), startLine, startColumn));
            } else if (ch == '"') {
                advance();
                StringBuilder string = new StringBuilder();
                while (pos < input.length() && input.charAt(pos) != '"') {
                    if (input.charAt(pos) == '\n' || input.charAt(pos) == '\r') fail("unterminated string", startLine, startColumn);
                    if (input.charAt(pos) == '\\') {
                        advance();
                        if (pos == input.length()) fail("unterminated string", startLine, startColumn);
                        char escape = take();
                        if (escape != 'n') fail("unsupported string escape", line, column - 1);
                        string.append('\n');
                    } else string.append(take());
                }
                if (pos == input.length()) fail("unterminated string", startLine, startColumn);
                advance();
                tokens.add(new Token(Kind.STRING, string.toString(), startLine, startColumn));
            } else {
                String two = "" + ch + peek(1);
                if (Set.of("==", ">=", "<=", "++", "--").contains(two)) { advance(); advance(); tokens.add(new Token(Kind.SYMBOL, two, startLine, startColumn)); }
                else if ("#<>.,;(){}=+-*&".indexOf(ch) >= 0) { advance(); tokens.add(new Token(Kind.SYMBOL, String.valueOf(ch), startLine, startColumn)); }
                else fail("unexpected character '" + ch + "'", startLine, startColumn);
            }
        }
        tokens.add(new Token(Kind.EOF, "", line, column));
        return tokens;
    }

    private char peek(int offset) { return pos + offset < input.length() ? input.charAt(pos + offset) : '\0'; }
    private char take() { char ch = input.charAt(pos); advance(); return ch; }
    private void advance() { if (input.charAt(pos++) == '\n') { line++; column = 1; } else column++; }
    private void skipComment() {
        while (pos < input.length() && !(input.charAt(pos) == '*' && peek(1) == '/')) advance();
        if (pos == input.length()) fail("unterminated block comment", line, column);
        advance(); advance();
    }
    private static void fail(String message, int line, int column) { throw new CompileError(line + ":" + column + ": " + message); }
}
