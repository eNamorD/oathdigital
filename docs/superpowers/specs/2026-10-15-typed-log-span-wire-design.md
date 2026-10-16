# Typed Log Span Wire

> Status: design confirmed 2026-10-15, from the 2026-10-13 architecture review
> (candidate E, "Type the log's span wire"). It gives the game log's wire types
> the treatment the [typed decision form](2026-09-25-typed-decision-form-design.md)
> gave the projection's question half, and supersedes the "Wire contract"
> section of the [game log design](2026-09-25-game-log-design.md). No
> implementation is authorized by this document alone; a plan follows it.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) and the game log design define
**entry**, **headline**, **line**, **span** and **viewer**.

## Purpose

`LogSpanWire` is a flat tag with four unchecked optional siblings:

```scala
final case class LogSpanWire(kind: String, text: String,
    id: Option[String] = None, value: Option[Int] = None,
    unit: Option[String] = None,
    cards: Vector[CardDetailsProjection] = Vector.empty)
```

Eight kinds share those six fields. No field but `kind` and `text` applies to
every kind, and the codec never checks a field against the kind beside it. A
payload with `kind = "amount"` and no `value`, or `kind = "dice"` and no
faces, round-trips clean and renders a wrong line in a player's log with
nothing raised anywhere.

The failure is already in the tree. `GameLogProjector.span` writes a
`Vector[String]` of die faces into a field named `id`, joined by spaces:

```scala
case dice: LogSpan.Dice => LogSpanWire("dice", dice.text,
  id = Some(dice.faces.mkString(" ")), unit = Some(dice.die))
```

The frontend splits it back on whitespace. Two of the four optional fields are
dead on the kinds that populate them: `card` and `site` spans both carry an
`id`, and `GameLogPane.spanNode` routes both through a branch that reads only
`text`.

The type this wire should have already exists. `LogSpan` in
`application/gamelog/LogEntry.scala` is a sealed family with one case per kind,
each carrying exactly its own fields, and it already imports
`CardDetailsProjection` from `oathdigital.protocol`. `GameLogProjector.span` is
a nine-arm map from that good type onto the flat one, and it loses information
in two of its arms. The mapping is not a boundary; it is the defect.

Give the log wire the shape the formatter already has, by moving the
formatter's types to the wire rather than mirroring them there.

## Ownership and interface

### The types move, they are not duplicated

`LogSpan`, `LogEntry`, `LogSection`, `LogTakenBack` and `LogKind` move from
`src/main/scala/oathdigital/application/gamelog/LogEntry.scala` to
`shared/src/main/scala/oathdigital/protocol/projection/LogProjectionDtos.scala`.
`LogSpanWire`, `LogEntryWire`, `LogSectionWire` and `LogTakenBackWire` are
deleted, and `LogPageWire` is renamed `LogPage`. Both of
`GameLogProjector`'s mapping functions, `span` and `wire`, are deleted with
them; the projector keeps only its page assembly and its `after` bounds check.

The move is legal and the architecture tests already point at it.
`BackendArchitectureSuite` forbids `application` from importing `server` and
`serialization` only, and the `model` and `gameplay` restrictions do not apply
to `application`; `gamelog` already imports `oathdigital.protocol`. The same
suite's "projection and bootstrap transport DTOs are defined only in shared
protocol" test states the direction this slice follows.

`Posted` stays in `gamelog`. It is the formatter's private intermediate, not
transport, and it is the one type here that should not move.

Names are bare, namespaced by their package, as the typed decision form design
settled for the same reason: the `Wire` suffix exists in this codebase to
separate a wire type from a domain twin, and after the move there are no
twins. The family stays a `sealed trait` with `final case class` cases rather
than becoming an `enum`, which is what the
[Scala 3 modernization design](2026-09-24-scala-3-modernization-design.md)
decides for parameterized families.

### The span family

```scala
sealed trait LogSpan extends Product with Serializable:
  def text: String

object LogSpan:
  final case class Text(value: String) extends LogSpan
  final case class Player(id: String, name: String) extends LogSpan
  final case class Card(id: String, name: String) extends LogSpan
  final case class Site(id: String, name: String) extends LogSpan
  final case class Amount(value: Int, unit: String) extends LogSpan
  final case class Cost(value: Int) extends LogSpan
  final case class Dice(first: DieFace, rest: Vector[DieFace]) extends LogSpan
  final case class Cards(cards: Vector[CardDetailsProjection]) extends LogSpan
```

Three facts the flat record could not hold, now held by the type:

**A roll's faces are faces.** `Dice` carried them as a space-joined string in
a field named `id`, and its die as a string in `unit`. It now carries a
non-empty sequence of typed faces, and the die it was rolled on is read from
them rather than sent beside them.

**`Cost` has one unit.** Both construction sites pass the literal `"Supply"`,
and have since the span was introduced. A field with one possible value is not
one of the case's fields; the word moves into `text`. Adding a second currency
later is a one-parameter change the compiler will route to every site, which is
the point of sealing the family.

**`Card` and `Site` keep their ids** although nothing reads them today. They
are correct, the formatter holds them for its own use, and a card name in the
log is an obvious inspection affordance beside the `Cards` span that already
opens one. The spec records them as deliberately unconsumed so the next review
does not read them as oversight.

Ids stay `String`. `CardDetailsProjection` sets that convention with
`cardId: String`, and `shared` cannot import `model`.

### Die faces

Seven faces are spelled in four tables today:

| # | Location | Produces |
|---|----------|----------|
| 1 | `LogWords.attackWire` / `defenseWire` | wire names, for the log |
| 2 | `WalkerDecisionProjector.faceName` | wire names, for the decision wire |
| 3 | `LogWords.attackName` / `defenseName` | display names, for the log |
| 4 | `DieFace.words` (frontend) | display names, for the die chips |

Tables 1 and 2 agree. **Tables 3 and 4 have drifted.** The server calls
`one-sword` "sword" and `one-shield` "shield"; the client calls them "one
sword" and "one shield". The same roll is described two ways on the same page:
the log sentence reads "Rolled sword, sword, sword for the attack" while each
chip's `aria-label` reads "one sword". Nothing checks them against each other.
This is the drift the untyped face vocabulary permitted, and it is the reason
to type the face rather than only to move it out of `id`.

```scala
enum Die(val key: String):
  case Attack extends Die("attack")
  case Defense extends Die("defense")

enum DieFace(val key: String, val word: String, val die: Die):
  case HollowSword extends DieFace("hollow-sword", "hollow sword", Die.Attack)
  case OneSword extends DieFace("one-sword", "sword", Die.Attack)
  case TwoSwordsSkull
      extends DieFace("two-swords-skull", "two swords and a skull", Die.Attack)
  case Blank extends DieFace("blank", "blank", Die.Defense)
  case OneShield extends DieFace("one-shield", "shield", Die.Defense)
  case TwoShields extends DieFace("two-shields", "two shields", Die.Defense)
  case Doubler extends DieFace("doubler", "doubler", Die.Defense)
```

Tables 3 and 4 collapse into `word`, and the server's wording wins. Its set is
the internally consistent one: "sword" is parallel with "two swords and a
skull", and "shield" with "two shields", so the set reads correctly in a
sentence and as a label without "one" as a crutch. The frontend's `words` map
is deleted. No log assertion in any suite moves; the visible change is the
`aria-label` on two of the seven chips.

Table 1 is absorbed into `key`. **Table 2 stays**, because it serves the
decision wire and widening this slice to a second wire would be scope it has
not earned. It is guarded instead by a test that asserts its output set equals
the face enum's keys, which is the remedy the
[decision form tag separation decision](2026-09-25-decision-form-tag-separation-decision.md)
recommends for exactly this shape: "enforce it with a test that asserts the
sets match... rather than with a constant that makes divergence impossible by
making a dangerous rename easy."

### Three derived fields leave the wire

`text`, `depth` and a roll's die are each a total function of fields already
on the wire. Because the family now lives in `shared`, both runtimes compile
the function, and the receiver computes what the sender would otherwise have
to send.

**`text`** is a `def` on the trait, as it already is on the formatter's type.
The reason it was transmitted — a client that ignores span kinds still renders
a sentence — is retired below with the tolerance that motivated it.
`Cost.text` prepends U+2212; that is display formatting, and it belongs where
both sides read it.

**`depth`** is a `def` on `LogKind`. The partition is total and disjoint
today: `Posted.headline` is called only with `Round`, `Turn`, `Phase` and
`Victory`, and `Posted.line` only with `Section`, `Action`, `Decision`,
`Delta`, `Roll` and `Trigger`. Moving it onto the kind retires the two
`Posted` constructors as separate entry points and makes a wrong depth
impossible to write.

**`Dice.die`** is `first.die`. The formatter only ever built a dice span from a
uniform pool — `LogWords.dice` partitions the faces by type and returns
`None` for a mixed or empty one — so the die was always recoverable from the
faces beside it.

`Dice` holds its faces as `(first, rest)` rather than a `Vector`, so that
reading the die is a total function rather than a documented precondition.
This is the one place the design accepts an awkward constructor: `Cards` keeps
a plain `Vector` because nothing is derived from its head.

### Invariants the decoder enforces

Four conditions were doc comments or unreachable guards. They become decode
rejections, at the one place untrusted bytes enter:

- A span kind the client does not know.
- A field a case does not declare, through the existing `exact` check.
- An empty `cards` array. `LogWords.listed` only builds a `Cards` span past
  the inline threshold, and emits a `Text` span when the viewer may identify
  none of the cards, so `GameLogPane`'s `case "cards" if span.cards.nonEmpty`
  guard is already unreachable. It is deleted.
- A mixed-die `faces` array, and a `section` on a headline kind — the
  invariant `LogEntry`'s doc comment states as "only a line can carry a
  section".

### The codec

`LogPageCodec` gains a discriminated encode and decode for `LogSpan`, in the
shape `BoardTargetRefProjection` and `SiteForcesProjection` already use. The
discriminator key stays `"kind"` and the eight tag values keep their
spellings. The per-case field sets replace the single six-field `SpanFields`
set, so `exact` becomes real: `{"kind": "text", "text": "..."}` is the whole of
a text span, and a `value` beside it is rejected rather than ignored.

`Text`'s payload is keyed `"text"`, not `"value"`: `value` is the `Int` field
on `Amount`, and reusing the key for a `String` on one case is the confusion
the sealed shape exists to end.

`LogEntry` no longer encodes `depth`, and `kind` still encodes as
`LogKind.key`, so the entry's JSON keeps the string tag the game log design
specified. `LogSectionWire.action` likewise stays a string: it is open-ended
(`use-power:<id>`) and cannot be an enum. Neither is the defect this slice
addresses — `LogKind` is a flat vocabulary with no correlated siblings, so a
wrong value cannot produce a plausible-but-wrong line the way a missing `id`
on a dice span can.

### A log page this client cannot parse

Strict decoding moves the failure, and on this route it currently has nowhere
to go. `GameJson.logResponse` maps every log decode error to
`GameClientFailure.DecodeFailure`, and `TableSession.fetchLog` consumes the
result with `result.foreach`, so a `Left` is discarded: "a failure changes
nothing and the next advancing position asks again." Under strict decoding
that would mean a stale browser bundle meeting a new span kind gets a log pane
that silently stops updating, forever, with no message — worse than both the
projection route and the tolerance being replaced.

The machinery to do better already exists, built by the typed decision form
slice. `ProtocolDecodeFailure.UnknownVariant` is produced by a discriminated
decoder on an unknown tag, and its own doc comment already names this use --
"the sender knows a form, a kind or a type this reader was not built with".
`GameJson.decodeProjection` maps it to
`GameClientFailure.UnsupportedProjection`; and `TableSession.unsupported`
treats that as non-transient, stops polling and surfaces the Reload
affordance.

`logResponse` is wired the same way: `UnknownVariant` becomes
`UnsupportedProjection`, every other decode failure stays `DecodeFailure`, and
`fetchLog` routes a failure to the session's existing unsupported branch
instead of dropping it. No new failure type, no new session state.

As on the projection route, the only case this can arise from is a browser
holding a stale cached bundle: `build.sbt` links the frontend with
`fullLinkJS` and copies `main.js` into the server's own `resourceManaged`, so
client and server ship as one artifact and there is no version-skew window in
normal operation.

### Retiring the unknown-kind tolerance

`LogPageCodec`'s header records the opposite posture: "Kinds are not checked
against a list: a client shows an unknown span kind as its text, so a newer
server's span does not break an older page."

That rationale does not hold, and this slice retires it deliberately rather
than by accident. The page is served by the binary that serves the log, so
"newer server, older page" is not a state this system can reach. Keeping the
tolerance would cost an `Unknown` case that every downstream match carries
forever, which is the silent-wrong-line posture the slice exists to remove. The
comment is deleted and this paragraph is the record of why, so a later review
does not read the deletion as an oversight and restore it.

### The formatter and the frontend

`GameLogFormatter` and the thirteen other line-building files under
`application/gamelog`
change their imports and nothing else; their construction sites are already
written against the case shapes, because those are the types being moved.
`LogWords.dice` returns typed faces instead of two parallel `Vector[String]`,
and its four private naming functions are deleted.

`GameLogPane.spanNode` matches the sealed family instead of a string, so its
`case _ =>` fallback is gone and a new span kind fails compilation until the
pane handles it. `DieFace.roll` takes `Vector[DieFace]` instead of
`Vector[String]`, and `DieFace.words` and the `dice` set of known die names
are deleted: an unknown face and an unknown die are no longer values the type
admits. `DieFace.symbols` stays — the glyph composition is the client's own
drawing concern and has no server twin.

## Where the typing stops

`Amount.unit` stays a `String`, deliberately.

It is not a tag with correlated siblings. It is display content, drawn from
catalog note data and already inflected for its count by `PowerNotes.word`
before it reaches the span. Typing it would drag the resource vocabulary and
its pluralization into the protocol package to serve a field nothing branches
on.

The defect this slice removes is *a tag plus unchecked optional siblings*, not
*the presence of a `String`*. A review that cannot tell the two apart will
come back for this field, and for `LogSection.action`, which is open-ended for
the same kind of reason.

## Behavior changes

Three, all deliberate.

A log page carrying a span kind this client does not know, or a field a case
does not declare, now fails to decode. The pane keeps the entries it has,
polling stops, and the session reports the client as out of date. Previously
the span rendered as its text and polling continued.

Two die faces are described differently. `one-sword` and `one-shield` chips
now carry the `aria-label`s "sword" and "shield" instead of "one sword" and
"one shield", matching the words the log sentence has always used.

The wire is smaller: `text` leaves every span, `depth` leaves every entry, and
a dice span's die is no longer sent. No log line's rendered text changes.

## Verification

The compiler carries most of this; the tests cover what it cannot see.

**The wire shape**, at the `LogPageCodec` round trip. `LogPageCodecSuite`
round-trips the flat record today and becomes one round trip per span case,
plus the rejections the new strictness introduces: an unknown kind, a field
belonging to another case, an empty `cards` array, a mixed-die `faces` array,
and a `section` on a headline kind.

**The face tables that remain apart.** A new assertion that
`WalkerDecisionProjector.faceName`'s output set equals the `DieFace` enum's
keys. This is the test the tag separation decision recommends: a failure names
the divergence and leaves the fix to a human, without coupling the two wires.

**The formatter**, unchanged. Under the wording decision above, no existing
assertion in `GameLogCampaignSuite`, `GameLogDetailSuite`,
`GameLogRerollSuite` or `LogWordsSuite` moves. Their construction sites change
shape where they build spans directly; their expectations do not. That is the
evidence that the slice is structural.

**The pane**, at `GameLogPaneSuite`, `GameLogTakenBackPaneSuite` and
`TestLog`: construction sites change to the case shapes, and the dice
assertions read typed faces.

**The stale-bundle path**, at `TableSession`: a log response carrying an
unknown span kind stops polling and reports the client unsupported, the same
assertion shape the projection route's test already uses.

## Considered and rejected

- **Mirror the family on the wire, keeping `GameLogProjector.span` as a total
  typed map.** Rejected. This is what the typed decision form slice did, but
  that slice's projector earns its mapping: it resolves option labels from
  live state, narrows through `WalkerSimulation.previewParked`, and redacts
  per viewer. `GameLogProjector.span` renames fields and nothing else, and one
  of its arms renames them wrongly. A mapping layer that only loses
  information is the defect, and mirroring preserves it in typed form while
  still needing nine hand-maintained arms.
- **Type the dice faces but keep `names` on the wire.** Rejected, though it
  was the honest narrow option. It buys the type without forcing the wording
  decision, and leaves tables 3 and 4 both alive and still drifted. The drift
  is the finding; deferring it would ship the slice that found it without
  fixing it.
- **Keep the unknown-kind tolerance behind an `LogSpan.Unknown(text)` case.**
  Rejected: it protects against a version skew the build makes impossible, and
  costs an exhaustive-match escape hatch in perpetuity.
- **Type `LogEntry.kind` on the wire.** Rejected, examined. A flat vocabulary
  with no correlated siblings is not this defect, and the frontend reads
  `kind` to build CSS class names and pick a heading level, which a string
  serves correctly.
- **`Dice(die: Die, faces: Vector[DieFace])` with a cross-field decode
  check.** Rejected: it keeps the redundancy and then pays to police it.
- **Split `Dice` into `AttackDice` and `DefenseDice`.** Rejected: it makes
  mixing unrepresentable, but doubles the dice case and the face enum to
  prevent a combination `LogWords.dice` cannot construct.
- **A golden formatter pass over a scripted journal.** Rejected: the gamelog
  suites already assert line text, and under the wording decision none of
  those expectations move, so a golden pass would restate coverage that exists.

## Delivery

One slice, two commits, because the two halves raise different review
questions.

1. **The collapse.** Move the five types to `shared`, delete the four `Wire`
   twins and both mapping functions, rename `LogPageWire`, derive `text` and
   `depth`, drop `Cost.unit`, write the discriminated codec with its per-case
   field sets and its rejections, and point the remaining `gamelog` files,
   the pane and the suites at the moved types. Dice still carries
   `(die: String, faces: Vector[String], names: Vector[String])` at the end of
   this commit: typed as a case, not yet as faces.
2. **The faces.** Add `Die` and `DieFace`, collapse tables 3 and 4 onto
   `word`, change `Dice` to `(first, rest)`, delete `DieFace.words` and the
   four naming functions in `LogWords`, add the table 2 set-equality test, and
   wire `logResponse` and `fetchLog` to the unsupported path.

Both files stay well inside the 800-line production cap: the DTO file lands
near 180 lines and `LogPageCodec` near 230.

## Consequences

- The game log's wire and the formatter's output are one set of types. A new
  span kind is added once, and the compiler names every site that must handle
  it — the pane included.
- `GameLogProjector` keeps only the page assembly and the `after` bounds
  check. If it loses that too, the file should go.
- The die face vocabulary has one display spelling and one wire spelling.
  `WalkerDecisionProjector.faceName` remains a second wire table on purpose,
  held to the first by a test rather than a shared constant.
- A stale browser bundle now reports itself on the log route as it already
  does on the projection route, instead of stalling the pane in silence.
- The "Wire contract" section of the game log design is superseded by this
  document and carries a pointer to it.
