"""Flag positional construction of multi-field data classes.

Both build breaks this round came from inserting a field into the middle of a
declaration while a call site still passed arguments positionally. A positional
call to a class with several same-shaped params is the latent landmine.
"""
import re, sys, pathlib

def strip_comments(src: str) -> str:
    """Blank out // comments and "strings" so commas inside them are not read
    as argument separators (this cost one false alarm already)."""
    out, i, n = [], 0, len(src)
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            out.append(" " * (j - i)); i = j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("".join(c if c == "\n" else " " for c in src[i:j])); i = j
        elif src[i] == '"':
            j = i + 1
            while j < n and src[j] != '"':
                j += 2 if src[j] == "\\" else 1
            j = min(j + 1, n)
            out.append('"' + " " * (j - i - 2) + '"'); i = j
        else:
            out.append(src[i]); i += 1
    return "".join(out)


root = pathlib.Path(sys.argv[1])
decls = {}
for f in root.rglob("*.kt"):
    src = strip_comments(f.read_text(encoding="utf-8"))
    for m in re.finditer(r"(?:data )?class (\w+)\s*\(", src):
        i = m.end() - 1
        depth, j = 0, i
        while j < len(src):
            if src[j] == '(': depth += 1
            elif src[j] == ')':
                depth -= 1
                if depth == 0: break
            j += 1
        body = src[i+1:j]
        params = [p for p in re.findall(r"va[lr] (\w+)\s*:", body)]
        if len(params) >= 3:
            decls[m.group(1)] = (params, f.name)

hits = []
for f in root.rglob("*.kt"):
    src = strip_comments(f.read_text(encoding="utf-8"))
    for name, (params, where) in decls.items():
        for m in re.finditer(r"(?<![\w.])" + name + r"\(", src):
            line_no = src[:m.start()].count("\n") + 1
            i = m.end() - 1
            depth, j = 0, i
            while j < len(src):
                if src[j] == '(': depth += 1
                elif src[j] == ')':
                    depth -= 1
                    if depth == 0: break
                j += 1
            args = src[i+1:j]
            if "data class" in src[max(0, m.start()-60):m.start()]: continue
            if not args.strip(): continue
            # split top-level commas
            parts, d, cur = [], 0, ""
            for ch in args:
                if ch in "([{": d += 1
                elif ch in ")]}": d -= 1
                if ch == "," and d == 0:
                    parts.append(cur); cur = ""
                else: cur += ch
            parts.append(cur)
            positional = [p for p in parts if p.strip() and not re.match(r"\s*\w+\s*=[^=]", p)]
            if positional:
                hits.append((f.name, line_no, name, len(positional), len(parts)))

for h in sorted(set(hits)):
    print(f"{h[0]}:{h[1]}  {h[2]}(...)  {h[3]} positional of {h[4]} args")
print(f"\n{len(set(hits))} positional construction(s) of multi-field classes")


# ---------------------------------------------------------------------------
# Duplicate top-level declarations.
#
# A second `data class PlanDayDto` in the same package does not just fail on
# its own line: every reference to that name anywhere in the module becomes
# unresolved, so one mistake prints as a page of unrelated errors in other
# files. Cheap to check, expensive to debug from the log.
# ---------------------------------------------------------------------------
decl_lines = {}
for f in root.rglob("*.kt"):
    text = strip_comments(f.read_text(encoding="utf-8"))
    for m in re.finditer(r"^(?:@\w+\s*)*\s*(?:data |sealed |enum |value )?class (\w+)",
                         text, re.M):
        decl_lines.setdefault(m.group(1), []).append(
            f"{f.name}:{text[:m.start()].count(chr(10)) + 1}"
        )

dupes = {k: v for k, v in decl_lines.items() if len(v) > 1}
if dupes:
    print("\nDUPLICATE TOP-LEVEL CLASS NAMES:")
    for name, where in sorted(dupes.items()):
        print(f"  {name}: {', '.join(where)}")
    print(f"{len(dupes)} duplicate class name(s) — these break every reference to the name")
else:
    print("\nNo duplicate top-level class names.")
