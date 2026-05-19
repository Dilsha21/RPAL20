# RPAL20 Interpreter

A Java implementation of an interpreter for the RPAL (Right-reference Pedagogic Algorithmic Language) programming language.

## Build

```
make
```

This compiles all source files and places class files in the project root.

## Run

```
java rpal20 <file>
```

**Print the AST:**
```
java rpal20 -ast <file>
```

**Print the Standardized Tree:**
```
java rpal20 -st <file>
```

## Example

```
java rpal20 rpal_test_programs/add
java rpal20 -ast rpal_test_programs/add
java rpal20 -st rpal_test_programs/add
```

## Project Structure

```
src/
  rpal20.java          - Main entry point
  lexer/               - Tokenizer
  parser/              - Recursive descent parser, AST
  standardizer/        - AST → Standardized Tree
  cse/                 - CSE Machine evaluator
rpal_test_programs/    - Sample RPAL programs
Makefile
```
