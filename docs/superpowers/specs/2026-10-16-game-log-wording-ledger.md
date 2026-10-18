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

- **`TradeProcedure.tradedForNoSecrets` — "Traded with {card} for no
  secrets".** The
  `bought` slot normally holds a thing gained ("for 2 secrets"), so the
  no-gain case reads as though "no secrets" were the commodity. A Trade that
  yields nothing is a different sentence, not the same sentence with a zero
  in it.

- **`SearchProcedure.keptCards` — "Drew {cards} from {source} and kept
  none".** "None" after "kept" is terse to the point of ambiguity: it can be
  read as the name of what was kept. The surrounding lines all name a thing.
  Slice 3 split Search's line in two, and the joined form this quotes went
  with it: a Search that keeps nothing writes no "Kept" line at all now.
  The question of how to say "kept nothing" is unchanged and unanswered.

- **`CardPlayProcedure`'s adviser placement key — "Played {card} as an
  adviser".** A card kept
  from a Search already has a "kept" line, so the pair reads twice about one
  card. Worth deciding which of the two carries the placement.

- **`CampaignNotes.skipped` and `CampaignNotes.nothingToAttack` —
  "Skipped the Campaign." and "Nothing left to attack: the Campaign
  ended."** Two outcomes of one branch, written
  in two registers: the first is an actor's choice, the second a reason and
  an outcome joined by a colon. They post at the same place and should read
  as alternatives.

- **`CampaignNotes.lost` against `CampaignNotes.playerLost` — the
  capitalisation dance.** When the loser is
  the acting player the line has no subject, so the first clause is
  capitalised in code: "Lost 2 warbands and burned 1 favor", against "Blue
  lost 2 warbands and burned 1 favor" for anyone else. The same event reads
  as two different kinds of sentence depending on who is reading. Worth
  deciding whether the subject should simply always be named.

  It no longer blocks slice 4 of the procedures-journal-their-notes
  migration: splitting the sentence per node leaves the subject-omitted
  case carrying one clause, so it is a flat key and today's words survive.
  The question of whether the subject should always be named is open on
  its own merits.

- **`SetupProcedure.placedPawn` — "{Blue} placed pawn at {site}".** Missing
  article. Every other line in the module writes a determiner.

- **`StartLines.scala:66` — "Continued" with a bare cost span.** Recover's
  repeat line says the Supply spent and nothing about what was continued. It
  is the only line in the log that is a single word plus a number.

- **`CampaignNotes.activatedOathkeeperTitle` and
  `CampaignNotes.activatedUsurperTitle` — the title plan's activation line
  lost its label.** It read "{Blue} activated Oathkeeper title: add 1
  defense die", the label `TitleDefensePlan` authored for its offer, which
  the rule rendered through `ChoiceWords.option`. A note's arguments are
  typed references, never text, so it now reads "{Blue} activated the
  Oathkeeper title". The dice it adds are told by the Defense total, so
  nothing the log does not say twice is lost — worth deciding whether the
  other plan activations should name what the plan does too, since each of
  them names only its card.

- **`CampaignNotes.attack` and `CampaignNotes.defense` — a Campaign's
  totals read after the plan that rewrote them.** `CampaignLines.recorded`
  read forward to `journal.segmentEnd` to suppress a total a plan
  overwrites, so "Attack: 8" read *before* "Outriders: Skulls ignored.".
  The note sits after the result window closes, so the plan's own line
  comes first. Worth deciding which order a reader wants: the number then
  its reason, or the reason then the number.

- **`CampaignNotes.lost`, `playerLost`, `banditsLost`, `defenderLost` and
  `sentTo` — a defeated Raid defender's losses read as three sentences.**
  One joined sentence became "{Blue} lost 1 warband", "{Blue} burned 1
  favor and discarded Tinker" and "{Blue} was sent to the Mine", because
  the clauses are applied in three different nodes and a note reaches one
  leaf. Worth deciding whether three sentences are better read than one; it
  is no longer a mechanism question.

- **A Campaign's loss lines now come before what the victory gained.** The
  rule aggregated both at completion and posted the gains first: "Took Wine
  of Welcome from Blue" then the loss sentence, and "Placed 2 warbands on
  Ancient City" then "The bandits lost 3 warbands". The notes sit in the
  nodes that apply each, and the losses node runs before the Raid's
  transfer and the Conquest's placement, so each loss line now reads first.
  Worth deciding whether a victory should be told spoils-first.

## Fixed

_(none yet)_

## A facedown card's slot, in a negotiation only

`LogWords.slotted` (`LogWords.scala:62-76`) names a card the viewer cannot
identify by its row and 1-based slot, "facedown adviser (slot 2)". Nothing
else in the Game Log does: every other card word falls back to "a Denizen".
Slice 2 migrated Negotiation's two card lines to `NoteArg.Card` and so
dropped the slot for a viewer outside the deal. `slotted` keeps its other
caller, `ChoiceWords.scala:34`, which describes a parked adviser choice, so
the slot survives in one place and not the other.

The question is which fallback is right everywhere. A slot tells a reader
*which* of a player's facedown advisers moved, which a count does not; it
also leaks a position the deal's parties may not have shown. Decide it once
and apply it to every card line, not to one procedure and one choice.

## A Trade that gained nothing names the right resource

`ActionLines.tradeLines` posted its completion fallback with the literal
`"no secrets"`, so a Trade **for favor** whose suit bank was empty read
`Traded with Hiding Place for no secrets`. No test or golden covered the
branch. `TradeProcedure.tradedForNoFavor` now reads `… for no favor`
(procedures journal their own notes, slice 3 plan-time ruling 3).

The same rule matched every bank-to-player move in the event it formatted, so
a Trade a power gained again on — The Old Oak's bonus secret, Mounted
Library's doubling — posted a second `Traded with …` line. A probe run at
plan time measured it: a Trade for secrets on The Old Oak, with a beast
adviser held, posted two identical `Traded with The Old Oak for 1 secret`
lines. The note posts once, naming what the Trade itself gained.

## Search's one line became two, and its draw powers speak for themselves

`ActionLines.search` wrote `Drew {cards} from {pile} and kept {cards}` as one
line, from the journal. A note cannot: the kept set is in the action's
answers, the drawn set is in state, and the pile phrase was read off the
`Draw` operation **as powers had rewritten it**. The keep answer arrives in a
different walker command than the draw, so nothing can see all three at once
(procedures journal their own notes, slice 3 plan-time ruling 1).

The line is now two — `Drew {cards} from {pile}` at the draw, `Kept {cards}`
at the keep answer — and the three phrases the rule read off the operation
are lines of the powers that cause them (ruling 5):

- **Wine of Welcome** — "Drawing from the bottom of the World Deck".
- **Mushrooms** — "Drawing one card from the bottom of the {region} discard".
- **Painted Trumpet** — "Drawing the top card of each discard pile instead".
- **Spinning Bee** gains a second line, "Drew {cards}, and discarded them
  all", for its no-match draw. That is the one case where neither of
  Search's own lines is written at all: this power empties the temporary
  hand inside the draw's own window.

Each of the first three replaces a phrase the Search line used to carry, so
nothing is lost; Painted Trumpet's line corrects the pile the Search line
names, which is the pile the Search was priced at rather than the piles drawn
from.

### A Search above five cards is counted, not listed

The rule called `LogWords.cards` at any length. A note's `Cards` argument
goes through `LogWords.listed`, which above `LogWords.Inline` (5) writes
`6 cards` when the viewer identifies none of them, and a `LogSpan.Cards` span
when it identifies some. Six drawn cards is reachable — three printed, one
more from Augury, two more from Truthful Harp — so a Search that large now
reads `Drew 6 cards from the World Deck` for a viewer who sees only backs,
where the rule wrote `Drew 6 Denizens`. `ProcedureNoteLinesSuite` pins the
all-backs branch; the actor's own view of six takes `listed`'s span branch,
which needs a script with both draw extensions picked and is recorded here
rather than tested.

## A card play's line now follows the lines its own batch caused

`ActionLines.playedAdviser` anchored "Played {card} …" on the play's own
recorded batch, and its `!(index + 1 until at).exists(isDelta)` guard kept a
later delta from taking the line. The note sits after the play leaf instead,
and `WalkerSteps.recordBatch` runs `AdviserArrivalPass.after` and any
executed trigger's follow-up **inside** that leaf's own execution. So a power
whose effect fires during the play writes its line first and the play's line
reads second, where it used to read first.

The only such power today is `SuitOnlyAdvisers` (Family Wagon, Vow of Union):
"Family Wagon: Buried Wrestlers." now precedes "Played Family Wagon as an
adviser". No golden covers it, which is why the slice's golden proof did not
show it.

Putting the line back in front needs it journaled inside the play's own
batch, and a `Note` is not a `CoreOperation`; the alternative — reading the
destination before the play — is the inference this slice deleted, since the
regional pile a discard reaches is decided by `CardPlay.operations`. The
question is unchanged and is no longer waiting on slice 4: it is a ROADMAP
task, "A card play's line before the lines its own batch causes".
