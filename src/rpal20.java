import cse.CSEMachine;
import parser.ASTNode;
import lexer.Lexer;
import parser.Parser;
import standardizer.Standardizer;

/**
 * rpal20 – Main entry point for the RPAL interpreter.
 *
 * Usage:
 * java rpal20 <source_file> → lex, parse, standardize, evaluate
 * java rpal20 -ast <source_file> → print AST only
 * java rpal20 -st <source_file> → print Standardized Tree only
 *
 * INTEGRATION STATUS (Member 2 branch)
 * ─────────────────────────────────────
 * ✅ Standardizer (src/standardizer/Standardizer.java)
 * ✅ CSE Machine (src/cse/CSEMachine.java, src/cse/Environment.java)
 * ✅ Lexer (src/lexer/Lexer.java)
 * ✅ Parser (src/parser/Parser.java)
 */
public class rpal20 {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: java rpal20 [-ast|-st] <source_file>");
            System.exit(1);
        }

        // ── Parse flags and filename ──────────────────────────────────────────
        boolean printAst = false;
        boolean printSt = false;
        String filename = null;

        for (String arg : args) {
            switch (arg) {
                case "-ast":
                    printAst = true;
                    break;
                case "-st":
                    printSt = true;
                    break;
                default:
                    filename = arg;
                    break;
            }
        }

        if (filename == null) {
            System.err.println("Error: no source file specified.");
            System.exit(1);
        }

        // ── Step 1: Lex + Parse → AST ─────────────────────────────────────────
        ASTNode ast;
        try {
            Lexer lexer = new Lexer(filename);
            Parser parser = new Parser(lexer.tokenize());
            ast = parser.parse();
        } catch (Exception e) {
            System.err.println("Parse error: " + e.getMessage());
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

}
