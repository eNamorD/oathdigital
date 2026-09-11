package oathdigital.model

// The complete walker decision contract: what a parked `Decide` may ask
// (`DecisionQuery` and the option vocabulary it is built from) and what a
// player may answer (`DecisionAnswer`).
//
// The answer half MUST live in the model: answered decisions are persisted
// on `CurrentGameState.walkerPending` between commands, so they cannot name
// a gameplay type. The question half lives here by CHOICE rather than by
// that constraint. Splitting an option into a persisted
// `DecisionOptionRef` and a display-carrying `DecisionOption` removed the
// layering pressure that would have forced the question half into
// gameplay, and one file that states both what may be asked and what may be
// answered is worth more than the symmetry of separating them. Do not
// "restore" the split.
//
// The cost, stated plainly: a button's label and a section's label are
// prompt copy, and they live here. Game-object names do not. A relic's name
// and a denizen's title are resolved by `GamePresentationProjector` from an
// option's reference at projection time and never enter a query, an answer
// or a validator.
//
// The generic validator is the one piece that is NOT here: it returns typed
// `OathViolation`s, which the model may not name, so it lives in
// `gameplay/walker/DecisionQueries.scala`.

/** Stable identity of one selectable thing in a decision.
  *
  * This is the half that persists: an answer records references, never
  * options, so a relabelled prompt leaves every recorded answer valid. A
  * reference carries identity and nothing else — a card reference names the
  * card globally and deliberately omits the site or player it is currently
  * at, because location is a legality fact used while BUILDING a query, not
  * part of a card's identity.
  */
sealed trait DecisionOptionRef extends Product with Serializable
object DecisionOptionRef {
  /** A choice with no game object behind it, keyed by a stable string
    * (e.g. `"continue"`, `"stop"`).
    */
  final case class Button(key: String) extends DecisionOptionRef
  final case class Player(id: PlayerId) extends DecisionOptionRef
  final case class Site(id: SiteId) extends DecisionOptionRef
  final case class Denizen(id: DenizenId) extends DecisionOptionRef
  final case class Relic(id: RelicId) extends DecisionOptionRef
  final case class Vision(id: VisionId) extends DecisionOptionRef
  final case class Deck(id: CardDeck) extends DecisionOptionRef
}

/** One selectable option on a decision: its stable reference, plus whatever
  * display detail the action itself authors.
  *
  * Only [[DecisionOption.Button]] authors any, because only a button has no
  * game object whose name the projector could resolve. Every other case is
  * its reference and nothing more; it exists as a distinct case so that a
  * query cannot pair a button's label with a card, and so the projector can
  * dispatch on the option kind.
  */
sealed trait DecisionOption extends Product with Serializable {
  def ref: DecisionOptionRef
}
object DecisionOption {
  final case class Button(ref: DecisionOptionRef.Button, label: String)
      extends DecisionOption
  final case class Player(ref: DecisionOptionRef.Player) extends DecisionOption
  final case class Site(ref: DecisionOptionRef.Site) extends DecisionOption
  final case class Denizen(ref: DecisionOptionRef.Denizen)
      extends DecisionOption
  final case class Relic(ref: DecisionOptionRef.Relic) extends DecisionOption
  final case class Vision(ref: DecisionOptionRef.Vision) extends DecisionOption
  final case class Deck(ref: DecisionOptionRef.Deck) extends DecisionOption
}

/** One named bucket a [[DecisionQuery.Partition]] spreads its options across.
  *
  * @param key stable identity, and what a [[DecisionPlacement]] names.
  * @param label prompt copy for the section, authored by the action.
  * @param minRequired fewest options the section must receive; `0` means the
  *   section may be left empty. Never negative.
  */
final case class DecisionSection(key: String, label: String, minRequired: Int)

/** The question a parked `Decide` asks.
  *
  * Sealed, unlike [[DecisionAnswer]]: an action declares its decision by
  * populating one of these shapes, never by inventing a new shape, which is
  * what lets one generic validator and one generic projector serve every
  * action. A new shape is a change to this file and to both of them.
  */
sealed trait DecisionQuery extends Product with Serializable
object DecisionQuery {
  /** Pick exactly one of `options`. */
  final case class ChooseOne(options: Vector[DecisionOption])
      extends DecisionQuery

  /** Spread every option across the declared sections, respecting each
    * section's minimum.
    */
  final case class Partition(sections: Vector[DecisionSection],
      options: Vector[DecisionOption]) extends DecisionQuery
}

/** One option assigned to one section in a [[DecisionAnswer.PartitionAnswer]].
  */
final case class DecisionPlacement(option: DecisionOptionRef,
    sectionKey: String)

/** Open decision answer carried by a walker `Decide` leaf and stored in
  * `PendingTree.answered`.
  *
  * The answer must be MODEL-safe: answered decisions are persisted on
  * `CurrentGameState.walkerPending` between commands (the legacy
  * `PendingProcedure` precedent stores model answers the same way), so the
  * family and every concrete case live in the model, never importing
  * gameplay.
  *
  * Deliberately NOT sealed: a power or action may declare its own answer
  * case wherever it lives (the same-file-sealed restriction on the family
  * root is why the root stays open). The generic cases below are the ones
  * the engine validates against a [[DecisionQuery]].
  */
trait DecisionAnswer extends Product with Serializable

object DecisionAnswer {
  /** Answer to a [[DecisionQuery.ChooseOne]]: the one option reference the
    * player selected.
    */
  final case class ChooseOneAnswer(selected: DecisionOptionRef)
      extends DecisionAnswer

  /** Answer to a [[DecisionQuery.Partition]]: every declared option
    * reference, each placed in exactly one declared section.
    */
  final case class PartitionAnswer(placements: Vector[DecisionPlacement])
      extends DecisionAnswer

  /** Recover per-roll choice, resolved at the `"recover.choice"` decision:
    * continue rolling (another 1-supply payment) or stop and abandon without
    * a relic
    * (Stop is only legal while the recovery has not yet succeeded).
    */
  sealed trait RecoverChoice extends Product with Serializable
  object RecoverChoice {
    case object Continue extends RecoverChoice
    case object Stop extends RecoverChoice
  }

  /** Answer to the per-roll choice decision. */
  final case class RecoverChoiceAnswer(choice: RecoverChoice)
      extends DecisionAnswer

  /** Answer to the success-only `"recover.relic"` decision: which facedown
    * relic at the site the actor takes into their play area facedown.
    */
  final case class RecoverRelicAnswer(relicId: RelicId)
      extends DecisionAnswer

  /** Answer to the `"forge.assignment"` decision: which of the site's three
    * empty denizens receives each of the printed Forge cost's three
    * resources.
    *
    * Placed and shaped like [[RecoverRelicAnswer]] (Task 2 ruling R13):
    * plain model data next to the family root, carrying only the choice the
    * player made. [[ForgeResourceAssignment]] is already model data (it is
    * what the legacy `ForgeCommand.Complete` carried), so this case reuses it
    * rather than inventing a second spelling of the same fact. The forged
    * relic is deliberately NOT a field: it is the authoritative relic-deck
    * top, read off state when the trailing operation node runs (ruling R12),
    * so there is no free choice here to record.
    */
  final case class ForgeAssignmentAnswer(
      assignments: Vector[ForgeResourceAssignment]) extends DecisionAnswer
}
