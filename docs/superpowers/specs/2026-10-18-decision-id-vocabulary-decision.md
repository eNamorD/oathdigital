# The Decision-Id Vocabulary: Where It Is Shared, and Where It Is Not

> Status: decided 2026-10-18, from the architecture review of 2026-10-13, which
> raised this as candidate H — the third consecutive review to raise it. It
> records a verdict for each of the three things that review bundled together,
> so a fourth review finds them already weighed. Taken alongside the
> [decision form tag separation decision](2026-09-25-decision-form-tag-separation-decision.md),
> which settled a neighbouring question the opposite way, for reasons that do
> not all carry over.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **phase menu**, **parked
decision** and **answered options**.

## Decision

The review's finding was that `MenuPanel` re-declares a decision id, three sets
of option ids and a power prefix that the backend also declares, and that die
faces are spelled out in six separate tables. That is three different problems
wearing one description, and they get three different answers.

1. **The major/minor/power/ending classification moves onto the wire.** It was
   never duplicated; it was never published. `PhaseMenu` tags each option, the
   projection carries the tag, and `MenuPanel` stops knowing the fifteen action
   ids.
2. **`"phase.menu"` stays spelled in both places.** The frontend needs it to
   route one panel. One string, one site, a comment naming its owner, and no
   enforcement.
3. **The decision wire's die faces become typed**, as the log's already are, so
   the shared face vocabulary is read rather than restated. The two face tables
   in `serialization` stay as they are.

No shared constants object is created. `shared/src/main/scala/oathdigital/protocol`
holds the shapes the two sides mail each other and the codecs for them; a
hand-maintained copy of names the engine owns is a different kind of resident,
and `scripts/check-architecture.py` keeps that module from reaching the engine's
own types in any case.

## Why this survived three reviews

Three reasons were already written down, in three different places, and none of
them was wrong:

- `WalkerDecisionProjector` carries a comment explaining that the application
  layer may not import the serialization layer, so its face table cannot simply
  call the serialization one, and naming the game log as the same precedent.
- `shared/.../projection/DieFaces.scala` explains that the decision wire still
  spells its faces itself "not because it may not read this enum, but because
  that wire was out of the typed log slice's scope", and names the test holding
  the two in agreement meanwhile.
- The 2026-09-25 record rejected a near-identical proposal for the form tags,
  on the grounds that a shared constant turns a safe rename into a journal
  migration.

What was missing was the distinction between them. The first two are statements
about one table that is hard to move; the third is a statement about a
vocabulary whose two halves have different lifetimes. Neither covers the option
ids in `MenuPanel`, and a reviewer reading either would reasonably conclude the
whole area had been settled.

One argument that looked load-bearing is not. The ids are durable: the working
journal holds 1419 events, in which `decisionId` appears 453 times,
`use-power:` keys 23 times and `two-swords-skull` 14 times (counted in the
working journal while taking this decision). It is tempting to conclude that
duplication protects the journal.
It does not. The backend's own declaration is already a single constant, so a
rename there changes what recorded history means whether or not the frontend
holds a copy. The frontend copy stands between nobody and the journal. Its only
effect is that divergence is silent: a renamed id puts a button in the unheaded
group, or drops the whole menu to the generic panel, and nothing fails.

## Item 1: the classification is published

`ActOffers.all` recorded major-versus-minor as list order plus a prose comment,
and `MenuPanel` re-derived it by holding the ids. The split is a printed Oath
rule, so it gets a name:

- `ActionClass` (`Major`, `Minor`) in `oathdigital.model`, carried by
  `ActOffer`.
- `OptionGroup` (`Major`, `Minor`, `Power`, `Ending`, `Other`) in
  `oathdigital.model`, carried by a new `DecisionOption.Grouped` decorator
  alongside `Priced`, `Badged`, `Unavailable` and `Warned`.
- `DecisionOptionProjection.group`, an optional tag on the wire.

`PhaseMenu.offers` wraps every option it builds: Take Wealth as `Other`, an Act
offer as its `ActionClass`, a phase power as `Power`, the phase's ending as
`Ending`. The projector unwraps `Grouped` exactly as it unwraps `Badged`.

Group order and heading words stay in the client. Order and wording are layout;
the tag is a fact. Putting English heading copy on the projection beside the tag
would create a second thing to keep in agreement, which is the problem being
fixed.

The client groups by tag and sends an absent or unknown tag to the unheaded
group. That keeps the property the panel already had: a client that meets an
option it has never heard of still draws it.

## Item 2: one routing literal stays

After item 1 the frontend's only remaining use of the decision id is choosing
`MenuPanel` over the generic choose-one panel. Two alternatives were available
and both were declined: putting the string in `shared` (a copied game fact in
the post office), and having the projection carry a panel-routing hint (a second
routing vocabulary invented for one caller). If panel routing becomes
server-driven in general, the hint becomes the right answer; for one caller it
is not.

Nothing enforces the agreement. A test pinning the client's literal to the
engine's value would make the coupling official and would have to be edited by
anyone renaming the decision, which costs more than the failure it prevents: a
menu that renders as a plain list of buttons.

## Item 3: the faces

The review counted six tables. At the time of this decision there are three,
and they are not alike, because the typed log slice had already built the shared
home and converted the client to it.

`shared/.../projection/DieFaces.scala` is that home: seven faces, each with its
wire `key`, the `word` it is read by, and the `die` it is printed on. The client
reads `word` from it and keeps only glyph composition, which has no server twin.

- `WalkerDecisionProjector.faceName` restates the wire keys in the application
  layer, which imports the protocol module freely and has a model-to-shared
  mapping next door in `LogWords.rolledFace`. **This one goes.** The decision
  wire's roll faces become `Vector[DieFace]`, the mapping is written once as
  `application.RolledFaces` and read by both wires, and the set-equality test
  that held the two tables in agreement is replaced by the compiler. The
  projection is rebuilt per request and has never been persisted, so typing it
  costs no journal compatibility. Two more tables go with it, both made dead by
  the typing: the client's pool-to-die guess, since a typed face carries its
  own die, and the roll row's unknown-key fallback.
- `GameEventJsonSupport` (four encode and decode tables) and
  `WalkerValueCodec.decodeDieFace` are durable journal codecs in
  `serialization`, which imports the protocol module nowhere. **These stay.**
  Reading the projection protocol's enum from a durable codec is the
  durable-depends-on-transient inversion the 2026-09-25 record describes: a
  rename made for the projection's benefit would silently change what recorded
  rolls mean. The duplication is visible and inert; that coupling would be
  invisible and live.

## Considered and rejected

- **A shared constants object holding the decision id, the option ids and the
  power prefix.** Rejected. It is the fix the reviews kept reaching for, and it
  solves the wrong half: the client does not need the names, it needs the
  classification. Sharing the names would make the client's knowledge of all
  fifteen actions official, and would park game facts in the module whose job is
  message shapes.
- **A drift test pinning the client's literals to the engine's ids.** Rejected
  for the option ids, because publishing the classification removes the literals
  instead of freezing them. Rejected for the one remaining routing literal on
  cost grounds, as above.
- **The projection carrying group order and heading words.** Rejected: it moves
  layout decisions to the server and adds UI copy to the wire.
- **A `wire` member on the `AttackDieFace` and `DefenseDieFace` enums in
  `model`.** Rejected: a shared home already exists, and this would build a
  second one beside it.
- **The projector classifying menu options by asking `ActOffers` directly**
  (`application` may import `gameplay`). Not unreasonable, and smaller than the
  decorator. Rejected because a projector with a special case for `phase.menu`
  is the same shape of knowledge the client is being relieved of, moved one
  module inward; the decorator keeps the projector general.

## What remains duplicated, on purpose

- `MenuPanel.decisionId` and `PhaseMenu.decisionId` both spell `"phase.menu"`.
- `DieFaceChip.symbols` keys glyph vectors by face key. It is held to
  `DieFace.values` by a suite, and the glyph names are the client's own drawing
  concern.
- `GameEventJsonSupport` and `WalkerValueCodec` spell the seven face keys for
  the journal, independently of the shared enum.

## Consequences

- A review that notices any of the three remaining overlaps should read this
  file rather than propose removing them. That they are spelled twice is
  recorded here and is not new information.
- A new Act action needs an `ActionClass`, and no client change.
- A new die face needs an entry in the shared enum, a mapping in
  `RolledFaces`, a glyph vector in `DieFaceChip`, and entries in the two
  journal codecs. The suite that holds `symbols` to `DieFace.values` will name
  the first omission.
- Renaming an option id or the decision id remains a journal format change, not
  a refactor. Nothing in this decision makes it easier, and the client copy
  never made it harder.
- Revisit item 2 if panel routing becomes server-driven for other panels, and
  item 3's journal tables if the journal gains a migration facility.
