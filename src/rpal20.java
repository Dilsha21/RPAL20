import cse.CSEMachine;
import parser.ASTNode;
import parser.DummyParser;
import standardizer.Standardizer;

/**
 * rpal20 – Main entry point for the RPAL interpreter.
 *
 * Usage:
 *   java rpal20 <source_file>         → lex, parse, standardize, evaluate
 *   java rpal20 -ast <source_file>    → print AST only
 *   java rpal20 -st  <source_file>    → print Standardized Tree only
 *
 * INTEGRATION STATUS (Member 2 branch)
 * ─────────────────────────────────────
 *   ✅ Standardizer   (src/standardizer/Standardizer.java)
 *   ✅ CSE Machine    (src/cse/CSEMachine.java, src/cse/Environment.java)
 *   ⏳ Lexer          → to be provided by Member 1 (Senilka)
 *   ⏳ Parser         → to be provided by Member 1 (Senilka)
 *
 * While the real Lexer/Parser are not available, this main class falls back to
 * DummyParser so the back-end can be run and tested independently.
 */
public class rpal20 {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java rpal20 [-ast|-st] <source_file>");
            System.exit(1);
        }

        // ── Parse flags and filename ──────────────────────────────────────────
        boolean printAst = false;
        boolean printSt  = false;
        String  filename = null;

        for (String arg : args) {
            switch (arg) {
                case "-ast": printAst = true;  break;
                case "-st":  printSt  = true;  break;
                default:     filename = arg;   break;
            }
        }

        if (filename == null) {
            System.err.println("Error: no source file specified.");
            System.exit(1);
        }

        // ── Step 1: Lex + Parse → AST ─────────────────────────────────────────
        ASTNode ast;
        try {
            // TODO: Replace with real Lexer + Parser once Member 1 integrates.
            //
            //   Lexer  lexer  = new Lexer(filename);
            //   Parser parser = new Parser(lexer);
            //   ast = parser.parse();
            //
            // For now, use DummyParser based on the filename stem.
            String programName = stripPath(filename);
            ast = DummyParser.get(programName);
        } catch (IllegalArgumentException e) {
            System.err.println("DummyParser: " + e.getMessage());
            System.err.println("(Real Lexer/Parser not yet integrated — "
                    + "add a test case to DummyParser or wait for Member 1.)");
            System.exit(1);
            return;
        }

        // ── -ast flag: print AST and stop ─────────────────────────────────────
        if (printAst) {
            ast.print(0);
            return;
        }

        // ── Step 2: Standardize AST → ST ─────────────────────────────────────
        Standardizer std = new Standardizer();
        ASTNode st = std.standardize(ast);

        // ── -st flag: print ST and stop ───────────────────────────────────────
        if (printSt) {
            st.print(0);
            return;
        }

        // ── Step 3: Evaluate ST with the CSE Machine ──────────────────────────
        CSEMachine cse = new CSEMachine();
        try {
            cse.evaluate(st);
            // Output is produced as a side-effect of Print() built-in
        } catch (RuntimeException e) {
            System.err.println("Runtime error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Extract the base filename stem from a path.
     * e.g. "rpal/fn1" → "fn1",  "rpal_test_programs/rpal_01" → "rpal_01"
     */
    private static String stripPath(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
