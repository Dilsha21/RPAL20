package lexer;

import java.util.List;

/**
 * LexerTest.java  —  Unit tests for the RPAL Lexer
 *
 * Run with:  java lexer.LexerTest
 *
 * Tests cover every token type and edge case:
 *   identifiers, keywords, integers, strings,
 *   operators, punctuation, comments, whitespace,
 *   and the sample program from the project spec.
 */
public class LexerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testIdentifiers();
        testKeywords();
        testIntegers();
        testStrings();
        testOperators();
        testPunctuation();
        testComments();
        testWhitespace();
        testMixedExpression();
        testSampleProgram();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    // ── Test groups ───────────────────────────────────────────────────────────

    static void testIdentifiers() {
        section("Identifiers");
        check("x",          1, TokenType.IDENTIFIER, "x");
        check("myVar",      1, TokenType.IDENTIFIER, "myVar");
        check("Psum",       1, TokenType.IDENTIFIER, "Psum");
        check("x_1",        1, TokenType.IDENTIFIER, "x_1");
        check("Order",      1, TokenType.IDENTIFIER, "Order");
        check("Print",      1, TokenType.IDENTIFIER, "Print");
    }

    static void testKeywords() {
        section("Keywords");
        // Every RPAL keyword must be classified as KEYWORD, not IDENTIFIER
        for (String kw : new String[]{
            "let","in","fn","where","aug","rec","and","or","not",
            "true","false","nil","dummy","within","eq","ne","ls","le","gr","ge"
        }) {
            check(kw, 1, TokenType.KEYWORD, kw);
        }
        // 'letting' starts with a keyword prefix but is an identifier
        check("letting",   1, TokenType.IDENTIFIER, "letting");
        check("infix",     1, TokenType.IDENTIFIER, "infix");
        check("notok",     1, TokenType.IDENTIFIER, "notok");
    }

    static void testIntegers() {
        section("Integers");
        check("0",          1, TokenType.INTEGER, "0");
        check("42",         1, TokenType.INTEGER, "42");
        check("12345",      1, TokenType.INTEGER, "12345");
    }

    static void testStrings() {
        section("Strings");
        check("'hello'",         1, TokenType.STRING, "'hello'");
        check("''",              1, TokenType.STRING, "''");
        check("'hello world'",   1, TokenType.STRING, "'hello world'");
        check("'it\\'s fine'",   1, TokenType.STRING, "'it\\'s fine'");
        check("'tab\\there'",    1, TokenType.STRING, "'tab\\there'");
    }

    static void testOperators() {
        section("Operators");
        check("+",   1, TokenType.OPERATOR, "+");
        check("-",   1, TokenType.OPERATOR, "-");
        check("*",   1, TokenType.OPERATOR, "*");
        check("/",   1, TokenType.OPERATOR, "/");
        check("**",  1, TokenType.OPERATOR, "**");
        check("->",  1, TokenType.OPERATOR, "->");
        check("=",   1, TokenType.OPERATOR, "=");
        check("&",   1, TokenType.OPERATOR, "&");
        check("@",   1, TokenType.OPERATOR, "@");
        check("|",   1, TokenType.OPERATOR, "|");
    }

    static void testPunctuation() {
        section("Punctuation");
        check("(",   1, TokenType.PUNCTUATION, "(");
        check(")",   1, TokenType.PUNCTUATION, ")");
        check(",",   1, TokenType.PUNCTUATION, ",");
        check(";",   1, TokenType.PUNCTUATION, ";");
    }

    static void testComments() {
        section("Comments");
        // Comment alone → only EOF
        List<Token> t1 = lex("// this is a comment\n");
        assertEqual("comment-only token count", 1, t1.size());
        assertEqual("comment-only EOF", TokenType.EOF, t1.get(0).type);

        // Comment after token
        List<Token> t2 = lex("x // comment\n");
        assertEqual("comment-after: 2 tokens", 2, t2.size());
        assertEqual("comment-after: ID", TokenType.IDENTIFIER, t2.get(0).type);
        assertEqual("comment-after: EOF", TokenType.EOF, t2.get(1).type);

        // Two comments
        List<Token> t3 = lex("// line1\n// line2\nx");
        assertEqual("two-comments: 2 tokens", 2, t3.size());
        assertEqual("two-comments: ID", "x", t3.get(0).value);
    }

    static void testWhitespace() {
        section("Whitespace");
        // Spaces, tabs, newlines are all ignored between tokens
        List<Token> t1 = lex("  x   y  ");
        assertEqual("spaces: 3 tokens", 3, t1.size());
        assertEqual("spaces: first is x", "x", t1.get(0).value);
        assertEqual("spaces: second is y", "y", t1.get(1).value);

        // Newlines also update line counter
        List<Token> t2 = lex("x\ny");
        assertEqual("newline: second token line", 2, t2.get(1).line);
    }

    static void testMixedExpression() {
        section("Mixed expressions");
        // x + 1
        List<Token> t1 = lex("x + 1");
        assertEqual("x+1: 4 tokens", 4, t1.size());
        assertEqual("x+1: ID",  TokenType.IDENTIFIER, t1.get(0).type);
        assertEqual("x+1: OP",  TokenType.OPERATOR,   t1.get(1).type);
        assertEqual("x+1: INT", TokenType.INTEGER,     t1.get(2).type);

        // fn x . x + 1
        List<Token> t2 = lex("fn x . x + 1");
        assertEqual("lambda: fn is KEYWORD", TokenType.KEYWORD, t2.get(0).type);
        assertEqual("lambda: . is OPERATOR", TokenType.OPERATOR, t2.get(2).type);

        // Tuple: (1, 2, 3)
        List<Token> t3 = lex("(1, 2, 3)");
        assertEqual("tuple: ( is PUNCT", TokenType.PUNCTUATION, t3.get(0).type);
        assertEqual("tuple: , is PUNCT", TokenType.PUNCTUATION, t3.get(2).type);
    }

    static void testSampleProgram() {
        section("Sample program (project spec)");
        // The exact program from the assignment spec
        String prog =
            "let Sum(A) = Psum (A,Order A )\n" +
            "where rec Psum (T,N) = N eq 0 -> 0\n" +
            "   | Psum(T,N-1)+T N\n" +
            "in Print ( Sum (1,2,3,4,5) )";

        List<Token> tokens = lex(prog);

        // First token must be the keyword 'let'
        assertEqual("spec: first token is 'let'", "let", tokens.get(0).value);
        assertEqual("spec: first token is KEYWORD", TokenType.KEYWORD, tokens.get(0).type);

        // 'Sum' is an identifier (not a keyword)
        assertEqual("spec: Sum is IDENTIFIER", TokenType.IDENTIFIER, tokens.get(1).type);

        // Last token before EOF is ')'
        Token beforeEOF = tokens.get(tokens.size() - 2);
        assertEqual("spec: last real token is ')'", ")", beforeEOF.value);

        // No token should have type UNKNOWN / cause an exception
        for (Token t : tokens) {
            if (t.isEOF()) break;
            assertNotNull("spec: all types non-null", t.type);
        }

        System.out.println("  Total tokens in sample program: " + tokens.size());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Lex a single-token source and assert the first token matches. */
    private static void check(String src, int expectedLine,
                               TokenType expectedType, String expectedValue) {
        List<Token> tokens = lex(src);
        String label = "\"" + src + "\"";
        assertEqual(label + " type",  expectedType,  tokens.get(0).type);
        assertEqual(label + " value", expectedValue, tokens.get(0).value);
        assertEqual(label + " line",  expectedLine,  tokens.get(0).line);
    }

    /** Tokenize a raw source string (no file I/O). */
    private static List<Token> lex(String src) {
        Lexer lexer = new Lexer(src, true);
        return lexer.tokenize();
    }

    private static void section(String name) {
        System.out.println("\n── " + name + " ──");
    }

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected.equals(actual)) {
            System.out.println("  PASS  " + label);
            passed++;
        } else {
            System.out.println("  FAIL  " + label
                + "  expected=" + expected + "  got=" + actual);
            failed++;
        }
    }

    private static void assertNotNull(String label, Object val) {
        if (val != null) {
            passed++;
        } else {
            System.out.println("  FAIL  " + label + " was null");
            failed++;
        }
    }
}