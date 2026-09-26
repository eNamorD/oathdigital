# Typed Decision Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the flat twelve-field `DecisionQueryProjection` with a sealed trait carrying one case per form, so the question half of the parked-decision wire holds the same closed vocabulary the answer half already holds. Delete the frontend's `DecisionForm`. Stop the three draft reconcilers comparing raw form strings. Turn a form this client does not know from a silently blank action pane into a visible out-of-date notice with a Reload button.

**Architecture:** `DecisionQueryProjection` becomes `sealed trait` + six cases in `shared/src/main`, with `heading` and `offeredOptions` abstract on the trait and every other field per case. `ActionProjectionCodec` discriminates on `"form"` with a per-case `exact` field set. `WalkerDecisionProjector` keeps its six-arm match over the model's `DecisionQuery` and constructs a case. The frontend routes by matching the wire type: `ParkedDecision.Surface` cases carry narrowed queries, the panels read per-case fields, and `SessionDrafts.reconcile` stays the one place that decides which draft a parked decision gets. An unknown discriminator becomes `ProtocolDecodeFailure.UnknownVariant`, then `GameClientFailure.UnsupportedProjection`; `TableSession` owns the out-of-date decision and exposes a flag, `TableScreen` only renders.

**Tech Stack:** Scala 3.9.0, Scala.js 1.22.0, munit, ujson 4.4.3 on both platforms, jsdom for the frontend suite. Build through `./sbtw`.

**Spec:** `docs/superpowers/specs/2026-09-25-typed-decision-form-design.md`, with the companion
[decision form tag separation decision](../specs/2026-09-25-decision-form-tag-separation-decision.md).

## Global Constraints

- **Two commits, not six.** Task 1 is commit 1. Tasks 2 to 6 are commit 2: the shared type reaches the backend, the frontend and their tests together, and `-Werror` means the build does not compile in between. Only Task 6 commits. Run the gates named in each task where they can run; where they cannot, the task says so.
- Unchanged, and to be checked in the diff: the model's `DecisionQuery`; `DecisionAnswerWire` and `CommandNestedCodecs`; `DecisionAnswerCodec` and its eight journal tag constants; the durable journal format; every projected string and label; option and section order; which surface a form reaches; rendered DOM.
- The six form spellings stay `choose-one`, `choose-many`, `choose-amount`, `partition`, `distribute`, `negotiate`, and the discriminator key stays `"form"` — deliberately not the `"kind"` its two neighbours in `ActionProjectionCodec` use, because **form** is the word `CONTEXT.md` gives the concept. Say so in the codec's doc comment so a later reader does not "fix" it.
- Per-case `exact` sets are what makes the strictness real. Each case's `exact` set is `"form"` plus exactly the keys that case's `encode` writes, and nothing else.
- `shared/src/main` may import only `oathdigital.protocol` (`scripts/check-architecture.py`). The new cases need nothing else; do not reach for a `model` type.
- `-Werror` with `-Wunused:imports,privates,locals,implicits,nowarn` on both projects: every match over the new trait must be total at the point of the commit, and an import or private member the retyped code no longer uses fails the build. Treat the compiler as the authority on the edit list — the file lists below are what inspection found and are not guaranteed complete.
- Production files stay under 800 lines (`scripts/check-architecture.py`). `ActionProjectionDtos.scala` is 298 and `ActionProjectionCodec.scala` is 336 today; the codec grows most, so extract the section and slot encode/decode helpers rather than inlining them six times.
- Commit trailer: the committing model's own `Co-Authored-By` line.
- Never commit `node_modules` or `.tooling` symlinks; stage explicit paths only.
- Record the baseline test counts from the first full run of Task 1 and use them as the expected counts in later tasks. Do not invent counts.

### Amendments this plan makes to the spec

1. **The agreement test is a pair over one test-only table, not a single test.** No compilation unit sees both sides: `src/test` is JVM and cannot see `oathdigital.frontend`; `frontend/src/test` is Scala.js and cannot see `oathdigital.application`; `shared/src/test/scala` is a test source directory of *both* (`build.sbt:40`, `build.sbt:268`) and so must compile against neither. That impossibility is the defect the slice removes. Until then the two sides agree through a test-only table in `shared/src/test`, which is deliberately not shared with `DecisionAnswerCodec`'s journal tags.
2. **Two more suites need logic edits** than the spec's Verification section lists: `AlchemistSuite` (`src/test/.../powers/action/AlchemistSuite.scala:66-70` asserts `query.form`, `query.slots` and `(query.minTotal, query.maxTotal)` on the projection) and `DicePowerDecisionProjectionSuite` (`:36-53` reads `projection.query.get.options`, which the trait renames to `offeredOptions`).
3. **`Surface.Selection` loses its `form` field.** It existed only to remember which of the two forms the query was; the query's own type now says. `Surface.Selection(decision, query)` where `query: SelectionForm`.
4. **Two confirm-label call sites become literals.** `partitionConfirmLabel(query)` is `query.confirmLabel.getOrElse("Confirm")`, and it is called today on a choose-many (`WalkerSelectionPanels.renderMany`) and on a choose-one (`WalkerPanelSupport.renderBoardPanel`) — neither of which the projector ever gives a confirm label. Under the new type those two read the literal `"Confirm"`, which is byte-identical output. `DistributePanelRenderer` reads the required `String` directly.
5. **The encoded JSON of a query loses the null keys its form does not declare.** Today every query writes all twelve keys, ten of them `null` for a choose-one. This is a wire change in bytes, not in meaning, and no other client exists: `build.sbt:21-38` links the frontend into the server's own resources. The spec's "no correct payload changes" is about semantics; state the byte change plainly in commit 2's message.

---

### Task 1: The vocabulary pin, against the untyped code

Commit 1. It must be able to fail today, which is why it is separate and first: nothing currently asserts that the set of forms the projector emits equals the set the frontend recognises.

**Files:**
- Create: `shared/src/test/scala/oathdigital/protocol/DecisionFormVocabulary.scala`
- Create: `frontend/src/test/scala/oathdigital/frontend/DecisionFormAgreementSuite.scala`
- Modify: `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala` (one new test)

**Interfaces:**
- Consumes: `WalkerDecisionProjector` through the existing `parked(action)` / `projectorFor(tree)` seam in `WalkerDecisionProjectorSuite`; `ParkedDecision.DecisionForm.parse`.
- Produces: `object DecisionFormVocabulary` with the six spellings and `All: Set[String]`, visible to both platforms' test configurations.

- [ ] **Step 1: The shared table**

Create `shared/src/test/scala/oathdigital/protocol/DecisionFormVocabulary.scala`:

```scala
package oathdigital.protocol

/** The projection wire's form vocabulary, as data, for the one commit in
  * which it is not a type.
  *
  * Scaffolding. The typed `DecisionQueryProjection` replaces it in the next
  * commit, where agreement between the projector and the client is
  * structural, and this table is deleted with the last reader.
  *
  * It lives here because no single compilation unit sees both sides:
  * `src/test` cannot see `oathdigital.frontend`, `frontend/src/test` cannot
  * see `oathdigital.application`, and `shared/src/test/scala` -- a test
  * source directory of both projects -- must compile against neither. That
  * is the defect: today the two vocabularies can only agree by convention.
  * Until the type exists they agree through this table, and adding a seventh
  * form means adding it here, which fails whichever side has not learnt it.
  *
  * Deliberately NOT the journal's answer tags. `DecisionAnswerCodec` spells
  * five of these strings for durable rows and must keep spelling them
  * independently; see
  * `docs/superpowers/specs/2026-09-25-decision-form-tag-separation-decision.md`.
  */
object DecisionFormVocabulary:
  val ChooseOne = "choose-one"
  val ChooseMany = "choose-many"
  val ChooseAmount = "choose-amount"
  val Partition = "partition"
  val Distribute = "distribute"
  val Negotiate = "negotiate"

  val All: Set[String] = Set(ChooseOne, ChooseMany, ChooseAmount, Partition,
    Distribute, Negotiate)
```

- [ ] **Step 2: The frontend half**

Create `frontend/src/test/scala/oathdigital/frontend/DecisionFormAgreementSuite.scala`:

```scala
package oathdigital.frontend

import oathdigital.protocol.DecisionFormVocabulary
import ParkedDecision.DecisionForm

/** The client half of the vocabulary pin: the set of forms this client
  * recognises is exactly the set the projector emits. One set equality, so
  * it fails in both directions -- a form the projector emits that this
  * client parses as `Unknown`, and a case of `DecisionForm` no projected
  * form reaches.
  *
  * Deleted with `DecisionForm` in the next commit.
  */
class DecisionFormAgreementSuite extends munit.FunSuite:
  test("this client recognises exactly the projector's forms"):
    assertEquals(DecisionFormVocabulary.All.map(DecisionForm.parse),
      Set[DecisionForm](DecisionForm.ChooseOne, DecisionForm.ChooseMany,
        DecisionForm.ChooseAmount, DecisionForm.Partition,
        DecisionForm.Distribute, DecisionForm.Negotiate))

  test("a form outside the vocabulary keeps its spelling as unknown"):
    assertEquals(DecisionForm.parse("choose-two"),
      DecisionForm.Unknown("choose-two"))
```

`DecisionForm` has a parameterized case (`Unknown(raw)`), so Scala 3 generates no `values` for it; the six cases are listed by hand, which is the whole reason this is a pin and not a property.

- [ ] **Step 3: The backend half**

Add one test to `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala`, after `"a parked choose-amount projects its bounds and confirm label"`. Add `import oathdigital.protocol.DecisionFormVocabulary` to the file's imports; everything else it needs comes from the existing `oathdigital.model._`.

```scala
  /** The projector half of the vocabulary pin: every shape the model can
    * declare projects a form in `DecisionFormVocabulary`, and between them
    * the six shapes emit all of it. Deleted with the untyped `form` field in
    * the next commit, where the case set IS the vocabulary.
    */
  test("the projector emits exactly the shared form vocabulary"):
    val (context, actor) = parked(ActionRef.Recover)
    val siteIds = context.ready.game.current.map.sites.keys.toVector.take(2)
    val options = siteIds.map(id => DecisionOption.Site(DecisionOptionRef.Site(id)))
    val queries: Vector[DecisionQuery] = Vector(
      DecisionQuery.ChooseOne(options),
      DecisionQuery.ChooseMany(1, 2, options),
      DecisionQuery.ChooseAmount(0, 1, Some("Amount"), "Confirm"),
      DecisionQuery.Partition(
        Vector(DecisionSection("keep", "Keep", 1, Some(1)),
          DecisionSection("rest", "Rest", 0)),
        options, Some("Split")),
      DecisionQuery.Distribute.exactly(
        Vector(DistributeSlot(DecisionOptionRef.FavorBank(Suit.Arcane), 0, 1, None)),
        total = 1, heading = Some("Spread"), confirmLabel = "Place"),
      DecisionQuery.Negotiate(Vector(actor), Map.empty, Set.empty, Map.empty,
        Set(actor), Some("Deal")))
    val forms = queries.flatMap(query =>
      projectorFor(Sequence(Decide("test.form", actor, query)))
        .project(context).flatMap(_.query).map(_.form))
    assertEquals(forms.size, queries.size)
    assertEquals(forms.toSet, DecisionFormVocabulary.All)
```

If the `Negotiate` arm cannot be projected from empty terms and bounds, do not weaken the assertion by deleting a form: drop that one entry, assert
`assertEquals(forms.toSet + DecisionFormVocabulary.Negotiate, DecisionFormVocabulary.All)`,
and add a comment pointing at `NegotiationDealProjectionSuite:46`, which already pins `Some("negotiate")`. Record which branch you took — Task 3 deletes this test either way.

- [ ] **Step 4: Run both suites**

Run: `./sbtw "testOnly oathdigital.application.WalkerDecisionProjectorSuite" "frontend/testOnly oathdigital.frontend.DecisionFormAgreementSuite"`
Expected: green. If the frontend assertion fails, the vocabularies already disagree — stop and report it; that is a live defect, not a test bug.

- [ ] **Step 5: Run the full gate and record the baseline**

Run: `./sbtw "test" "frontend/test"` then `python3 scripts/check-architecture.py` and `python3 scripts/check-markdown-links.py`
Expected: all green. Write the backend and frontend test counts into the task notes; later tasks compare against them.

- [ ] **Step 6: Commit**

```bash
git add shared/src/test/scala/oathdigital/protocol/DecisionFormVocabulary.scala frontend/src/test/scala/oathdigital/frontend/DecisionFormAgreementSuite.scala src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala
git commit -m "test(protocol): pin the decision form vocabulary on both sides

Nothing asserted that the set of forms the projector emits equals the set
the frontend recognises: a seventh form would have projected, parsed as
Unknown, rendered a blank action pane and left the suite green. The two
sides now agree through one test-only table, because no compilation unit
sees both -- which is the defect the typed wire removes next.

Co-Authored-By: <the committing model's trailer>"
```

---

### Task 2: The shared type, its codec, and the unknown-discriminator failure

Commit 2 begins here. **Do not commit at the end of this task.** After it, `shared/src/main` compiles and `src/main` and `frontend/src/main` do not.

**Files:**
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala` (`:70-106`: the doc comment and the flat record)
- Modify: `shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala` (`:167-232`: `encodeDecisionQuery`/`decodeDecisionQuery`)
- Modify: `shared/src/main/scala/oathdigital/protocol/CommandProtocol.scala` (`:5-21`: one new case)
- Modify: `shared/src/test/scala/oathdigital/protocol/ProjectionProtocolSuite.scala` (the fixture at `:62-68`, the tests at `:97`, `:134`, `:152`, `:171`, `:186`, `:198`, and two new rejection tests)

**Interfaces:**
- Consumes: `DecisionOptionProjection`, `DecisionSectionProjection`, `DecisionSlotProjection`, `NegotiationDealProjection`, and `ProjectionCodecSupport`'s helpers.
- Produces: `sealed trait DecisionQueryProjection` with `heading` and `offeredOptions`, six cases, a discriminated codec, and `ProtocolDecodeFailure.UnknownVariant`.

- [ ] **Step 1: The trait and its six cases**

In `ActionProjectionDtos.scala`, replace the `final case class DecisionQueryProjection(...)` at `:94-106` with the trait and its object. Follow `SiteForcesProjection` (`WorldProjectionDtos.scala:36-47`): common fields abstract on the trait, per-case field lists, bare case names namespaced by the trait.

```scala
sealed trait DecisionQueryProjection extends Product with Serializable:
  /** The one piece of copy every form carries, and for the same reason the
    * model's `DecisionQuery` declares it on its own trait: every shape has a
    * frame to title, and a client handed none falls back to generic copy.
    */
  def heading: Option[String]

  /** Every option the question puts in front of the player, wherever the
    * form keeps them. A distribute query keeps each option inside a slot, and
    * `GameProjection.offeredCards` used to know that; as a question every
    * form answers, the caller no longer does.
    */
  def offeredOptions: Vector[DecisionOptionProjection]

object DecisionQueryProjection:
  /** Pick exactly one option. No confirm label: the answer submits on the
    * click, so there is no confirm step to name.
    */
  final case class ChooseOne(options: Vector[DecisionOptionProjection],
      heading: Option[String] = None) extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = options

  /** Pick between `minOptions` and `maxOptions` of the options. The bounds
    * count OPTIONS; `ChooseAmount`'s bound a value, which is why they are no
    * longer one `minimum`/`maximum` pair serving both.
    */
  final case class ChooseMany(options: Vector[DecisionOptionProjection],
      minOptions: Int, maxOptions: Int, heading: Option[String] = None)
      extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = options

  /** Pick an integer from `minAmount` to `maxAmount`. No options: the range
    * is the question. `suggested` is where the panel opens.
    */
  final case class ChooseAmount(minAmount: Int, maxAmount: Int,
      suggested: Option[Int], confirmLabel: String,
      heading: Option[String] = None) extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = Vector.empty

  /** Spread every option across the declared sections. The one form whose
    * confirm label is optional.
    */
  final case class Partition(sections: Vector[DecisionSectionProjection],
      options: Vector[DecisionOptionProjection],
      confirmLabel: Option[String] = None, heading: Option[String] = None)
      extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = options

  /** Assign amounts across the slots, summing to between `minTotal` and
    * `maxTotal`. Every option it offers sits on a slot.
    */
  final case class Distribute(slots: Vector[DecisionSlotProjection],
      minTotal: Int, maxTotal: Int, confirmLabel: String,
      heading: Option[String] = None) extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = slots.map(_.option)

  /** A deal, as the viewer may see it. The deal is not optional here: the
    * projector always attaches one, and the old `Option` meant a negotiate
    * query with nothing to negotiate could be built.
    */
  final case class Negotiate(deal: NegotiationDealProjection,
      heading: Option[String] = None) extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = Vector.empty
```

Rewrite the doc comment at `:70-93`. What it currently documents in prose — which form carries sections, which carries slots, that a choose-one never carries a confirm label — the type now says, so delete those sentences rather than repeating them. Keep the two paragraphs that the type does *not* say: that there is deliberately no prebuilt wire answer on an option, and that panel copy is two optional strings and not the start of a form language.

- [ ] **Step 2: The unknown-discriminator failure**

In `CommandProtocol.scala`, after `InvalidValue`:

```scala
  /** A discriminated wire type carrying a variant this build does not know.
    *
    * Distinct from `InvalidValue` because the cause is the vocabulary, not
    * the value: the sender knows a form, a kind or a type this reader was not
    * built with. For a projection that means the client is out of date, which
    * a caller can act on; a bad value means the payload is wrong, which it
    * cannot.
    */
  final case class UnknownVariant(path: String, message: String)
      extends ProtocolDecodeFailure
```

Nothing matches exhaustively on `ProtocolDecodeFailure` (`GameHttpWire.inputError` reads `path` and `message` off the trait), so this addition compiles everywhere. Leave `CommandNestedCodecs`' `InvalidValue` for an unknown answer kind alone: the answer half is outside this slice's boundary.

- [ ] **Step 3: The discriminated codec**

In `ActionProjectionCodec.scala`, replace `encodeDecisionQuery`/`decodeDecisionQuery` (`:167-232`). Extract the section and slot rows, which are inlined today, so each is written once rather than per case.

```scala
  /** A projected decision query: the `form` discriminator and, per form,
    * exactly the fields that form declares. A payload carrying a field its
    * own form does not declare is rejected rather than ignored, which is what
    * the per-case `exact` sets are for.
    *
    * The discriminator key is `form`, not the `kind` its two neighbours in
    * this file use. Deliberate: **form** is the word `CONTEXT.md` gives the
    * concept, and the project has already paid to have it mean one thing.
    *
    * An option's `kind`/`id` pair, including one embedded in a slot, is the
    * same spelling a submitted and a journalled answer use, so this codec
    * writes no table of its own -- it copies the two strings through. The six
    * form spellings are this wire's own and are deliberately not shared with
    * `DecisionAnswerCodec`'s journal tags; see
    * `docs/superpowers/specs/2026-09-25-decision-form-tag-separation-decision.md`.
    */
  def encodeDecisionQuery(value: DecisionQueryProjection): ujson.Value =
    value match
      case DecisionQueryProjection.ChooseOne(options, heading) => ujson.Obj(
        "form" -> "choose-one",
        "options" -> encoded(options)(encodeOptionRow),
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.ChooseMany(options, minOptions, maxOptions,
          heading) => ujson.Obj(
        "form" -> "choose-many",
        "options" -> encoded(options)(encodeOptionRow),
        "minOptions" -> minOptions, "maxOptions" -> maxOptions,
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.ChooseAmount(minAmount, maxAmount,
          suggested, confirmLabel, heading) => ujson.Obj(
        "form" -> "choose-amount",
        "minAmount" -> minAmount, "maxAmount" -> maxAmount,
        "suggested" -> intOption(suggested),
        "confirmLabel" -> confirmLabel,
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.Partition(sections, options, confirmLabel,
          heading) => ujson.Obj(
        "form" -> "partition",
        "sections" -> encoded(sections)(encodeSection),
        "options" -> encoded(options)(encodeOptionRow),
        "confirmLabel" -> stringOption(confirmLabel),
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.Distribute(slots, minTotal, maxTotal,
          confirmLabel, heading) => ujson.Obj(
        "form" -> "distribute",
        "slots" -> encoded(slots)(encodeSlot),
        "minTotal" -> minTotal, "maxTotal" -> maxTotal,
        "confirmLabel" -> confirmLabel,
        "heading" -> stringOption(heading))
      case DecisionQueryProjection.Negotiate(deal, heading) => ujson.Obj(
        "form" -> "negotiate",
        "deal" -> encodeDeal(deal),
        "heading" -> stringOption(heading))

  def decodeDecisionQuery(raw: ujson.Value, path: String)
      : Result[DecisionQueryProjection] = for
    value <- obj(raw, path)
    form <- string(value, "form", path)
    query <- (form match
      case "choose-one" => for
        _ <- exact(value, Set("form", "options", "heading"), path)
        options <- optionRows(value, path)
        heading <- optionalString(value, "heading", path)
      yield DecisionQueryProjection.ChooseOne(options, heading)
      case "choose-many" => for
        _ <- exact(value, Set("form", "options", "minOptions", "maxOptions",
          "heading"), path)
        options <- optionRows(value, path)
        minOptions <- int(value, "minOptions", path)
        maxOptions <- int(value, "maxOptions", path)
        heading <- optionalString(value, "heading", path)
      yield DecisionQueryProjection.ChooseMany(options, minOptions, maxOptions,
        heading)
      case "choose-amount" => for
        _ <- exact(value, Set("form", "minAmount", "maxAmount", "suggested",
          "confirmLabel", "heading"), path)
        minAmount <- int(value, "minAmount", path)
        maxAmount <- int(value, "maxAmount", path)
        suggested <- optionalInt(value, "suggested", path)
        confirmLabel <- string(value, "confirmLabel", path)
        heading <- optionalString(value, "heading", path)
      yield DecisionQueryProjection.ChooseAmount(minAmount, maxAmount,
        suggested, confirmLabel, heading)
      case "partition" => for
        _ <- exact(value, Set("form", "sections", "options", "confirmLabel",
          "heading"), path)
        sectionRaws <- array(value, "sections", path)
        sections <- traverse(sectionRaws, s"$path.sections")(decodeSection)
        options <- optionRows(value, path)
        confirmLabel <- optionalString(value, "confirmLabel", path)
        heading <- optionalString(value, "heading", path)
      yield DecisionQueryProjection.Partition(sections, options, confirmLabel,
        heading)
      case "distribute" => for
        _ <- exact(value, Set("form", "slots", "minTotal", "maxTotal",
          "confirmLabel", "heading"), path)
        slotRaws <- array(value, "slots", path)
        slots <- traverse(slotRaws, s"$path.slots")(decodeSlot)
        minTotal <- int(value, "minTotal", path)
        maxTotal <- int(value, "maxTotal", path)
        confirmLabel <- string(value, "confirmLabel", path)
        heading <- optionalString(value, "heading", path)
      yield DecisionQueryProjection.Distribute(slots, minTotal, maxTotal,
        confirmLabel, heading)
      case "negotiate" => for
        _ <- exact(value, Set("form", "deal", "heading"), path)
        dealRaw <- field(value, "deal", path)
        deal <- decodeDeal(dealRaw, s"$path.deal")
        heading <- optionalString(value, "heading", path)
      yield DecisionQueryProjection.Negotiate(deal, heading)
      case other => Left(oathdigital.protocol.ProtocolDecodeFailure
        .UnknownVariant(s"$path.form", s"unknown decision form '$other'"))
    ): Result[DecisionQueryProjection]
  yield query

  private def optionRows(value: ujson.Obj, path: String)
      : Result[Vector[DecisionOptionProjection]] =
    array(value, "options", path)
      .flatMap(traverse(_, s"$path.options")(decodeOptionRow))

  private def encodeSection(section: DecisionSectionProjection): ujson.Value =
    ujson.Obj("key" -> section.key, "label" -> section.label,
      "minRequired" -> section.minRequired,
      "maxAllowed" -> intOption(section.maxAllowed))

  private def decodeSection(raw: ujson.Value, path: String)
      : Result[DecisionSectionProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("key", "label", "minRequired", "maxAllowed"), path)
    key <- string(row, "key", path); label <- string(row, "label", path)
    minimum <- int(row, "minRequired", path)
    maximum <- optionalInt(row, "maxAllowed", path)
  yield DecisionSectionProjection(key, label, minimum, maximum)

  private def encodeSlot(slot: DecisionSlotProjection): ujson.Value =
    ujson.Obj("option" -> encodeOptionRow(slot.option),
      "minimum" -> slot.minimum, "maximum" -> slot.maximum,
      "suggested" -> intOption(slot.suggested))

  private def decodeSlot(raw: ujson.Value, path: String)
      : Result[DecisionSlotProjection] = for
    row <- obj(raw, path)
    _ <- exact(row, Set("option", "minimum", "maximum", "suggested"), path)
    option <- field(row, "option", path).flatMap(
      decodeOptionRow(_, s"$path.option"))
    minimum <- int(row, "minimum", path)
    maximum <- int(row, "maximum", path)
    suggested <- optionalInt(row, "suggested", path)
  yield DecisionSlotProjection(option, minimum, maximum, suggested)
```

Two notes on the helpers: `optionalString`/`optionalInt` require the key to be *present* (possibly `null`), and every `encode` arm above writes the keys its `exact` set lists, so they agree. The `: Result[DecisionQueryProjection]` ascription on the match is the same belt-and-braces `WorldProjectionCodec.decodeForces` uses.

- [ ] **Step 4: Retype the shared suite**

In `ProjectionProtocolSuite.scala`:

- The populated fixture (`:62-68`) becomes `DecisionQueryProjection.Partition(sections, options, confirmLabel = Some("Complete Forge"), heading = Some("Forge a relic"))` — note the argument order changed, sections first. Delete the now-false comment at `:57-60` ("A choose-one query is the same type with no sections, so this one round-trip covers both"): it no longer is, and each form now has its own round trip.
- `"a decision query declaring no panel copy round-trips as absent"` (`:97`) becomes a `ChooseOne` with no heading. Drop `assertEquals(bare.confirmLabel, None)`: a choose-one has no such field to assert about, which is the improvement.
- `:134` `"a choose-one option round-trips its details…"`, `:152` negotiate, `:171` choose-many and choose-amount, `:186` and `:198` distribute: each constructs its case with per-case fields. `minimum`/`maximum` become `minOptions`/`maxOptions` on the choose-many and `minAmount`/`maxAmount` on the choose-amount; `minTotal`/`maxTotal` become plain `Int`s; `deal` becomes a plain value.
- Add one round trip for each of the six forms if the rewrite above leaves any uncovered, so the per-case codec has per-case coverage.

Then add the rejection tests the new strictness earns, following the `rejected(edit)` pattern already in the file at `:234-243`:

```scala
  /** The types rule out a form outside the vocabulary and a field one form
    * lends another, so only JSON from outside can carry either. The fixture's
    * query is a partition, so `minTotal` is a distribute field it must refuse
    * and `sections` is one it must require.
    */
  test("the decoder refuses an unknown form and a field the form does not declare"):
    def rejected(edit: ujson.Value => Unit): Option[ProtocolDecodeFailure] =
      val json = ujson.read(GameProjectionCodec.encode(projection))
      edit(json)
      GameProjectionCodec.decode(ujson.write(json)).left.toOption
    val unknown = rejected(_("walkerDecision")("query")("form") = "choose-two")
    assertEquals(unknown.map(_.path), Some("$.walkerDecision.query.form"))
    assert(clue(unknown).exists(_.isInstanceOf[ProtocolDecodeFailure.UnknownVariant]))
    assertEquals(rejected(_("walkerDecision")("query")("minTotal") = 3).map(_.path),
      Some("$.walkerDecision.query.minTotal"))
    assertEquals(
      rejected(_("walkerDecision")("query").obj.remove("sections")).map(_.path),
      Some("$.walkerDecision.query.sections"))
```

If `.obj` is not available on the ujson value in this version, reach the map as `json("walkerDecision")("query").asInstanceOf[ujson.Obj].value.remove("sections")`.

- [ ] **Step 5: Compile the shared tree**

Run: `./sbtw "Test/compile"`
Expected: `shared` and its suite compile; `src/main` fails on `WalkerDecisionProjector` and `GameProjection`, which Task 3 fixes. This is the declared red window — record the failures and move on rather than patching call sites out of order.

---

### Task 3: The projector and `offeredCards`

Still commit 2. No commit at the end.

**Files:**
- Modify: `src/main/scala/oathdigital/application/WalkerDecisionProjector.scala` (`:214-244`)
- Modify: `src/main/scala/oathdigital/application/GameProjection.scala` (`:143-147`)
- Modify: `src/test/scala/oathdigital/application/WalkerDecisionProjectorSuite.scala` (delete the Task 1 pin; retype `:134`, `:162`, `:173` and the field reads at `:136-141`, `:152`, `:175`, `:435`)
- Modify: `src/test/scala/oathdigital/application/WalkerDecisionProjectionSuite.scala` (`:72`, `:236` constructions; `:260`, `:363`, `:368`, `:373` reads)
- Modify: `src/test/scala/oathdigital/application/PhasePowerProjectorSuite.scala` (`:131`, `:144`)
- Modify: `src/test/scala/oathdigital/application/NegotiationDealProjectionSuite.scala` (`:46`)
- Modify: `src/test/scala/oathdigital/application/GameServerGatewaySubmitBeginSuite.scala` (`:81`)
- Modify: `src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala` (`:856`, `:859`)
- Modify: `src/test/scala/oathdigital/application/DicePowerDecisionProjectionSuite.scala` (`:36`, `:38`, `:51`, `:53`)
- Modify: `src/test/scala/oathdigital/gameplay/powers/action/AlchemistSuite.scala` (`:66-70`)

**Interfaces:**
- Consumes: the new trait from Task 2; the model's `DecisionQuery`, unchanged.
- Produces: a projector that constructs cases; `offeredCards` reading `offeredOptions`.

- [ ] **Step 1: Construct cases in the projector**

Replace the six arms of `queryProjection` (`:214-244`). Keep the surrounding doc comment and the `described` helper exactly as they are — the all-or-nothing suppression rule, the label resolution, the `playable` narrowing and the per-viewer redaction are all unchanged, and none of them belongs on a wire type.

```scala
    query match
      case DecisionQuery.ChooseOne(options, heading) =>
        described(options).map(projected =>
          DecisionQueryProjection.ChooseOne(projected, heading))
      case DecisionQuery.ChooseMany(min, max, options, heading) =>
        described(options).map(projected =>
          DecisionQueryProjection.ChooseMany(projected, min, max, heading))
      case negotiate: DecisionQuery.Negotiate =>
        Some(DecisionQueryProjection.Negotiate(
          deals.project(ready, viewer, negotiate), negotiate.heading))
      case DecisionQuery.ChooseAmount(min, max, heading, confirmLabel,
          suggested) =>
        Some(DecisionQueryProjection.ChooseAmount(min, max, suggested,
          confirmLabel, heading))
      case DecisionQuery.Partition(sections, options, heading, confirmLabel) =>
        described(options).map(projected =>
          DecisionQueryProjection.Partition(
            sections.map(section => DecisionSectionProjection(section.key,
              section.label, section.minRequired, section.maxAllowed)),
            projected, confirmLabel, heading))
      case DecisionQuery.Distribute(slots, minTotal, maxTotal, heading,
          confirmLabel) =>
        described(slots.flatMap(slot => DecisionOption.forRef(slot.ref)))
          .filter(_.size == slots.size).map(options =>
            DecisionQueryProjection.Distribute(
              slots.zip(options).map { case (slot, option) =>
                DecisionSlotProjection(option, slot.minimum, slot.maximum,
                  slot.suggested) },
              minTotal, maxTotal, confirmLabel, heading))
```

Which form a given model query produces does not change. The `Some(...)`/`described(...).map(...)` split per arm does not change either.

- [ ] **Step 2: `offeredCards` through `offeredOptions`**

In `GameProjection.scala:143-147`:

```scala
  private def offeredCards(decision: WalkerDecisionProjection): Vector[String] =
    decision.query.toVector.flatMap { query =>
      val options = query.offeredOptions
      options.map(_.id) ++ options.flatMap(_.card).map(_.cardId)
    }
```

The `query.options ++ query.slots.map(_.option)` that knew distribute keeps its options inside slots is gone. Keep the doc comment above it; it is still exactly true.

- [ ] **Step 3: Delete the backend pin and retype the backend suites**

Delete the `"the projector emits exactly the shared form vocabulary"` test added in Task 1, and its `DecisionFormVocabulary` import. Say why in the commit message: after the type is shared the assertion is true by construction, and a test that cannot fail is worse than none.

Then let the compiler drive the rest. The mechanical shapes:

- `assertEquals(query.form, "distribute")` becomes a type assertion on the projected query. Prefer a pattern that also narrows for the following assertions:

  ```scala
      val query = projectorFor(tree).project(context).flatMap(_.query) match
        case Some(distribute: DecisionQueryProjection.Distribute) => distribute
        case other => fail(s"expected a distribution, got $other")
      assertEquals(query.minTotal -> query.maxTotal, 2 -> 2)
  ```

  This is stronger than the string it replaces: it proves the case *and* gives the per-case fields without an `Option` in the way. `WarningSignalsSuite:103-107` already uses this shape against the model's `DecisionQuery`, so it is house style.
- `assertEquals(..., Some("negotiate"))` and `assertEquals(decision.query.map(_.form), ...)` become a case test on the projected query.
- `(query.minimum, query.maximum)` becomes `(query.minOptions, query.maxOptions)` on a choose-many and `(query.minAmount, query.maxAmount)` on a choose-amount, both without `Some`.
- `(query.minTotal, query.maxTotal)` loses its `Some`s. `AlchemistSuite:70` is the one outside `application`.
- `query.confirmLabel` is a plain `String` on a choose-amount and a distribute, and stays `Option[String]` on a partition.
- `projection.query.get.options` in `DicePowerDecisionProjectionSuite` becomes `projection.query.get.offeredOptions`, which needs no narrowing and is the cheapest correct edit.
- `DecisionQueryProjection("choose-one", …)` constructions become `DecisionQueryProjection.ChooseOne(…)`.

Find every remaining site with:

```bash
grep -rn 'query\.form\|\.form ==\|DecisionQueryProjection(' src/main src/test
```

Expect zero matches when the task is done. The compiler is the authority on the rest.

- [ ] **Step 4: Compile the backend**

Run: `./sbtw "Test/compile"`
Expected: `src/main`, `src/test` and `shared` compile; `frontend` fails, which Task 4 fixes.

- [ ] **Step 5: Run the backend suite**

Run: `./sbtw "test"`
Expected: green at the Task 1 baseline minus one (the deleted pin). Any behavioural failure is a defect in the retype, not a test to adjust.

---

### Task 4: The frontend — the route, the drafts, the panels

Still commit 2. No commit at the end.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/ParkedDecision.scala` (delete `:38-54`, retype `:56-60`, `:80-104`, `:185-215`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/PartitionDecisionState.scala` (`:158`, `:216-232`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionDraft.scala` (`:14`, `:20`, `:22-23`, `:45-47`, `:63-85`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/DistributeDecisionState.scala` (`:74`, `:96-108`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerPanelSupport.scala` (`:133-146`, `:253-332`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/WalkerSelectionPanels.scala` (`:9`, `:16-70`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/DistributePanelRenderer.scala` (`:32-35`)
- Delete: `frontend/src/test/scala/oathdigital/frontend/DecisionFormAgreementSuite.scala`
- Delete: `shared/src/test/scala/oathdigital/protocol/DecisionFormVocabulary.scala`
- Modify: `frontend/src/test/scala/oathdigital/frontend/ParkedDecisionSuite.scala` (the `query(form)` helper at `:30`, the local `surface(form)` at `:48`, and the twenty-six literals that follow; delete the `DecisionForm.parse` assertion at `:71-72`)
- Modify: `frontend/src/test/scala/oathdigital/frontend/WalkerSelectionPanelsSuite.scala` (the form-branching helper at `:23`)
- Modify: `frontend/src/test/scala/oathdigital/frontend/SessionDraftsSuite.scala`, `WalkerSelectionDraftSuite.scala`, `PartitionDecisionStateSuite.scala`, `PartitionPanelRenderSuite.scala`, `DistributePanelRenderSuite.scala`, `WalkerChoicePanelRenderSuite.scala`, `RecoverPanelSuite.scala`, `CardChoicePanelSuite.scala`, `BoardSurfaceSuite.scala`, `SuitGlyphSuite.scala`, `ServerModeUiSuite.scala` (constructor changes)
- Possibly modify: `frontend/src/main/scala/oathdigital/frontend/WorldBoardRenderer.scala:168` — `Surface.Board`'s query is a `ChooseOne`, which still has `.options`, so this likely needs no edit. Confirm with the compiler rather than editing on spec.

**Interfaces:**
- Consumes: the new trait through the existing `DecisionQueryState` alias (`frontend.scala:52-53`). `DecisionQueryState.ChooseOne` works in type position through the `val` alias, exactly as `SiteForces.Exile` already does in `ServerUiSupport.scala:74`.
- Produces: `ParkedDecision.Surface` cases carrying narrowed queries; `SelectionForm` as a union of the two case types; three reconcilers that match the type.

- [ ] **Step 1: Delete `DecisionForm` and retype the route**

In `ParkedDecision.scala`, delete `enum DecisionForm` and `object DecisionForm` (`:38-54`) along with the doc comment above them, and narrow the rest:

```scala
  /** The two forms the selection panel answers: toggles for a choose-many,
    * a dropdown for a choose-amount. The query IS the form now, so the
    * surface carries one value rather than a query and a tag that could
    * disagree with it.
    */
  type SelectionForm =
    DecisionQueryState.ChooseMany | DecisionQueryState.ChooseAmount
```

`RecoverStep.Choice` and `.Relic` take `DecisionQueryState.ChooseOne`. `Surface.ChooseOne` and `Surface.Board` take `DecisionQueryState.ChooseOne`, `Surface.Partition` a `.Partition`, `Surface.Distribute` a `.Distribute`, and `Surface.Selection(decision, query: SelectionForm)` loses its third field. Update the `Surface` doc comment's first sentence if it names the form.

`chooseOneQuery` narrows by type:

```scala
  private def chooseOneQuery(decision: WalkerDecisionState)
      : Option[DecisionQueryState.ChooseOne] =
    decision.query.collect { case one: DecisionQueryState.ChooseOne => one }
```

`formSurface` matches the wire type, and the match is total — `-Werror` proves it, and the `Unknown` arm has nothing left to represent:

```scala
  private def formSurface(decision: WalkerDecisionState,
      query: DecisionQueryState, showGameplayControls: Boolean)
      : Option[Surface] =
    query match
      // Confirmed from the pane: a pawn is placed once a game and cannot be
      // moved back, so one click on a crowded board must not commit it.
      case one: DecisionQueryState.ChooseOne
          if decision.decisionId.startsWith(pawnPlacementDecisionIdPrefix) =>
        Some(Surface.Board(decision, one, confirm = true))
      // A Recover choose-one at a decision id Recover's panel does not know
      // is not handed to the generic panel either: there is no answer this
      // client could safely build for it.
      case _: DecisionQueryState.ChooseOne
          if decision.action == "recover" || decision.kind != "decide" =>
        None
      case one: DecisionQueryState.ChooseOne =>
        Some(Surface.ChooseOne(decision, one))
      case partition: DecisionQueryState.Partition =>
        Some(Surface.Partition(decision, partition))
      case distribute: DecisionQueryState.Distribute =>
        Some(Surface.Distribute(decision, distribute))
      case many: DecisionQueryState.ChooseMany =>
        Some(Surface.Selection(decision, many))
      case amount: DecisionQueryState.ChooseAmount =>
        Some(Surface.Selection(decision, amount))
      case negotiate: DecisionQueryState.Negotiate =>
        Some(Surface.Negotiate(negotiate.deal, negotiate.deal.editing
          .filter(_ => showGameplayControls).map(decision.decisionId -> _)))
```

The negotiate arm loses its `query.deal.map(...)`: the deal is no longer optional, so the branch where a negotiate query had no deal and therefore no surface is gone with the state that allowed it.

Update the object's own doc comment (`:5-20`): "a query whose `form` names the shape of answer it wants" and "re-testing the form" describe the world before this slice. What the route now does is choose a surface for a typed question.

- [ ] **Step 2: The three reconcilers stop comparing strings**

`WalkerPartitionDraft` (`PartitionDecisionState.scala`): the field becomes `query: DecisionQueryState.Partition`, and

```scala
    decision.flatMap(parked => parked.query.collect {
        case partition: DecisionQueryState.Partition =>
          parked.decisionId -> partition })
```

`WalkerDistributeDraft` (`DistributeDecisionState.scala`): the field becomes `query: DecisionQueryState.Distribute`, the reconcile `collect`s a `.Distribute`, and `query.minTotal.getOrElse(0)`/`query.maxTotal.getOrElse(0)` become `query.minTotal`/`query.maxTotal`.

`WalkerSelectionDraft`: the trait keeps `def query: DecisionQueryState` (each case overrides it with its own narrower type). `WalkerChooseManyDraft.query` becomes `DecisionQueryState.ChooseMany` with `minimum`/`maximum` reading `query.minOptions`/`query.maxOptions`; `WalkerAmountDraft.query` becomes `DecisionQueryState.ChooseAmount` with `query.minAmount`/`query.maxAmount`. The reconcile:

```scala
  def reconcile(previous: Option[WalkerSelectionDraft],
      context: BoardSelectionContext, decision: Option[WalkerDecisionState])
      : Option[WalkerSelectionDraft] =
    val asked: Option[(String, ParkedDecision.SelectionForm)] =
      decision.flatMap(parked => parked.query match
        case many: DecisionQueryState.ChooseMany =>
          Some(parked.decisionId -> many)
        case amount: DecisionQueryState.ChooseAmount =>
          Some(parked.decisionId -> amount)
        case _ => None)
    asked.map { case (decisionId, query) =>
      previous.filter(draft => draft.context == context &&
          draft.decisionId == decisionId && draft.query == query)
        .getOrElse(query match
          case many: DecisionQueryState.ChooseMany =>
            WalkerChooseManyDraft(context, decisionId, many, Vector.empty)
          case amount: DecisionQueryState.ChooseAmount =>
            // Where the question says to open, clamped to its own range so a
            // suggestion can never seed an illegal amount.
            WalkerAmountDraft(context, decisionId, amount,
              amount.suggested.fold(amount.minAmount)(value =>
                math.max(amount.minAmount,
                  math.min(amount.maxAmount, value)))))
    }
```

The explicit `Option[(String, SelectionForm)]` annotation is load-bearing: without it the two `Some`s infer their least upper bound, `DecisionQueryState`, and the union is lost. `parked.query` is an `Option`, so match its `Some`/`None` or `flatMap` through it — whichever reads better in the file.

`WalkerBoardDraft` needs no change: `parked.query.contains(draft.query)` still typechecks with `draft.query` widened to the trait. Leave it, and note it in the commit message so a reviewer does not go looking for the edit.

- [ ] **Step 3: The panels read per-case fields**

`WalkerPanelSupport`:
- `decisionHeading(query: DecisionQueryState)` is unchanged — `heading` is on the trait.
- `partitionConfirmLabel(query: DecisionQueryState.Partition): String` and `partitionConfirmLabel(query: DecisionQueryState.Partition, draft: WalkerPartitionDraft)`, `keepOne(query: DecisionQueryState.Partition)` and `partitionInstruction(query: DecisionQueryState.Partition)` all narrow to the only form that has sections or an optional confirm label.
- `renderBoardPanel`'s confirm button takes the literal `"Confirm"`, with a one-line comment: a board surface is a choose-one, and a choose-one declares no confirm label, so the old `getOrElse` fallback was always this string.

`WalkerSelectionPanels`:
- Drop `import ParkedDecision.DecisionForm`.
- `render` matches `surface.query` instead of `surface.form`:

  ```scala
      surface.query match
        case many: DecisionQueryState.ChooseMany => draft
          .collect { case value: WalkerChooseManyDraft => value }
          .foreach(renderMany(many, _, canControl, panel, controls))
        case amount: DecisionQueryState.ChooseAmount => draft
          .collect { case value: WalkerAmountDraft => value }
          .foreach(renderAmount(surface.decision, amount, _, canControl,
            panel, controls))
  ```
- `renderMany(query: DecisionQueryState.ChooseMany, …)`: the instruction line becomes
  `if query.minOptions == query.maxOptions then s"Choose ${query.minOptions}." else s"Choose ${query.minOptions} to ${query.maxOptions}."`,
  and the confirm button takes the literal `"Confirm"` for the same reason as the board panel.
- `renderAmount(…, query: DecisionQueryState.ChooseAmount, …)`: the dropdown range becomes `(query.minAmount to query.maxAmount)`.

`DistributePanelRenderer`:
- The minimum line becomes `Option.when(query.minTotal < query.maxTotal)(query.minTotal).foreach(…)`.
- The confirm button reads `query.confirmLabel` directly; it is a required `String` on a distribute.

Every one of these produces byte-identical DOM. Prove it with the panel suites rather than by reading.

- [ ] **Step 4: Delete the pin**

Delete `frontend/src/test/scala/oathdigital/frontend/DecisionFormAgreementSuite.scala` and, now that its last reader is gone, `shared/src/test/scala/oathdigital/protocol/DecisionFormVocabulary.scala`.

- [ ] **Step 5: Retype the frontend suites**

`ParkedDecisionSuite` is the largest: its `query(form: String)` helper becomes one constructor per form, and the local `surface(form: String)` inside `"each form routes to the surface that answers it"` becomes one assertion per form. The `Surface.Selection` assertions lose their `DecisionForm.ChooseMany`/`ChooseAmount` argument. `"an unknown form routes to no surface and keeps its spelling"` loses its `DecisionForm.parse` assertion; keep the test only if something is left to assert, and delete it outright otherwise — the unknown form is now refused at the codec, and `ProjectionProtocolSuite` asserts that.

`WalkerSelectionPanelsSuite:23`'s `if query.form == "choose-many" then DecisionForm.ChooseMany` helper disappears with the field it computed: the surface takes the query.

The other eleven suites are constructor changes. Find every site with:

```bash
grep -rn 'DecisionQueryState(\|DecisionQueryProjection(\|DecisionForm\|query\.form\|\.form ==' frontend/src/main frontend/src/test shared/src/test
```

Expect zero matches when the task is done.

- [ ] **Step 6: Compile and run the frontend suite**

Run: `./sbtw "frontend/test"`
Expected: green at the Task 1 baseline minus one (the deleted pin), with every panel suite passing unchanged in its assertions. A panel suite that needed its *expected DOM* edited is a preservation failure — stop and report it.

---

### Task 5: The out-of-date path

Still commit 2. No commit at the end.

**Files:**
- Modify: `frontend/src/main/scala/oathdigital/frontend/GameClient.scala` (`:68-92` the failure family and `isTransient`; `:220-222` `GameJson.decodeProjection`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableControls.scala` (`:34-41` `SessionControls`)
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableSession.scala` (`:9-13` `Navigation`, the state at `:31-39`, `accept` at `:107-123`, `loadExisting` and `reconnect`, the `SessionControls` and `TableView` blocks, and the companion)
- Modify: `frontend/src/main/scala/oathdigital/frontend/TableScreen.scala` (`:11-20` `TableView`, `:56-63` the failure notice)
- Modify: `frontend/src/main/scala/oathdigital/frontend/ServerModeUi.scala` (`BrowserNavigation`)
- Modify: `frontend/src/test/scala/oathdigital/frontend/TableSessionSuite.scala` (`RecordingNavigation`, three new tests)
- Possibly modify: `frontend/src/test/scala/oathdigital/frontend/ServerModeUiSuite.scala` if it builds a `Navigation` or asserts the failure notice

**Interfaces:**
- Consumes: `ProtocolDecodeFailure.UnknownVariant` from Task 2.
- Produces: `GameClientFailure.UnsupportedProjection`; `Navigation.reload()`; `SessionControls.reloadClient()`; `TableView.clientOutOfDate`.

- [ ] **Step 1: The client failure**

In `GameClient.scala`, add to `GameClientFailure`:

```scala
  /** A projection this build cannot parse: the server sent a variant of a
    * discriminated wire type this client does not know. The frontend ships
    * inside the server's own resources, so in normal operation there is no
    * skew window; what remains is a browser holding a stale cached bundle.
    */
  final case class UnsupportedProjection(path: String, detail: String)
      extends GameClientFailure:
    override val message: String = s"$path: $detail"
```

Leave `isTransient` alone: an out-of-date client is not transient, and retrying cannot help.

In `GameJson.decodeProjection`:

```scala
  def decodeProjection(json: String): Either[GameClientFailure, GameProjection] =
    GameProjectionCodec.decode(json).left.map:
      case unknown: ProtocolDecodeFailure.UnknownVariant =>
        GameClientFailure.UnsupportedProjection(unknown.path, unknown.message)
      case error => GameClientFailure.DecodeFailure(error.path, error.message)
```

Add `ProtocolDecodeFailure` to the file's `oathdigital.protocol` import list. Leave the preview decode at `:191-193` as a plain `DecodeFailure`: a preview response is not the table's position, and a stale bundle will have already failed on a projection.

- [ ] **Step 2: The session owns the decision**

In `TableSession.scala`:

- `Navigation` gains a third effect, documented with the other two:

  ```scala
    /** Reloads the page, to fetch the client the server is now serving. */
    def reload(): Unit
  ```
- A private flag beside the others: `private var outOfDate = false`.
- The companion gains the predicate, beside `needsSeatLink`:

  ```scala
    /** A projection this client cannot parse: the server has moved on and
      * the bundle in this browser is stale. Polling cannot recover from it
      * and only a reload can, so the session stops asking and says so.
      */
    def unsupported(error: GameClientFailure): Boolean = error match
      case _: GameClientFailure.UnsupportedProjection => true
      case _ => false
  ```
- `accept`'s `Left` branch raises the flag and stops polling:

  ```scala
        case Left(error) =>
          coordinator.recordFailure(request, error)
          if TableSession.unsupported(error) then outOfDate = true
          if GameClientFailure.isTransient(error) ||
              TableSession.needsSeatLink(trusted, error) ||
              TableSession.unsupported(error) then
            polling.stop()
          if TableSession.needsSeatLink(trusted, error) then
            coordinator.switchSession(gameId, selectedPlayer)
            projection = None
          failure = Some(error)
          redraw()
  ```

  `polling.stop()` here is enough, and `poll` needs no change: it calls `polling.complete(request, continuePolling = !transient)` *before* `accept`, so a next poll may already be scheduled, and `stop()` invalidates the timer — the same two-step the transient path already relies on. The projection is deliberately left on display: it is read-only truth from a moment ago, and every control on it submits against a sequence the server would refuse.
- `loadExisting` and `reconnect` reset `outOfDate = false` wherever they already set `failure = None`.
- `SessionControls` implementation gains `def reloadClient(): Unit = navigation.reload()`.
- `TableView` implementation gains `def clientOutOfDate: Boolean = outOfDate`.

In `TableControls.scala`, `SessionControls` declares `def reloadClient(): Unit`. It is the same path the screen already uses for Reconnect, so the button reaches the session the way every other screen control does.

In `TableScreen.scala`, `TableView` declares `def clientOutOfDate: Boolean`. The three names stay distinct — the private `outOfDate`, the view's `clientOutOfDate`, the control's `reloadClient` — which is what that trait's doc comment asks for.

In `ServerModeUi.scala`, `BrowserNavigation` implements `def reload(): Unit = dom.window.location.reload()`.

- [ ] **Step 3: The screen only renders**

In `TableScreen.render`, extend the failure notice at `:56-63`. The copy lives here, exactly as the seat-link copy already does:

```scala
    failure.foreach { error =>
      val notice = text("div", "status error",
        if view.clientOutOfDate then
          "This table is running a newer version of the game than this " +
            "page. Reload to continue."
        else if TableSession.needsSeatLink(view.trusted, error) then
          "Open your assigned seat link to restore access to this game."
        else error.message)
      notice.setAttribute("role", "alert")
      actionContent.appendChild(notice)
      if view.clientOutOfDate then
        val reload = button("Reload", "reloadClient")
        reload.onclick = _ => session.reloadClient()
        actionContent.appendChild(reload)
    }
```

The Disconnected branch above it is untouched; this mirrors it. The screen makes no decision — it reads one flag.

- [ ] **Step 4: Test the out-of-date path at the session**

Add `var reloads = 0` / `def reload(): Unit = reloads += 1` to `RecordingNavigation` in `TableSessionSuite`, and three tests:

```scala
  /** Spec, "An unparseable form": the failure moves from a blank pane to a
    * stopped session with the last good position still on screen.
    */
  test("an unsupported projection stops polling, keeps the position and asks for a reload"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fire()
      val unsupported = GameClientFailure.UnsupportedProjection(
        "$.walkerDecision.query.form", "unknown decision form 'choose-two'")
      fixture.client.answerLoad(Left(unsupported))
      settle().map { _ =>
        assertEquals(fixture.session.viewedProjection.map(_.nextSequence), Some(2L))
        assertEquals(fixture.session.shownFailure, Some(unsupported))
        assert(fixture.session.clientOutOfDate)
        assert(!fixture.clock.pending)
      }
    }

  test("an ordinary decode failure keeps polling and asks for no reload"):
    displayed(2).flatMap { fixture =>
      fixture.clock.fire()
      fixture.client.answerLoad(Left(
        GameClientFailure.DecodeFailure("$.phase", "expected string")))
      settle().map { _ =>
        assert(!fixture.session.clientOutOfDate)
        assert(fixture.clock.pending)
      }
    }

  test("reload leaves the page to the browser"):
    displayed(2).map { fixture =>
      fixture.session.reloadClient()
      assertEquals(fixture.navigation.reloads, 1)
    }
```

- [ ] **Step 5: Run the frontend suite**

Run: `./sbtw "frontend/test"`
Expected: green, three tests above the Task 4 count. `ServerModeUiSuite` unchanged in its assertions.

---

### Task 6: Glossary, spec records, full gate, commit

Closes commit 2.

**Files:**
- Modify: `CONTEXT.md` (`:17-21`, the **Form** entry)
- Modify: `docs/superpowers/specs/2026-09-25-typed-decision-form-design.md` (the status blockquote)
- Modify: `docs/superpowers/specs/2026-09-24-parked-decision-route-design.md` (close its parked follow-up)

**Interfaces:**
- Consumes: Tasks 2 to 5.
- Produces: nothing.

- [ ] **Step 1: Sharpen the glossary entry**

In `CONTEXT.md`, the **Form** entry gains one clause and nothing else. The existing sentences stay exactly true, including "or none when it asks nothing (a roll)", because `query` is still optional:

```
**Form**:
The kind of answer a parked decision asks for: choose one, choose many, choose
an amount, partition, distribute, or negotiate. A parked decision has exactly
one form, or none when it asks nothing (a roll). The form determines what else
the question carries: its options, its sections, its slots, its bounds and the
name of its confirm control.
_Avoid_: query type, decision type, panel type
```

Nothing else is added. The out-of-date notice is a message, not a domain concept. If another session has edited nearby entries, keep their wording and add only this clause.

- [ ] **Step 2: Record the slice in its spec**

Replace the spec's status blockquote's first two sentences with `> Status: implemented 2026-09-25 (two commits, this plan).` Keep the rest of the paragraph, including both links.

- [ ] **Step 3: Close the parked follow-up**

In `docs/superpowers/specs/2026-09-24-parked-decision-route-design.md`, the "Out of scope" bullet reading "A typed form or a server-declared surface hint on the wire. Separate protocol slice; this design makes it a one-file change on the frontend." gains a closing sentence reading "Done 2026-09-25 by the typed decision form
design; it cost rather more than one file, because the drafts and the panels
read the query too.", with "typed decision form design" linked to
`2026-09-25-typed-decision-form-design.md` — a sibling path, since both files
live in `docs/superpowers/specs/`. Leaving the bullet standing but unlinked is how a later reader concludes the work was never done.

- [ ] **Step 4: Run the full gate**

Run, in order:

```bash
./sbtw "test" "frontend/test" "frontend/fastLinkJS"
```

```bash
python3 scripts/check-architecture.py && python3 scripts/check-markdown-links.py
```

```bash
./sbtw clean coverage test coverageReport frontend/test
```

Expected: both suites green, the link succeeds, the architecture check passes (no production file over 800 lines; `shared/src/main` importing only `oathdigital.protocol`), the markdown link check passes, and coverage stays at or above the 86.8 floor. If coverage dips, the gap is a case whose `offeredOptions` or codec arm nothing reaches — add the missing round trip rather than lowering the ratchet.

- [ ] **Step 5: Inspect the diff**

Confirm, by reading `git diff --cached` rather than by memory:

- The six form spellings and the `"form"` discriminator key are unchanged.
- `src/main/scala/oathdigital/serialization/DecisionAnswerCodec.scala` is untouched, and its eight journal tag constants still spell themselves.
- `shared/src/main/scala/oathdigital/protocol/CommandIntents.scala` and `CommandNestedCodecs.scala` are untouched.
- `grep -rn 'query\.form\|\.form ==\|DecisionForm' src frontend shared` returns nothing outside `.md` files.
- No panel suite's expected DOM changed.

- [ ] **Step 6: Commit**

```bash
git add shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionDtos.scala shared/src/main/scala/oathdigital/protocol/projection/ActionProjectionCodec.scala shared/src/main/scala/oathdigital/protocol/CommandProtocol.scala src/main/scala/oathdigital/application/WalkerDecisionProjector.scala src/main/scala/oathdigital/application/GameProjection.scala frontend/src/main/scala/oathdigital/frontend CONTEXT.md docs/superpowers/specs shared/src/test/scala/oathdigital/protocol src/test frontend/src/test
git status --short
```

Check the status output before committing: the two deleted scaffold files must show as deletions, and nothing under `node_modules` or `.tooling` may appear. Then:

```bash
git commit -m "refactor(protocol): type the question half of the parked-decision wire

DecisionQueryProjection was one flat record: a form string plus eleven
sibling fields, of which only heading applied to every form. A payload
with form = distribute and no slots round-tripped clean, and the same
six-value vocabulary was spelled independently in four places. It is now
a sealed trait with one case per form, heading and offeredOptions on the
trait, and a codec that discriminates on form with a per-case exact set,
so a field one form lends another is refused rather than ignored.

The frontend's DecisionForm goes with it: it existed only because the
wire had no type. The three draft reconcilers stop reading the raw
string, so the draft set no longer forms its own opinion about which
form is parked. minimum/maximum split into minOptions/maxOptions and
minAmount/maxAmount, which bounded a count and a value through one pair.

A form this client does not know now fails the projection decode as
ProtocolDecodeFailure.UnknownVariant, surfaced as UnsupportedProjection:
the session stops polling, keeps the last good position, and the pane
offers Reload. Previously it decoded, routed to no surface, drew a blank
action pane and kept polling forever.

Two notes for the reader. The commit-1 vocabulary pin is deleted, not
kept: after the type is shared the agreement it asserted is true by
construction, and a test that cannot fail is worse than none. And the
encoded JSON of a query loses the null keys its form does not declare --
a change in bytes, not in meaning, and the frontend ships inside the
server's own resources, so there is no other client to mind.

The journal's answer tags are untouched.

Co-Authored-By: <the committing model's trailer>"
```
