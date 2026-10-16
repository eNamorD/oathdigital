# A Registry for the Game Log's Procedure Lines

> Status: designed 2026-10-16 from candidate F of the 2026-10-13 architecture
> review, which rated it Strong. Designing it re-rated it **Worth exploring**,
> and corrected one of the three defects the card named; both corrections are
> recorded below under "The rating, corrected", because the reasoning that
> produced them repeats if only the conclusions are kept. This design sits
> inside the [Game Log design](2026-09-25-game-log-design.md) and extends
> nothing it decided. It honours the
> [replay posture decision](2026-09-24-replay-posture-decision.md): nothing new
> is journaled and no validation runs on load. No implementation is authorized
> by this document alone; a plan follows it.

## Why now

Four places under `application/gamelog`, across three files, each learn every
procedure in the game. Adding a procedure means remembering all of them, and
one of the four forgives being forgotten.

| Place | What it knows per procedure | What a miss costs |
|---|---|---|
| `StartLines.name` | the tag's name, or that the procedure has no tag | compile error |
| `StartLines.opening` | where the tag posts, and Campaign's kind and defender | compile error |
| `ActionLines.lines` | the action line and the event it posts at | compile error |
| `DetailLines.NarratedIds` | which of its decisions another line already tells | **nothing: the log posts a stray "Chose …" line** |

A fifth place, `LogSections.of`, is exhaustive by family (`case _: ActionRef
=> true`), so a new procedure inherits an answer rather than failing. It is
per-procedure knowledge living outside the three files that hold the rest.

The inconsistency is inside one module: three matches the compiler keeps
total, one `Set[String]` the compiler cannot see into, and one match that
answers by family.

## The rating, corrected

Candidate F was rated Strong on the claim that a registry would keep the
compile-time exhaustiveness the three good files have. It cannot *add* that
property, and the shape the review pointed at would have removed it.

**A registry in this codebase is a lookup guarded by a test.**
`WalkerProcedureRegistry.entries` is a `Map[ProcedureRef, Entry]`; a miss
returns `Left(OathViolation.InvalidEventOrder)` at runtime, and the gate is a
unit test asserting `entries.keySet == ProcedureRef.all` minus the five
procedures built from powers (`WalkerProcedureRegistrySuite`, "the production
entries register every procedure reference"). Copying that shape here would
trade three compile-time guarantees for one runtime `Left` — a loss dressed as
a pattern. A registry *can* keep compile-time safety, by being built from an
exhaustive match rather than written as a literal map (ruling 1). The match
then moves rather than vanishes, and what the change buys is locality, not
safety.

**The `ContributingPower` evidence argues against the candidate, not for
it.** The review cited the 193 adapters behind that trait's three required
members as proof the pattern works. Powers need a registry because the set of
powers is open and no exhaustive match over them is possible. `ProcedureRef`
is sealed (`model/ProcedureRef.scala`): fifteen `ActionRef`s, three
`PhaseTransitionRef`s, seven `TriggeredProcedureRef`s, and the parameterized
`ActionRef.UsePower`. The reason the power registry is good design is
precisely the reason the same construction would be a downgrade for
procedures. This is recorded as reasoning, not only as a struck conclusion:
an open set and a closed set want opposite tools, and evidence drawn from one
does not carry to the other.

**"A procedure declares its log lines next to itself" is not available.**
`scripts/check-architecture.py` forbids `gameplay` importing `application`, so
a procedure cannot name `LogSpan`, `LogKind` or `LogJournal`. More decisive:
the three files do not hold per-procedure *wording*. They hold per-procedure
*rules that read the journal* — matches over `OpStep` operations,
`journal.answers`, and look-ahead to the segment end. "Mustered {n} warbands
with {card}" is a match over a `Move` out of a warband bank plus a card read
from an earlier answer, not a template. Those rules can only live in
`application`. Co-location there is the correct claim, and "next to itself"
means "next to its siblings in one table".

**The third defect, corrected.** The card read: "`StartLines.campaign`
re-runs the procedure's own derivation of a fact the journal already
records." That implies the derivation is redundant; it is the opposite.
`RecordCampaignResult` is the `.orElse` fallback and is recorded at the
battle's end, after the tag line's anchor, and the kind and defender
decisions are asked only when there is a choice, so when they are skipped the
journal holds no answer either. The derivation is the primary and only early
source. The defect, restated:

> `StartLines.campaign` reconstructs kind and defender two ways in one
> function; the reconstruction belongs behind one named function on
> `CampaignSetup`.

**Therefore: Worth exploring.** A new procedure goes from three files to two.
One silently-failing place closes. Nothing absorbs behaviour, no interface
gets smaller, and nothing is deleted. The ambition belongs to the follow-up
candidate under "Out of scope", which can delete a per-procedure match rather
than relocate it.

## Rulings

These were settled while designing and bind the rest of this document.

1. **Match-built, never map-first.** The registry is produced by an
   exhaustive `rules(procedure: ProcedureRef)` match and exposed as a map
   built from `ProcedureRef.all`. It is total by construction, so the lookup
   cannot miss and a new procedure fails compilation.
2. **No family wildcards.** Every finite member is enumerated individually.
   `case _: PhaseTransitionRef => bare` would shorten the table and
   re-open the hole this design exists to close, one layer further in.
3. **The log stays in `application`.** No log vocabulary moves to `gameplay`.
   It would compile and break no import rule, which is what makes it easy to
   do by accident.
4. **Nothing new is journaled.** No new event, no new durable field, no
   replay-time validation. The replay posture decision of 2026-09-24 stands
   untouched.
5. **Each procedure states its own narration**, even when two procedures ask
   the same decision.

## The registry

A new file, `application/gamelog/ProcedureLog.scala`.

```scala
private[gamelog] final case class ProcedureLog(
    tag: Option[String],
    anchor: Anchor = Anchor.AtFirstEvent,
    opening: Opening = Opening.none,
    continued: Continued = Continued.none,
    lines: ActionLine,
    narrated: Set[String] = Set.empty,
    narratedPrefixes: Vector[String] = Vector.empty,
    isSection: Boolean = true)
```

| Member | What it declares | Today |
|---|---|---|
| `tag` | the name the tag entry opens with, `None` for a procedure with no modifier window | `StartLines.name` |
| `anchor` | the event the tag posts at: the run's first event by default, else Muster and Trade's cost step, Recover's dice-pool step, Travel's and Search's first settled step, the facedown adviser's pick, or Campaign's kind and defender | `StartLines.opening`'s match |
| `opening` | what the line after the tag says beyond its modifiers; only Campaign declares one | `StartLines.campaign` |
| `continued` | the repeated line a procedure posts when the player pays again; only Recover declares one | `StartLines.continued` |
| `lines` | the action line and the event its facts complete at | `ActionLines.lines` |
| `narrated`, `narratedPrefixes` | the decisions of this procedure whose answer another line already tells | `DetailLines.NarratedIds` and `NarratedPrefixes` |
| `isSection` | whether the run draws a section band | `LogSections.of` |

`Anchor`, `Opening`, `Continued` and `ActionLine` are type aliases over the
signatures these rules already have — `(LogJournal, Run, Int)` for the
anchor's predicate, and that plus `Option[PlayerId]` for the three that build
spans — named so the table above reads as declarations rather than as
function types.

`anchor`, `opening` and `continued` are separate members, not folded into
`lines`: they post at different events from the action line, and folding
`opening` in would make the tag line depend on the action-line rule.
`StartLines.opens` disappears — it is `tag.nonEmpty`.

`ActionRef.UsePower(power)` is one case of the match, as it is one case of
`WalkerProcedureRegistry.lookup`. The five procedures the walker registry
builds from powers rather than from `entries` are ordinary cases here: a
phase menu and a follow-up offer post no lines, and saying so once is the
point.

### Who consults it

`GameLogFormatter` looks the entry up once per run, in `walker`, and calls
through it. It already reaches around `StartLines` for `name` and `opens` and
calls `LogSections.forRun` directly, so three of the absorbed members have
the formatter as their consumer today. `DetailLines` receives the run's entry
instead of a pre-flattened `Set`.

## What the three files keep

The per-procedure matches go. The rule bodies stay where they are, as named
defs the table references — the shape `WalkerProcedureRegistry` uses when it
writes `build = SearchProcedure.build`. `ActionLines` keeps its Search,
Campaign, Negotiation and economy bodies and its shared helpers; `StartLines`
keeps tag assembly, the cost span and modifier naming; `DetailLines` keeps the
delta, roll and decision rules, which are generic and name no procedure.

Expected sizes, against the 800-line cap enforced by
`scripts/check-architecture.py` and `BackendArchitectureSuite`: `ActionLines`
442 → about 330, `StartLines` 186 → about 130, `ProcedureLog` about 200. The
17-line `LogSections` goes: `of` becomes the `isSection` member, and
`forRun`'s one expression moves to the formatter, which is its only caller.
No file approaches the cap, and a single file holding
every rule body would have passed it, which is why the table holds references.

## Narration

Each entry declares its own narrated decision ids and prefixes. The prefixes
key cleanly to their owners: `setup.` to Setup, `campaign.` to Campaign,
`negotiation.` to Negotiation, `travel.` to Travel, `cardplay.place.` and
`cardplay.discard.` to the procedures that answer them.

Two ids are asked under more than one procedure and so appear in more than
one entry: `cardplay.place.*` under Search, Play Facedown Adviser and Use
Power, since a Search plays its kept card through Card Play and the Oracle
plays a card as if searched; and `phase.menu`, one id shared by the Wake, Rest
and Act menus. **This is not the duplication the review flags elsewhere.**
Nothing can drift, because ruling 2 makes each of the three entries state its
own narration and omitting it fails to compile. A fact repeated under
compiler enforcement is a declaration; a fact repeated under a reader's
memory is duplication.

`ModifierPicks.isDecision` (a suffix predicate) and `RerollChoice.isDecision`
(a pool predicate) match under any procedure with a modifier window or a
roll, so there is no procedure to key them on. They stay the two calls
`DetailLines.narrated` already makes, owned by the layer that owns the
decisions, and they compute rather than enumerate, so they cannot silently
miss a decision. Nothing about them needs a new home or a list.

After this change nothing in the narration path is hand-maintained: the set
is registry-derived, the prefixes are entry data, and the cross-cutting cases
are two function calls that were always correct.

## Campaign

`StartLines.campaign` reads kind and defender from `CampaignSetup.kindOf` and
`CampaignSetup.defenderOf` against the answers so far, then falls back to the
recorded `RecordCampaignResult`. Both reads move behind one named function on
`CampaignSetup`, which answers "what are this campaign's kind and defender at
this point of its run" and owns the two ways of answering it. The log asks
once and learns neither.

Nothing is journaled and nothing is validated: the fallback reads an
operation the journal already holds, which is what it does today.

## Tests

- **Golden logs, unchanged.** Slice 1's proof is that every golden `LogEntry`
  vector is byte-identical before and after. Co-location that changes a line
  has changed behaviour.
- **Registry totality** is by construction, so there is no coverage test to
  write for it. `ProcedureRef.all` builds the map, and `rules` is exhaustive
  under `-Werror`. A test asserting the keys would assert what the compiler
  guarantees.
- **Narration coverage**, the only thing that catches a missing string: in
  `GameLogPropertiesSuite`, beside its sibling property "every `ProcedureRef`
  key the formatter handles appears in some script". Across the scripted
  journals, every decision id reached is narrated by its procedure's entry,
  matched by one of the two predicates, or posts a `Decision` line.
  Declared-but-unexercised narrations are reported, not asserted: an exact-set
  assertion would make every new script a two-file edit, which is the cost
  this design exists to cut.
  Its scope is entry membership. It asserts nothing the two predicates
  already guarantee.
- **The existing log suites stand unchanged.** Any edit to them in slice 1 is
  evidence the refactor moved something.

`LogScripts.scala` is at 800 lines, the test cap exactly. A new script cannot
be added to it; slice 2 needs no new script, and if a later one does it opens
a second scripts file rather than growing this one.

## Slices

1. **Co-location.** `ProcedureLog`, the exhaustive `rules` match, the tag,
   anchor, opening, continued, action-line and section members, the
   formatter's lookup, and the deletion of `LogSections`. Proof: golden logs
   byte-identical.
2. **Narration and Campaign.** Narration moves onto the entries,
   `DetailLines` reads the entry, the `CampaignSetup` function absorbs the two
   reads, and the coverage property lands.

Slice 1 is pure co-location and slice 2 is the only part that touches
behaviour-adjacent code. Mixed into one slice, a golden-log diff could not say
which half caused it.

## Out of scope

- **Procedures journaling their own notes**, the way powers do since the
  [power log lines design](2026-09-26-power-log-lines-design.md). This is the
  depth change: `MusterProcedure` emits a `Note` at its gain, declares its
  wording in model vocabulary, and `ActionLines`' Muster case is *deleted*
  rather than relocated. The journal stays the source of truth and old games
  reword for free. A procedure can already do this — `CardPlayRules.homeland`
  puts a `Note` with `RuleNotes.homelandDiscard` into its own tree. Its price,
  stated as price and not as caveat: a note renders as "{source}: {sentence}"
  and `PowerSourceRef` names a card, site or banner, so a procedure's own line
  needs a sourceless note variant and a wire case for it; and the migration
  touches about fifteen `gameplay` procedure files. It is a candidate of its
  own, not a roadmap footnote, and it is where this module's ambition lives.
- **A typed decision-id vocabulary**, which is candidate H's. This design
  invents none and is not blocked on it: the coverage property catches a
  missing narration whether or not ids are ever typed. If H concludes that
  typed ids subsume entry membership, that is a follow-up to this work, not a
  merge.
- **The formatter's second pass over the journal**, which is candidate I's.
  Untouched here.
- **`DeltaMeaning`**, which is candidate G's.

## Resolved decisions

Settled with the product owner while designing, in order:

- The gate is compile-time, delivered by building the map from an exhaustive
  match. The walker registry's map-plus-coverage-test posture was considered
  and rejected: it would make this module less safe than it is today.
- Co-location happens in `application/gamelog`. The gameplay-side reading of
  "next to itself" is the separate candidate above.
- Narration is derived from the entries, not moved onto the procedures.
  Moving a `narrated` flag onto a procedure would put log vocabulary into
  `gameplay`; deriving it keeps the concern where it belongs and matches the
  registry's own construction.
- The entry holds function references, not rule bodies. A new procedure
  therefore costs two files, the table and the file holding its body, and the
  design claims that rather than a bare "one file".
- `LogSections.of` and `StartLines.opens` are absorbed. A registry that
  claims to be the one place and leaves two others is not one.
- Every finite member is enumerated; no family wildcards.
- Two slices, not one, so a golden-log diff stays diagnostic.
