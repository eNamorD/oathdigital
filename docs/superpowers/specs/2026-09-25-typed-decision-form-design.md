# Typed Decision Form

> Status: design approved in conversation on 2026-09-25, from an architecture
> review. No implementation is authorized by this document alone. This is the
> follow-up the [parked decision route design](2026-09-24-parked-decision-route-design.md)
> parked as "a typed form or a server-declared surface hint on the wire.
> Separate protocol slice; this design makes it a one-file change on the
> frontend." The tag separation it depends on is recorded in the
> [decision form tag separation decision](2026-09-25-decision-form-tag-separation-decision.md).

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**,
**form**, **surface** and **draft set**. This spec sharpens **form**.

## Purpose

A **form** is a bare string on the projection wire.
`DecisionQueryProjection` is one flat record: `form: String` plus eleven
sibling fields, of which only `heading` applies to every form. Six apply to
exactly one form each, and the codec never cross-checks any of them against
`form`. A payload with `form = "distribute"` and no slots, or
`form = "choose-one"` carrying sections, round-trips clean.

The same six-value vocabulary is spelled in four independent places: the
projector writes the literals, `CommandNestedCodecs` re-declares them for the
answer half, `DecisionAnswerCodec` re-declares them as journal tags, and the
frontend parses them back into a local `DecisionForm`. Three frontend modules
then bypass that parse and compare the raw string again, so the **draft set**
forms its own opinion about which form is parked, independently of the route.

Nothing asserts that the set of forms the projector emits equals the set the
frontend recognises. A seventh form, projected and not handled on the
frontend, parses to `DecisionForm.Unknown`, routes to no **surface**, and
renders a blank action pane, with the whole suite green.

Adding a form today edits twenty-two files.

The answer half of the same wire is already a sealed family:
`DecisionAnswerWire` has eight cases, each carrying its own fields, with a
discriminated codec. The question half carries the same information and has
none of that. `sealed trait DecisionQuery` already exists in the model, so the
type is destroyed at the shared boundary and rebuilt twice downstream, first
as `DecisionForm` and then as `Surface`.

Give the question half the shape the answer half already has.

## Ownership and interface

### `DecisionQueryProjection`

`DecisionQueryProjection` becomes a sealed trait in the shared projection
package, with one case per form. It stays a sealed trait rather than an
`enum`: the [Scala 3 modernization design](2026-09-24-scala-3-modernization-design.md)
decides that "mixed and parameterized families stay sealed traits" and puts
"converting mixed families to parameterized enums" out of scope. Every one of
the forty-seven enums in `src/main` is flat, and `shared/src/main` has none.
The precedents to follow are both nearby: `SiteForcesProjection` in the same
package, which puts the genuinely common fields on the trait as abstract
accessors and gives each case its own field list, and
`BoardTargetRefProjection` in the file this slice edits.

Two members sit on the trait:

```scala
sealed trait DecisionQueryProjection:
  def heading: Option[String]
  def offeredOptions: Vector[DecisionOptionProjection]
```

`heading` is the one field every form carries, and the model's own
`DecisionQuery` already declares it on its trait for the same reason.
`offeredOptions` exists for `GameProjection.offeredCards`, which today reads
`query.options ++ query.slots.map(_.option)` and therefore has to know that
distribute keeps its options inside slots. As an accessor it becomes a
question every form can answer, and the caller stops knowing about slots.

The six cases carry exactly their own fields. Case names are bare, namespaced
by the trait, as `SiteForcesProjection`'s are; the `Wire` suffix on the answer
half exists to separate it from the model's `DecisionAnswer`, a collision the
question half does not have.

- `ChooseOne(options, heading)`
- `ChooseMany(options, minOptions, maxOptions, heading)`
- `ChooseAmount(minAmount, maxAmount, suggested, confirmLabel, heading)`
- `Partition(sections, options, confirmLabel, heading)`
- `Distribute(slots, minTotal, maxTotal, confirmLabel, heading)`
- `Negotiate(deal, heading)`

Three facts the flat record could not hold, now held by the type:

`confirmLabel` is a required `String` on `ChooseAmount` and `Distribute`, an
`Option[String]` on `Partition`, and absent from the other three. Today it is
one `Option` on the record, and the contract that choose-one never carries one
lives in a doc comment.

`minimum` and `maximum` were one wire pair serving two unrelated bounds:
choose-many bounds a **number of options**, choose-amount bounds a **value**.
They split into `minOptions`/`maxOptions` and `minAmount`/`maxAmount`. Three
frontend modules currently read that pair without the form in hand.

`options` was populated for three forms and explicitly emptied for the other
three. Now only the cases that have options declare them.

The payloads stay the already-presented projection types
(`DecisionOptionProjection`, `DecisionSectionProjection`,
`DecisionSlotProjection`, `NegotiationDealProjection`). The new vocabulary
mirrors the model type's case set and arity, and nothing more: the projector
resolves option labels from live state, narrows the option set through
`WalkerSimulation.previewParked` for procedures that require a playable
option, redacts a negotiation per viewer, and drops the whole query when any
option fails to present. None of that belongs on a wire type, and none of it
moves. `shared/src/main` may import only `oathdigital.protocol`, which the new
cases satisfy; anything from `model` would have to be fully qualified, as
`SiteForcesProjection` does for `PlayerColor`.

### The codec

`ActionProjectionCodec` gains a discriminated encode and decode for the new
trait, in the shape `BoardTargetRefProjection` already uses in the same file
and `SiteForcesProjection` uses in `WorldProjectionCodec`.

The discriminator key stays `"form"`, and the six tag values stay as they are.
Both neighbours in the file discriminate on `"kind"`, and this one
deliberately does not: `form` is the name the glossary gives the concept, and
the project has already paid to have that word mean one thing. This is a
choice, not an oversight.

The flat `exact` field set becomes one per case, as
`decodeDecisionAnswerWire` already does. `exact` is what makes the strictness
real: a payload carrying a field that its form does not declare is rejected
rather than ignored.

### The projector

`WalkerDecisionProjector.queryProjection` keeps its six-arm match over the
model's `DecisionQuery` and constructs a case instead of a record with named
arguments. Nothing about which form a given model query produces changes.

### The frontend

`DecisionForm` and its `parse` are deleted. They exist only because the wire
had no type; the moment it has one, they are a third spelling of the same
vocabulary. `DecisionForm.Unknown` has nothing left to represent, because an
unrecognised form no longer reaches the frontend as a value — see below.

`Surface` stays. It is the frontend's own concept, the choice between the
action pane and the world board, and it is not the form. `ParkedDecision`'s
`formSurface` becomes a match on the wire type. `SelectionForm`, currently a
union of two `DecisionForm` singleton types, becomes a union of the two
corresponding case types.

The three **draft set** reconcilers stop comparing strings.
`PartitionDecisionState`, `WalkerSelectionDraft` and `DistributeDecisionState`
each match the wire type instead. `SessionDrafts.reconcile` remains the single
entry point that decides which draft a parked decision gets, and it now
decides it from the same type the route reads, not from a parallel reading of
the same string.

### An unparseable form

Strict decoding moves the failure. Today an unrecognised form degrades
locally: no surface, a blank action pane, the rest of the table drawn. With a
closed vocabulary, a form the client does not know fails the whole projection
decode.

This is the right trade because there is no version-skew window in normal
operation: the frontend JavaScript is linked into the server's own resources,
so client and server ship together. The case that remains is a browser holding
a stale cached bundle, and for that a clear "reload" is a better answer than a
table with one pane silently missing.

Three changes carry it:

`ProtocolDecodeFailure` gains a case for an unknown discriminator. The module
has six cases and no way to say "this variant is not one I know"; the answer
half currently reports it as `InvalidValue`. The new case makes the cause a
type rather than a parsed path, and it generalises to every discriminated wire
type in the module.

`GameClientFailure` gains `UnsupportedProjection`, which `decodeProjection`
produces when the decode failure is that unknown discriminator. It is not
transient.

`TableSession` owns the decision that this failure means the client is out of
date, and exposes it to the screen as a flag. It stops polling: a decode
failure is currently non-transient, which leaves polling running, so a stale
client would retry forever against a payload it cannot parse. It keeps the
previous projection, which is read-only truth from a moment ago and costs the
viewer nothing in safety, since every control on it submits against a sequence
the server would reject. `TableScreen` renders human copy and a Reload
button, mirroring the existing Disconnected branch's Reconnect, and makes no
decision of its own. The session owns it because the session is tested and the
screen is not.

## Two vocabularies, deliberately not one

The projection's form tags and the journal's answer tags share five spellings
and must not share a constant. The projection wire is transient and free to
change; the journal is durable, forward-only, and holds 376 live answer tags.
A shared constant is precisely the channel through which a projection-side
rename would reach those rows. This slice touches the question half only.
Recorded separately, with the evidence, in the
[decision form tag separation decision](2026-09-25-decision-form-tag-separation-decision.md).

## Behavior changes

Two, both deliberate.

A projection whose parked decision carries a form this client does not know,
or a form carrying a field it does not declare, now fails to decode. The
viewer keeps the last good table, polling stops, and the pane says the client
is out of date and offers Reload. Previously the projection decoded and the
action pane rendered empty, with polling continuing.

No correct payload changes. The six tag values, the discriminator key, every
projected string, option order, and the rendered DOM are all unchanged.

## Verification

Five seams, all of them already existing. No new test surface.

The wire shape, at the `ActionProjectionCodec` round trip:
`ProjectionProtocolSuite` already round-trips the flat record and becomes one
round trip per case. Add the rejection cases the new strictness introduces: a
field belonging to another form, a missing required field, and an unknown
discriminator.

The projector's case selection, at `WalkerDecisionProjector.project`:
`WalkerDecisionProjectorSuite`, `WalkerDecisionProjectionSuite`,
`PhasePowerProjectorSuite`, `NegotiationDealProjectionSuite`,
`GameServerGatewaySubmitBeginSuite` and `GameApplicationServiceSuite` each
assert a form value today; those assertions become case matches.

Form to **surface**, at `ParkedDecision.route`: `ParkedDecisionSuite`, fourteen
tests and pure. Its `query(form: String)` and local `surface(form: String)`
helpers retype, and the twenty-six literals follow mechanically. The
assertion that `parse("choose-two")` yields `Unknown("choose-two")` is deleted
with the type it tests.

Which draft a form gets, at `SessionDrafts.reconcile`: `SessionDraftsSuite`.
This is where the three reconcilers' change is verified, at the one entry
point, rather than at each reconciler.

The out-of-date path, at `TableSession`: `TableSessionSuite`, over its existing
`FakeClient`, `ManualClock`, `RecordingNavigation` and repaint counter. Assert
that an unknown-discriminator decode failure stops polling, keeps the previous
projection, and raises the flag; and that an ordinary decode failure does not.

Eleven further suites need only a constructor change:
`WalkerChoicePanelRenderSuite`, `ServerModeUiSuite`,
`WalkerSelectionDraftSuite`, `PartitionPanelRenderSuite`,
`PartitionDecisionStateSuite`, `DistributePanelRenderSuite`, `SuitGlyphSuite`,
`RecoverPanelSuite`, `CardChoicePanelSuite`, `BoardSurfaceSuite`, and
`WalkerSelectionPanelsSuite`, whose helper branches on the form string.

The agreement test in commit 1 is a scaffold and is replaced, not kept. It
asserts what nothing asserts today: that the set of forms the projector can
emit equals the set the frontend recognises. After the type is shared that
assertion is true by construction, and a test that cannot fail is worse than
none, so commit 2 replaces it with the per-case round trips above. Say so in
commit 2's message.

Run both full suites, `python3 scripts/check-architecture.py`, and the
coverage gate. `-Werror` is on for both projects, so every match over the new
trait must be total at the point of the commit; that is a benefit here, and it
is also why there is no half-typed intermediate state to land.

Inspect the diff for: unchanged tag values, unchanged discriminator key,
unchanged journal tags in `DecisionAnswerCodec`, and no remaining
`query.form ==` comparison anywhere.

## Preservation boundary

Gameplay behavior, the model's `DecisionQuery`, the answer half of the wire,
`DecisionAnswerCodec` and its eight journal tags, the durable journal format,
projected strings and labels, option and section order, **surface** selection,
and rendered DOM all stay as they are. Any rule defect found on the way is a
separate change.

## Files

Shared: `ActionProjectionDtos.scala` (the trait and its six cases),
`ActionProjectionCodec.scala` (discriminated encode and decode, per-case
`exact`), `CommandProtocol.scala` (the unknown-discriminator failure case).

Application: `WalkerDecisionProjector.scala` (construct cases),
`GameProjection.scala` (`offeredCards` through `offeredOptions`).

Frontend: `ParkedDecision.scala` (delete `DecisionForm`, retype `Surface`
selection and `SelectionForm`), `PartitionDecisionState.scala`,
`WalkerSelectionDraft.scala`, `DistributeDecisionState.scala` (match the type),
`WalkerPanelSupport.scala`, `WalkerSelectionPanels.scala`,
`DistributePanelRenderer.scala`, `WorldBoardRenderer.scala` (read per-case
fields), `GameClient.scala` (`UnsupportedProjection`), `TableSession.scala`
(own the out-of-date decision), `TableScreen.scala` (copy and Reload button).

Twelve production files, about forty-two edit sites. Twenty test files, about
seventy-five sites, of which nine need logic edits and eleven only a
constructor change. Every file stays well under the eight-hundred-line cap.

## Commits

Own branch, two commits, each green.

1. The agreement test alone, against the current untyped code. It must be able
   to fail today; that is why it is separate and first.
2. Everything else, as one atomic change the compiler verifies: the trait and
   its cases, the codec, the projector, the frontend including the three draft
   reconcilers, the out-of-date path, `CONTEXT.md`, and the replacement of the
   commit-1 scaffold with per-case round trips.

## Glossary

`CONTEXT.md` gains one clause on **form**: that a form determines which fields
the **parked decision** carries. The rest of the entry stays exactly true,
including "A parked decision has exactly one form, or none when it asks
nothing (a roll)", because `query` is already optional.

Nothing else is added. The out-of-date notice is a message, not a domain
concept.

## Out of scope

- The option-kind vocabulary. `DecisionOptionRef.kind` has eleven variants
  matched as bare literals in four frontend modules, with no parse at all. It
  is a second vocabulary with a different shape; bundling it doubles this
  slice for no shared mechanism.
- A shared home for the id grammars: `RuleSourceRef.stableKey` parsed
  independently on both platforms, and the `recover.`, `setup.pawn-placement.`
  and `cardplay.` decision-id prefixes. That is its own slice, and this one
  does not need it: `shared/` already exists and already holds wire DTOs.
- Unifying the projection and journal tag vocabularies. Recorded as its own
  decision, linked above.
- A protocol version field on the projection. It detects skew rather than the
  failure, and it needs its own versioning policy.
- Converting the six existing shared sealed traits to enums.
- `WalkerDecisionProjectorSuite`'s fabricated parks. The suite's assertions
  change shape here; driving it through a **situation** instead is a separate
  slice.
