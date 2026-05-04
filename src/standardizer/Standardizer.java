package standardizer;

import parser.ASTNode;
import parser.ASTNode.NodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Standardizer: Converts an Abstract Syntax Tree (AST) into a
 * Standardized Tree (ST) by applying the RPAL standardization rules.
 *
 * Rules applied (matching rpal.exe -st output):
 *
 *   let x = E in P    →  gamma( lambda(x, P), E )
 *   where P x = E     →  gamma( lambda(x, P), E )
 *   fn Vb1 Vb2 . E    →  lambda(Vb1, lambda(Vb2, E))
 *   f Vb+ = E         →  =( f, lambda(Vb+, E) )         [fcn_form]
 *   rec =(f, E)        →  =( f, gamma(Ystar, lambda(f, E)) )
 *   D1 within D2       →  =( x2, gamma(lambda(x1, E2), E1) )
 *   and(=(...),...)    →  =( ,(x1,x2,...), tau(E1,E2,...) )
 *   E1 @ f E2          →  gamma( gamma(f, E1), E2 )
 */
public class Standardizer {

    /**
     * Entry point: standardize the whole tree in place (post-order traversal).
     * Children are standardized before the current node is rewritten.
     *
     * @param root  the root of the AST produced by the parser
     * @return      the same root, now representing the standardized tree (ST)
     */
    public ASTNode standardize(ASTNode root) {
        // Post-order: standardize children first, then this node
        for (ASTNode child : root.getChildren()) {
            standardize(child);
        }
        applyRule(root);
        return root;
    }

    /**
     * Dispatch to the matching standardization rule for a single node.
     * Nodes not matching any rule are left unchanged.
     */
    private void applyRule(ASTNode node) {
        switch (node.nodeType) {
            case LET:      standardizeLet(node);     break;
            case WHERE:    standardizeWhere(node);   break;
            case LAMBDA:   standardizeLambda(node);  break;
            case FCN_FORM: standardizeFcnForm(node); break;
            case WITHIN:   standardizeWithin(node);  break;
            case AND_DEF:  standardizeAnd(node);     break;
            case REC:      standardizeRec(node);     break;
            case AT:       standardizeAt(node);      break;
            default:       break;
        }
    }

    // ─── Rule: let ────────────────────────────────────────────────────────────
    /**
     * let X = E in P  →  gamma( lambda(X, P), E )
     *
     * AST shape:  let → [ =(X, E),  P ]
     */
    private void standardizeLet(ASTNode node) {
        ASTNode eq = node.getChild(0);   // '=' node
        ASTNode P  = node.getChild(1);   // body expression (the 'in' part)
        ASTNode X  = eq.getChild(0);     // bound variable(s)
        ASTNode E  = eq.getChild(1);     // binding expression

        // Build lambda(X, P)
        ASTNode lambda = newNode("lambda");
        lambda.addChild(X);
        lambda.addChild(P);

        // Rewrite this node as gamma(lambda(X, P), E)
        rewriteGamma(node, lambda, E);
    }

    // ─── Rule: where ──────────────────────────────────────────────────────────
    /**
     * P where X = E  →  gamma( lambda(X, P), E )
     *
     * AST shape:  where → [ P,  =(X, E) ]
     */
    private void standardizeWhere(ASTNode node) {
        ASTNode P  = node.getChild(0);   // body expression (the 'P' part)
        ASTNode eq = node.getChild(1);   // '=' node
        ASTNode X  = eq.getChild(0);     // bound variable(s)
        ASTNode E  = eq.getChild(1);     // binding expression

        ASTNode lambda = newNode("lambda");
        lambda.addChild(X);
        lambda.addChild(P);

        rewriteGamma(node, lambda, E);
    }

    // ─── Rule: lambda (multiple parameters) ──────────────────────────────────
    /**
     * lambda(Vb1, Vb2, ..., VbN, E)  →  lambda(Vb1, lambda(Vb2, ...lambda(VbN, E)))
     *
     * The parser may produce a flat lambda with N parameters followed by a body.
     * We nest them right-to-left here so each lambda has exactly one parameter.
     *
     * If the lambda already has exactly 2 children (one param + one body), no change.
     */
    private void standardizeLambda(ASTNode node) {
        int n = node.childCount();
        if (n <= 2) return;  // Already lambda(Vb, E) — nothing to do

        // Snapshot children before we clear the node
        List<ASTNode> kids = new ArrayList<>(node.getChildren());
        ASTNode body = kids.get(n - 1);  // rightmost child is the body expression

        // Build nested lambdas from right to left over parameters kids[1 .. n-2]
        ASTNode nested = body;
        for (int i = n - 2; i >= 1; i--) {
            ASTNode lam = newNode("lambda");
            lam.addChild(kids.get(i));
            lam.addChild(nested);
            nested = lam;
        }

        // Rewrite this node as lambda(Vb1, nested)
        node.getChildren().clear();
        node.addChild(kids.get(0));  // Vb1 (first parameter)
        node.addChild(nested);
    }

    // ─── Rule: fcn_form ───────────────────────────────────────────────────────
    /**
     * f Vb+ = E  →  =( f, lambda(Vb1, lambda(Vb2, ... lambda(VbN, E))) )
     *
     * AST shape:  fcn_form → [ f, Vb1, Vb2, ..., E ]
     *   children[0]     = function name identifier
     *   children[1..n-2]= parameters Vb+
     *   children[n-1]   = body expression E
     */
    private void standardizeFcnForm(ASTNode node) {
        List<ASTNode> kids = new ArrayList<>(node.getChildren());
        int n = kids.size();

        ASTNode fname      = kids.get(0);      // function name
        ASTNode bodyExpr   = kids.get(n - 1);  // body E

        // Build nested lambdas for parameters kids[1 .. n-2] around the body
        ASTNode lambdaTree = bodyExpr;
        for (int i = n - 2; i >= 1; i--) {
            ASTNode lam = newNode("lambda");
            lam.addChild(kids.get(i));
            lam.addChild(lambdaTree);
            lambdaTree = lam;
        }

        // Rewrite this node as =(fname, lambdaTree)
        rewriteNode(node, "=", NodeType.EQUAL, null);
        node.addChild(fname);
        node.addChild(lambdaTree);
    }

    // ─── Rule: within ─────────────────────────────────────────────────────────
    /**
     * within( =(x1, E1), =(x2, E2) )
     *   →  =( x2,  gamma( lambda(x1, E2),  E1 ) )
     *
     * AST shape:  within → [ =(x1, E1),  =(x2, E2) ]
     */
    private void standardizeWithin(ASTNode node) {
        ASTNode eq1 = node.getChild(0);
        ASTNode eq2 = node.getChild(1);

        ASTNode x1 = eq1.getChild(0);
        ASTNode E1 = eq1.getChild(1);
        ASTNode x2 = eq2.getChild(0);
        ASTNode E2 = eq2.getChild(1);

        // lambda(x1, E2) — x1 from D1, body from D2
        ASTNode lam = newNode("lambda");
        lam.addChild(x1);
        lam.addChild(E2);

        // gamma(lambda(x1, E2), E1)
        ASTNode gamma = newNode("gamma");
        gamma.addChild(lam);
        gamma.addChild(E1);

        // Rewrite as =(x2, gamma(...))
        rewriteNode(node, "=", NodeType.EQUAL, null);
        node.addChild(x2);
        node.addChild(gamma);
    }

    // ─── Rule: and (simultaneous definitions) ────────────────────────────────
    /**
     * and( =(x1,E1), =(x2,E2), ... )
     *   →  =( ,(x1, x2, ...),  tau(E1, E2, ...) )
     *
     * AST shape:  and → [ =(x1,E1), =(x2,E2), ... ]
     */
    private void standardizeAnd(ASTNode node) {
        List<ASTNode> defs = new ArrayList<>(node.getChildren());

        ASTNode comma = newNode(",");   // tuple of variable names
        ASTNode tau   = newNode("tau"); // tuple of binding expressions

        for (ASTNode eq : defs) {
            comma.addChild(eq.getChild(0));  // xi  (the bound name)
            tau.addChild(eq.getChild(1));    // Ei  (the binding expression)
        }

        // Rewrite as =( comma(x1,...), tau(E1,...) )
        rewriteNode(node, "=", NodeType.EQUAL, null);
        node.addChild(comma);
        node.addChild(tau);
    }

    // ─── Rule: rec ────────────────────────────────────────────────────────────
    /**
     * rec =(f, E)  →  =( f,  gamma( Ystar, lambda(f, E) ) )
     *
     * AST shape:  rec → [ =(f, E) ]
     */
    private void standardizeRec(ASTNode node) {
        ASTNode eq = node.getChild(0);   // '=' node
        ASTNode f  = eq.getChild(0);     // the recursive function name
        ASTNode E  = eq.getChild(1);     // the function body

        // lambda(f, E)  — note: deepCopy f because it's used twice (LHS and inside lambda)
        ASTNode lam = newNode("lambda");
        lam.addChild(f.deepCopy());
        lam.addChild(E);

        // gamma(Ystar, lambda(f, E))
        ASTNode ystar = newNode("Ystar");
        ASTNode gamma = newNode("gamma");
        gamma.addChild(ystar);
        gamma.addChild(lam);

        // Rewrite rec node as =(f, gamma(Ystar, lambda(f, E)))
        rewriteNode(node, "=", NodeType.EQUAL, null);
        node.addChild(f);
        node.addChild(gamma);
    }

    // ─── Rule: @ (infix application) ─────────────────────────────────────────
    /**
     * E1 @ f E2  →  gamma( gamma(f, E1),  E2 )
     *
     * AST shape:  @ → [ E1, f, E2 ]
     *   E1 is the first argument, f is the infix function, E2 is the second argument.
     */
    private void standardizeAt(ASTNode node) {
        ASTNode E1 = node.getChild(0);  // left operand
        ASTNode f  = node.getChild(1);  // infix function identifier
        ASTNode E2 = node.getChild(2);  // right operand

        // inner: gamma(f, E1)
        ASTNode inner = newNode("gamma");
        inner.addChild(f);
        inner.addChild(E1);

        // outer: gamma(gamma(f, E1), E2)
        rewriteGamma(node, inner, E2);
    }

    // ─── Shared helpers ───────────────────────────────────────────────────────

    /**
     * Create a new internal ASTNode with the given raw type string.
     * NodeType is resolved automatically by the ASTNode constructor.
     */
    private ASTNode newNode(String type) {
        return new ASTNode(type);
    }

    /**
     * Rewrite {@code node} in-place as  gamma(left, right).
     * Clears existing children, sets type to "gamma", adds new children.
     */
    private void rewriteGamma(ASTNode node, ASTNode left, ASTNode right) {
        rewriteNode(node, "gamma", NodeType.APPLY, null);
        node.addChild(left);
        node.addChild(right);
    }

    /**
     * Clear and reset a node's type/nodeType/value in preparation for rewriting.
     * Children list is cleared; caller must re-add the correct children.
     *
     * @param node      the node to reset
     * @param type      new raw type string (e.g. "gamma", "=", "lambda")
     * @param nodeType  corresponding NodeType enum value
     * @param value     new value (null for internal nodes)
     */
    private void rewriteNode(ASTNode node, String type, NodeType nodeType, String value) {
        node.type     = type;
        node.nodeType = nodeType;
        node.value    = value;
        node.getChildren().clear();
    }
}
