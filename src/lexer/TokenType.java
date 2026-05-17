package lexer;

/**
 * TokenType.java
 * 
 * Defines all possible token categories produced by the RPAL Lexer.
 *
 * RPAL Lexical categories (from RPAL_Lex.pdf):
 *   Identifier  →  Letter (Letter | Digit | '_')*
 *   Integer     →  Digit+
 *   String      →  ' (any printable char except ')* '
 *   Keyword     →  reserved words (subset of identifiers)
 *   Operator    →  sequence of operator characters
 *   Punctuation →  ( ) , ;
 *   EOF         →  end of input
 */
public enum TokenType {

    // ── Literals ──────────────────────────────────────────────────────────────

    /** Variable and function names: x, myFunc, Print, Order */
    IDENTIFIER,

    /** Whole-number literals: 0, 42, 1000 */
    INTEGER,

    /** Single-quoted string literals: 'hello', 'it''s' */
    STRING,

    // ── Reserved words ────────────────────────────────────────────────────────

    /**
     * RPAL keywords — these look like identifiers lexically but are reserved.
     * Full list: let in fn where aug rec and or not true false nil
     *            dummy within eq ne ls le gr ge
     */
    KEYWORD,

    // ── Operators ─────────────────────────────────────────────────────────────

    /**
     * One or more consecutive operator characters.
     * Operator chars: + - * < > & . @ / : = ~ | $ ! # % ^ \
     * Examples: +  ->  **  >=  &  @
     */
    OPERATOR,

    // ── Punctuation ───────────────────────────────────────────────────────────

    /**
     * Single-character delimiters: ( ) , ;
     * These are NOT operator characters in RPAL.
     */
    PUNCTUATION,

    // ── End of file ───────────────────────────────────────────────────────────

    /**
     * Sentinel token returned once the entire source has been consumed.
     * The parser checks for EOF to know when to stop.
     */
    EOF
}