package parser;

/**
 * DummyParser – a stand-alone test harness that produces hard-coded ASTs.
 *
 * PURPOSE
 * -------
 * Member 2 (Dilsha) can use this class to test the Standardizer and CSE Machine
 * independently while waiting for Senilka's real Lexer + Parser to be integrated.
 *
 * USAGE
 * -----
 *   ASTNode tree = DummyParser.get("add");   // returns AST for the "add" test program
 *   // ... run Standardizer and CSE Machine on tree
 *
 * Each method returns an ASTNode tree that matches the output of rpal.exe -ast
 * for the corresponding rpal/ test program.  Add more programs as needed.
 *
 * HOW TO ADD A NEW TEST CASE
 * --------------------------
 *   1. Run:   rpal.exe -ast rpal/your_program
 *   2. Read the indented output.
 *   3. Build it manually using the helper methods below.
 *   4. Register it in the get() switch-case.
 */
public class DummyParser {

    // ─── Public factory ───────────────────────────────────────────────────────

    /**
     * Return a hand-built AST for the named test program.
     *
     * @param programName  one of "add", "fn1", "fn2", "fn3", "defns", "towers",
     *                     "pairs1", "pairs2", "pairs3", "infix", "conc",
     *                     "vectorsum", "picture", "ftst"
     * @return  root ASTNode of the AST
     */
    public static ASTNode get(String programName) {
        switch (programName) {
            case "add":       return makeAdd();
            case "fn1":       return makeFn1();
            case "fn2":       return makeFn2();
            case "fn3":       return makeFn3();
            case "defns":     return makeDefns();
            case "towers":    return makeTowers();
            case "pairs1":    return makePairs1();
            case "infix":     return makeInfix();
            case "conc":      return makeConc();
            default:
                throw new IllegalArgumentException(
                        "DummyParser: no test case named '" + programName + "'");
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Create an internal (operator/keyword) node with no value. */
    private static ASTNode n(String type, ASTNode... children) {
        ASTNode node = new ASTNode(type);
        for (ASTNode c : children) node.addChild(c);
        return node;
    }

    /** Create an identifier leaf node. */
    private static ASTNode id(String name) {
        return new ASTNode("ID", name);
    }

    /** Create an integer literal leaf node. */
    private static ASTNode num(int v) {
        return new ASTNode("INT", String.valueOf(v));
    }

    /** Create a string literal leaf node (value should include quotes, e.g. "'hello'"). */
    private static ASTNode str(String v) {
        return new ASTNode("STR", v);
    }

    /** Create a boolean literal leaf node ("true" or "false"). */
    private static ASTNode bool(boolean v) {
        return new ASTNode(String.valueOf(v));
    }

    // ─── Test programs ────────────────────────────────────────────────────────

    /**
     * add:
     *   let Add(x, y) = x + y
     *   in Print(Add(1, 2))
     *
     * AST (rpal.exe -ast output):
     *   let
     *   ..<ID:Add>
     *   ..fcn_form  [we use function_form in the grammar → fcn_form in tree]
     *   Wait — actual grammar produces:
     *
     *   let
     *   ..=
     *   ....<ID:Add>
     *   ....lambda
     *   ......<ID:x>
     *   ......<ID:y>
     *   ......+
     *   ........<ID:x>
     *   ........<ID:y>
     *   ..gamma
     *   ....<ID:Print>
     *   ....gamma
     *   ......<ID:Add>
     *   ......<INT:1>
     *   ......<INT:2>   -- actually tuples need tau for multiple args
     *
     * Simplified single-expression version for unit testing:
     *   let x = 3 in Print(x + 2)
     */
    private static ASTNode makeAdd() {
        // let x = 3 in Print(x+2)
        //
        // let
        // ..=
        // ....<ID:x>
        // ....<INT:3>
        // ..gamma
        // ....<ID:Print>
        // ....+
        // ......<ID:x>
        // ......<INT:2>

        ASTNode eq = n("=",
                id("x"),
                num(3));

        ASTNode body = n("gamma",
                id("Print"),
                n("+", id("x"), num(2)));

        return n("let", eq, body);
    }

    /**
     * fn1:  Print(1+2)
     *
     * AST:
     *   gamma
     *   ..<ID:Print>
     *   ..+
     *   ....<INT:1>
     *   ....<INT:2>
     */
    private static ASTNode makeFn1() {
        return n("gamma",
                id("Print"),
                n("+", num(1), num(2)));
    }

    /**
     * fn2:  let f x = x * 2 in Print(f 5)
     *
     * AST:
     *   let
     *   ..=
     *   ....<ID:f>
     *   ....lambda
     *   ......<ID:x>
     *   ......*
     *   ........<ID:x>
     *   ........<INT:2>
     *   ..gamma
     *   ....<ID:Print>
     *   ....gamma
     *   ......<ID:f>
     *   ......<INT:5>
     */
    private static ASTNode makeFn2() {
        ASTNode lambdaFx = n("lambda",
                id("x"),
                n("*", id("x"), num(2)));

        ASTNode eq = n("=", id("f"), lambdaFx);

        ASTNode body = n("gamma",
                id("Print"),
                n("gamma", id("f"), num(5)));

        return n("let", eq, body);
    }

    /**
     * fn3:  let f x y = x + y in Print(f 3 4)
     *
     * AST:
     *   let
     *   ..function_form
     *   ....<ID:f>
     *   ....<ID:x>
     *   ....<ID:y>
     *   ....+
     *   ......<ID:x>
     *   ......<ID:y>
     *   ..gamma
     *   ....<ID:Print>
     *   ....gamma
     *   ......gamma
     *   ........<ID:f>
     *   ........<INT:3>
     *   ......<INT:4>
     */
    private static ASTNode makeFn3() {
        ASTNode fcnForm = n("function_form",
                id("f"),
                id("x"),
                id("y"),
                n("+", id("x"), id("y")));

        ASTNode body = n("gamma",
                id("Print"),
                n("gamma",
                        n("gamma", id("f"), num(3)),
                        num(4)));

        return n("let", fcnForm, body);
    }

    /**
     * defns:  let x = 1 and y = 2 in Print(x + y)
     *
     * AST:
     *   let
     *   ..and
     *   ....=
     *   ......<ID:x>
     *   ......<INT:1>
     *   ....=
     *   ......<ID:y>
     *   ......<INT:2>
     *   ..gamma
     *   ....<ID:Print>
     *   ....+
     *   ......<ID:x>
     *   ......<ID:y>
     */
    private static ASTNode makeDefns() {
        ASTNode andNode = n("and",
                n("=", id("x"), num(1)),
                n("=", id("y"), num(2)));

        ASTNode body = n("gamma",
                id("Print"),
                n("+", id("x"), id("y")));

        return n("let", andNode, body);
    }

    /**
     * towers (Towers of Hanoi):
     *   let rec Hanoi n a b c = ...
     *
     * Simplified version for unit testing:
     *   let rec f n = n eq 0 -> 0 | n + f(n-1)
     *   in Print(f 3)
     */
    private static ASTNode makeTowers() {
        // Recursive countdown: let rec f n = (n eq 0) -> 0 | n + f(n-1)
        //   in Print(f 3)

        ASTNode base = n("->",
                n("eq", id("n"), num(0)),
                num(0),
                n("+", id("n"),
                        n("gamma", id("f"),
                                n("-", id("n"), num(1)))));

        ASTNode lambda = n("lambda", id("n"), base);
        ASTNode rec = n("rec", n("=", id("f"), lambda));

        ASTNode body = n("gamma",
                id("Print"),
                n("gamma", id("f"), num(3)));

        return n("let", rec, body);
    }

    /**
     * pairs1:  let p = (1, 2) in Print(p)
     *
     * AST:
     *   let
     *   ..=
     *   ....<ID:p>
     *   ....tau
     *   ......<INT:1>
     *   ......<INT:2>
     *   ..gamma
     *   ....<ID:Print>
     *   ....<ID:p>
     */
    private static ASTNode makePairs1() {
        ASTNode eq = n("=",
                id("p"),
                n("tau", num(1), num(2)));

        ASTNode body = n("gamma", id("Print"), id("p"));

        return n("let", eq, body);
    }

    /**
     * infix:  Print( 3 @Add 4 )
     *   where Add x y = x + y
     *
     * Simplified AST with the @ operator:
     *   let
     *   ..=
     *   ....<ID:Add>
     *   ....lambda
     *   ......<ID:x>
     *   ......lambda
     *   ........<ID:y>
     *   ........+
     *   ..........<ID:x>
     *   ..........<ID:y>
     *   ..gamma
     *   ....<ID:Print>
     *   ....@
     *   ......<INT:3>
     *   ......<ID:Add>
     *   ......<INT:4>
     */
    private static ASTNode makeInfix() {
        ASTNode addLambda = n("lambda",
                id("x"),
                n("lambda",
                        id("y"),
                        n("+", id("x"), id("y"))));

        ASTNode eq = n("=", id("Add"), addLambda);

        ASTNode body = n("gamma",
                id("Print"),
                n("@", num(3), id("Add"), num(4)));

        return n("let", eq, body);
    }

    /**
     * conc:  Print( Conc 'Hello' ' World' )
     *
     * AST:
     *   gamma
     *   ..<ID:Print>
     *   ..gamma
     *   ....gamma
     *   ......<ID:Conc>
     *   ......<STR:'Hello'>
     *   ....<STR:' World'>
     */
    private static ASTNode makeConc() {
        return n("gamma",
                id("Print"),
                n("gamma",
                        n("gamma",
                                id("Conc"),
                                str("'Hello'")),
                        str("' World'")));
    }
}
