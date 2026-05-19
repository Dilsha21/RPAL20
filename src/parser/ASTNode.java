package parser;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single node in the Abstract Syntax Tree (AST)
 * and the Standardized Tree (ST) for the RPAL interpreter.
 */
public class ASTNode {

    public enum NodeType {
        // Leaf node types (have a value, no children)
        IDENTIFIER,     
        INTEGER,        
        STRING,         
        BOOLEAN,        
        NIL,           
        DUMMY,          

        LET,            
        LAMBDA,         
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


    // Structural role of this node 
    public NodeType nodeType;
    public String type;
    public String value;

    private List<ASTNode> children;


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
     * Leaf node constructor.
     * @param type   
     * @param value  
     */
    public ASTNode(String type, String value) {
        this.type     = type;
        this.value    = value;
        this.nodeType = resolveNodeType(type);
        this.children = new ArrayList<>();
    }


    //Append a child to the right (end) of the children list.
    public void addChild(ASTNode child) {
        children.add(child);
    }

    // Insert a child at a specific index.
    public void addChild(int index, ASTNode child) {
        children.add(index, child);
    }

    //Return the nth child (0-indexed).
    public ASTNode getChild(int index) {
        return children.get(index);
    }

    // Return all children.
    public List<ASTNode> getChildren() {
        return children;
    }

    // Number of children. 
    public int childCount() {
        return children.size();
    }

    // True if this node has no children 
    public boolean isLeaf() {
        return children.isEmpty();
    }


    public ASTNode deepCopy() {
        ASTNode copy = new ASTNode(this.type, this.value);
        copy.nodeType = this.nodeType;
        for (ASTNode child : children) {
            copy.addChild(child.deepCopy());
        }
        return copy;
    }

    
     //Print the AST in the indented format.

    public void print(int depth) {
        System.out.println(".".repeat(depth) + toLabel());
        for (ASTNode child : children) {
            child.print(depth + 1);
        }
    }

    public String toLabel() {
        switch (nodeType) {
            case IDENTIFIER: return "<ID:" + value + ">";
            case INTEGER:    return "<INT:" + value + ">";
            case STRING:     return "<STR:" + value + ">";
            case BOOLEAN:    return "<" + value + ">";  
            case NIL:        return "<nil>";
            case DUMMY:      return "<dummy>";
            default:         return type;              
        }
    }

    @Override
    public String toString() {
        return toLabel() + " [" + childCount() + " children]";
    }


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
