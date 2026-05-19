package lexer;

// Token.java
// A single lexical unit produced by the Lexer. Every token has a type and a value.

public class Token {


    public final TokenType type;


    public final String value;

    public final int line;


    /**
     * Create a new token.
     *
     * @param type  the lexical category
     * @param value the matched source text
     * @param line  the source line where this token starts
     */
    public Token(TokenType type, String value, int line) {
        this.type = type;
        this.value = value;
        this.line = line;
    }


    public boolean isEOF() {
        return type == TokenType.EOF;
    }

    public boolean isKeyword(String word) {
        return type == TokenType.KEYWORD && value.equals(word);
    }

    public boolean isOperator(String op) {
        return type == TokenType.OPERATOR && value.equals(op);
    }


    public boolean isPunct(String ch) {
        return type == TokenType.PUNCTUATION && value.equals(ch);
    }

    @Override
    public String toString() {
        return String.format("Token(%-12s | %-15s | line %d)",
                type, "'" + value + "'", line);
    }
}