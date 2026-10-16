# Parked Decision Query Read Sites: The Tax Is Destructuring, Not Construction

> Status: decision recorded 2026-10-15, from the 2026-10-13 architecture review,
> where it was candidate D ("Stop positional parked-decision queries"). The
> review rated D **Strong** on a premise that does not hold; this document
> records the corrected premise, re-rates the slice to **housekeeping**, and
> settles the shape of the fix. It supersedes D's proposed direction. No
> implementation is authorized by this document alone; the migration and the
> check are planned separately.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**,
**form**, **surface** and **answered options**. The three-table form/answer tag
separation recorded in
[the decision form tag separation decision](2026-09-25-decision-form-tag-separation-decision.md)
is settled and is not touched here.

## The premise the review got wrong

Candidate D was rated on this claim: adding `confirmVerb` to the parked-decision
query cost 48 files, and eight of those were procedure files edited for nothing
but argument re-ordering.

The 48 is right. The eight is wrong on both halves, and the direction D proposed
follows from the wrong half.

Commit `5203ff09` ("feat: board single-pick confirm names its pick") touched 48
files. They split exactly 24/24.

Twenty-four files are wildcard-only churn: the entire diff is
`ChooseOne(options, _)` becoming `ChooseOne(options, _, _)`. Two are production
(`PricedOptions.scala`, `WalkerSimulation.scala`); twenty-two are test or
testkit files.

Twenty-four files carry real content: six plumbing files (the field in
`Decisions.scala`, the DTO in `ActionProjectionDtos.scala`, the wire in
`ActionProjectionCodec.scala`, the mapping in `WalkerDecisionProjector.scala`,
the validation in `DecisionQueries.scala`, the render in
`BoardDecisionPanel.scala`), eight procedure files, and ten tests.

The eight procedure files each added a real `confirmVerb` declaration:
`SetupProcedure` "Start at", `TravelProcedure` "Travel to", `SearchProcedure`
"Search", `ChallengeProcedure` "Challenge", `RecoverProcedure` "Take",
`CampaignProcedure` "Raid", `CampaignRaid` "Move pawn to",
`CampaignPlanApplication` "Sacrifice from". They are the feature, not collateral.
No file in the commit re-ordered an argument, and none could have been spared:
construction already uses named arguments with defaults, so a procedure that
wants no verb pays nothing when a field is added.

The cost is therefore not at call sites. It is at read sites, where consumers
destructure a growing case class positionally. D's proposed direction — let a
parked decision declare what it needs rather than having every caller pass it
positionally — addresses construction, which is already free, and would have
left every positional pattern in place to break on the next field.

## Decision

**A `_` placeholder inside a pattern on a `DecisionQuery` or
`DecisionQueryProjection` subtype is forbidden. A fully-bound positional pattern
is allowed, and at two named sites it is required.**

The rule covers all seven forms of both hierarchies, and names both spellings of
the wire one: `DecisionQueryState` is a frontend alias for
`DecisionQueryProjection` (`frontend.scala:30-31`), so a frontend site must not
be able to sidestep the rule by using the alias.

Read sites that do not want every field use a typed bind —
`case one: DecisionQuery.ChooseOne => one.options` — which is what all 147
existing typed binds in the codebase already do, and what every read of
`ChooseMany` has always done.

**Two plumbing sites are pinned fully bound**, by explicit path allowlist:
`ActionProjectionCodec.scala`, whose seven patterns bind every field of every
form on the way to and from the wire, and the five fully-bound patterns in
`WalkerDecisionProjector.scala`, which carry a domain query to that wire. Their
arity coupling is the feature: it is the mechanism that fails the build when a
new field reaches neither the wire nor the mapping onto it.

`DecisionQueries.scala` is **not** pinned, and its eight wildcard-bearing
patterns are part of the migration. `accepts` must stay blind to copy: the
model states the invariant outright at `Decisions.scala:435-438` — "Copy NEVER
affects legality. `DecisionQueries` reads options and sections and never this"
— so forcing legality code to name `heading` and `confirmVerb` would contradict
a documented rule rather than guard anything. `wellFormed` validates
selectively for the same reason, binding every field of `ChooseAmount` and
`Distribute` because it checks every one of them, and ignoring copy elsewhere.

**The check lives in `BackendArchitectureSuite`**, with the pinning rule placed
immediately adjacent to the main rule so the pair reads as one intent rather
than as a rule and an exception.

**The migration is two commits**: the 52 read-site edits first, then the check.

**The positional whole-query equality assertions in tests are out of scope, and stay so.** They are contract checks, not arity coupling; see the rejections below.

## Why

### The tax is measurable, and it is four fifths test code

Repo-wide today, counting patterns in pattern position:

| | patterns with `_` | files | fully bound | files |
|---|---|---|---|---|
| `DecisionQuery.ChooseOne` | 35 | 27 | 3 | 3 |
| `DecisionQuery.ChooseAmount` | 5 | 4 | 2 | 2 |
| `DecisionQuery.Partition` | 5 | 4 | 1 | 1 |
| `DecisionQuery.Distribute` | 2 | 2 | 2 | 2 |
| `DecisionQuery.Negotiate` | 2 | 1 | 0 | 0 |
| `DecisionQuery.ChooseMany` | 0 | 0 | 0 | 0 |
| `DecisionQuery.Inspect` | 0 | 0 | 2 | 2 |
| `DecisionQueryProjection.Inspect` | 3 | 2 | 2 | 2 |
| `DecisionQueryProjection`, other six forms | 0 | 0 | 6 | 1 |

Fifty-two wildcard-bearing patterns in 34 files, against 18 fully-bound
patterns in 6 files, alongside 147 typed binds. The violations split 10
production in 3 files against 42 test in 31 files.

Seven forms, not six. `DecisionQuery` and `DecisionQueryProjection` each have
seven subtypes, `Inspect` among them, which `CONTEXT.md`'s **Form** entry does
not list — it names choose one, choose many, choose an amount, partition,
distribute and negotiate. The first census taken for this decision was built
from that vocabulary and undercounted by three, all of them
`DecisionQueryProjection.Inspect` patterns in gameplay power tests. The
**Form** entry has since been corrected to name inspect.

Under this rule, `5203ff09` would have touched 24 files instead of 48.

### The codebase already contains its own fix, applied inconsistently

`ChooseMany` is the widest form in the hierarchy — eight fields — and has zero
positional patterns. Every read of it is a typed bind. It pays nothing per field
and always has. `ChooseOne` has three fields and 35 wildcard-bearing patterns.
The distribution is the argument: the fix is a convention the codebase already
holds in one place, not a new query shape.

### Not every positional pattern is a tax, and the difference is load-bearing

Production wire code has zero wildcard-bearing patterns and seven fully-bound
ones, one per form, all in `ActionProjectionCodec`. That is the layer that
should break when a field is added, and it does. (The only wire-hierarchy
violations are three `Inspect` patterns in two gameplay power tests, which the
migration converts like any other.) `5203ff09` demonstrates the mechanism
working: the codec and the projector were both forced to notice `confirmVerb`
and both carried it. (`DecisionQueries` was edited in that commit too, but it
was not forced to — `(options, _, _)` would have compiled. Its blank-verb
validation was a choice, which is part of why it is not pinned.)

A rule banning positional patterns outright would have deleted that alarm. It
would have permitted `confirmVerb` to reach the DTO with nothing on the wire and
nothing failing. Splitting on the `_` placeholder removes the tax and keeps the
alarm; pinning the two wire-facing sites stops a later cleanup pass from
disarming it by tidying them into typed binds.

### The suite is the right home because it needs no new capability

`BackendArchitectureSuite` already walks all three test roots — `src/test/scala`,
`frontend/src/test/scala`, `shared/src/test/scala` — twice, via
`scalaFilesUnder`: once for the test-side 800-line cap and once for the
catalog-id naming convention. The catalog-id rule is structurally the rule this
decision adds: a regex over every test source with a named `exempt` set carrying
a one-line reason per entry. That is also the allowlist shape the pinning rule
needs, fifteen lines from where it goes.

`scripts/check-architecture.py` reads production roots only. Since 42 of the 52
violations are in test code, a rule there would catch 10 of 52 in 3 of 34 files
and would have prevented none of `5203ff09`'s twenty-two test churn files.

Reach does not distinguish the two homes: `verify-alpha-release.sh` runs the
script at line 81 and `./sbtw ... test frontend/test` at line 83, and reports
them in one evidence line. Only fit distinguishes them.

### Two commits, because only one of them has a decision in it

The check cannot land before the migration without turning the build red, so it
is last either way. Separating it keeps the commit that contains judgement — the
regex, the allowlist, the adjacency — reviewable on its own, instead of being
read at the bottom of 34 files of uniform churn. Splitting the migration further
buys nothing: it is one mechanical shape and the compiler verifies every site,
which is why the original change went in as one commit of 48 files.

## Considered and rejected

- **D's original direction: let a parked decision declare what it needs, so
  callers stop passing positionally.** Rejected because the cost it names does
  not exist. Construction uses named arguments with defaults and is already free;
  the eight procedure files cited as evidence were declaring the feature. A new
  query shape would have left all 52 positional patterns in place and still
  breaking on the next field. This is the rejection most worth remembering: it
  was a redesign that looked like progress and bought nothing.
- **Typed binds everywhere; ban positional patterns outright.** Rejected. It is
  the tidier rule and the worse one: it deletes the compiler alarm at the
  encoder, the projector and the validator, which is the only thing that catches
  a field nobody wired up.
- **The rule in `scripts/check-architecture.py`.** Rejected. It would need a
  test-root constant whose only consumer is this rule, and the script's identity
  is coarse structural invariants over production code — file size, dependency
  direction — not source idiom. The suite needs no new capability and already
  owns conventions of this kind.
- **Pinning `DecisionQueries.scala` fully bound alongside the codec and the
  projector.** Rejected, and this was the first version of this decision, which
  the implementation plan caught. Eight of the 52 violations are inside that
  file, and they belong there: `accepts` decides legality, and
  `Decisions.scala:435-438` states that copy never affects legality and that
  `DecisionQueries` reads options and sections and never the heading. A rule
  requiring it to bind every field would force legality code to name display
  fields, which is the opposite of the invariant. The alarm does not need it:
  `ActionProjectionCodec` binds every field of all seven forms, so a new field on
  any form fails the build there.
- **The rule in both homes.** Rejected: one rule in two spellings is drift
  waiting to happen. Note, though, that the 800-line cap is *already* duplicated
  across both homes (`check-architecture.py:29` and the suite at 295-301), so
  the split between the two mechanisms is a convention worth keeping rather than
  an invariant the repo currently holds.
- **`ChooseOne` only, or a check that fails forward on the other forms.**
  Rejected. The rule cannot name one subtype without reading as arbitrary, and
  the other 14 violations are the same one-line edit. A check that fails on
  existing code acquires an ignore list within a week.
- **Including the positional whole-query equality assertions.** Rejected, and
  on inspection not worth a slice of their own. This entry first said an
  assertion such as
  `assertEquals(asked.query, ChooseOne(options, Some(heading), Some(verb)))`
  "breaks on any added field". That is wrong for a field with a default: the
  assertion still compiles, and it passes until the thing under test declares
  a non-default value, which is exactly the change the assertion exists to
  catch. `5203ff09` shows it. Every whole-query assertion it touched was
  edited to add a `confirmVerb` the query now genuinely declares (Search,
  Travel to, Take, Move pawn to); none was edited for a field the query left
  alone. The count was also loose: "56" came from a grep with a line of
  context either side. A statement-level count finds 68 `assertEquals` calls
  with a query constructor as an operand, in 26 files. They are contract
  checks on the copy a power or procedure publishes, so a typed bind would
  weaken them to a field comparison. Leave them.
- **Creating a housekeeping ledger in the repo to file this under.** Rejected:
  the review's housekeeping ledger is a section of its HTML report, not a repo
  artifact, and inventing a file to hold one entry produces a document nobody
  updates. Promote it if a second item lands.

## Consequences

- An architecture review that counts a large file total on a field addition to a
  parked-decision query should read this file first. The file count alone does
  not say where the cost is, and in the one measured case it pointed at the
  wrong half of the change.
- `CONTEXT.md`'s **Form** entry listed six forms while `DecisionQuery` has
  seven; the missing one was `Inspect`, and the first census taken for this
  decision undercounted because it was built from the vocabulary. The entry
  now names inspect, so the vocabulary and the ADT agree.
- The two pinned sites are load-bearing in a way their code does not announce.
  Treat converting either of them to a typed bind as removing a build check, not
  as tidying. `DecisionQueries` is deliberately not among them; its blindness to
  copy is the invariant at `Decisions.scala:435-438`, not an oversight.
- `BackendArchitectureSuite` is named for the backend but already polices
  `frontend/src/main/scala` and all three test roots. Do not rename it on the
  strength of its name, and do not assume a rule in it is backend-only.
- **`ParkedDecision.scala` was untouched by `5203ff09`, and that is a result.**
  The [parked decision route design](2026-09-24-parked-decision-route-design.md)
  asked for a frontend module that routes a parked decision to its surface
  without reading query fields. Forty-eight files changed around it, including
  the render site it dispatches to, and its 254 lines did not move. That is the
  design being paid back, and it is the kind of confirmation that otherwise
  never gets written down.
- The slice is a convention plus a check. No interface gets deeper, no module
  absorbs behaviour, and deleting it would lose no behaviour — which is why it
  is re-rated to housekeeping rather than kept alongside the review's structural
  candidates. That is not a reason to skip it: 52 sites that break on a field
  none of them read is a real and recurring tax, and the fix is cheap and
  permanent. It is a reason not to let it compete with blocking work.
