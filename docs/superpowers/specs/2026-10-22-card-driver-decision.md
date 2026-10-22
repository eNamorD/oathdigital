# One Driver for a Card's Ruling: Refused on the Evidence

> Status: decided 2026-10-22, from the architecture review of 2026-10-13,
> which raised this as candidate K and rated it worth exploring. The
> candidate had two halves. The first was designed and shipped as
> [a table continued from a state still checks its cards](2026-10-21-table-from-checks-design.md).
> This records why the second half — one shared driver for "run this card's
> power to its ruling" — is not built. No code changed.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **table**,
**situation** and **first-game input**.

## What the review proposed

The review's reasoning, from the pass-3 report, was that `Table` answers
"what is on the board" while a card test's hard question is "drive this
card's power to the point its ruling bites — commit a force, finish the plan
windows, answer a parked decision, read the resulting note". That question,
it said, "is answered by a different fixture per action, each with its own
vocabulary", naming `PlanDriver`'s `commit` / `finishPlanWindows` / `Run`,
`WhenPlayedHarness`, `SearchFixture` and `TravelFixture`. It proposed one
`Table`-rooted driver — state this table, start this procedure, answer these
decisions in order, read the notes and the parked decision — and called it
"the highest-leverage test-side change available".

The cost model behind that judgement was explicit: "adding a card to a new
action costs a new fixture".

## Why it is refused

That cost is not being paid, and the measurement window that shows it
postdates the review.

The four per-action drivers the review named were all created between
2026-09-20 and 2026-09-21, when the actions themselves were built. The last
addition of anything of their kind anywhere under `src/test` is
`RiverFixture` on 2026-09-27.

Since 2026-10-09, at `38468789`, the repository took 587 commits and gained
**76 new power suites**. It gained no new per-action driver. The one test
fixture added in that window, `application/TollConsentFixture`, is an
application-layer consent fixture, not a driver for a card's ruling. Seventy-
six card suites were written against drivers that already existed.

The review's supporting number does not survive either. It cited `PlanDriver`
in 6 of 250 card commits. At HEAD the file is touched by 31 of the last 250
commits — but those 250 commits span six days, and their subjects are engine
work and campaign features, not per-card edits: the walker payload rewrite,
the reroll choice after every roll, follow-ups as dequeued walker offers,
option restrictions over battle plan offers. One of them,
`f206d24f test(campaign): read plan notes through one shared said`, is the
project consolidating a vocabulary on its own, in the ordinary way, when that
vocabulary actually cost something.

A fifth claim is already closed: the review counted `Table.unchecked` at 190
uses. It is 1, and `private[testkit]`, since the first half of this
candidate.

## What the review got right

The fragmentation is real and is not disputed here. Six modules name the same
four moves differently:

| move | names in use |
|---|---|
| start the procedure | `PlanDriver.commit`, `WhenPlayedHarness.play` / `fire`, `SearchFixture.start` / `keep` / `place`, `TravelFixture.travel`, `PhasePowerHarness.use`, `ActPowerHarness.use(rules, …)` |
| read what it parked on | `awaits`, `question`, `startParksOn`, `parkedDecision` |
| read the notes | `said`, in three modules |
| prove the journal rebuilds it | `replayed`, `assertJournalRebuilds` |

Thirty files named `*Fixture` / `*Harness` / `*Driver` hold 3,049 lines
between them; all non-suite test support is 8,162 lines. The review's 31
files and 5,881 lines counted a different set, but the order of magnitude was
right and the overlap is real: 131 suites use `PhasePowerHarness`, 95 use
`ActPowerHarness`, 28 use `WhenPlayedHarness`, and 63 suites use two or more
of the three.

What does not follow is that one driver should replace them. Consistency that
nothing is currently paying for is not worth a rewrite of roughly 180 suites,
and the project's testing doctrine — DAMP over DRY, every test earns its
place — argues the other way.

## Two things measured on the way, recorded so they are not re-measured

**There is no common setup to absorb.** The 291 `Table.start` chains inside
`gameplay/powers` suites produce 232 distinct step sequences. The most common
appears 8 times. A rule test states every fact it relies on, so each card
needs a different board; that is the Table definition working, not
repetition. Any driver would have had to take the whole board as a parameter.

**The per-suite wire proof is a thinner gap than it looks.** Of the 180 power
suites that run a power through a harness, 164 prove the journal rebuilds the
state, 121 prove the events round-trip the wire, and 16 prove neither. That
looks like a hole a shared driver could close by construction. It is mostly
already closed elsewhere: `WalkerOperationWireSuite` proves that every
`CoreOperation`, every `Location` and every `Piece` variant round-trips, by
hand-kept lists against encoders that are total over the sealed hierarchies.
What a per-suite `assertWireRoundTrips` adds on top is value-level — the
particular field combinations one power emits. Worth having, not worth a
driver.

## The decision

Candidate K's second half is closed as refused. No shared driver is built,
no per-action fixture is retired, and no vocabulary is renamed.

If the fragmentation begins to cost something, the place to take it up is the
consolidation of `PhasePowerHarness`, `ActPowerHarness` and
`WhenPlayedHarness` — a separate candidate with its own numbers, of which the
63 suites importing two or more of the three is the first. This record exists
so that the 2026-10-13 report's framing, and its "highest-leverage test-side
change" rating, are not re-opened from the stale numbers.
