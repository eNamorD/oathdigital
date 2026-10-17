# Game Log Wording Ledger

Lines whose words are worth improving, found while reading the Game Log and
left alone. Not a design: nothing here is decided, and nothing here is
authorized.

The [procedures journal their own notes design](2026-10-16-procedures-journal-their-notes-design.md)
forbids changing wording inside a migration (its ruling 6), so that a golden
diff means one thing. This file is where the improvements wait. Append to it
when a migration passes a line it may not fix; strike an entry when a commit
fixes it, naming the commit.

Each entry: where the line is built, what it reads today, and why it is worth
a second look. No replacement text is proposed — picking the words is the
work, not the finding.

## Open

- **`ActionLines.scala:104` — "Traded with {card} for no secrets".** The
  `bought` slot normally holds a thing gained ("for 2 secrets"), so the
  no-gain case reads as though "no secrets" were the commodity. A Trade that
  yields nothing is a different sentence, not the same sentence with a zero
  in it.

- **`ActionLines.scala:298-299` — "Drew {cards} from {source} and kept
  none".** "None" after "kept" is terse to the point of ambiguity: it can be
  read as the name of what was kept. The surrounding lines all name a thing.

- **`ActionLines.scala:362` — "Played {card} as an adviser".** A card kept
  from a Search already has a "kept" line, so the pair reads twice about one
  card. Worth deciding which of the two carries the placement.

- **`CampaignLines.scala:138-139` — "Skipped the Campaign." and "Nothing
  left to attack: the Campaign ended."** Two outcomes of one branch, written
  in two registers: the first is an actor's choice, the second a reason and
  an outcome joined by a colon. They post at the same place and should read
  as alternatives.

- **`CampaignLines.losses` — the capitalisation dance.** When the loser is
  the acting player the line has no subject, so the first clause is
  capitalised in code: "Lost 2 warbands and burned 1 favor", against "Blue
  lost 2 warbands and burned 1 favor" for anyone else. The same event reads
  as two different kinds of sentence depending on who is reading. Worth
  deciding whether the subject should simply always be named.

- **`SetupLines.scala:16` — "{Blue} placed pawn at {site}".** Missing
  article. Every other line in the module writes a determiner.

- **`StartLines.scala:66` — "Continued" with a bare cost span.** Recover's
  repeat line says the Supply spent and nothing about what was continued. It
  is the only line in the log that is a single word plus a number.

## Fixed

_(none yet)_
