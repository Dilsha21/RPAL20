package parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single node in the Abstract Syntax Tree (AST)
 * and the Standardized Tree (ST) for the RPAL interpreter.
 *
 * Used by: Parser (builds it), Standardizer (rewrites it),
 *          CSE Machine (flattens it into control structures).
 */
public class ASTNode {

    // ─── Node type categories ─────────────────────────────────────────────────
    public enum NodeType {
        // Leaf node types (have a value, no children)
        IDENTIFIER,     // variable names:  x, y, myFunc
        INTEGER,        // integer literals: 0, 42, -1
        STRING,         // string literals:  'hello'
        BOOLEAN,        // true, false
        NIL,            // nil  (empty list)
        DUMMY,          // dummy (placeholder in CSE)

        // Internal / keyword node types (have children, no value)
        LET,            // let D in E
        LAMBDA,         // fn Vb+ . E  →  lambda after standardization
        WHERE,          // E where Dr
        TAU,            // tuple constructor (tau n)
        AUG,            // list augmentation  (aug)
        CONDITIONAL,    // B -> T | E  (becomes beta in CSE)
        OR, AND, NOT,   // logical operators
        EQ, NE,         // equality operators
        LT, LE, GT, GE, // comparison operators
        PLUS, MINUS,    // arithmetic
        MULTIPLY, DIVIDE, POWER, NEG, // arithmetic
        AT,             // infix application  @
        APPLY,          // function application (gamma in CSE)
        WITHIN,         // within expression
        AND_DEF,        // simultaneous definitions (and)
        REC,            // rec definition
        EQUAL,          // = inside definition
        COMMA,          // , inside tuple-binding
        FCN_FORM,       // f x y = E  (function form)

        // Tree-level markers (used during standardization / CSE)
        YSTAR,          // Y* combinator node (added by standardizer for rec)
        PARAM_LIST,     // list of parameters in a lambda
        ENV_MARKER,     // environment marker (used by CSE machine)

        UNKNOWN         // fallback — should never appear in a valid tree
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    /** Structural role of this node (e.g. LET, PLUS, IDENTIFIER). */
    public NodeType nodeType;

    /**
     * Raw string tag — matches the label printed by rpal.exe.
     * Examples: "let", "+", "<ID:x>", "<INT:5>", "lambda"
     */
    public String type;

    /**
     * Concrete value for leaf nodes only.
     * Null for internal nodes.
     * Examples: "x" for an identifier, "42" for an integer, "hello" for a string.
     */
    public String value;

    /** Ordered list of child nodes (left to right = first to last child). */
    private List<ASTNode> children;

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Internal node constructor (operator, keyword, structure).
     * @param type  string label, e.g. "let", "+", "lambda"
     */
    public ASTNode(String type) {
        this.type     = type;
        this.value    = null;
        this.nodeType = resolveNodeType(type);
        this.children = new ArrayList<>();
    }

    /**
     * Leaf node constructor (identifier, literal).
     * @param type   string label, e.g. "ID", "INT", "STR"
     * @param value  the concrete value, e.g. "x", "42", "hello"
     */
    public ASTNode(String type, String value) {
        this.type     = type;
        this.value    = value;
        this.nodeType = resolveNodeType(type);
        this.children = new ArrayList<>();
    }

    // ─── Child management ────────────────────────────────────────────────────

    /** Append a child to the right (end) of the children list. */
    public void addChild(ASTNode child) {
        children.add(child);
    }

    /** Insert a child at a specific index (used during standardization rewrites). */
    public void addChild(int index, ASTNode child) {
        children.add(index, child);
    }

    /** Return the nth child (0-indexed). */
    public ASTNode getChild(int index) {
        return children.get(index);
    }

    /** Return all children. */
    public List<ASTNode> getChildren() {
        return children;
    }

    /** Number of children. */
    public int childCount() {
        return children.size();
    }

    /** True if this node has no children — it is a leaf (literal or identifier). */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    /**
     * Deep-copy this node and its entire subtree.
     * Needed by the standardizer when duplicating subtrees (e.g. rec rule).
     */
    public ASTNode deepCopy() {
        ASTNode copy = new ASTNode(this.type, this.value);
        copy.nodeType = this.nodeType;
        for (ASTNode child : children) {
            copy.addChild(child.deepCopy());
        }
        return copy;
    }

    /**
     * Print the AST in the indented format expected by rpal.exe.
     * Call with depth=0 on the root to print the whole tree.
     *
     * Format:
     *   let
     *   ..<ID:x>
     *   ..gamma
     *   ....<ID:f>
     */
    public void print(int depth) {
        System.out.println(".".repeat(depth) + toLabel());
        for (ASTNode child : children) {
            child.print(depth + 1);
        }
    }

    /**
     * The printed label for this node — matches rpal.exe -ast output exactly.
     *   Internal node  →  just the type string, e.g. "let", "+"
     *   ID leaf        →  "<ID:x>"
     *   INT leaf       →  "<INT:42>"
     *   STR leaf       →  "<STR:'hello'>"
     *   true/false     →  "<true>" / "<false>"
     *   nil            →  "<nil>"
     *   dummy          →  "<dummy>"
     */
    public String toLabel() {
        switch (nodeType) {
            case IDENTIFIER: return "<ID:" + value + ">";
            case INTEGER:    return "<INT:" + value + ">";
            case STRING:     return "<STR:" + value + ">";
            case BOOLEAN:    return "<" + value + ">";   // value is "true" or "false"
            case NIL:        return "<nil>";
            case DUMMY:      return "<dummy>";
            default:         return type;                // internal node label
        }
    }

    @Override
    public String toString() {
        return toLabel() + " [" + childCount() + " children]";
    }

    // ─── NodeType resolver ────────────────────────────────────────────────────

    /**
     * Map the raw string type tag to its NodeType enum value.
     * Called once in the constructor so the rest of the code
     * can switch on nodeType instead of comparing strings.
     */
    private NodeType resolveNodeType(String type) {
        if (type == null) return NodeType.UNKNOWN;
        switch (type) {
            // Leaves
            case "ID":    return NodeType.IDENTIFIER;
            case "INT":   return NodeType.INTEGER;
            case "STR":   return NodeType.STRING;
            case "true":
            case "false": return NodeType.BOOLEAN;
            case "nil":   return NodeType.NIL;
            case "dummy": return NodeType.DUMMY;

            // Keywords / structure
            case "let":       return NodeType.LET;
            case "lambda":    return NodeType.LAMBDA;
            case "where":     return NodeType.WHERE;
            case "tau":       return NodeType.TAU;
            case "aug":       return NodeType.AUG;
            case "->":        return NodeType.CONDITIONAL;
            case "or":        return NodeType.OR;
            case "&":         return NodeType.AND;
            case "not":       return NodeType.NOT;
            case "eq":        return NodeType.EQ;
            case "ne":        return NodeType.NE;
            case "ls":        return NodeType.LT;
            case "le":        return NodeType.LE;
            case "gr":        return NodeType.GT;
            case "ge":        return NodeType.GE;
            case "+":         return NodeType.PLUS;
            case "-":         return NodeType.MINUS;
            case "*":         return NodeType.MULTIPLY;
            case "/":         return NodeType.DIVIDE;
            case "**":        return NodeType.POWER;
            case "neg":       return NodeType.NEG;
            case "@":         return NodeType.AT;
            case "gamma":     return NodeType.APPLY;
            case "within":    return NodeType.WITHIN;
            case "and":       return NodeType.AND_DEF;
            case "rec":       return NodeType.REC;
            case "=":         return NodeType.EQUAL;
            case ",":         return NodeType.COMMA;
            case "function_form": return NodeType.FCN_FORM;
            case "Ystar":     return NodeType.YSTAR;
            default:          return NodeType.UNKNOWN;
        }
    }
}
