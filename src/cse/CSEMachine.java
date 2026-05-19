package cse;

import parser.ASTNode;
import parser.ASTNode.NodeType;

import java.util.*;

/**
 * CSE (Control-Stack-Environment) Machine.
 *
 * Evaluates a Standardized Tree (ST) by:
 *   1. Flattening the ST into numbered control structures (deltas).
 *   2. Running the abstract machine:
 *        - Control structure (delta array)
 *        - Value stack
 *        - Environment chain
 *
 * After evaluation the final value on the stack is the program result.
 */
public class CSEMachine {

    // ─── Control-structure elements ───────────────────────────────────────────

    /**
     * A "name" token pushed onto the control — will be looked up in the env.
     */
    static class NameToken {
        final String name;
        NameToken(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    /**
     * A lambda token in the control structure.
     * Holds the index of the delta it refers to and the bound variable(s).
     */
    static class LambdaToken {
        final int    deltaIndex;    // which delta this lambda's body lives in
        final Object boundVar;      // NameToken (single) or List<NameToken> (tuple)
        Environment  closureEnv;    // environment captured at creation time

        LambdaToken(int deltaIndex, Object boundVar) {
            this.deltaIndex = deltaIndex;
            this.boundVar   = boundVar;
        }
        @Override public String toString() {
            return "<lambda:" + deltaIndex + ">";
        }
    }

    /**
     * A closure = lambda token + the environment it was created in.
     * Pushed onto the value stack when a lambda is "evaluated".
     */
    static class Closure {
        final LambdaToken lambda;
        final Environment env;
        Closure(LambdaToken lambda, Environment env) {
            this.lambda = lambda;
            this.env    = env;
        }
        @Override public String toString() {
            return "<closure:" + lambda.deltaIndex + ">";
        }
    }

    /** Environment-exit marker placed on the control stack. */
    static class EnvMarker {
        final int envIndex;
        EnvMarker(int envIndex) { this.envIndex = envIndex; }
    }

    /** Beta (conditional) marker. */
    static class BetaToken {
        final int thenDelta;
        final int elseDelta;
        BetaToken(int t, int e) { thenDelta = t; elseDelta = e; }
    }

    /** Tau (tuple constructor) marker — arity tells how many stack items to collect. */
    static class TauToken {
        final int arity;
        TauToken(int n) { arity = n; }
    }

    /**
     * An RPAL tuple value — an ordered list of RPAL values.
     */
    static class RpalTuple {
        final List<Object> elements;
        RpalTuple(List<Object> elements) { this.elements = new ArrayList<>(elements); }
        @Override public String toString() {
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < elements.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(rpalValueToString(elements.get(i)));
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /** Y* (Ystar) combinator token on the stack. */
    static class YstarToken {
        @Override public String toString() { return "<Y*>"; }
    }

    /**
     * An eta-closure: the fixed-point expansion of a recursive closure.
     * When applied, it expands to  gamma(closure, eta).
     */
    static class EtaClosure {
        final Closure closure;
        EtaClosure(Closure c) { this.closure = c; }
        @Override public String toString() { return "<eta:" + closure.lambda.deltaIndex + ">"; }
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    /** All control structures (deltas). delta[0] is the entry point. */
    private final List<List<Object>> deltas = new ArrayList<>();

    /** The value stack. */
    private final Deque<Object> stack = new ArrayDeque<>();

    /** The current control structure (list of tokens/nodes to process). */
    private List<Object> control;
    private int controlPointer;

    /** Environment stack (chain). */
    private Environment currentEnv;

    /** Counter used to assign unique IDs to environments (for debugging). */
    private int envCounter = 0;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Run the CSE machine on the given Standardized Tree.
     * Returns the final value produced by the program.
     *
     * @param stRoot  root of the Standardized Tree
     * @return        the RPAL value result
     */
    public Object evaluate(ASTNode stRoot) {
        // Step 1: Flatten ST into control structures
        // delta[0] is a placeholder (unused); delta[1] is the entry point.
        // This matches the reference rpal.exe numbering where user lambdas start at delta[2].
        deltas.add(new ArrayList<>());          // delta[0] unused
        deltas.add(new ArrayList<>());          // delta[1] is the entry delta
        flattenST(stRoot, 1);

        // Step 2: Set up initial machine state
        currentEnv  = new Environment(null, 0); // e0: empty root environment
        envCounter  = 1;
        control     = new ArrayList<>(deltas.get(1));
        controlPointer = 0;

        // Push initial env marker (e0) onto the stack  (not strictly needed but
        // keeps the machine symmetric)
        stack.push(currentEnv);

        // Step 3: Run the machine
        run();

        // Step 4: Return the top of the value stack
        return stack.isEmpty() ? null : stack.peek();
    }

    // ─── Phase 1: Flatten ST → deltas ────────────────────────────────────────

    /**
     * Recursively walk the ST and populate delta[deltaIndex].
     *
     * Most nodes are pushed as-is (as primitives or NameTokens).
     * Lambda and conditional nodes create new deltas.
     */
    private void flattenST(ASTNode node, int deltaIndex) {
        List<Object> delta = deltas.get(deltaIndex);

        switch (node.nodeType) {

            // ── Identifier → NameToken ──
            case IDENTIFIER: {
                delta.add(new NameToken(node.value));
                break;
            }

            // ── Literals → boxed Java values ──
            case INTEGER: {
                delta.add(Integer.parseInt(node.value));
                break;
            }
            case STRING: {
                // Strip surrounding single-quotes stored by the lexer
                String s = node.value;
                if (s.startsWith("'") && s.endsWith("'"))
                    s = s.substring(1, s.length() - 1);
                // Unescape RPAL escape sequences
                s = unescapeRpal(s);
                delta.add(s);
                break;
            }
            case BOOLEAN: {
                delta.add(Boolean.parseBoolean(node.value));
                break;
            }
            case NIL: {
                delta.add(new RpalTuple(Collections.emptyList()));
                break;
            }
            case DUMMY: {
                delta.add("dummy");
                break;
            }

            // ── Lambda → create new delta, push LambdaToken ──
            case LAMBDA: {
                int newIdx = deltas.size();
                deltas.add(new ArrayList<>());

                // Bound variable(s): child 0 is param(s), child 1 is body
                ASTNode paramNode = node.getChild(0);
                Object  boundVar  = extractBoundVar(paramNode);

                LambdaToken tok = new LambdaToken(newIdx, boundVar);
                delta.add(tok);

                // Flatten body into the new delta
                flattenST(node.getChild(1), newIdx);
                break;
            }

            // ── Conditional (->)  →  beta + two new deltas ──
            case CONDITIONAL: {
                // children: condition, then-expr, else-expr
                int thenIdx = deltas.size();   deltas.add(new ArrayList<>());
                int elseIdx = deltas.size();   deltas.add(new ArrayList<>());

                flattenST(node.getChild(1), thenIdx);
                flattenST(node.getChild(2), elseIdx);

                // Flatten condition into current delta, then push beta
                flattenST(node.getChild(0), deltaIndex);
                delta.add(new BetaToken(thenIdx, elseIdx));
                break;
            }

            // ── Tau → push TauToken after flattening all children ──
            case TAU: {
                for (ASTNode child : node.getChildren()) {
                    flattenST(child, deltaIndex);
                }
                delta.add(new TauToken(node.childCount()));
                break;
            }

            // ── Apply (gamma) → flatten both children, then gamma marker ──
            case APPLY: {
                // For gamma, flatten operator then operand, push "gamma"
                flattenST(node.getChild(1), deltaIndex);  // rand first
                flattenST(node.getChild(0), deltaIndex);  // rator second
                delta.add("gamma");
                break;
            }

            // ── Ystar ──
            case YSTAR: {
                delta.add(new YstarToken());
                break;
            }

            // ── Binary/Unary operators ──
            case PLUS: case MINUS: case MULTIPLY: case DIVIDE: case POWER:
            case EQ: case NE: case LT: case LE: case GT: case GE:
            case OR: case AND:
            case AUG: {
                // Push both operands then the operator itself
                flattenST(node.getChild(0), deltaIndex);
                flattenST(node.getChild(1), deltaIndex);
                delta.add(opToken(node.nodeType));
                break;
            }
            case NEG: case NOT: {
                flattenST(node.getChild(0), deltaIndex);
                delta.add(opToken(node.nodeType));
                break;
            }

            default:
                // Fallback: push node type string
                delta.add(node.type);
                break;
        }
    }

    /**
     * Extract the bound variable descriptor from a lambda's parameter node.
     * Returns a NameToken for a single ID, or a List<NameToken> for a tuple binding.
     */
    private Object extractBoundVar(ASTNode paramNode) {
        if (paramNode.nodeType == NodeType.IDENTIFIER) {
            return new NameToken(paramNode.value);
        }
        if (paramNode.nodeType == NodeType.COMMA) {
            // Tuple binding: (x, y, ...)
            List<NameToken> names = new ArrayList<>();
            for (ASTNode child : paramNode.getChildren()) {
                names.add(new NameToken(child.value));
            }
            return names;
        }
        // Single identifier wrapped in some other node
        return new NameToken(paramNode.value);
    }

    /** Map NodeType to a string operator token for the control structure. */
    private String opToken(NodeType t) {
        switch (t) {
            case PLUS:     return "+";
            case MINUS:    return "-";
            case MULTIPLY: return "*";
            case DIVIDE:   return "/";
            case POWER:    return "**";
            case EQ:       return "eq";
            case NE:       return "ne";
            case LT:       return "ls";
            case LE:       return "le";
            case GT:       return "gr";
            case GE:       return "ge";
            case OR:       return "or";
            case AND:      return "&";
            case NEG:      return "neg";
            case NOT:      return "not";
            case AUG:      return "aug";
            default:       return t.name().toLowerCase();
        }
    }

    // ─── Phase 2: Run the CSE machine ─────────────────────────────────────────

    /**
     * Main evaluation loop.
     * Processes the current control list from left to right.
     * The control list is managed as a Deque for efficient head-removal.
     */
    private void run() {
        // Convert control to a deque for O(1) pop-front
        Deque<Object> ctrl = new ArrayDeque<>(deltas.get(1));

        while (!ctrl.isEmpty()) {
            Object token = ctrl.pollFirst();

            // ── Literals / values ──────────────────────────────────────────
            if (token instanceof Integer || token instanceof Boolean
                    || token instanceof String && !isOperator(token)
                    || token instanceof RpalTuple) {
                stack.push(token);

            // ── NameToken → look up in environment ──────────────────────
            } else if (token instanceof NameToken) {
                String name = ((NameToken) token).name;
                Object val  = lookupBuiltin(name);
                if (val == null) val = currentEnv.lookup(name);
                if (val == null) throw new RuntimeException("Unbound identifier: " + name);
                stack.push(val);

            // ── LambdaToken → create closure and push ────────────────────
            } else if (token instanceof LambdaToken) {
                LambdaToken lam = (LambdaToken) token;
                stack.push(new Closure(lam, currentEnv));

            // ── YstarToken → push Ystar ──────────────────────────────────
            } else if (token instanceof YstarToken) {
                stack.push(new YstarToken());

            // ── gamma → apply rator to rand ──────────────────────────────
            } else if ("gamma".equals(token)) {
                applyGamma(ctrl);

            // ── TauToken → collect n items from stack into a tuple ───────
            } else if (token instanceof TauToken) {
                int n = ((TauToken) token).arity;
                List<Object> elems = new ArrayList<>(n);
                for (int i = 0; i < n; i++) elems.add(stack.pop());
                Collections.reverse(elems);
                stack.push(new RpalTuple(elems));

            // ── BetaToken → conditional branch ───────────────────────────
            } else if (token instanceof BetaToken) {
                BetaToken beta = (BetaToken) token;
                Object cond = stack.pop();
                if (!(cond instanceof Boolean))
                    throw new RuntimeException("Conditional requires a boolean, got: " + cond);
                int idx = ((Boolean) cond) ? beta.thenDelta : beta.elseDelta;
                // Prepend the chosen delta to the front of the control
                List<Object> chosen = deltas.get(idx);
                for (int i = chosen.size() - 1; i >= 0; i--) {
                    ctrl.addFirst(chosen.get(i));
                }

            // ── EnvMarker → restore environment ──────────────────────────
            } else if (token instanceof EnvMarker) {
                // Pop value, pop env marker from stack, restore env
                Object result = stack.pop();
                // Remove the matching environment marker from the stack
                while (!stack.isEmpty() && !(stack.peek() instanceof Environment)) {
                    stack.pop();  // shouldn't happen in correct code
                }
                if (!stack.isEmpty() && stack.peek() instanceof Environment) {
                    Environment prev = (Environment) stack.pop();
                    currentEnv = prev;
                }
                stack.push(result);

            // ── Unary operators ──────────────────────────────────────────
            } else if ("neg".equals(token)) {
                Object v = stack.pop();
                stack.push(-toInt(v));
            } else if ("not".equals(token)) {
                Object v = stack.pop();
                stack.push(!toBool(v));

            // ── Binary operators ─────────────────────────────────────────
            } else if (isBinaryOp(token)) {
                Object right = stack.pop();
                Object left  = stack.pop();
                stack.push(applyBinaryOp((String) token, left, right));

            // ── aug ───────────────────────────────────────────────────────
            } else if ("aug".equals(token)) {
                Object right = stack.pop();
                Object left  = stack.pop();
                stack.push(augTuple(left, right));

            // ── Anything else: push as-is ─────────────────────────────────
            } else {
                stack.push(token);
            }
        }
    }

    // ─── gamma application ────────────────────────────────────────────────────

    /**
     * Handle a gamma (function application).
     * Pops rator then rand from the stack and applies.
     */
    @SuppressWarnings("unchecked")
    private void applyGamma(Deque<Object> ctrl) {
        Object rator = stack.pop();
        Object rand  = stack.pop();

        // ── Closure application ──────────────────────────────────────────
        if (rator instanceof Closure) {
            Closure cls = (Closure) rator;

            // Create new environment extending the closure's environment
            Environment newEnv = new Environment(cls.env, envCounter++);
            bindParams(cls.lambda.boundVar, rand, newEnv);

            // Push env marker onto value stack to mark the frame boundary
            stack.push(currentEnv);  // save previous env

            // Switch to new environment
            currentEnv = newEnv;

            // Prepend the lambda's control delta + env marker to the control
            List<Object> body = deltas.get(cls.lambda.deltaIndex);
            // Push env-exit marker at the end of the body
            ctrl.addFirst(new EnvMarker(newEnv.id));
            for (int i = body.size() - 1; i >= 0; i--) {
                ctrl.addFirst(body.get(i));
            }

        // ── Y* application → create eta-closure ──────────────────────────
        } else if (rator instanceof YstarToken) {
            if (rand instanceof Closure) {
                stack.push(new EtaClosure((Closure) rand));
            } else {
                throw new RuntimeException("Y* applied to non-closure: " + rand);
            }

        // ── Eta-closure application → expand: eta @ rand = closure(eta) @ rand ──
        } else if (rator instanceof EtaClosure) {
            EtaClosure eta = (EtaClosure) rator;
            // Correct Y* expansion:
            //   eta @ rand  →  (closure @ eta) @ rand
            // Push rand back, then closure, then eta, then two gammas
            stack.push(rand);   // will be consumed by outer gamma
            stack.push(eta);    // eta serves as argument to closure
            stack.push(eta.closure);
            ctrl.addFirst("gamma"); // outer: result @ rand
            ctrl.addFirst("gamma"); // inner: closure @ eta

        // ── Built-in function application ───────────────────────────────
        } else if (rator instanceof String && ((String) rator).startsWith("Conc:")) {
            // Second argument to the curried Conc function
            String s1 = ((String) rator).substring(5);  // strip "Conc:" prefix
            stack.push(s1 + (String) rand);

        } else if (rator instanceof String) {
            Object result = applyBuiltinFunction((String) rator, rand);
            stack.push(result);

        // ── Tuple indexing: tuple @ integer ──────────────────────────────
        } else if (rator instanceof RpalTuple) {
            int idx = toInt(rand) - 1;  // RPAL tuples are 1-indexed
            RpalTuple t = (RpalTuple) rator;
            if (idx < 0 || idx >= t.elements.size())
                throw new RuntimeException("Tuple index out of bounds: " + (idx+1));
            stack.push(t.elements.get(idx));

        } else {
            throw new RuntimeException("Cannot apply: " + rator + " to " + rand);
        }
    }

    /**
     * Bind formal parameters to actual arguments in newEnv.
     */
    @SuppressWarnings("unchecked")
    private void bindParams(Object boundVar, Object rand, Environment newEnv) {
        if (boundVar instanceof NameToken) {
            newEnv.bind(((NameToken) boundVar).name, rand);
        } else if (boundVar instanceof List) {
            // Tuple binding
            List<NameToken> names = (List<NameToken>) boundVar;
            RpalTuple tuple = (RpalTuple) rand;
            for (int i = 0; i < names.size(); i++) {
                newEnv.bind(names.get(i).name, tuple.elements.get(i));
            }
        }
    }

    // ─── Operators ────────────────────────────────────────────────────────────

    private boolean isBinaryOp(Object token) {
        if (!(token instanceof String)) return false;
        switch ((String) token) {
            case "+": case "-": case "*": case "/": case "**":
            case "eq": case "ne": case "ls": case "le": case "gr": case "ge":
            case "or": case "&":
                return true;
            default: return false;
        }
    }

    private boolean isOperator(Object token) {
        if (!(token instanceof String)) return false;
        String s = (String) token;
        if (s.startsWith("Conc:")) return false;  // partial Conc — treat as value
        return isBinaryOp(token) || "neg".equals(s) || "not".equals(s)
                || "gamma".equals(s) || "aug".equals(s);
    }

    private Object applyBinaryOp(String op, Object left, Object right) {
        switch (op) {
            case "+":  return toInt(left) + toInt(right);
            case "-":  return toInt(left) - toInt(right);
            case "*":  return toInt(left) * toInt(right);
            case "/":  return toInt(left) / toInt(right);
            case "**": return (int) Math.pow(toInt(left), toInt(right));
            case "eq": return rpalEqual(left, right);
            case "ne": return !((Boolean) rpalEqual(left, right));
            case "ls": return toInt(left) <  toInt(right);
            case "le": return toInt(left) <= toInt(right);
            case "gr": return toInt(left) >  toInt(right);
            case "ge": return toInt(left) >= toInt(right);
            case "or": return toBool(left) || toBool(right);
            case "&":  return toBool(left) && toBool(right);
            default:   throw new RuntimeException("Unknown operator: " + op);
        }
    }

    private Object rpalEqual(Object a, Object b) {
        if (a instanceof Integer && b instanceof Integer) return a.equals(b);
        if (a instanceof String  && b instanceof String)  return a.equals(b);
        if (a instanceof Boolean && b instanceof Boolean) return a.equals(b);
        return false;
    }

    private Object augTuple(Object left, Object right) {
        List<Object> elems = new ArrayList<>();
        if (left instanceof RpalTuple) {
            elems.addAll(((RpalTuple) left).elements);
        }
        elems.add(right);
        return new RpalTuple(elems);
    }

    // ─── Built-in functions ───────────────────────────────────────────────────

    /**
     * Check if a name refers to a built-in function constant.
     * Returns the built-in tag string, or null if it's not a built-in.
     */
    private Object lookupBuiltin(String name) {
        switch (name) {
            case "Print": case "print":
            case "Isinteger": case "Isstring": case "Istruthvalue":
            case "Istuple": case "Isfunction": case "Isdummy":
            case "Order": case "Null":
            case "Stem": case "Stern": case "Conc":
            case "ItoS":
                return name;  // the name itself is the function tag
            default:
                return null;
        }
    }

    /**
     * Apply a built-in function to an argument.
     */
    private Object applyBuiltinFunction(String name, Object arg) {
        switch (name) {

            case "Print": case "print": {
                System.out.println(rpalValueToString(arg));
                return "dummy";
            }

            case "Isinteger":   return arg instanceof Integer;
            case "Isstring":    return arg instanceof String;
            case "Istruthvalue":return arg instanceof Boolean;
            case "Istuple":     return arg instanceof RpalTuple;
            case "Isfunction":  return arg instanceof Closure || arg instanceof EtaClosure;
            case "Isdummy":     return "dummy".equals(arg);

            case "Order": {
                if (arg instanceof RpalTuple) return ((RpalTuple) arg).elements.size();
                throw new RuntimeException("Order: expected tuple, got " + arg);
            }
            case "Null": {
                if (arg instanceof RpalTuple) return ((RpalTuple) arg).elements.isEmpty();
                throw new RuntimeException("Null: expected tuple, got " + arg);
            }

            case "Stem": {
                String s = (String) arg;
                if (s.isEmpty()) throw new RuntimeException("Stem of empty string");
                return String.valueOf(s.charAt(0));
            }
            case "Stern": {
                String s = (String) arg;
                if (s.isEmpty()) throw new RuntimeException("Stern of empty string");
                return s.substring(1);
            }
            case "Conc": {
                // Conc is curried: Conc s1 → partial, then partial s2 → s1 ++ s2
                // Return a tagged string so the second application can recognise it.
                return "Conc:" + arg;
            }

            case "ItoS": {
                return String.valueOf(toInt(arg));
            }

            default:
                throw new RuntimeException("Unknown built-in: " + name);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int toInt(Object v) {
        if (v instanceof Integer) return (Integer) v;
        throw new RuntimeException("Expected integer, got: " + v + " (" + (v==null?"null":v.getClass().getSimpleName()) + ")");
    }
    private boolean toBool(Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        throw new RuntimeException("Expected boolean, got: " + v);
    }

    /**
     * Convert an RPAL runtime value to its printable string representation.
     * Matches rpal.exe output format exactly.
     */
    static String rpalValueToString(Object v) {
        if (v instanceof Integer)    return v.toString();
        if (v instanceof Boolean)    return v.toString();
        if (v instanceof String)     return (String) v;
        if (v instanceof RpalTuple)  return v.toString();
        if (v instanceof Closure) {
            Closure c = (Closure) v;
            return "[lambda closure: " + boundVarToString(c.lambda.boundVar)
                    + ": " + c.lambda.deltaIndex + "]";
        }
        if (v instanceof EtaClosure) {
            EtaClosure e = (EtaClosure) v;
            return "[lambda closure: " + boundVarToString(e.closure.lambda.boundVar)
                    + ": " + e.closure.lambda.deltaIndex + "]";
        }
        if (v == null)               return "nil";
        return v.toString();
    }

    @SuppressWarnings("unchecked")
    private static String boundVarToString(Object boundVar) {
        if (boundVar instanceof NameToken) {
            return ((NameToken) boundVar).name;
        }
        if (boundVar instanceof List) {
            List<NameToken> names = (List<NameToken>) boundVar;
            StringBuilder sb = new StringBuilder("(");
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(names.get(i).name);
            }
            sb.append(")");
            return sb.toString();
        }
        return boundVar.toString();
    }

    /**
     * Unescape RPAL string escape sequences.
     */
    private String unescapeRpal(String s) {
        return s.replace("\\t", "\t")
                .replace("\\n", "\n")
                .replace("\\'", "'")
                .replace("\\\\", "\\");
    }
}
