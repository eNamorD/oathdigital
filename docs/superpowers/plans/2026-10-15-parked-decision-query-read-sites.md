# Parked Decision Query Read Sites Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert all 52 wildcard-bearing positional patterns on the parked-decision query to typed binds, then add an architecture rule that stops them coming back.

**Architecture:** A read site that does not want every field of a `DecisionQuery` or `DecisionQueryProjection` subtype binds the subtype and reads fields by name (`case one: DecisionQuery.ChooseOne => one.options`) instead of destructuring positionally with `_` placeholders. Two wire-facing sites stay fully bound on purpose, because their arity coupling is the build's only alarm that a new field reached neither the wire nor the mapping onto it. A new rule in `BackendArchitectureSuite` enforces both halves across production and test sources.

**Tech Stack:** Scala 3.9.0, munit 1.0.4, sbt via `./sbtw`, Python 3 for `scripts/check-architecture.py`.

**Spec:** [docs/superpowers/specs/2026-10-15-parked-decision-query-read-sites-decision.md](../specs/2026-10-15-parked-decision-query-read-sites-decision.md)

## Global Constraints

- Scala 3.9.0 with `-Werror` (`build.sbt:61-68`): every compiler warning fails the build. `-Wunused:imports,privates,locals,implicits,nowarn` is on, so an unused binder is an error.
- Production Scala files stay at or below 800 lines, enforced by `scripts/check-architecture.py` and `BackendArchitectureSuite`. Test files have the same cap in the suite.
- `DecisionQuery` and `DecisionQueryProjection` each have **seven** subtypes: `ChooseOne`, `ChooseMany`, `ChooseAmount`, `Partition`, `Distribute`, `Negotiate`, `Inspect`. `CONTEXT.md`'s **Form** entry lists only six and omits `Inspect`; work from the ADT in `src/main/scala/oathdigital/model/Decisions.scala`, not from the vocabulary.
- `DecisionQueryState` is a type and value alias for `DecisionQueryProjection` (`frontend/src/main/scala/oathdigital/frontend/frontend.scala:30-31`). Both spellings reach the same seven case classes.
- Binder naming follows the existing 83 typed binds: `one`, `many`, `amount`, `partition`, `distribute`, `negotiate`, `inspect`.
- Copy never affects legality (`src/main/scala/oathdigital/model/Decisions.scala:435-438`). Nothing in this plan may make `DecisionQueries.accepts` read `heading` or a confirm label.
- The three-table form/answer tag separation recorded in `docs/superpowers/specs/2026-09-25-decision-form-tag-separation-decision.md` is settled. This plan does not touch tag spellings.
- Commit messages end with `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

## Out of scope

The 56 positional whole-query equality assertions in tests (for example `assertEquals(asked.query, DecisionQuery.ChooseOne(options, Some(heading), Some(verb)))` at `src/test/scala/oathdigital/gameplay/SearchProcedureSuite.scala:55` and `TravelProcedureSuite.scala:248`). These are fully bound, so the rule in Task 2 permits them. Converting them decides what each test is for, one at a time. Leave every one of them alone.

---

## File Structure

**Modified in Task 1 — production (3 files, 10 sites):**
- `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala` — 8 sites in `wellFormed` and `accepts`
- `src/main/scala/oathdigital/gameplay/walker/PricedOptions.scala` — 1 site in `options`
- `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala` — 1 site in the preview walk

**Modified in Task 1 — test (31 files, 42 sites):** listed site by site in Step 6 below.

**Unchanged, and must stay unchanged (the pinned sites):**
- `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala:75,81,90,97,104,111` plus its `Inspect` arm — seven fully-bound patterns, one per form
- `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala:278,299,304,308,314` — five fully-bound patterns

**Modified in Task 2:**
- `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala` — two new tests appended

---

## Task 1: Migrate every wildcard-bearing read site to a typed bind

**Files:**
- Modify: `src/main/scala/oathdigital/gameplay/walker/DecisionQueries.scala:67,90,152,242,272,279,286,292`
- Modify: `src/main/scala/oathdigital/gameplay/walker/PricedOptions.scala:49`
- Modify: `src/main/scala/oathdigital/gameplay/walker/WalkerSimulation.scala:236`
- Modify: 31 test files, enumerated in Step 6
- Test: the existing suites cover every site; no new test file

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: a tree in which every pattern on a `DecisionQuery`/`DecisionQueryProjection` subtype either binds the subtype by name or binds every field with no `_`. Task 2's rule depends on that being true, and fails if any site is missed.

- [ ] **Step 1: Record the baseline**

Run both and keep the output; Task 1 must not change either result.

```bash
./sbtw test frontend/test 2>&1 | tail -20
```

Expected: all suites pass. Note the total test count — it must be identical at the end of this task.

- [ ] **Step 2: Migrate `DecisionQueries.wellFormed` (3 sites)**

`wellFormed` validates selectively. It keeps its two fully-bound patterns at
`:137` (`ChooseAmount`) and `:187` (`Distribute`) — do not touch those.

Follow the idiom this file already uses for `ChooseMany` at `:105-106`: bind
the subtype, then alias the fields the arm reads as locals on one line, so the
body below is unchanged. That keeps these arms readable and keeps the diff to
the first two lines of each.

`:67` — three references, so read fields directly:

```scala
    case one: DecisionQuery.ChooseOne =>
      val refs = one.options.map(_.ref)
      for
        _ <- require(refs.nonEmpty, decisionId, "declares no options")
        _ <- require(refs.distinct.size == refs.size, decisionId,
          "declares duplicate options")
        _ <- require(one.options.exists(_.available), decisionId,
          "declares no option that may be chosen")
        _ <- require(one.confirmVerb.forall(_.trim.nonEmpty), decisionId,
          "declares a blank confirm verb")
      yield ()
```

`:90-91` — five references across the arm, so alias. Replace the two pattern
lines with these two; lines `:92-103` stay exactly as they are:

```scala
    case negotiate: DecisionQuery.Negotiate =>
      val (participants, terms, accepted, bounds, acceptors) =
        (negotiate.participants, negotiate.terms, negotiate.accepted,
          negotiate.bounds, negotiate.acceptors)
      val members = participants.toSet
```

`:152` — fifteen references across a thirty-line arm, so alias. Replace the
single pattern line with these two; lines `:153-185` stay exactly as they are:

```scala
    case partition: DecisionQuery.Partition =>
      val (sections, options) = (partition.sections, partition.options)
      val refs = options.map(_.ref)
```

- [ ] **Step 3: Migrate `DecisionQueries.accepts` (5 sites)**

Every one of these must stay blind to copy — bind the subtype, read only the
fields the legality check already reads, and add no reference to `heading`,
`confirmVerb` or any confirm label.

`:242`

```scala
    case one: DecisionQuery.ChooseOne => answer match
      case DecisionAnswer.ChooseOneAnswer(selected) =>
        require(one.options.filter(_.available).map(_.ref).contains(selected),
          decisionId, "does not offer the selected option")
```

`:272` — the query's binder would collide with the answer's `amount`, so alias
the bounds instead and leave the body untouched:

```scala
    case query: DecisionQuery.ChooseAmount => answer match
      case DecisionAnswer.ChooseAmountAnswer(amount) =>
        val (min, max) = (query.min, query.max)
        require(amount >= min && amount <= max, decisionId,
          s"amount $amount is outside $min..$max")
```

`query` as a binder is already used seven times in this tree for exactly this
reason.

`:279`

```scala
    case partition: DecisionQuery.Partition => answer match
      case DecisionAnswer.PartitionAnswer(placements) =>
        acceptsPartition(decisionId,
          partition.sections.map(s => s.key -> s).toMap,
          partition.options.map(_.ref), placements)
```

`:286`

```scala
    case distribute: DecisionQuery.Distribute => answer match
      case DecisionAnswer.DistributeAnswer(amounts) =>
        acceptsDistribution(decisionId, distribute.slots,
          distribute.minTotal, distribute.maxTotal, amounts)
```

`:292` — three references across the arm, so alias. Replace the pattern line
with these two; lines `:293-303` stay exactly as they are:

```scala
    case negotiate: DecisionQuery.Negotiate =>
      val (participants, bounds, acceptors) =
        (negotiate.participants, negotiate.bounds, negotiate.acceptors)
      answer match
```

- [ ] **Step 4: Migrate `PricedOptions.scala:49` and `WalkerSimulation.scala:236`**

`PricedOptions.scala:49`

```scala
      case one: DecisionQuery.ChooseOne => one.options
```

The field is `options`; the old binder was `offered`, so the body changes from `offered` to `one.options`. The `case _ => Vector.empty` arm below stays.

`WalkerSimulation.scala:236`

```scala
            case one: DecisionQuery.ChooseOne =>
              Right(one.options.filter(_.available).map(option =>
```

Leave the rest of that expression as it is, and keep the `case _ => Left(...)` arm at `:244`.

- [ ] **Step 5: Compile the production change on its own**

```bash
./sbtw compile
```

Expected: success, no warnings. `-Werror` means an unused binder or a stale reference fails here. If a binder is reported unused, the arm reads no field — use `case _: DecisionQuery.ChooseOne =>` for that arm rather than binding a name you do not read.

- [ ] **Step 6: Migrate the 42 test sites**

Each entry gives the line, the binder to introduce, and every name whose references in that arm must become a field read. The transformation is the same at every site: replace `Form(a, _, _)` with `binder: Hier.Form` and prefix each previously-bound name with `binder.`. Where an arm reads a name more than two or three times, alias instead — `val (sections, options) = (partition.sections, partition.options)` — which is the idiom `DecisionQueries.scala:105-106` already uses and leaves the body unchanged. Leave each arm's `case other => fail(...)` or `case _ =>` fallback alone.

`src/test/scala/oathdigital/application/gamelog/LogScripts.scala`

- `:552` — bind `amount: DecisionQuery.ChooseAmount`; rewrite `max` as `amount.max`
- `:555` — bind `amount: DecisionQuery.ChooseAmount`; rewrite `max` as `amount.max`

`src/test/scala/oathdigital/gameplay/CampaignPlanWindowSuite.scala`

- `:92` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/CardPlayProcedureSuite.scala`

- `:118` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/ChallengeProcedureSuite.scala`

- `:213` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`, `heading` as `one.heading`
- `:242` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/EconomyFixture.scala`

- `:20` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`, `heading` as `one.heading`

`src/test/scala/oathdigital/gameplay/LazyPruningSuite.scala`

- `:50` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`
- `:129` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/ModifierMenuOptionsSuite.scala`

- `:55` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`
- `:100` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`
- `:138` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`
- `:172` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/PassWhenEmptySuite.scala`

- `:46` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/PowerNoteHideSuite.scala`

- `:30` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/SearchModifierChoiceSuite.scala`

- `:43` — bind `one: DecisionQuery.ChooseOne`; rewrite `listed` as `one.listed`

`src/test/scala/oathdigital/gameplay/VisionPlaySuite.scala`

- `:159` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/powers/action/OracularPigSuite.scala`

- `:36` — bind `inspect: DecisionQueryProjection.Inspect`; rewrite `cards` as `inspect.cards`, `heading` as `inspect.heading`

`src/test/scala/oathdigital/gameplay/powers/action/TavernSongsSuite.scala`

- `:42` — bind `inspect: DecisionQueryProjection.Inspect`; rewrite `cards` as `inspect.cards`, `heading` as `inspect.heading`
- `:68` — bind `inspect: DecisionQueryProjection.Inspect`; rewrite `cards` as `inspect.cards`

`src/test/scala/oathdigital/gameplay/powers/action/TheRedSeerSuite.scala`

- `:44` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/powers/action/WardOfSilenceSuite.scala`

- `:38` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/powers/campaign/PlanDriver.scala`

- `:138` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/powers/campaign/VowOfUnionSuite.scala`

- `:118` — bind `amount: DecisionQuery.ChooseAmount`; rewrite `min` as `amount.min`, `max` as `amount.max`

`src/test/scala/oathdigital/gameplay/powers/campaign/WarningSignalsSuite.scala`

- `:114` — bind `distribute: DecisionQuery.Distribute`; rewrite `slots` as `distribute.slots`, `min` as `distribute.min`, `max` as `distribute.max`

`src/test/scala/oathdigital/gameplay/powers/economy/MusterModifierChoiceSuite.scala`

- `:45` — bind `one: DecisionQuery.ChooseOne`; rewrite `listed` as `one.listed`

`src/test/scala/oathdigital/gameplay/powers/economy/TradeModifierChoiceSuite.scala`

- `:43` — bind `one: DecisionQuery.ChooseOne`; rewrite `listed` as `one.listed`
- `:57` — bind `one: DecisionQuery.ChooseOne`; rewrite `heading` as `one.heading`

`src/test/scala/oathdigital/gameplay/powers/search/LandWardenSuite.scala`

- `:75` — bind `partition: DecisionQuery.Partition`; rewrite `sections` as `partition.sections`, `heading` as `partition.heading`

`src/test/scala/oathdigital/gameplay/powers/travel/TentsSuite.scala`

- `:87` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/powers/travel/TravelModifierChoiceSuite.scala`

- `:30` — bind `one: DecisionQuery.ChooseOne`; rewrite `listed` as `one.listed`

`src/test/scala/oathdigital/gameplay/powers/wake/HungerSuite.scala`

- `:50` — bind `partition: DecisionQuery.Partition`; rewrite `options` as `partition.options`

`src/test/scala/oathdigital/gameplay/walker/ChooseOneConsentSuite.scala`

- `:69` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/gameplay/walker/ModifierChoiceSuite.scala`

- `:100` — bind `one: DecisionQuery.ChooseOne`; rewrite `listed` as `one.listed`

`src/test/scala/oathdigital/gameplay/walker/WalkerPreviewSuite.scala`

- `:90` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`, `heading` as `one.heading`

`src/test/scala/oathdigital/testkit/CatacombsGame.scala`

- `:75` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/testkit/EmptyWindowModifier.scala`

- `:35` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/testkit/PlacementPicks.scala`

- `:52` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`
- `:82` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`

`src/test/scala/oathdigital/testkit/Situation.scala`

- `:122` — bind `one: DecisionQuery.ChooseOne`; rewrite `options` as `one.options`
- `:125` — bind `partition: DecisionQuery.Partition`; rewrite `sections` as `partition.sections`, `options` as `partition.options`
- `:136` — bind `amount: DecisionQuery.ChooseAmount`; rewrite `min` as `amount.min`

Four of these sites need more than the mechanical rewrite, because they rebuild
the query rather than only reading it. At `ChallengeProcedureSuite.scala:213`,
`EconomyFixture.scala:20` and `WalkerPreviewSuite.scala:90` the arm currently
reconstructs `DecisionQuery.ChooseOne(options :+ extra, heading)` positionally,
which silently drops any `confirmVerb` the original query declared. Use `copy`
instead, which preserves every field the fixture is not changing:

```scala
          case one: DecisionQuery.ChooseOne =>
            decide.copy(query = one.copy(
              options = one.options :+ DecisionOption.Button(extra, "Extra")))
              : Operation
```

Apply the same shape at the other two, substituting each site's own added
option: `DecisionOption.Banner(darkestSecretOption)` at
`ChallengeProcedureSuite.scala:214` and
`DecisionOption.Denizen(DecisionOptionRef.Denizen(card))` at
`EconomyFixture.scala:22`. Dropping the positional `heading` argument is the
point — `copy` carries it.

The fourth is `Situation.scala`, where all three patterns are nested inside
`Park(Decide(...))` and two carry guards that read the bound names:

```scala
    case Park(Decide(_, _, one: DecisionQuery.ChooseOne, _, _, _, _),
        _, _, _) if one.options.exists(_.available) =>
      DecisionAnswer.ChooseOneAnswer(one.options.filter(_.available).head.ref)
    case Park(Decide(_, _, partition: DecisionQuery.Partition,
        _, _, _, _), _, _, _)
        if partition.sections.nonEmpty && partition.options.nonEmpty =>
      val keep = partition.sections.find(_.minRequired > 0)
        .getOrElse(partition.sections.head)
      val discard = partition.sections.find(_.key != keep.key).getOrElse(keep)
```

Then rewrite the remainder of that `Partition` arm's `sections` and `options`
references, and at `:136`:

```scala
    case Park(Decide(_, _, amount: DecisionQuery.ChooseAmount, _, _,
        _, _), _, _, _) =>
      DecisionAnswer.ChooseAmountAnswer(amount.min)
```

`LogScripts.scala:552` and `:555` are nested the same way:

```scala
          amount: DecisionQuery.ChooseAmount, _, _, _, _), _, _, _) =>
        ChooseAmountAnswer(amount.max)
```

- [ ] **Step 7: Run the full suite**

```bash
./sbtw test frontend/test 2>&1 | tail -20
```

Expected: every suite passes, with the same test count recorded in Step 1. A changed count means an arm was dropped or added, not migrated.

- [ ] **Step 8: Verify no wildcard-bearing pattern survives**

```bash
python3 - <<'EOF'
import re,pathlib
forms="ChooseOne|ChooseMany|ChooseAmount|Partition|Distribute|Negotiate|Inspect"
occ=re.compile(r"\b(DecisionQuery|DecisionQueryProjection|DecisionQueryState)\.("+forms+r")\(")
def args(t,i):
    d=0;o=""
    while i<len(t):
        c=t[i]
        if c=="(":d+=1
        elif c==")":
            d-=1
            if d==0:return o
        if d>=1 and not(d==1 and c=="("):o+=c
        i+=1
    return o
bad=[]
for r in ["src/main","src/test","shared/src/main","shared/src/test",
          "frontend/src/main","frontend/src/test"]:
    for f in pathlib.Path(r).rglob("*.scala"):
        t=f.read_text()
        for m in occ.finditer(t):
            b=t[:m.start()];ci=b.rfind("case ")
            if ci<0:continue
            btw=b[ci:]
            if "=>" in btw or "=" in btw or "val " in btw or len(btw)>200:continue
            a=args(t,m.end()-1)
            if not a.strip():continue
            if any(x.strip()=="_" for x in
                   re.split(r",(?![^()\[\]]*[)\]])",a)):
                bad.append(f"{f}:{t[:m.start()].count(chr(10))+1}")
print(len(bad),"remaining");[print(" ",x) for x in bad]
EOF
```

Expected: `0 remaining`.

- [ ] **Step 9: Run the architecture and link checks**

```bash
python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py
```

Expected: both pass.

- [ ] **Step 10: Commit**

```bash
git add src/main/scala/oathdigital/gameplay/walker src/test/scala shared/src/test frontend/src/test
git commit -F - <<'MSG'
refactor: parked-decision query read sites bind by name

Every pattern on a DecisionQuery or DecisionQueryProjection subtype that
does not want all of its fields now binds the subtype and reads fields by
name, so adding a field no longer edits the site. 52 patterns across 34
files, 10 of them production.

Three test fixtures rebuilt a ChooseOne positionally when adding an option,
which dropped any confirmVerb the query declared; they use copy now and
keep it.

DecisionQueries is migrated rather than pinned: accepts must stay blind to
copy, which Decisions.scala:435-438 states as an invariant.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```

---

## Task 2: Enforce the convention in BackendArchitectureSuite

**Files:**
- Modify: `src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala` (append two tests)

**Interfaces:**
- Consumes: the migrated tree from Task 1. The first test fails if any site was missed.
- Produces: two munit tests, `"a parked-decision query pattern binds no wildcard"` and `"the wire-facing query patterns stay fully bound"`. Nothing depends on them.

**Why this home:** the suite already walks all six source roots through `scalaFilesUnder` (`:288`), already polices `frontend/src/main/scala` despite its name, and its catalog-id test (`:313-332`) is the same shape — a regex over every test source with a named `exempt` set. `scripts/check-architecture.py` reads production roots only, so a rule there would see 10 of the 52 sites.

- [ ] **Step 1: Write the first rule and watch it pass on the migrated tree**

Append to `BackendArchitectureSuite.scala`, immediately after the catalog-id test so the pair of query rules sits together. Scala's regex cannot balance parentheses, so the matcher walks the argument list:

```scala
  // A parked-decision query grows fields: ChooseOne gained confirmVerb and
  // cost 52 read sites that never read it. A site that wants some fields
  // binds the subtype and reads them by name; a `_` placeholder in the
  // pattern is what couples the site to the arity. The two wire-facing
  // sites below are the exception, and the next test pins them.
  private val QueryForms = Vector("ChooseOne", "ChooseMany", "ChooseAmount",
    "Partition", "Distribute", "Negotiate", "Inspect")

  private val QueryHierarchies = Vector("DecisionQuery",
    "DecisionQueryProjection", "DecisionQueryState")

  /** Every `Hier.Form(...)` argument list that sits in pattern position,
    * as `path:line -> arguments`. `DecisionQueryState` is an alias for
    * `DecisionQueryProjection` (frontend.scala:30-31), so both are read.
    */
  private def queryPatterns(roots: Vector[Path]): Vector[(String, String)] =
    // Longest alternative first: `DecisionQuery` is a prefix of the other
    // two, and `\b` keeps `MyDecisionQuery` out.
    val opening = ("""\b""" +
      QueryHierarchies.sortBy(-_.length).mkString("(", "|", ")") +
      """\.""" + QueryForms.mkString("(", "|", ")") + """\(""").r
    scalaFilesUnder(roots).flatMap { path =>
      val text = Files.readString(path)
      opening.findAllMatchIn(text).flatMap { found =>
        val before = text.substring(0, found.start)
        val clause = before.lastIndexOf("case ")
        val between = if clause < 0 then "" else before.substring(clause)
        val inPattern = clause >= 0 && between.length <= 200 &&
          !between.contains("=>") && !between.contains("=")
        Option.when(inPattern) {
          var depth = 0
          var index = found.end - 1
          val collected = new StringBuilder
          var done = false
          while index < text.length && !done do
            val character = text.charAt(index)
            if character == '(' then depth += 1
            else if character == ')' then
              depth -= 1
              if depth == 0 then done = true
            if !done && !(depth == 1 && character == '(') then
              collected.append(character)
            index += 1
          val line = before.count(_ == '\n') + 1
          s"$path:$line" -> collected.toString
        }
      }
    }

  private def wildcardQueryPatterns(roots: Vector[Path]): Vector[String] =
    queryPatterns(roots).collect {
      case (where, arguments) if arguments.split(",")
        .exists(_.trim == "_") => where
    }.sorted

  test("a parked-decision query pattern binds no wildcard"):
    val roots = Vector(Paths.get("src/main/scala"),
      Paths.get("frontend/src/main/scala"), Paths.get("shared/src/main/scala"),
      Paths.get("src/test/scala"), Paths.get("frontend/src/test/scala"),
      Paths.get("shared/src/test/scala"))
    assertEquals(wildcardQueryPatterns(roots), Vector.empty)
```

- [ ] **Step 2: Run it**

```bash
./sbtw "testOnly oathdigital.gameplay.BackendArchitectureSuite"
```

Expected: PASS. A failure lists `path:line` for each site Task 1 missed — migrate those, then re-run.

- [ ] **Step 3: Prove the rule bites**

Temporarily reintroduce one violation:

```bash
python3 - <<'EOF'
from pathlib import Path
p = Path("src/test/scala/oathdigital/testkit/PlacementPicks.scala")
t = p.read_text()
t = t.replace("case one: DecisionQuery.ChooseOne => one.options",
              "case DecisionQuery.ChooseOne(options, _, _) => options", 1)
p.write_text(t)
EOF
./sbtw "testOnly oathdigital.gameplay.BackendArchitectureSuite"
```

Expected: FAIL, naming `src/test/scala/oathdigital/testkit/PlacementPicks.scala` and its line. A pass here means the matcher never reached test sources — fix it before continuing.

- [ ] **Step 4: Revert the deliberate violation**

```bash
git checkout -- src/test/scala/oathdigital/testkit/PlacementPicks.scala
./sbtw "testOnly oathdigital.gameplay.BackendArchitectureSuite"
```

Expected: PASS again.

- [ ] **Step 5: Write the pinning rule**

Append immediately after the first test, so the pair reads as one intent:

```scala
  // The two wire-facing sites are the exception the rule above allows and
  // this test requires. ActionProjectionCodec binds every field of every
  // form on the way to and from the wire, and WalkerDecisionProjector
  // binds every field it carries onto that wire. Their arity coupling is
  // the only thing that fails the build when a new field reaches neither:
  // confirmVerb was caught by exactly these two. Converting either to a
  // typed bind removes a build check; it is not tidying.
  // DecisionQueries is deliberately absent — accepts must stay blind to
  // copy (Decisions.scala:435-438).
  private val PinnedQueryReaders = Vector(
    "shared/src/main/scala/oathdigital/protocol/projection/" +
      "ActionProjectionCodec.scala",
    "src/main/scala/oathdigital/application/WalkerDecisionProjector.scala")

  test("the wire-facing query patterns stay fully bound"):
    val roots = PinnedQueryReaders.map(Paths.get(_))
    val patterns = queryPatterns(roots)
    assert(patterns.sizeIs >= 12,
      s"expected the pinned readers' patterns, found ${patterns.size}")
    assertEquals(wildcardQueryPatterns(roots), Vector.empty)
```

`scalaFilesUnder` walks a file path as well as a directory, so passing the
two files directly works. The `sizeIs >= 12` floor is the seven patterns in
`ActionProjectionCodec` plus the five in `WalkerDecisionProjector`; it fails
if a pinned file is renamed out from under the allowlist, which an emptied
`assertEquals` alone would not catch.

- [ ] **Step 6: Run it**

```bash
./sbtw "testOnly oathdigital.gameplay.BackendArchitectureSuite"
```

Expected: PASS, both tests.

- [ ] **Step 7: Prove the pinning rule bites**

```bash
python3 - <<'EOF'
from pathlib import Path
p = Path("src/main/scala/oathdigital/application/WalkerDecisionProjector.scala")
t = p.read_text()
t = t.replace("case DecisionQuery.ChooseOne(options, heading, confirmVerb) =>",
              "case DecisionQuery.ChooseOne(options, heading, _) =>", 1)
p.write_text(t)
EOF
./sbtw "testOnly oathdigital.gameplay.BackendArchitectureSuite"
```

Expected: FAIL on the pinning test, naming `WalkerDecisionProjector.scala:278`.
(The compile may fail first on the now-unused `confirmVerb` reference under
`-Werror`; that is also a pass for this step's purpose — the field cannot be
dropped silently. Either outcome proves the pin.)

- [ ] **Step 8: Revert and run the whole gate**

```bash
git checkout -- src/main/scala/oathdigital/application/WalkerDecisionProjector.scala
./sbtw test frontend/test 2>&1 | tail -20
python3 scripts/check-architecture.py
```

Expected: all suites pass with the Task 1 test count plus two; the architecture script passes.

- [ ] **Step 9: Commit**

```bash
git add src/test/scala/oathdigital/gameplay/BackendArchitectureSuite.scala
git commit -F - <<'MSG'
test(architecture): a query pattern binds no wildcard

Two rules, adjacent so they read as one intent. The first forbids a `_`
placeholder in any pattern on a DecisionQuery or DecisionQueryProjection
subtype, across production and test sources; 42 of the 52 sites it governs
were in test code, which scripts/check-architecture.py does not read. The
second pins ActionProjectionCodec and WalkerDecisionProjector fully bound,
because their arity coupling is the check that fails when a new field
reaches neither the wire nor the mapping onto it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
MSG
```
