package lexer;

/**
 * Token.java
 *
 * A single lexical unit produced by the Lexer.
 * Every token has a type (what category it belongs to) and a value
 * (the exact text from the source file).
 *
 * Examples:
 *   Token(KEYWORD,     "let")
 *   Token(IDENTIFIER,  "myFunc")
 *   Token(INTEGER,     "42")
 *   Token(STRING,      "'hello world'")   ← includes the surrounding quotes
 *   Token(OPERATOR,    "->")
 *   Token(PUNCTUATION, "(")
 *   Token(EOF,         "")
 */
public class Token {

    // ── Fields ────────────────────────────────────────────────────────────────

    /** What kind of token this is. */
    public final TokenType type;

    /**
     * The raw text of the token as it appeared in the source.
     * For STRING tokens this includes the surrounding single quotes.
     * For EOF this is an empty string.
     */
    public final String value;

    /**
     * Source line number (1-based) — useful for error messages.
     * Set by the Lexer when the token is created.
     */
    public final int line;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Create a new token.
     *
     * @param type   the lexical category
     * @param value  the matched source text
     * @param line   the source line where this token starts
     */
    public Token(TokenType type, String value, int line) {
        this.type  = type;
        this.value = value;
        this.line  = line;
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /** True if this token is the end-of-file sentinel. */
    public boolean isEOF() {
        return type == TokenType.EOF;
    }

    /**
     * True if this token is a keyword with the given text.
     * Example: token.isKeyword("let")
     */
    public boolean isKeyword(String word) {
        return type == TokenType.KEYWORD && value.equals(word);
    }

    /**
     * True if this token is an operator with the given symbol.
     * Example: token.isOperator("->")
     */
    public boolean isOperator(String op) {
        return type == TokenType.OPERATOR && value.equals(op);
    }

    /**
     * True if this token is a punctuation character.
     * Example: token.isPunct("(")
     */
    public boolean isPunct(String ch) {
        return type == TokenType.PUNCTUATION && value.equals(ch);
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("Token(%-12s | %-15s | line %d)",
                type, "'" + value + "'", line);
    }
}