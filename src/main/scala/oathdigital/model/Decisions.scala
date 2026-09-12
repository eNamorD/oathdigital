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
// The cost, stated plainly: a button's label, a section's label and the two
// strings a panel titles itself with are prompt copy, and they live here.
// Game-object names do not. A relic's name and a denizen's title are
// resolved by `GamePresentationProjector` from an option's reference at
// projection time and never enter a query, an answer or a validator.
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
sealed trait DecisionOptionRef extends Product with Serializable {
  /** Which of the seven variants this is, as a stable wire string. */
  def kind: String

  /** The variant's identity as a stable wire string, paired with [[kind]].
    *
    * A reference travels off-process three ways — in a submitted answer, in
    * a journalled answer, and (from Task 4) in a projected option — and all
    * three spell it as this pair. The spelling lives here, next to the cases
    * it names, so those three code paths cannot drift into three
    * independently written tables of the same seven strings; that drift is
    * the failure mode this whole vocabulary exists to remove.
    */
  def wireId: String
}
object DecisionOptionRef {
  /** A choice with no game object behind it, keyed by a stable string
    * (e.g. `"continue"`, `"stop"`).
    */
  final case class Button(key: String) extends DecisionOptionRef {
    val kind: String = "button"
    def wireId: String = key
  }
  final case class Player(id: PlayerId) extends DecisionOptionRef {
    val kind: String = "player"
    def wireId: String = id.value
  }
  final case class Site(id: SiteId) extends DecisionOptionRef {
    val kind: String = "site"
    def wireId: String = id.value
  }
  final case class Denizen(id: DenizenId) extends DecisionOptionRef {
    val kind: String = "denizen"
    def wireId: String = id.value
  }
  final case class Relic(id: RelicId) extends DecisionOptionRef {
    val kind: String = "relic"
    def wireId: String = id.value
  }
  final case class Vision(id: VisionId) extends DecisionOptionRef {
    val kind: String = "vision"
    def wireId: String = id.value
  }
  final case class Deck(id: CardDeck) extends DecisionOptionRef {
    val kind: String = "deck"
    def wireId: String = id.key
  }

  /** Safe parse of the [[DecisionOptionRef.kind]]/[[DecisionOptionRef.wireId]]
    * pair from untrusted input: `None` for an unknown kind or an id that
    * variant cannot carry, never a thrown `require`.
    *
    * Total over the seven variants, and the exact inverse of the two
    * accessors above — a new variant that forgets this method fails to
    * compile, because the match below is exhaustive over nothing and the
    * accessors are abstract.
    */
  def fromWire(kind: String, wireId: String): Option[DecisionOptionRef] =
    if (wireId.trim.isEmpty) None
    else kind match {
      case "button" => Some(Button(wireId))
      case "player" => Some(Player(PlayerId(wireId)))
      case "site" => Some(Site(SiteId(wireId)))
      case "denizen" => Some(Denizen(DenizenId(wireId)))
      case "relic" => Some(Relic(RelicId(wireId)))
      case "vision" => Some(Vision(VisionId(wireId)))
      case "deck" => CardDeck.fromKey(wireId).map(Deck(_))
      case _ => None
    }
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
sealed trait DecisionQuery extends Product with Serializable {
  /** What the panel asking this question calls itself, authored by the
    * action that declared the decision.
    *
    * On the trait because every shape has a frame to title. Optional
    * because a decision may have nothing worth saying above its options,
    * and a client that is handed no heading falls back to generic copy of
    * its own rather than being given one from here.
    *
    * The action is the only place that knows what to call its own question,
    * which is the same reason a button carries its label — and the
    * alternative, already tried and deleted, was a frontend helper
    * branching on the action name to title a panel whose interaction was
    * otherwise entirely generic.
    *
    * Copy NEVER affects legality. `DecisionQueries` reads options and
    * sections and never this, which is what lets a prompt be rewritten —
    * by an author or by a power — without invalidating a single recorded
    * answer.
    */
  def heading: Option[String]
}
object DecisionQuery {
  /** Pick exactly one of `options`. */
  final case class ChooseOne(options: Vector[DecisionOption],
      heading: Option[String] = None) extends DecisionQuery

  /** Spread every option across the declared sections, respecting each
    * section's minimum.
    *
    * @param confirmLabel what the control that submits the arrangement is
    *   called. On this shape alone, and the asymmetry is the point: a
    *   choose-one answer submits the moment an option is clicked, so there
    *   is no confirm step to name and a field for one would mean nothing.
    */
  final case class Partition(sections: Vector[DecisionSection],
      options: Vector[DecisionOption], heading: Option[String] = None,
      confirmLabel: Option[String] = None) extends DecisionQuery
}

/** One option assigned to one section in a [[DecisionAnswer.PartitionAnswer]].
  */
final case class DecisionPlacement(option: DecisionOptionRef,
    sectionKey: String)

/** What a player submitted for a walker `Decide` leaf, stored in
  * `PendingTree.answered`.
  *
  * The answer must be MODEL-safe: answered decisions are persisted on
  * `CurrentGameState.walkerPending` between commands (the legacy
  * `PendingProcedure` precedent stores model answers the same way), so the
  * family and every concrete case live in the model, never importing
  * gameplay.
  *
  * SEALED, and this reversed an earlier decision, so the reason matters.
  * The family used to be open so a power could declare its own answer case.
  * That freedom is now unreachable: [[DecisionQuery]] is sealed, and
  * `DecisionQueries.accepts` is total over it and rejects anything that is
  * not one of the two cases below. A third case could therefore be
  * constructed but never recorded — while every encoder over the family had
  * to carry a runtime throw for a case the walker cannot produce. Sealing
  * turns that structural fact into a compile-time one: the journal codec's
  * match is exhaustive with no fallthrough, and a genuinely new answer shape
  * is a change to this file, to the query it answers, and to the validator,
  * which is where such a change belongs.
  */
sealed trait DecisionAnswer extends Product with Serializable

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
}
