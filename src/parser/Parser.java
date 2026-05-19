package parser;

import lexer.Token;
import lexer.TokenType;
import lexer.Lexer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Parser.java — RPAL Recursive Descent Parser
 *
 * Consumes the token stream produced by Lexer and builds an Abstract Syntax
 * Tree (ASTNode). One private method per grammar rule, exactly matching the
 * rule names from RPAL_Grammar.pdf:
 *
 * E Ew T Ta Tc B Bt Bs Bp A At Af Ap R Rn
 * D Da Dr Db Vb Vl
 *
 * ── Tree-building strategy
 * ────────────────────────────────────────────────────
 *
 * A shared node stack (Deque<ASTNode>) is used. Every grammar method leaves
 * its result on the top of the stack. The helper buildTree(tag, n) pops n
 * children, creates a parent node labelled tag, and pushes it back.
 *
 * Example: parseLet()
 * D() ← pushes D subtree
 * E() ← pushes E subtree
 * buildTree("let", 2) ← pops 2, creates let(D, E), pushes it
 *
 * ── Handling left recursion
 * ───────────────────────────────────────────────────
 *
 * Rules that are left-recursive in the grammar (B, Bt, Ta, A, At, Ap, R) are
 * converted to iterative while-loops. Each iteration pops the already-built
 * left subtree and the new right subtree and combines them.
 *
 * ── Usage
 * ─────────────────────────────────────────────────────────────────────
 *
 * // Member 2 can call this directly after integration:
 * Parser parser = new Parser("path/to/file.rpal");
 * ASTNode root = parser.parse();
 * root.print(0); // matches rpal.exe -ast output
 */
public class Parser {

    // ── Internal state ────────────────────────────────────────────────────────

    /** Flat list of tokens from the Lexer (includes EOF sentinel at end). */
    private final List<Token> tokens;

    /** Index of the token currently being examined. */
    private int pos;

    /** Convenience alias: tokens.get(pos) — always the "current" token. */
    private Token current;

    /**
     * The node stack used by all grammar methods.
     * Each method pushes its result; buildTree(tag, n) combines them.
     */
    private final Deque<ASTNode> stack = new ArrayDeque<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Build a parser for a source file.
     *
     * @param filePath path to the RPAL source file
     * @throws IOException if the file cannot be read
     */
    public Parser(String filePath) throws IOException {
        this.tokens = new Lexer(filePath).tokenize();
        this.pos = 0;
        this.current = tokens.get(0);
    }

    /**
     * Build a parser directly from a token list (for unit testing).
     *
     * @param tokens pre-built token list (must end with an EOF token)
     */
    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
        this.current = tokens.get(0);
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Parse the full RPAL program and return the root AST node.
     *
     * Corresponds to the top-level grammar symbol E.
     * After E() the stack must contain exactly one node.
     *
     * @return root of the Abstract Syntax Tree
     * @throws ParseException if the token stream does not match the grammar
     */
    public ASTNode parse() {
        E();
        if (!current.isEOF()) {
            throw new ParseException(
                    "Unexpected token after expression: " + current, current.line);
        }
        if (stack.size() != 1) {
            throw new ParseException(
                    "Internal parser error: stack size is " + stack.size() + " (expected 1)", 0);
        }
        return stack.pop();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Grammar rules — Expression level
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * E -> 'let' D 'in' E => 'let'
     * | 'fn' Vb+ '.' E => 'lambda' (builds nested lambdas for multiple Vb)
     * | Ew
     */
    private void E() {
        if (isKeyword("let")) {
            // let D in E → let(D, E)
            consume(); // 'let'
            D();
            consumeKeyword("in");
            E();
            buildTree("let", 2);

        } else if (isKeyword("fn")) {
            // fn Vb+ . E
            // For n Vb's, build n nested lambdas from the inside out:
            // fn x y . E → lambda(x, lambda(y, E))
            consume(); // 'fn'
            int vbCount = 1;
            Vb(); // at least one Vb required
            while (isType(TokenType.IDENTIFIER) || isPunct("(")) {
                Vb();
                vbCount++;
            }
            consumeOperator("."); // '.' separates parameters from body
            E();
            // Stack: [Vb1, Vb2, ..., VbN, E]
            // First call combines innermost pair, then each subsequent call
            // wraps the result one level outward.
            buildTree("lambda", 2); // lambda(VbN, E)
            while (vbCount > 1) {
                buildTree("lambda", 2); // lambda(Vb(N-1), lambda(...))
                vbCount--;
            }

        } else {
            Ew();
        }
    }

    /**
     * Ew -> T 'where' Dr => 'where'
     * | T
     */
    private void Ew() {
        T();
        if (isKeyword("where")) {
            consume(); // 'where'
            Dr();
            buildTree("where", 2);
        }
    }

    // ── Tuple / augmentation ──────────────────────────────────────────────────

    /**
     * T -> Ta (',' Ta)+ => 'tau' (node with n Ta children)
     * | Ta
     *
     * A tuple requires at least two elements; a lone Ta produces no tau node.
     */
    private void T() {
        Ta();
        int n = 1;
        while (isPunct(",")) {
            consume(); // ','
            Ta();
            n++;
        }
        if (n > 1) {
            buildTree("tau", n);
        }
    }

    /**
     * Ta -> Ta 'aug' Tc => 'aug' (LEFT-RECURSIVE → iterative)
     * | Tc
     */
    private void Ta() {
        Tc();
        while (isKeyword("aug")) {
            consume(); // 'aug'
            Tc();
            buildTree("aug", 2);
        }
    }

    /**
     * Tc -> B '->' Tc '|' Tc => '->'
     * | B
     *
     * Conditional expression. Three children: condition, then-branch, else-branch.
     */
    private void Tc() {
        B();
        if (isOperator("->")) {
            consume(); // '->'
            Tc();
            consumeOperator("|");
            Tc();
            buildTree("->", 3);
        }
    }

    // ── Boolean level ─────────────────────────────────────────────────────────

    /**
     * B -> B 'or' Bt => 'or' (LEFT-RECURSIVE → iterative)
     * | Bt
     */
    private void B() {
        Bt();
        while (isKeyword("or")) {
            consume(); // 'or'
            Bt();
            buildTree("or", 2);
        }
    }

    /**
     * Bt -> Bt '&' Bs => '&' (LEFT-RECURSIVE → iterative)
     * | Bs
     */
    private void Bt() {
        Bs();
        while (isOperator("&")) {
            consume(); // '&'
            Bs();
            buildTree("&", 2);
        }
    }

    /**
     * Bs -> 'not' Bp => 'not'
     * | Bp
     */
    private void Bs() {
        if (isKeyword("not")) {
            consume(); // 'not'
            Bp();
            buildTree("not", 1);
        } else {
            Bp();
        }
    }

    /**
     * Bp -> A ('gr'|'>') A => 'gr'
     * | A ('ge'|'>=') A => 'ge'
     * | A ('ls'|'<') A => 'ls'
     * | A ('le'|'<=') A => 'le'
     * | A 'eq' A => 'eq'
     * | A 'ne' A => 'ne'
     * | A
     *
     * Only one comparison per Bp (not chained), so no loop needed.
     */
    private void Bp() {
        A();
        if (isKeyword("gr") || isOperator(">")) {
            consume();
            A();
            buildTree("gr", 2);
        } else if (isKeyword("ge") || isOperator(">=")) {
            consume();
            A();
            buildTree("ge", 2);
        } else if (isKeyword("ls") || isOperator("<")) {
            consume();
            A();
            buildTree("ls", 2);
        } else if (isKeyword("le") || isOperator("<=")) {
            consume();
            A();
            buildTree("le", 2);
        } else if (isKeyword("eq")) {
            consume();
            A();
            buildTree("eq", 2);
        } else if (isKeyword("ne")) {
            consume();
            A();
            buildTree("ne", 2);
        }
        // else: plain A — no comparison operator, leave as-is
    }

    // ── Arithmetic level ──────────────────────────────────────────────────────

    /**
     * A -> A '+' At => '+' (LEFT-RECURSIVE → iterative)
     * | A '-' At => '-'
     * | '+' At (unary plus — transparent, no AST node)
     * | '-' At => 'neg'
     * | At
     */
    private void A() {
        if (isOperator("+")) {
            // Unary plus: discard operator, parse At (no node built)
            consume();
            At();
        } else if (isOperator("-")) {
            // Unary minus: parse At, wrap in 'neg'
            consume();
            At();
            buildTree("neg", 1);
        } else {
            At();
        }
        // Binary + and - (left-recursive, iterative)
        while (isOperator("+") || isOperator("-")) {
            String op = current.value; // capture "+" or "-"
            consume();
            At();
            buildTree(op, 2);
        }
    }

    /**
     * At -> At '*' Af => '*' (LEFT-RECURSIVE → iterative)
     * | At '/' Af => '/'
     * | Af
     */
    private void At() {
        Af();
        while (isOperator("*") || isOperator("/")) {
            String op = current.value;
            consume();
            Af();
            buildTree(op, 2);
        }
    }

    /**
     * Af -> Ap '**' Af => '**' (right-associative — natural recursion)
     * | Ap
     */
    private void Af() {
        Ap();
        if (isOperator("**")) {
            consume(); // '**'
            Af(); // recurse for right-associativity
            buildTree("**", 2);
        }
    }

    /**
     * Ap -> Ap '@' '<ID>' R => '@' (LEFT-RECURSIVE → iterative)
     * | R
     *
     * Infix application: x @ f y means f(x, y).
     * The '@' node has three children: left-operand, function-name-ID, right-R.
     */
    private void Ap() {
        R();
        while (isOperator("@")) {
            consume(); // '@'
            if (!isType(TokenType.IDENTIFIER)) {
                throw new ParseException(
                        "Expected identifier after '@'", current.line);
            }
            // Push the function name as an ID leaf
            stack.push(new ASTNode("ID", current.value));
            consume(); // the identifier
            R();
            buildTree("@", 3); // @(left, ID, right)
        }
    }

    // ── Application / atoms ───────────────────────────────────────────────────

    /**
     * R -> R Rn => 'gamma' (LEFT-RECURSIVE → iterative)
     * | Rn
     *
     * Function application. f x y → gamma(gamma(f, x), y)
     * We keep applying as long as the current token can start an Rn.
     */
    private void R() {
        Rn();
        while (canStartRn()) {
            Rn();
            buildTree("gamma", 2);
        }
    }

    /**
     * Returns true if the current token can legally start an Rn production.
     * Used by R() to decide whether to keep applying (gamma-chaining).
     */
    private boolean canStartRn() {
        // Identifiers, integers, strings
        if (isType(TokenType.IDENTIFIER))
            return true;
        if (isType(TokenType.INTEGER))
            return true;
        if (isType(TokenType.STRING))
            return true;
        // Boolean / nil / dummy literals
        if (isKeyword("true"))
            return true;
        if (isKeyword("false"))
            return true;
        if (isKeyword("nil"))
            return true;
        if (isKeyword("dummy"))
            return true;
        // Parenthesised sub-expression
        if (isPunct("("))
            return true;
        return false;
    }

    /**
     * Rn -> '<ID>'
     * | '<INT>'
     * | '<STR>'
     * | 'true' (leaf node, value = "true")
     * | 'false' (leaf node, value = "false")
     * | 'nil' (leaf node)
     * | 'dummy' (leaf node)
     * | '(' E ')' (grouped expression — no new node)
     */
    private void Rn() {
        if (isType(TokenType.IDENTIFIER)) {
            stack.push(new ASTNode("ID", current.value));
            consume();

        } else if (isType(TokenType.INTEGER)) {
            stack.push(new ASTNode("INT", current.value));
            consume();

        } else if (isType(TokenType.STRING)) {
            stack.push(new ASTNode("STR", current.value));
            consume();

        } else if (isKeyword("true")) {
            stack.push(new ASTNode("true"));
            consume();

        } else if (isKeyword("false")) {
            stack.push(new ASTNode("false"));
            consume();

        } else if (isKeyword("nil")) {
            stack.push(new ASTNode("nil"));
            consume();

        } else if (isKeyword("dummy")) {
            stack.push(new ASTNode("dummy"));
            consume();

        } else if (isPunct("(")) {
            consume(); // '('
            if (isPunct(")")) {
                // Empty tuple / empty actual parameter list
                consume(); // ')'
                buildTree("()", 0);
            } else {
                E(); // result pushed onto stack by E()
                consumePunct(")");
            }

        } else {
            throw new ParseException(
                    "Unexpected token in Rn: " + current, current.line);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Grammar rules — Definition level
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * D -> Da 'within' D => 'within'
     * | Da
     */
    private void D() {
        Da();
        if (isKeyword("within")) {
            consume(); // 'within'
            D();
            buildTree("within", 2);
        }
    }

    /**
     * Da -> Dr ('and' Dr)+ => 'and' (n Dr children)
     * | Dr
     *
     * Simultaneous definitions separated by 'and'.
     */
    private void Da() {
        Dr();
        int n = 1;
        while (isKeyword("and")) {
            consume(); // 'and'
            Dr();
            n++;
        }
        if (n > 1) {
            buildTree("and", n);
        }
    }

    /**
     * Dr -> 'rec' Db => 'rec'
     * | Db
     */
    private void Dr() {
        if (isKeyword("rec")) {
            consume(); // 'rec'
            Db();
            buildTree("rec", 1);
        } else {
            Db();
        }
    }

    /**
     * Db -> Vl '=' E => '=' (simple or tuple binding)
     * | '<ID>' Vb+ '=' E => 'function_form'
     * | '(' D ')' (grouped definition — no new node)
     *
     * Disambiguation after seeing the first IDENTIFIER:
     * • Next token is ',' or '=' → Vl = E (Vl may be a single ID)
     * • Next token is an ID or '(' → function_form (Vb follows)
     *
     * A leading '(' can either start a grouped definition or a tuple binding
     * such as '(x, y) = ...'. We peek ahead to the matching ')' and check if
     * the next token is '=' before deciding which form to parse.
     */
    private void Db() {
        if (isPunct("(")) {
            if (isTupleBindingAhead()) {
                // Tuple binding: (x, y) = E
                consume(); // '('
                Vl();
                consumePunct(")");
                consumeOperator("=");
                E();
                buildTree("=", 2);

            } else {
                // Grouped definition: ( D )
                consume(); // '('
                D();
                consumePunct(")");
            }

        } else if (isType(TokenType.IDENTIFIER)) {
            // Push the first identifier — used by both branches below
            stack.push(new ASTNode("ID", current.value));
            consume();

            if (isType(TokenType.IDENTIFIER) || isPunct("(")) {
                // ── Function form: <ID> Vb+ '=' E ──────────────────────────
                // The ID is the function name (already on stack).
                // Parse one or more Vb parameter groups.
                int vbCount = 0;
                do {
                    Vb();
                    vbCount++;
                } while (isType(TokenType.IDENTIFIER) || isPunct("("));
                consumeOperator("=");
                E();
                // Stack: [funcName-ID, Vb1, ..., VbN, E]
                // E() may return a where-expression (Ew → T 'where' Dr).
                // That where node stays as the function body — matching the
                // reference: function_form(name, Vb+, where(expr, Dr))
                buildTree("function_form", vbCount + 2);

            } else {
                // ── Variable binding: Vl '=' E ─────────────────────────────
                // The first ID is already on the stack.
                // Check for a multi-variable Vl (comma-separated IDs).
                int idCount = 1;
                while (isPunct(",")) {
                    consume(); // ','
                    if (!isType(TokenType.IDENTIFIER)) {
                        throw new ParseException(
                                "Expected identifier after ',' in Vl", current.line);
                    }
                    stack.push(new ASTNode("ID", current.value));
                    consume();
                    idCount++;
                }
                // If multiple IDs, build a comma node to represent the Vl
                if (idCount > 1) {
                    buildTree(",", idCount);
                }
                consumeOperator("=");
                E();
                buildTree("=", 2); // =(Vl, E)
            }

        } else {
            throw new ParseException(
                    "Expected '(' or identifier in definition (Db)", current.line);
        }
    }

    // ── Variable / parameter lists ────────────────────────────────────────────

    /**
     * Vb -> '<ID>' (single identifier parameter)
     * | '(' Vl ')' (parenthesised parameter list)
     * | '(' ')' => '()' (empty parameter list)
     */
    private void Vb() {
        if (isType(TokenType.IDENTIFIER)) {
            stack.push(new ASTNode("ID", current.value));
            consume();

        } else if (isPunct("(")) {
            consume(); // '('
            if (isPunct(")")) {
                // Empty parameter list ()
                consume(); // ')'
                buildTree("()", 0);
            } else {
                // Parenthesised variable list
                Vl();
                consumePunct(")");
                // Vl result stays on stack (no extra node needed)
            }

        } else {
            throw new ParseException(
                    "Expected identifier or '(' in Vb", current.line);
        }
    }

    /**
     * Vl -> '<ID>' (',' '<ID>')+ => ',' (n ID children)
     * | '<ID>' (single ID, no comma node)
     *
     * A variable list inside parentheses, e.g. (x, y, z)
     */
    private void Vl() {
        if (!isType(TokenType.IDENTIFIER)) {
            throw new ParseException(
                    "Expected identifier in variable list (Vl)", current.line);
        }
        stack.push(new ASTNode("ID", current.value));
        consume();
        int n = 1;
        while (isPunct(",")) {
            consume(); // ','
            if (!isType(TokenType.IDENTIFIER)) {
                throw new ParseException(
                        "Expected identifier after ',' in variable list (Vl)", current.line);
            }
            stack.push(new ASTNode("ID", current.value));
            consume();
            n++;
        }
        // Only build a comma node when there are multiple identifiers
        if (n > 1) {
            buildTree(",", n);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tree-building helper
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Pop n nodes from the stack, create a new internal node labelled tag
     * with those nodes as children (in left-to-right order), and push it.
     *
     * The stack is LIFO so the last-pushed child is popped first.
     * We collect into an array in reverse to restore the original order.
     *
     * Example (n = 2, stack top-to-bottom: [E, D]):
     * children[1] = pop() = E
     * children[0] = pop() = D
     * → let(D, E) pushed
     *
     * @param tag the label for the new ASTNode (e.g. "let", "+", "gamma")
     * @param n   number of children to pop (0 is valid — creates a leaf-like node)
     */
    private void buildTree(String tag, int n) {
        ASTNode node = new ASTNode(tag);
        // Collect children in reverse pop order to restore left-to-right
        ASTNode[] children = new ASTNode[n];
        for (int i = n - 1; i >= 0; i--) {
            if (stack.isEmpty()) {
                throw new ParseException(
                        "Internal error: stack underflow building '" + tag + "'", 0);
            }
            children[i] = stack.pop();
        }
        for (ASTNode child : children) {
            node.addChild(child);
        }
        stack.push(node);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Token-inspection helpers
    // ══════════════════════════════════════════════════════════════════════════

    /** True if the current token is a keyword with the given text. */
    private boolean isKeyword(String word) {
        return current.type == TokenType.KEYWORD && current.value.equals(word);
    }

    /** True if the current token is an operator with the given symbol. */
    private boolean isOperator(String op) {
        return current.type == TokenType.OPERATOR && current.value.equals(op);
    }

    /** True if the current token is a punctuation character. */
    private boolean isPunct(String ch) {
        return current.type == TokenType.PUNCTUATION && current.value.equals(ch);
    }

    /** True if the current token has the given TokenType. */
    private boolean isType(TokenType t) {
        return current.type == t;
    }

    /**
     * True if the current '(' starts a tuple binding of the form '(Vl) ='.
     */
    private boolean isTupleBindingAhead() {
        if (!isPunct("(")) {
            return false;
        }

        int depth = 0;
        for (int i = pos; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.type == TokenType.PUNCTUATION) {
                if ("(".equals(token.value)) {
                    depth++;
                } else if (")".equals(token.value)) {
                    depth--;
                    if (depth == 0) {
                        return i + 1 < tokens.size()
                                && tokens.get(i + 1).type == TokenType.OPERATOR
                                && "=".equals(tokens.get(i + 1).value);
                    }
                }
            }
        }

        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Token-consumption helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Advance to the next token unconditionally.
     * All consume* methods delegate here.
     */
    private void consume() {
        if (pos < tokens.size() - 1) {
            pos++;
        }
        current = tokens.get(pos);
    }

    /** Consume the current token, asserting it is a specific keyword. */
    private void consumeKeyword(String word) {
        if (!isKeyword(word)) {
            throw new ParseException(
                    "Expected keyword '" + word + "' but found " + current, current.line);
        }
        consume();
    }

    /** Consume the current token, asserting it is a specific operator. */
    private void consumeOperator(String op) {
        if (!isOperator(op)) {
            throw new ParseException(
                    "Expected operator '" + op + "' but found " + current, current.line);
        }
        consume();
    }

    /** Consume the current token, asserting it is a specific punctuation char. */
    private void consumePunct(String ch) {
        if (!isPunct(ch)) {
            throw new ParseException(
                    "Expected '" + ch + "' but found " + current, current.line);
        }
        consume();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Exception
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Thrown when the parser encounters a token sequence that does not match
     * any production rule. Includes the source line number for diagnostics.
     */
    public static class ParseException extends RuntimeException {
        public final int line;

        public ParseException(String message, int line) {
            super("Parse error at line " + line + ": " + message);
            this.line = line;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Main — standalone test / debug entry point
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Run the parser on a file and print the AST.
     * Output should match: rpal.exe -ast &lt;file&gt;
     *
     * Usage: java parser.Parser &lt;rpal-source-file&gt;
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java parser.Parser <rpal-source-file>");
            System.exit(1);
        }
        Parser parser = new Parser(args[0]);
        ASTNode root = parser.parse();
        root.print(0);
    }
}