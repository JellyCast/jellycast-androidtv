# Language and comments

Everything in the repository is English: code, identifiers, test names, log messages, exception
messages, comments, docs, rules, commit messages. Only user-facing text is translated, and only in
`res/values-xx/strings.xml`. Talk to the user in Dutch.

## Comments

- Code explains itself through names and structure. If a block needs a comment to be understood,
  rename, extract a function, or introduce a named constant or type instead.
- A comment is allowed only for a *why* that the code cannot express: a platform quirk, a device
  bug, a non-obvious constraint (e.g. "token goes in a header so it never ends up in a log").
  One or two short lines.
- No comments that restate a name, repeat the design spec, reference rule files or Paper, or record
  history (dates, measurements, "used to be"). History goes in the commit message or `NOTES.md`;
  design goes in `.claude/rules/design*.md`.
- No KDoc on internal code. KDoc only where a public contract is genuinely non-obvious.
- No commented-out code.

## Readable code

- Functions do one thing; a composable or function longer than ~60 lines is a candidate to split.
- Files longer than ~400 lines are split by responsibility (state holder, layout, key handling,
  drawing).
- Magic numbers get a named constant; units go in the name (`positionTicks`, `panelWidth`) or in a
  value class.
- Test names are backtick sentences in English describing behaviour:
  `` `without a server the app starts on the server screen` ``.

## Glossary

Use exactly these English terms in code and docs.

| Dutch (design talk) | English |
|---|---|
| dvd-kast (Films-weergave) | bookcase |
| plank | shelf |
| (film)rug | spine |
| opengeklapt paneel | open panel |
| plankwijzer | shelf indicator |
| rugkleur | spine color |
| sectiebalk | section bar |
| sectie Home, voorstel, stapel | Home section, suggestion, suggestion stack |
| Verder kijken | Continue watching |
| Nieuw | New |
| Nog niet begonnen | Not started |
| seriepagina | series page |
| filmdetail | movie detail |
| aflevering / seizoen | episode / season |
| gezien / half bekeken | watched / in progress |
| techniekregel | tech line |
| sprongpil | jump pill |
| laspunt | weld spark |
| neonrand | neon border |
| focusgloed | focus halo |
| skelet | skeleton |
| vangnet | fallback |
| omzetten (transcode) | transcode |
| doorgeven (passthrough) | passthrough |
| beeldfrequentie | frame rate |
| Terug | Back |
