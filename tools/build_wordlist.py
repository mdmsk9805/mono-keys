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
