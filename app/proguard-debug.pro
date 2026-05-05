# Debug builds: shrink (tree-shake) only — no renaming, no optimization passes.
# R8 removes unused classes and members; stack traces stay readable.
-dontobfuscate
-dontoptimize
