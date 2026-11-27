#!/usr/bin/env python3
"""Rebuild app/src/main/assets/words_en.txt.
Put en_50k.txt (hermitdave/FrequencyWords, content/2018/en/en_50k.txt, CC BY-SA 4.0)
and words_alpha.txt (dwyl/english-words, public domain) next to this script, then run it."""
alpha = set(open('words_alpha.txt').read().split())
out = []
for line in open('en_50k.txt'):
    w = line.split()[0]
    if w in alpha and w.isascii() and w.isalpha() and (len(w) >= 2 or w in ('a', 'i')):
        out.append(w)
    if len(out) >= 30000:
        break
open('../app/src/main/assets/words_en.txt', 'w').write("\n".join(out) + "\n")

# Bangla list (words_bn.txt): from hermitdave/FrequencyWords content/2018/bn/bn_50k.txt (CC BY-SA 4.0).
# Keep lines whose count is >= 5, whose word is only characters U+0980..U+09FF, at least 2 characters,
# with no Bangla digits or danda, dropping duplicates, in file order; write app/src/main/assets/words_bn.txt.
import re
bn, seen = [], set()
try:
    for line in open('bn_50k.txt', encoding='utf-8'):
        parts = line.split()
        if len(parts) < 2: continue
        w, c = parts[0], int(parts[1])
        if c < 5: break
        if re.match(r'^[\u0980-\u09FF]+$', w) and len(w) >= 2 and not re.search(r'[\u09E6-\u09EF\u0964\u0965]', w) and w not in seen:
            seen.add(w); bn.append(w)
    open('../app/src/main/assets/words_bn.txt', 'w', encoding='utf-8').write("\n".join(bn) + "\n")
except FileNotFoundError:
    pass  # put bn_50k.txt next to this script to build the Bangla list
