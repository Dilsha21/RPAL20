package parser;

import lexer.Lexer;
import lexer.Token;

import java.util.List;

/**
 * ParserTest.java  —  Unit tests for the RPAL Parser
 *
 * Tests are grouped by grammar rule.  Each test:
 *   1. Tokenizes a small RPAL snippet
 *   2. Parses it
 *   3. Asserts properties on the resulting AST
 *
 * Run with:  java parser.ParserTest
 *
 * ── How to verify against rpal.exe ───────────────────────────────────────────
 *   Write the snippet to a file, then compare:
 *     java parser.Parser myfile  vs  rpal.exe -ast myfile
 */
public class ParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // Expression level
        testLetExpression();
        testFnSingleParam();
        testFnMultipleParams();
        testWhereExpression();
        testTuple();
        testAugmentation();
        testConditional();

        // Boolean level
        testOrAnd();
        testNot();
        testComparisons();

        // Arithmetic
        testAddSub();
        testMulDiv();
        testPower();
        testUnaryNeg();
        testInfixAt();

        // Application
        testFunctionApplication();
        testNestedApplication();

        // Literals
        testLiterals();

        // Definitions
        testSimpleDefinition();
        testFunctionForm();
        testRecDefinition();
        testWithinDefinition();
        testAndDefinition();
        testTupleBinding();

        // Parameter lists
        testEmptyParamList();
        testTupleParamList();

        // The spec sample program
        testSpecSampleProgram();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) System.exit(1);
    }

    // ── Expression level ──────────────────────────────────────────────────────

    static void testLetExpression() {
        section("let expression");
        // let x = 5 in x
        ASTNode root = parse("let x = 5 in x");
        assertLabel(root, "let");
        assertEqual("let has 2 children", 2, root.childCount());
        assertLabel(root.getChild(0), "=");
        assertLabel(root.getChild(1), "ID");
        assertEqual("let body is 'x'", "x", root.getChild(1).value);
    }

    static void testFnSingleParam() {
        section("fn single param");
        // fn x . x
        ASTNode root = parse("fn x . x");
        assertLabel(root, "lambda");
        assertEqual("lambda has 2 children", 2, root.childCount());
        assertLabel(root.getChild(0), "ID");
        assertEqual("param is x", "x", root.getChild(0).value);
        assertLabel(root.getChild(1), "ID");
    }

    static void testFnMultipleParams() {
        section("fn multiple params — nested lambdas");
        // fn x y . x   =>  lambda(x, lambda(y, x))
        ASTNode root = parse("fn x y . x");
        assertLabel(root, "lambda");
        assertEqual("outer lambda: 2 children", 2, root.childCount());
        assertLabel(root.getChild(0), "ID");
        assertEqual("outer param is x", "x", root.getChild(0).value);

        ASTNode inner = root.getChild(1);
        assertLabel(inner, "lambda");
        assertEqual("inner param is y", "y", inner.getChild(0).value);
    }

    static void testWhereExpression() {
        section("where expression");
        // x where x = 5
        ASTNode root = parse("x where x = 5");
        assertLabel(root, "where");
        assertEqual("where has 2 children", 2, root.childCount());
        assertLabel(root.getChild(0), "ID");   // T = x
        assertLabel(root.getChild(1), "=");    // Dr = x = 5
    }

    static void testTuple() {
        section("tuple (tau)");
        // 1, 2, 3
        ASTNode root = parse("(1, 2, 3)");
        assertLabel(root, "tau");
        assertEqual("tau has 3 children", 3, root.childCount());
        assertLabel(root.getChild(0), "INT");
        assertLabel(root.getChild(2), "INT");
    }

    static void testAugmentation() {
        section("aug");
        // nil aug 1
        ASTNode root = parse("nil aug 1");
        assertLabel(root, "aug");
        assertEqual("aug has 2 children", 2, root.childCount());
    }

    static void testConditional() {
        section("conditional (->)");
        // true -> 1 | 2
        ASTNode root = parse("true -> 1 | 2");
        assertLabel(root, "->");
        assertEqual("-> has 3 children", 3, root.childCount());
        assertLabel(root.getChild(0), "true");
        assertLabel(root.getChild(1), "INT");
        assertLabel(root.getChild(2), "INT");
    }

    // ── Boolean level ─────────────────────────────────────────────────────────

    static void testOrAnd() {
        section("or / and");
        ASTNode orRoot = parse("true or false");
        assertLabel(orRoot, "or");
        assertEqual("or: 2 children", 2, orRoot.childCount());

        ASTNode andRoot = parse("true & false");
        assertLabel(andRoot, "&");
        assertEqual("&: 2 children", 2, andRoot.childCount());
    }

    static void testNot() {
        section("not");
        ASTNode root = parse("not true");
        assertLabel(root, "not");
        assertEqual("not: 1 child", 1, root.childCount());
        assertLabel(root.getChild(0), "true");
    }

    static void testComparisons() {
        section("comparisons");
        assertRootLabel("1 gr 2", "gr");
        assertRootLabel("1 ge 2", "ge");
        assertRootLabel("1 ls 2", "ls");
        assertRootLabel("1 le 2", "le");
        assertRootLabel("1 eq 2", "eq");
        assertRootLabel("1 ne 2", "ne");
        // Operator-symbol forms
        assertRootLabel("1 > 2",  "gr");
        assertRootLabel("1 >= 2", "ge");
        assertRootLabel("1 < 2",  "ls");
        assertRootLabel("1 <= 2", "le");
    }

    // ── Arithmetic ────────────────────────────────────────────────────────────

    static void testAddSub() {
        section("add / subtract");
        ASTNode plus = parse("1 + 2");
        assertLabel(plus, "+");
        assertEqual("+: left is 1", "1", plus.getChild(0).value);
        assertEqual("+: right is 2", "2", plus.getChild(1).value);

        // Left-associativity: 1 + 2 + 3  =>  +(+(1,2), 3)
        ASTNode chain = parse("1 + 2 + 3");
        assertLabel(chain, "+");
        assertLabel(chain.getChild(0), "+");    // inner + is left child
    }

    static void testMulDiv() {
        section("multiply / divide");
        assertRootLabel("2 * 3", "*");
        assertRootLabel("6 / 2", "/");
        // Precedence: 2 + 3 * 4  =>  +(2, *(3, 4))
        ASTNode root = parse("2 + 3 * 4");
        assertLabel(root, "+");
        assertLabel(root.getChild(1), "*");
    }

    static void testPower() {
        section("power (**)");
        // Right-associative: 2 ** 3 ** 4  =>  **(2, **(3, 4))
        ASTNode root = parse("2 ** 3 ** 4");
        assertLabel(root, "**");
        assertLabel(root.getChild(1), "**");    // right child is another **
    }

    static void testUnaryNeg() {
        section("unary neg");
        ASTNode root = parse("- 5");
        assertLabel(root, "neg");
        assertEqual("neg child is 5", "5", root.getChild(0).value);
    }

    static void testInfixAt() {
        section("infix @ operator");
        // x @ f y  =>  @(x, f, y)
        ASTNode root = parse("x @ f y");
        assertLabel(root, "@");
        assertEqual("@ has 3 children", 3, root.childCount());
        assertEqual("left operand", "x", root.getChild(0).value);
        assertEqual("function name", "f", root.getChild(1).value);
    }

    // ── Function application ──────────────────────────────────────────────────

    static void testFunctionApplication() {
        section("function application (gamma)");
        // f x  =>  gamma(f, x)
        ASTNode root = parse("f x");
        assertLabel(root, "gamma");
        assertEqual("gamma has 2 children", 2, root.childCount());
        assertEqual("function is f", "f", root.getChild(0).value);
        assertEqual("argument is x", "x", root.getChild(1).value);
    }

    static void testNestedApplication() {
        section("nested application (left-assoc)");
        // f x y  =>  gamma(gamma(f, x), y)
        ASTNode root = parse("f x y");
        assertLabel(root, "gamma");
        assertLabel(root.getChild(0), "gamma");   // left child is gamma(f,x)
        assertEqual("outer arg is y", "y", root.getChild(1).value);
    }

    // ── Literals ─────────────────────────────────────────────────────────────

    static void testLiterals() {
        section("literals");
        assertRootLabel("42",      "INT");
        assertRootLabel("'hello'", "STR");
        assertRootLabel("true",    "true");
        assertRootLabel("false",   "false");
        assertRootLabel("nil",     "nil");
        assertRootLabel("dummy",   "dummy");

        ASTNode intNode = parse("42");
        assertEqual("INT value", "42", intNode.value);

        ASTNode strNode = parse("'hello'");
        assertEqual("STR value", "'hello'", strNode.value);
    }

    // ── Definitions ───────────────────────────────────────────────────────────

    static void testSimpleDefinition() {
        section("simple definition (=)");
        // let x = 5 in x
        ASTNode letNode = parse("let x = 5 in x");
        ASTNode eqNode  = letNode.getChild(0);
        assertLabel(eqNode, "=");
        assertEqual("= has 2 children", 2, eqNode.childCount());
        assertLabel(eqNode.getChild(0), "ID");
        assertEqual("LHS is x", "x", eqNode.getChild(0).value);
        assertLabel(eqNode.getChild(1), "INT");
    }

    static void testFunctionForm() {
        section("function_form");
        // let f x = x in f 1  =>  function_form(f, x, x)
        ASTNode letNode  = parse("let f x = x in f 1");
        ASTNode ffNode   = letNode.getChild(0);
        assertLabel(ffNode, "function_form");
        // children: f (name), x (param), x (body) = 3
        assertEqual("function_form has 3 children", 3, ffNode.childCount());
        assertEqual("name is f", "f", ffNode.getChild(0).value);
        assertEqual("param is x", "x", ffNode.getChild(1).value);
    }

    static void testFunctionFormMultiParam() {
        section("function_form multi-param");
        // let f x y = x + y in f 1 2
        ASTNode letNode = parse("let f x y = x + y in f 1 2");
        ASTNode ffNode  = letNode.getChild(0);
        assertLabel(ffNode, "function_form");
        // children: f, x, y, body = 4
        assertEqual("function_form has 4 children", 4, ffNode.childCount());
    }

    static void testRecDefinition() {
        section("rec definition");
        // let rec f x = x in f 1
        ASTNode letNode = parse("let rec f x = x in f 1");
        ASTNode recNode = letNode.getChild(0);
        assertLabel(recNode, "rec");
        assertEqual("rec has 1 child", 1, recNode.childCount());
        assertLabel(recNode.getChild(0), "function_form");
    }

    static void testWithinDefinition() {
        section("within definition");
        // let x = 1 within y = x in y
        ASTNode letNode    = parse("let x = 1 within y = x in y");
        ASTNode withinNode = letNode.getChild(0);
        assertLabel(withinNode, "within");
        assertEqual("within has 2 children", 2, withinNode.childCount());
    }

    static void testAndDefinition() {
        section("and (simultaneous definitions)");
        // let x = 1 and y = 2 in x + y
        ASTNode letNode = parse("let x = 1 and y = 2 in x + y");
        ASTNode andNode = letNode.getChild(0);
        assertLabel(andNode, "and");
        assertEqual("and has 2 children", 2, andNode.childCount());
    }

    static void testTupleBinding() {
        section("tuple binding (Vl with comma)");
        // let (x, y) = (1, 2) in x + y
        // The Vl for (x,y) produces a ',' node with 2 ID children
        ASTNode letNode = parse("let (x, y) = (1, 2) in x + y");
        ASTNode eqNode  = letNode.getChild(0);
        assertLabel(eqNode, "=");
        ASTNode vl = eqNode.getChild(0);
        assertLabel(vl, ",");
        assertEqual("Vl has 2 IDs", 2, vl.childCount());
    }

    // ── Parameter lists ───────────────────────────────────────────────────────

    static void testEmptyParamList() {
        section("empty parameter list ()");
        // let f () = 1 in f ()
        ASTNode letNode = parse("let f () = 1 in f ()");
        ASTNode ffNode  = letNode.getChild(0);
        assertLabel(ffNode, "function_form");
        // children: f, (), 1
        assertEqual("function_form: 3 children", 3, ffNode.childCount());
        assertLabel(ffNode.getChild(1), "()");
    }

    static void testTupleParamList() {
        section("tuple parameter list (x, y)");
        // let f (x, y) = x + y in f (1, 2)
        ASTNode letNode = parse("let f (x, y) = x + y in f (1, 2)");
        ASTNode ffNode  = letNode.getChild(0);
        assertLabel(ffNode, "function_form");
        // Parameter is a Vl: ',' node with x, y
        ASTNode param = ffNode.getChild(1);
        assertLabel(param, ",");
        assertEqual("param list has 2 elements", 2, param.childCount());
    }

    // ── Full sample program from project spec ─────────────────────────────────

    static void testSpecSampleProgram() {
        section("Spec sample program");
        String prog =
            "let Sum(A) = Psum (A,Order A )\n" +
            "where rec Psum (T,N) = N eq 0 -> 0\n" +
            "                     | Psum(T,N-1)+T N\n" +
            "in Print ( Sum (1,2,3,4,5) )";

        ASTNode root = parse(prog);

        // Root must be 'let'
        assertLabel(root, "let");
        assertEqual("spec root has 2 children", 2, root.childCount());

        // The definition side must be 'where'
        assertLabel(root.getChild(0), "where");

        // 'where' left child is a function_form for Sum
        ASTNode whereNode = root.getChild(0);
        assertLabel(whereNode.getChild(0), "function_form");
        assertEqual("function name is Sum", "Sum",
                    whereNode.getChild(0).getChild(0).value);

        // Body of let must be 'gamma' (Print applied to something)
        ASTNode body = root.getChild(1);
        assertLabel(body, "gamma");

        System.out.println("  Spec program parsed successfully");
        System.out.println("  Root: " + root.toLabel() +
                           " [" + root.childCount() + " children]");
    }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Parse a raw RPAL snippet and return the root AST node. */
    private static ASTNode parse(String src) {
        try {
            Lexer lexer = new Lexer(src, true);
            Parser parser = new Parser(lexer.tokenize());
            return parser.parse();
        } catch (Exception e) {
            System.out.println("  FAIL  [exception] " + e.getMessage());
            failed++;
            return new ASTNode("ERROR");
        }
    }

    private static void section(String name) {
        System.out.println("\n── " + name + " ──");
    }

    /** Assert that a node's type label matches expected. */
    private static void assertLabel(ASTNode node, String expected) {
        String actual = node.type;
        if (expected.equals(actual)) {
            System.out.println("  PASS  label = " + expected);
            passed++;
        } else {
            System.out.println("  FAIL  label: expected=" + expected + " got=" + actual);
            failed++;
        }
    }

    /** Assert the root label of a parsed snippet. */
    private static void assertRootLabel(String src, String expected) {
        ASTNode root = parse(src);
        assertLabel(root, expected);
    }

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected.equals(actual)) {
            System.out.println("  PASS  " + label);
            passed++;
        } else {
            System.out.println("  FAIL  " + label +
                               ": expected=" + expected + " got=" + actual);
            failed++;
        }
    }
}