JC      = javac
JVM     = java
SRC     = src
MAIN    = rpal20

SOURCES = $(SRC)/$(MAIN).java \
          $(SRC)/lexer/TokenType.java \
          $(SRC)/lexer/Token.java \
          $(SRC)/lexer/Lexer.java \
          $(SRC)/parser/ASTNode.java \
          $(SRC)/parser/Parser.java \
          $(SRC)/standardizer/Standardizer.java \
          $(SRC)/cse/Environment.java \
          $(SRC)/cse/CSEMachine.java

.PHONY: all run ast st clean

all:
	$(JC) -d . -sourcepath $(SRC) $(SOURCES)

run: all
	$(JVM) $(MAIN) $(FILE)

ast: all
	$(JVM) $(MAIN) -ast $(FILE)

st: all
	$(JVM) $(MAIN) -st $(FILE)

clean:
	rm -rf lexer parser standardizer cse $(MAIN).class
