package lexer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Lexer.java — RPAL Lexical Analyzer
// Reads an RPAL source file character-by-character and produces a flat list
// of tokens. Whitespace and line comments (// ... \n) are silently discarded.
public class Lexer {

    // All reserved words in RPAL.

    private static final Set<String> KEYWORDS = new HashSet<>();
    static {
        // Control / binding
        KEYWORDS.add("let");
        KEYWORDS.add("in");
        KEYWORDS.add("fn");
        KEYWORDS.add("where");
        KEYWORDS.add("aug");
        KEYWORDS.add("rec");
        KEYWORDS.add("within");
        // Boolean / logical (written as words in RPAL)
        KEYWORDS.add("and");
        KEYWORDS.add("or");
        KEYWORDS.add("not");
        // Comparison operators spelled as words
        KEYWORDS.add("eq");
        KEYWORDS.add("ne");
        KEYWORDS.add("ls");
        KEYWORDS.add("le");
        KEYWORDS.add("gr");
        KEYWORDS.add("ge");
        // Literal values
        KEYWORDS.add("true");
        KEYWORDS.add("false");
        KEYWORDS.add("nil");
        KEYWORDS.add("dummy");
    }

    // Operator character set

    private static final Set<Character> OP_CHARS = new HashSet<>();
    static {
        for (char c : "+-*<>&.@/:=~|$!#%^\\".toCharArray()) {
            OP_CHARS.add(c);
        }
    }


    /** Full source text loaded from the input file. */
    private final String source;

    /** Current read position in source (index of next char to consume). */
    private int pos;

    /** Current line number (1-based) — for error reporting. */
    private int line;


    /**
     * Load the source file and initialise the lexer.
     *
     * @param filePath path to the .rpal source file
     * @throws IOException if the file cannot be read
     */
    public Lexer(String filePath) throws IOException {
        this.source = new String(Files.readAllBytes(Paths.get(filePath)));
        this.pos = 0;
        this.line = 1;
    }

    /**
     * Alternate constructor for unit-testing: supply source text directly.
     *
     * @param source raw RPAL source code as a string
     */
    public Lexer(String source, boolean isRawSource) {
        this.source = source;
        this.pos = 0;
        this.line = 1;
    }


    // Tokenize the entire source file in one pass; list ends with an EOF token.
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = nextToken();
            tokens.add(t);
        } while (!t.isEOF());
        return tokens;
    }

    // Produce the next meaningful token; skip whitespace/comments. Returns EOF when done.
    public Token nextToken() {
        skipWhitespaceAndComments();

        if (pos >= source.length()) {
            return new Token(TokenType.EOF, "", line);
        }

        char c = peek();

        if (Character.isLetter(c)) {
            return scanIdentifierOrKeyword();
        }

        if (Character.isDigit(c)) {
            return scanInteger();
        }

        if (c == '\'') {
            return scanString();
        }

        if (c == '(' || c == ')' || c == ',' || c == ';') {
            return new Token(TokenType.PUNCTUATION, String.valueOf(advance()), line);
        }

        // ── Operator (one or more op characters) ──────────────────────────
        if (OP_CHARS.contains(c)) {
            return scanOperator();
        }

        // ── Unexpected character ──────────────────────────────────────────
        // Consume it and report an error rather than looping forever.
        char bad = advance();
        throw new LexerException(
                "Unexpected character '" + bad + "' at line " + line);
    }


    // Consume whitespace and line comments (// … end-of-line).
    private void skipWhitespaceAndComments() {
        while (pos < source.length()) {
            char c = peek();

            // Whitespace: skip and track line numbers
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '\n') {
                advance();
                line++; // increment line counter on each newline

                // Line comment: '//' … end-of-line
            } else if (c == '/' && pos + 1 < source.length()
                    && source.charAt(pos + 1) == '/') {
                // Skip both '/' characters
                advance();
                advance();
                // Skip everything up to (but not including) the newline
                while (pos < source.length() && peek() != '\n') {
                    advance();
                }
                // The newline itself will be consumed on the next iteration

            } else {
                break; // Non-whitespace, non-comment character found
            }
        }
    }


    // Scan identifier or keyword. RPAL rule: Letter (Letter | Digit | '_')*
    private Token scanIdentifierOrKeyword() {
        int startLine = line;
        StringBuilder sb = new StringBuilder();

        // First character is guaranteed to be a letter (checked in nextToken)
        sb.append(advance());

        // Consume letters, digits, and underscores
        while (pos < source.length()
                && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            sb.append(advance());
        }

        String text = sb.toString();

        // Keyword check — reserved words take priority over identifiers
        if (KEYWORDS.contains(text)) {
            return new Token(TokenType.KEYWORD, text, startLine);
        }
        return new Token(TokenType.IDENTIFIER, text, startLine);
    }

    // Scan integer literal (Digit+). Unary minus handled by parser.
    private Token scanInteger() {
        int startLine = line;
        StringBuilder sb = new StringBuilder();

        while (pos < source.length() && Character.isDigit(peek())) {
            sb.append(advance());
        }

        return new Token(TokenType.INTEGER, sb.toString(), startLine);
    }

    // Scan string literal; returned value includes surrounding single quotes.
    private Token scanString() {
        int startLine = line;
        StringBuilder sb = new StringBuilder();

        // Opening quote
        sb.append(advance()); // consume and keep the '\''

        while (pos < source.length()) {
            char c = peek();

            if (c == '\'') {
                // Closing quote — consume and stop
                sb.append(advance());
                return new Token(TokenType.STRING, sb.toString(), startLine);
            }

            if (c == '\n') {
                // Unterminated string (newlines are not allowed raw in strings)
                throw new LexerException(
                        "Unterminated string literal at line " + startLine);
            }

            if (c == '\\') {
                // Escape sequence — keep the backslash and the next char as-is
                // (the CSE machine / evaluator will interpret them at runtime)
                sb.append(advance()); // backslash
                if (pos < source.length()) {
                    sb.append(advance()); // escaped character
                }
                continue;
            }

            // Ordinary printable character
            sb.append(advance());
        }

        // Reached end of file without closing quote
        throw new LexerException(
                "Unterminated string literal starting at line " + startLine);
    }

    // Scan operator token (one or more operator characters).
    private Token scanOperator() {
        int startLine = line;
        StringBuilder sb = new StringBuilder();

        while (pos < source.length() && OP_CHARS.contains(peek())) {
            // Stop before a '//' sequence — that would be a comment start,
            // but only if we have not yet consumed any characters (handled
            // in skip). If we ARE mid-operator (e.g. scanning "//"),
            // comments are already stripped, so this case won't occur.
            sb.append(advance());
        }

        return new Token(TokenType.OPERATOR, sb.toString(), startLine);
    }

    // Return current character without advancing.
    private char peek() {
        return source.charAt(pos);
    }

    // Return current character and advance pos by one.
    private char advance() {
        return source.charAt(pos++);
    }


    // Exception thrown for lexer errors.
    public static class LexerException extends RuntimeException {
        public LexerException(String message) {
            super(message);
        }
    }


    // Quick standalone test: java lexer.Lexer <file>
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java lexer.Lexer <rpal-source-file>");
            System.exit(1);
        }

        Lexer lexer = new Lexer(args[0]);
        List<Token> tokens = lexer.tokenize();

        System.out.println("=== Tokens for: " + args[0] + " ===");
        for (Token t : tokens) {
            System.out.println(t);
        }
        System.out.println("Total: " + tokens.size() + " tokens");
    }
}