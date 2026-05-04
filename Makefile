# ──────────────────────────────────────────────────────────────────────────────
# Makefile for RPAL Interpreter (CS 3513)
# Usage:
#   make            → compile everything
#   make run FILE=rpal/fn1
#                   → run interpreter on a test program
#   make ast FILE=rpal/fn1
#                   → print AST for a test program
#   make st  FILE=rpal/fn1
#                   → print Standardized Tree for a test program
#   make clean      → remove compiled class files
# ──────────────────────────────────────────────────────────────────────────────

JC      = javac
JVM     = java
SRC     = src
BIN     = bin
FLAGS   = -d $(BIN) -sourcepath $(SRC)
MAIN    = rpal20

.PHONY: all run ast st clean

# ── Build ─────────────────────────────────────────────────────────────────────
all:
	@mkdir -p $(BIN)
	$(JC) $(FLAGS) $(SRC)/$(MAIN).java

# ── Run ───────────────────────────────────────────────────────────────────────
run: all
	$(JVM) -cp $(BIN) $(MAIN) $(FILE)

ast: all
	$(JVM) -cp $(BIN) $(MAIN) -ast $(FILE)

st: all
	$(JVM) -cp $(BIN) $(MAIN) -st $(FILE)

# ── Clean ─────────────────────────────────────────────────────────────────────
clean:
	rm -rf $(BIN)
