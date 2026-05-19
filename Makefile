JC      = javac
JVM     = java
SRC     = src
OUTDIR  = bin
MAIN    = rpal20
SOURCES := $(shell find $(SRC) -name '*.java' 2>/dev/null)
ifeq ($(strip $(SOURCES)),)
SOURCES := $(shell git ls-files "$(SRC)/**/*.java" 2>/dev/null)
endif

.PHONY: all run ast st clean

all:
    @mkdir -p $(OUTDIR)
    $(JC) -d $(OUTDIR) -sourcepath $(SRC) $(SOURCES)

run: all
    $(JVM) -cp $(OUTDIR) $(MAIN) $(FILE)

ast: all
    $(JVM) -cp $(OUTDIR) $(MAIN) -ast $(FILE)

st: all
    $(JVM) -cp $(OUTDIR) $(MAIN) -st $(FILE)

clean:
    @rm -rf $(OUTDIR)
