# RPAL20 Interpreter - Comprehensive Project Document

## Table of Contents
1. [Project Overview](#project-overview)
2. [System Architecture](#system-architecture)
3. [Component Details](#component-details)
4. [Technical Implementation](#technical-implementation)
5. [Build and Execution](#build-and-execution)
6. [Test Programs](#test-programs)
7. [Code Structure](#code-structure)

---

## Project Overview

### Project Title
**RPAL20 Interpreter** - A Java implementation of an interpreter for the RPAL (Right-reference Pedagogic Algorithmic Language)

### Project Objectives
- Implement a complete lexical analyzer for RPAL
- Develop a recursive descent parser to build Abstract Syntax Trees (AST)
- Create a standardizer to convert AST into Standardized Trees (ST)
- Implement a CSE (Control-Stack-Environment) Machine for program evaluation
- Support multiple output modes: execution, AST visualization, and ST visualization

### Course Information
- **Course:** CS 3513 - Programming Languages
- **Programming Language:** Java
- **Platform:** Cross-platform (Windows, Linux, macOS)

### Project Scope
The RPAL20 interpreter performs the following pipeline:

```
Source File (.rpal)
        ↓
   LEXER (Tokenization)
        ↓
   PARSER (Syntax Analysis → AST)
        ↓
   STANDARDIZER (AST → ST)
        ↓
   CSE MACHINE (Evaluation)
        ↓
   Output (Program Result)
```

---

## System Architecture

### High-Level Pipeline

```
┌─────────────────────────────────────────────────────────────┐
│                    RPAL20 Interpreter                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Input: RPAL source code file                               │
│    ↓                                                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ PHASE 1: LEXICAL ANALYSIS (Lexer)                   │  │
│  │ ├─ Read source character-by-character              │  │
│  │ ├─ Recognize tokens: keywords, identifiers,        │  │
│  │ │  integers, strings, operators, punctuation       │  │
│  │ ├─ Handle comments and whitespace                  │  │
│  │ └─ Produce flat token stream                       │  │
│  └──────────────────────────────────────────────────────┘  │
│    ↓ Token Stream                                           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ PHASE 2: SYNTAX ANALYSIS (Parser)                   │  │
│  │ ├─ Implement recursive descent parser               │  │
│  │ ├─ Follow RPAL grammar rules                        │  │
│  │ ├─ Handle left-recursion via iteration              │  │
│  │ ├─ Build Abstract Syntax Tree (AST)                 │  │
│  │ └─ Report syntax errors                             │  │
│  └──────────────────────────────────────────────────────┘  │
│    ↓ AST                                                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ PHASE 3: TREE STANDARDIZATION (Standardizer)        │  │
│  │ ├─ Apply RPAL standardization rules                 │  │
│  │ ├─ Transform let, lambda, where expressions         │  │
│  │ ├─ Convert to gamma (function application) form     │  │
│  │ ├─ Generate Y* for recursive definitions            │  │
│  │ └─ Produce Standardized Tree (ST)                   │  │
│  └──────────────────────────────────────────────────────┘  │
│    ↓ ST                                                     │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ PHASE 4: EVALUATION (CSE Machine)                   │  │
│  │ ├─ Flatten ST into numbered control structures      │  │
│  │ ├─ Create environment chain (e0, e1, e2, ...)       │  │
│  │ ├─ Execute using stack-based evaluation             │  │
│  │ ├─ Handle lambda evaluation and closures            │  │
│  │ ├─ Manage function application and recursion        │  │
│  │ └─ Return final result value                         │  │
│  └──────────────────────────────────────────────────────┘  │
│    ↓                                                        │
│  Output: Program Result (integer, string, or tuple)        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Component Details

### 1. Lexer (Lexical Analysis)

**File:** `src/lexer/Lexer.java`

**Purpose:** Tokenizes RPAL source code into a stream of tokens.

**Key Features:**
- Character-by-character scanning
- Token type recognition:
  - **Identifiers:** Variable/function names (letter followed by letters, digits, underscores)
  - **Keywords:** Reserved words (let, in, fn, where, rec, and, or, not, true, false, nil, dummy, eq, ne, ls, le, gr, ge, aug, within)
  - **Integers:** Decimal number sequences
  - **Strings:** Single-quoted text with escape sequences (\t, \n, \\, \')
  - **Operators:** Sequences of operator characters (+, -, *, <, >, &, ., @, /, :, =, ~, |, $, !, #, %, ^, \)
  - **Punctuation:** ( ) , ;
- Comment handling: Lines starting with // are discarded
- Whitespace skipping: Spaces, tabs, newlines, carriage returns
- Line number tracking for error reporting

**Token Classes:**
- `Token.java` - Represents individual tokens with type, value, and line number
- `TokenType.java` - Enumeration of all token categories

**Public API:**
```java
Lexer lexer = new Lexer("path/to/program.rpal");
List<Token> tokens = lexer.tokenize();     // Get all tokens at once
Token t = lexer.nextToken();               // Get one token at a time
```

**Implementation Notes:**
- Buffered character reading for efficiency
- State machine approach for multi-character tokens
- KEYWORDS set for quick keyword lookup
- OP_CHARS set for operator character validation

---

### 2. Parser (Syntax Analysis)

**File:** `src/parser/Parser.java`

**Purpose:** Builds an Abstract Syntax Tree (AST) from token stream following RPAL grammar rules.

**Grammar Rules Implemented:**
- **E** - Expression (highest level)
- **Ew** - Expression with where clause
- **T** - Term
- **Ta** - Term with augmentation
- **Tc** - Tertiary expression
- **B** - Binary expression (left-recursive)
- **Bt** - Binary tail
- **Bs** - Boolean simplification
- **Bp** - Boolean primitive
- **A** - Arithmetic expression (left-recursive)
- **At** - Arithmetic tail
- **Af** - Arithmetic factor
- **Ap** - Arithmetic primitive
- **R** - Relational expression (left-recursive)
- **Rn** - Relational negation
- **D** - Definition
- **Da** - Definition augmentation
- **Dr** - Definition right-hand side
- **Db** - Definition binding
- **Vb** - Variable binding
- **Vl** - Variable list

**Parsing Strategy:**
- **Recursive Descent:** One method per grammar rule
- **Stack-Based Tree Building:** Deque<ASTNode> stack for AST construction
  - Each method pushes its result on stack
  - Helper `buildTree(tag, n)` pops n children, creates parent, pushes back
- **Left-Recursion Handling:** Convert left-recursive rules to iterative loops
  - Rules B, Bt, Ta, A, At, Ap, R use while-loops
  - Each iteration combines already-built left subtree with new right subtree

**Example (Let Expression):**
```
Parse: let x = 5 in x + 1
1. parseD() → pushes D subtree for x = 5
2. parseE() → pushes E subtree for x + 1
3. buildTree("let", 2) → pops 2 nodes, creates let node, pushes back
Result: AST node with label "let" and 2 children
```

**Error Handling:**
- Throws exceptions for syntax errors
- Reports unexpected token information
- Validates grammar rule adherence

**AST Node Types:**
See `ASTNode.java` for complete list including:
- Leaf nodes: IDENTIFIER, INTEGER, STRING, BOOLEAN, NIL, DUMMY
- Keyword nodes: LET, LAMBDA, WHERE, WITHIN, REC, AND_DEF
- Operator nodes: PLUS, MINUS, MULTIPLY, DIVIDE, POWER, NEG, AT, AND, OR, NOT
- Comparison nodes: EQ, NE, LT, LE, GT, GE
- Control nodes: CONDITIONAL, APPLY (gamma), TAU

---

### 3. AST Node

**File:** `src/parser/ASTNode.java`

**Purpose:** Represents nodes in both AST and ST with type and children.

**Node Types:**
1. **Leaf Nodes** (have value, no children):
   - IDENTIFIER - Variable/function names
   - INTEGER - Integer literals
   - STRING - String literals
   - BOOLEAN - true/false values
   - NIL - Empty list value
   - DUMMY - Placeholder value

2. **Internal Nodes** (have children, no value):
   - LET - let x = E in P
   - LAMBDA - fn x . E (or lambda after standardization)
   - WHERE - E where D
   - TAU - Tuple constructor
   - AUG - List augmentation
   - CONDITIONAL - B -> T | E
   - Logical: OR, AND, NOT
   - Comparison: EQ, NE, LT, LE, GT, GE
   - Arithmetic: PLUS, MINUS, MULTIPLY, DIVIDE, POWER, NEG
   - AT - Infix application (@)
   - APPLY - Function application (gamma in CSE)
   - WITHIN - within expression
   - AND_DEF - Simultaneous definitions
   - REC - Recursive definition
   - FCN_FORM - Function form: f x y = E

**Key Methods:**
```java
public void addChild(ASTNode child)        // Add child node
public ASTNode getChild(int i)             // Get child by index
public List<ASTNode> getChildren()         // Get all children
public void print(int indent)              // Pretty-print tree
```

**Tree Structure Example:**
```
                    let
                   /   \
                  =     x+1
                 / \
                x   5
```

---

### 4. Standardizer (Tree Transformation)

**File:** `src/standardizer/Standardizer.java`

**Purpose:** Converts AST into Standardized Tree by applying transformation rules.

**Standardization Rules:**

1. **Let Rule:**
   ```
   let x = E in P  →  gamma( lambda(x, P), E )
   ```
   Transforms let-binding into lambda application.

2. **Where Rule:**
   ```
   P where x = E  →  gamma( lambda(x, P), E )
   ```
   Transforms where-binding similar to let.

3. **Lambda Rule (Multiple Parameters):**
   ```
   fn x y z . E  →  lambda(x, lambda(y, lambda(z, E)))
   ```
   Curries multiple parameters into nested lambdas.

4. **Function Form Rule:**
   ```
   f x y = E  →  =(f, lambda(x, lambda(y, E)))
   ```
   Converts function definitions to equality bindings.

5. **Recursion Rule:**
   ```
   rec =(f, E)  →  =(f, gamma(Ystar, lambda(f, E)))
   ```
   Wraps recursive definitions with Y* combinator for recursion handling.

6. **Within Rule:**
   ```
   D1 within D2  →  =(x2, gamma(lambda(x1, E2), E1))
   ```
   Handles within expressions for local scoping.

7. **And Rule (Simultaneous Definitions):**
   ```
   and(=(x1, E1), =(x2, E2), ...)
   →  =(,(x1, x2, ...), tau(E1, E2, ...))
   ```
   Bundles simultaneous definitions into tuple bindings.

8. **At Rule (Infix Application):**
   ```
   E1 @ f E2  →  gamma( gamma(f, E1), E2 )
   ```
   Converts infix application to function application.

**Implementation Strategy:**
- Post-order traversal (children before parent)
- In-place tree modification
- Recursive `applyRule()` dispatcher for each node type

**Example Transformation:**
```
Input AST:   let x = 5 in x + 1
             let
            /  \
           =    +
          / \  / \
         x  5 x  1

After Standardization:
             gamma
            /      \
         lambda     5
        /     \
       x     +
           / \
          x  1
```

---

### 5. CSE Machine (Evaluation)

**File:** `src/cse/CSEMachine.java`

**Purpose:** Evaluates the Standardized Tree using a stack-based abstract machine.

**CSE Machine Components:**

1. **Control (C):** Array of control elements
   - Instruction-like elements: push names, lambda tokens, values
   - NameToken - Variable to look up in environment
   - LambdaToken - Function definition with delta index and bound variables
   - EnvMarker - Environment management token
   - BetaToken - Conditional branching (if-then-else)
   - TauToken - Tuple constructor

2. **Stack (S):** Value stack
   - Holds: integers, strings, booleans, nil, tuples, closures
   - FIFO (First-In, Last-Out) data structure
   - Operands pushed here, results left on top

3. **Environment (E):** Variable bindings
   - Chain of Environment frames
   - e0 (root) contains global definitions
   - e1, e2, ... (nested frames) for function calls
   - Lookup walks chain from current to root

**Machine State:**
```
┌─────────────────────────────────────┐
│ Control (C)                         │
│ [name, lambda, ..., delta(1), ...] │
├─────────────────────────────────────┤
│ Stack (S)                           │
│ [value1, value2, value3, ...]       │
├─────────────────────────────────────┤
│ Environment (E)                     │
│ e3 → e2 → e1 → e0 (root)            │
└─────────────────────────────────────┘
```

**Execution Cycle:**
1. Pop control element
2. Execute corresponding action:
   - **NameToken:** Look up in environment, push value to stack
   - **LambdaToken:** Create closure (lambda + environment), push to stack
   - **Integer/String/Boolean:** Push literal to stack
   - **TauToken(n):** Pop n stack items, create tuple, push back
   - **Operator:** Pop operands, compute result, push back
   - **APPLY (gamma):** Apply function to arguments
   - **CONDITIONAL (beta):** Branch based on stack top value
   - **EnvMarker:** Pop and match with environment exit
3. Repeat until control is empty
4. Stack top = result

**Key Classes:**

- **Closure:** Represents a captured function (lambda + environment)
  ```java
  class Closure {
      LambdaToken lambda;    // Which delta (function body)
      Environment env;       // Captured environment at creation
  }
  ```

- **RpalTuple:** Ordered list of values
  ```java
  class RpalTuple {
      List<Object> elements;
  }
  ```

- **Environment:** Single frame in environment chain
  - See `Environment.java`

**Primitive Operations:**
- **Arithmetic:** +, -, *, /, **
- **Comparison:** =, ≠, <, ≤, >, ≥
- **Logical:** and, or, not
- **Utilities:** Print, Order, Isinteger, Isstring, etc.

**Control Flattening:**
The standardized tree is flattened into numbered control structures (deltas):
```
Delta 0: Main program
  [instructions...]

Delta 1: First function body
  [instructions...]

Delta 2: Second function body
  [instructions...]
```

Each lambda refers to its delta by index.

---

### 6. Environment

**File:** `src/cse/Environment.java`

**Purpose:** Manages variable bindings in the CSE machine's execution environment.

**Structure:**
- Linked list of environment frames
- Each frame contains bindings for one scope level
- Root frame (e0) for global scope

**Key Methods:**
```java
public void define(String name, Object value)
public Object lookup(String name)
public Environment push()        // Create child environment
public Environment pop()         // Return to parent environment
```

**Example Environment Chain:**
```
During execution of: let x = 5 in (fn y . x + y) 3

e0: {x: 5}                    (root/global)
 ↓
e1: {y: 3}                    (function parameter y bound to 3)

Lookup "x" from e1: Not found locally, check parent (e0) → Found: 5
Lookup "y" from e1: Found locally: 3
```

---

## Technical Implementation

### Class Hierarchy and Dependencies

```
rpal20.java (main entry point)
    ├─ Lexer.java
    │   ├─ Token.java
    │   └─ TokenType.java
    │
    ├─ Parser.java
    │   ├─ Lexer.java
    │   ├─ Token.java
    │   └─ ASTNode.java
    │
    ├─ Standardizer.java
    │   ├─ ASTNode.java
    │   └─ ASTNode.NodeType
    │
    └─ CSEMachine.java
        ├─ ASTNode.java
        ├─ Environment.java
        └─ Various token classes
```

### Data Flow

```
Input File
    ↓
Lexer.tokenize()
    ↓ List<Token>
Parser.parse()
    ↓ ASTNode (AST root)
Standardizer.standardize()
    ↓ ASTNode (ST root)
CSEMachine.eval()
    ↓ Object (result value)
Output
```

### Main Entry Point

**File:** `src/rpal20.java`

**Usage:**
```bash
java rpal20 <file>              # Execute and print result
java rpal20 -ast <file>         # Print AST only
java rpal20 -st <file>          # Print Standardized Tree only
```

**Execution Flow:**
```java
1. Parse command-line arguments
2. Create Lexer from file
3. Tokenize source
4. Parse tokens → AST
5. If -ast flag: print AST and exit
6. Standardize AST → ST
7. If -st flag: print ST and exit
8. Evaluate ST with CSE Machine
9. Print final result
```

---

## Build and Execution

### Build System

**Build Tool:** Makefile (POSIX make)

**Targets:**
```bash
make            # Compile all source files
make run FILE=<file>    # Compile and run
make ast FILE=<file>    # Compile and show AST
make st FILE=<file>     # Compile and show ST
make clean      # Remove compiled class files
```

**Compilation Process:**
```
javac -d . -sourcepath src src/rpal20.java \
    src/lexer/TokenType.java \
    src/lexer/Token.java \
    src/lexer/Lexer.java \
    src/parser/ASTNode.java \
    src/parser/Parser.java \
    src/standardizer/Standardizer.java \
    src/cse/Environment.java \
    src/cse/CSEMachine.java
```

**Output:** Class files generated in root directory:
```
rpal20.class
lexer/
  Lexer.class
  Token.class
  TokenType.class
parser/
  ASTNode.class
  Parser.class
standardizer/
  Standardizer.class
cse/
  CSEMachine.class
  CSEMachine$*.class (inner classes)
  Environment.class
```

### Execution Examples

**Example 1: Simple Addition**
```bash
$ cat rpal_test_programs/add
let x = 2 + 3
in Print x

$ java rpal20 rpal_test_programs/add
5
```

**Example 2: Function Definition**
```bash
$ cat rpal_test_programs/fn1
let factorial = fn n . n eq 0 -> 1 | n * factorial (n-1)
in Print (factorial 5)

$ java rpal20 rpal_test_programs/fn1
120
```

**Example 3: View AST**
```bash
$ java rpal20 -ast rpal_test_programs/add
let
  =
    x
    +
      2
      3
  Print
    x
```

**Example 4: View Standardized Tree**
```bash
$ java rpal20 -st rpal_test_programs/add
gamma
  lambda
    x
    Print
      x
  +
    2
    3
```

---

## Test Programs

### Provided Test Programs

**Directory:** `rpal_test_programs/`

**Available Programs:**

1. **add** - Basic addition
   - Tests: Variable binding, arithmetic, output
   - Expected output: 5

2. **conc.1** - String concatenation
   - Tests: String operations, concatenation operator

3. **defns.1** - Multiple definitions
   - Tests: Definition syntax, scoping

4. **fn1, fn2, fn3** - Function definitions
   - Tests: Lambda expressions, recursion, parameter passing
   - fn1: Factorial (recursion)
   - fn2: Function parameters and application
   - fn3: Complex function operations

5. **ftst** - Function tests
   - Tests: Various function constructs

6. **infix, infix2** - Infix notation
   - Tests: @ operator (infix application)
   - Example: x @ f y means f(x, y)

7. **pairs1, pairs2, pairs3** - Tuple handling
   - Tests: Tuple construction and operations
   - Comma operator creates tuples

8. **picture** - Complex program
   - Tests: Multiple language features combined

9. **towers** - Tower of Hanoi
   - Tests: Complex recursion and list manipulation

10. **vectorsum** - Vector operations
    - Tests: List/vector processing with higher-order functions

### Test Execution

**Manual Testing:**
```bash
# Run single test
java rpal20 rpal_test_programs/add > output.txt

# Compare with reference implementation
./rpal.exe rpal_test_programs/add > expected.txt
diff output.txt expected.txt
```

**Batch Testing (script):**
```bash
for prog in rpal_test_programs/*; do
    echo "Testing $prog..."
    java rpal20 "$prog" > output.txt
    if diff output.txt expected_outputs/"$(basename $prog)".expected; then
        echo "✓ PASS"
    else
        echo "✗ FAIL"
    fi
done
```

---

## Code Structure

### Directory Layout

```
RPAL20/
├── Makefile                    # Build automation
├── README.md                   # Quick start guide
├── PROJECT_DOCUMENT.md         # This document
├── RPAL_Project_WorkPlan.pdf   # Project planning document
│
├── src/                        # Source code
│   ├── rpal20.java            # Main entry point
│   │
│   ├── lexer/                 # Phase 1: Lexical Analysis
│   │   ├── Lexer.java         # Tokenizer (main)
│   │   ├── Token.java         # Token representation
│   │   └── TokenType.java     # Token type enumeration
│   │
│   ├── parser/                # Phase 2: Syntax Analysis
│   │   ├── Parser.java        # Recursive descent parser (main)
│   │   └── ASTNode.java       # AST node representation
│   │
│   ├── standardizer/          # Phase 3: Standardization
│   │   └── Standardizer.java  # AST → ST transformation
│   │
│   └── cse/                   # Phase 4: Evaluation
│       ├── CSEMachine.java    # Stack machine execution (main)
│       └── Environment.java   # Environment/binding management
│
├── rpal_test_programs/        # Test suite
│   ├── add                    # Simple addition test
│   ├── fn1, fn2, fn3          # Function tests
│   ├── pairs1, pairs2, pairs3 # Tuple tests
│   ├── infix, infix2          # Infix notation tests
│   ├── towers                 # Tower of Hanoi
│   ├── vectorsum              # Vector operations
│   ├── rpal.exe               # Reference implementation
│   └── cygwin1.dll            # Runtime dependency
│
└── project/                   # Project documentation
    ├── ProgrammingProject.txt # Project requirements
    ├── RPAL_Grammar           # RPAL syntax specification
    └── RPAL_Lex               # RPAL lexical rules
```

### File Sizes and Complexity

| File | Lines | Purpose |
|------|-------|---------|
| Lexer.java | ~350 | Tokenization |
| Token.java | ~50 | Token class |
| TokenType.java | ~40 | Token types enum |
| Parser.java | ~500 | Recursive descent parsing |
| ASTNode.java | ~150 | AST node representation |
| Standardizer.java | ~300 | Tree transformation |
| CSEMachine.java | ~700 | Stack machine execution |
| Environment.java | ~80 | Environment management |
| rpal20.java | ~100 | Main entry point |
| **Total** | **~2,270** | **Full interpreter** |

### Key Interfaces

#### Lexer Public Interface
```java
public Lexer(String filePath) throws IOException
public List<Token> tokenize()
public Token nextToken()
public int getLine()
```

#### Parser Public Interface
```java
public Parser(String filePath) throws IOException
public Parser(List<Token> tokens)
public ASTNode parse() throws Exception
```

#### Standardizer Public Interface
```java
public ASTNode standardize(ASTNode root)
```

#### CSEMachine Public Interface
```java
public void eval(ASTNode root)
```

#### ASTNode Public Interface
```java
public ASTNode(NodeType type)
public ASTNode(NodeType type, String value)
public void addChild(ASTNode child)
public ASTNode getChild(int i)
public List<ASTNode> getChildren()
public void print(int indent)
public void setNodeType(NodeType type)
public NodeType getNodeType()
public String getValue()
```

### Important Implementation Details

#### Operator Precedence
Handled by grammar rules (higher precedence parsed deeper in recursion):
```
E    (lowest precedence)
Ew   (where binding)
T    (let binding)
Ta   (augmentation)
Tc   (conditional)
B    (or)
Bt   (and)
Bs   (not)
Bp   (comparison)
A    (arithmetic +, -)
At   (arithmetic *, /)
Af   (arithmetic **)
Ap   (unary -)
R    (relational <, =, etc.)
```

#### Left-Recursion Handling
Example for Binary Expression (B):
```
Grammar:  B := B 'or' Bt | Bt
Iterative: B := Bt ('or' Bt)*

1. Parse Bt → left subtree
2. While 'or' token:
   a. Parse next Bt → right subtree
   b. buildTree("or", 2) → combines left + right
   c. left = result
3. Return left
```

#### AST Construction
Stack-based approach:
```java
// Example: Parse "2 + 3"
parseAp();           // pushes: INTEGER(2)
parseA();            // sees +, loops
parseAp();           // pushes: INTEGER(3)
buildTree("+", 2);   // pops 2, creates +node, pushes back
// Stack: [+(2, 3)]
```

#### Standardization by Example

**Input:** `let x = 5 in x + 1`

**AST:**
```
let
  =
    x
    5
  +
    x
    1
```

**Standardization Process:**
1. Standardize children (depth-first)
2. Apply LET rule:
   - X = x (identifier)
   - E = 5 (integer)
   - P = x + 1 (addition)
   - Result: gamma(lambda(x, x+1), 5)

**ST Output:**
```
gamma
  lambda
    x
    +
      x
      1
  5
```

#### CSE Machine Execution

**Input:** ST for `gamma(lambda(x, x+1), 5)`

**Delta 0 (main):** `[gamma, delta(1), 5]`
**Delta 1 (lambda body):** `[+, x, 1]`

**Execution:**
```
Control: [gamma, delta(1), 5]
Stack: []
Env: e0

Step 1: Pop delta(1) → Create LambdaToken
Stack: [lambda(1)]

Step 2: Pop 5 → Push
Stack: [lambda(1), 5]

Step 3: Pop gamma → Apply lambda to 5
  - Bind x=5 in new environment e1
  - Push delta(1) control
Control: [delta(1), EnvMarker]
Stack: [lambda(1)]
Env: e1 (x=5)

Step 4: Execute delta(1)
  - Pop 1, 5, +
  - Compute 5+1=6
Stack: [6]

Step 5: Pop EnvMarker → Pop environment
Env: e0

Result: 6
```

---

## Summary

The RPAL20 interpreter is a complete implementation of the RPAL language processor, featuring:

- **Robust Lexer:** Handles all RPAL token types with proper keyword and operator recognition
- **Correct Parser:** Implements full RPAL grammar with proper left-recursion handling
- **Complete Standardizer:** Applies all RPAL standardization rules correctly
- **Functional CSE Machine:** Evaluates programs with proper closure handling, recursion, and tuple support

The modular architecture allows independent testing of each phase and clear separation of concerns, making the codebase maintainable and extensible.

