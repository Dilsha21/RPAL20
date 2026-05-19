package parser;

import lexer.Token;
import lexer.TokenType;
import lexer.Lexer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

// Parser.java — RPAL Recursive Descent Parser
// Consumes the token stream produced by Lexer and builds an AST. One private method per grammar rule.
public class Parser {

    /** Flat list of tokens from the Lexer (includes EOF sentinel at end). */
    private final List<Token> tokens;
    private int pos;
    private Token current;
    private final Deque<ASTNode> stack = new ArrayDeque<>();

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


    // Parse the full RPAL program and return the root AST node (top-level E).
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


    private void E() {
        if (isKeyword("let")) {
            // let D in E → let(D, E)
            consume(); // 'let'
            D();
            consumeKeyword("in");
            E();
            buildTree("let", 2);

        } else if (isKeyword("fn")) {
           
            consume(); // 'fn'
            int vbCount = 1;
            Vb(); // at least one Vb required
            while (isType(TokenType.IDENTIFIER) || isPunct("(")) {
                Vb();
                vbCount++;
            }
            consumeOperator("."); // '.' separates parameters from body
            E();
            
            buildTree("lambda", 2); // lambda(VbN, E)
            while (vbCount > 1) {
                buildTree("lambda", 2); // lambda(Vb(N-1), lambda(...))
                vbCount--;
            }

        } else {
            Ew();
        }
    }

    // Ew -> T 'where' Dr => 'where' | T
    private void Ew() {
        T();
        if (isKeyword("where")) {
            consume(); // 'where'
            Dr();
            buildTree("where", 2);
        }
    }


    // T -> Ta (',' Ta)+ => 'tau' | Ta
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

    // Ta -> Ta 'aug' Tc => 'aug' | Tc
    private void Ta() {
        Tc();
        while (isKeyword("aug")) {
            consume(); // 'aug'
            Tc();
            buildTree("aug", 2);
        }
    }

    // Tc -> B '->' Tc '|' Tc => '->' | B (conditional expression)
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


    // B -> B 'or' Bt => 'or' | Bt
    private void B() {
        Bt();
        while (isKeyword("or")) {
            consume(); // 'or'
            Bt();
            buildTree("or", 2);
        }
    }

    // Bt -> Bt '&' Bs => '&' | Bs
    private void Bt() {
        Bs();
        while (isOperator("&")) {
            consume(); // '&'
            Bs();
            buildTree("&", 2);
        }
    }

    // Bs -> 'not' Bp => 'not' | Bp
    private void Bs() {
        if (isKeyword("not")) {
            consume(); // 'not'
            Bp();
            buildTree("not", 1);
        } else {
            Bp();
        }
    }

    // Bp -> comparisons like A gr A, A ge A, etc. Only one comparison allowed.
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

    // A -> addition/subtraction and unary +/- handling
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

    // At -> multiplication/division
    private void At() {
        Af();
        while (isOperator("*") || isOperator("/")) {
            String op = current.value;
            consume();
            Af();
            buildTree(op, 2);
        }
    }

    // Af -> power '**' (right-associative) | Ap
    private void Af() {
        Ap();
        if (isOperator("**")) {
            consume(); // '**'
            Af(); // recurse for right-associativity
            buildTree("**", 2);
        }
    }

    // Ap -> infix '@' application
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


    // R -> function application (gamma chaining)
    private void R() {
        Rn();
        while (canStartRn()) {
            Rn();
            buildTree("gamma", 2);
        }
    }

    // Returns true if the current token can start an Rn production
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

    // Rn -> atomic expressions: ID, INT, STR, true/false, nil, dummy, or (E)
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

    // D -> Da 'within' D => 'within' | Da
    private void D() {
        Da();
        if (isKeyword("within")) {
            consume(); // 'within'
            D();
            buildTree("within", 2);
        }
    }

    // Da -> Dr ('and' Dr)+ => 'and' | Dr (simultaneous definitions)
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

    // Dr -> 'rec' Db | Db
    private void Dr() {
        if (isKeyword("rec")) {
            consume(); // 'rec'
            Db();
            buildTree("rec", 1);
        } else {
            Db();
        }
    }

    // Db -> Vl '=' E | function_form | grouped D. Disambiguate by peeking ahead.
    private void Db() {
        if (isPunct("(")) {
            if (isTupleBindingAhead()) {
                consume();
                Vl();
                consumePunct(")");
                consumeOperator("=");
                E();
                buildTree("=", 2);

            } else {
     
                consume();
                D();
                consumePunct(")");
            }

        } else if (isType(TokenType.IDENTIFIER)) {
            stack.push(new ASTNode("ID", current.value));
            consume();

            if (isType(TokenType.IDENTIFIER) || isPunct("(")) {

                int vbCount = 0;
                do {
                    Vb();
                    vbCount++;
                } while (isType(TokenType.IDENTIFIER) || isPunct("("));
                consumeOperator("=");
                E();
                buildTree("function_form", vbCount + 2);

            } else {

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

    // Vb -> '<ID>' | '(' Vl ')' | '()'
    private void Vb() {
        if (isType(TokenType.IDENTIFIER)) {
            stack.push(new ASTNode("ID", current.value));
            consume();

        } else if (isPunct("(")) {
            consume(); // '('
            if (isPunct(")")) {
         
                consume(); 
                buildTree("()", 0);
            } else {

                Vl();
                consumePunct(")");
   
            }

        } else {
            throw new ParseException(
                    "Expected identifier or '(' in Vb", current.line);
        }
    }

    // Vl -> variable list inside parentheses, possibly comma-separated
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
     * @param tag 
     * @param n  
     */
    private void buildTree(String tag, int n) {
        ASTNode node = new ASTNode(tag);
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

    private boolean isKeyword(String word) {
        return current.type == TokenType.KEYWORD && current.value.equals(word);
    }
    private boolean isOperator(String op) {
        return current.type == TokenType.OPERATOR && current.value.equals(op);
    }
    private boolean isPunct(String ch) {
        return current.type == TokenType.PUNCTUATION && current.value.equals(ch);
    }
    private boolean isType(TokenType t) {
        return current.type == t;
    }
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

    private void consume() {
        if (pos < tokens.size() - 1) {
            pos++;
        }
        current = tokens.get(pos);
    }

    private void consumeKeyword(String word) {
        if (!isKeyword(word)) {
            throw new ParseException(
                    "Expected keyword '" + word + "' but found " + current, current.line);
        }
        consume();
    }

    private void consumeOperator(String op) {
        if (!isOperator(op)) {
            throw new ParseException(
                    "Expected operator '" + op + "' but found " + current, current.line);
        }
        consume();
    }

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

    public static class ParseException extends RuntimeException {
        public final int line;

        public ParseException(String message, int line) {
            super("Parse error at line " + line + ": " + message);
            this.line = line;
        }
    }

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