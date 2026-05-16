package cse;

import java.util.HashMap;
import java.util.Map;

/**
 * A single environment frame in the CSE Machine's environment chain.
 *
 * Environments form a linked list (chain) via the {@code parent} reference.
 * Lookup walks the chain until the binding is found or the root is reached.
 *
 * Corresponds to the "e" component of the CSE machine state.
 */
public class Environment {

    /** The parent (enclosing) environment frame. Null for the root (e0). */
    private final Environment parent;

    /** Unique ID for debugging / env-marker matching. */
    final int id;

    /** Bindings in this frame: variable name → RPAL value. */
    private final Map<String, Object> bindings = new HashMap<>();

    /**
     * Create a new environment frame.
     *
     * @param parent  the enclosing environment (null for root)
     * @param id      unique integer id (assigned by the CSE machine)
     */
    public Environment(Environment parent, int id) {
        this.parent = parent;
        this.id     = id;
    }

    /**
     * Bind a name to a value in this frame.
     * Overwrites any existing binding in this frame (shadowing is intentional).
     *
     * @param name   variable name
     * @param value  RPAL runtime value
     */
    public void bind(String name, Object value) {
        bindings.put(name, value);
    }

    /**
     * Look up a name in the environment chain.
     * Searches this frame first, then walks up to the parent.
     *
     * @param name  variable name to look up
     * @return      the bound value, or null if not found anywhere in the chain
     */
    public Object lookup(String name) {
        if (bindings.containsKey(name)) {
            return bindings.get(name);
        }
        if (parent != null) {
            return parent.lookup(name);
        }
        return null;  // unbound
    }

    /**
     * Check whether a name is bound anywhere in the chain.
     *
     * @param name  variable name
     * @return      true if bound
     */
    public boolean contains(String name) {
        return lookup(name) != null;
    }

    /** @return the parent environment frame, or null if this is the root. */
    public Environment getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return "Environment[" + id + "] " + bindings.keySet();
    }
}
