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
sealed trait DecisionOptionRef extends Product with Serializable:
  /** Which of the seventeen variants this is, as a stable wire string. */
  def kind: String

  /** The variant's identity as a stable wire string, paired with [[kind]].
    *
    * A reference travels off-process three ways — in a submitted answer, in
    * a journalled answer, and (from Task 4) in a projected option — and all
    * three spell it as this pair. The spelling lives here, next to the cases
    * it names, so those three code paths cannot drift into three
    * independently written tables of the same eight strings; that drift is
    * the failure mode this whole vocabulary exists to remove.
    */
  def wireId: String
object DecisionOptionRef:
  /** A choice with no game object behind it, keyed by a stable string
    * (e.g. `"continue"`, `"stop"`).
    */
  final case class Button(key: String) extends DecisionOptionRef:
    val kind: String = "button"
    def wireId: String = key
  final case class Player(id: PlayerId) extends DecisionOptionRef:
    val kind: String = "player"
    def wireId: String = id.value
  final case class Site(id: SiteId) extends DecisionOptionRef:
    val kind: String = "site"
    def wireId: String = id.value
  final case class Denizen(id: DenizenId) extends DecisionOptionRef:
    val kind: String = "denizen"
    def wireId: String = id.value
  final case class Relic(id: RelicId) extends DecisionOptionRef:
    val kind: String = "relic"
    def wireId: String = id.value
  final case class Vision(id: VisionId) extends DecisionOptionRef:
    val kind: String = "vision"
    def wireId: String = id.value
  final case class Edifice(id: EdificeId) extends DecisionOptionRef:
    val kind: String = "edifice"
    def wireId: String = id.value
  /** One relic of another player, named by its position in their relic
    * vector rather than by the card, so a facedown relic's identity is never
    * disclosed by an option. The owner may contain a colon; the slot is
    * always the text after the last one.
    */
  final case class RelicSlot(owner: PlayerId, slot: Int)
      extends DecisionOptionRef:
    require(slot >= 0, "a relic slot must be non-negative")
    val kind: String = "relic-slot"
    def wireId: String = s"${owner.value}:$slot"
  /** One adviser slot of a player, named by its position in their advisers
    * rather than by the card, so a facedown adviser's identity is never
    * disclosed by an option. A slot below the printed capacity of three may
    * be empty; the powers that name a held adviser name an occupied one. The
    * owner may contain a colon; the slot is always the text after the last
    * one.
    */
  final case class AdviserSlot(owner: PlayerId, slot: Int)
      extends DecisionOptionRef:
    require(slot >= 0, "an adviser slot must be non-negative")
    val kind: String = "adviser-slot"
    def wireId: String = s"${owner.value}:$slot"
  /** A banner. It names no holder: who holds it is read from live state, so
    * an answer cannot name a holder that has since changed.
    */
  final case class Banner(banner: oathdigital.model.Banner)
      extends DecisionOptionRef:
    val kind: String = "banner"
    def wireId: String = banner.key
  final case class Deck(id: CardDeck) extends DecisionOptionRef:
    val kind: String = "deck"
    def wireId: String = id.key
  /** A suit's favor bank: the one shared place favor of that suit returns
    * to. A suit is a closed six-case enum, so this can never be absent.
    */
  final case class FavorBank(suit: Suit) extends DecisionOptionRef:
    val kind: String = "favor-bank"
    def wireId: String = suit.key

  /** Slot `index` of `site`'s card row, below the site's capacity: the empty
    * slot a card is played to, or the card a play there replaces. The site
    * may contain a colon; the index is always the text after the last one.
    */
  final case class SiteSlot(site: SiteId, index: Int) extends DecisionOptionRef:
    require(index >= 0, "a site slot must be non-negative")
    val kind: String = "site-slot"
    def wireId: String = s"${site.value}:$index"
  /** Relic `index` of `site`'s relics, in their order there: a relic named
    * by its place rather than by the card, so a facedown relic's identity
    * is never disclosed by an option. The site may contain a colon; the
    * index is always the text after the last one.
    */
  final case class SiteRelicSlot(site: SiteId, index: Int)
      extends DecisionOptionRef:
    require(index >= 0, "a site relic slot must be non-negative")
    val kind: String = "site-relic-slot"
    def wireId: String = s"${site.value}:$index"
  /** Playing the card to `owner`'s adviser slot `index`, faceup or facedown.
    * A choose-one answer is one ref, so the ref carries the facing, and an
    * adviser slot is offered as two options. The owner may contain a colon;
    * the facing follows the last one and the index the one before it.
    */
  final case class AdviserPlay(owner: PlayerId, index: Int, faceup: Boolean)
      extends DecisionOptionRef:
    require(index >= 0, "an adviser slot must be non-negative")
    val kind: String = "adviser-play"
    def wireId: String =
      s"${owner.value}:$index:${if faceup then "faceup" else "facedown"}"
  /** Playing a Vision faceup as `owner`'s revealed Vision. A faceup
    * Conspiracy takes no slot, so it never names this. */
  final case class RevealedVision(owner: PlayerId) extends DecisionOptionRef:
    val kind: String = "revealed-vision"
    def wireId: String = owner.value
  /** Discarding the played card to `region`'s discard pile. */
  final case class DiscardPile(region: Region) extends DecisionOptionRef:
    val kind: String = "discard-pile"
    def wireId: String = region.key

  /** Safe parse of the [[DecisionOptionRef.kind]]/[[DecisionOptionRef.wireId]]
    * pair from untrusted input: `None` for an unknown kind or an id that
    * variant cannot carry, never a thrown `require`.
    *
    * Total over the seventeen variants, and the exact inverse of the two
    * accessors above — a new variant that forgets this method fails to
    * compile, because the match below is exhaustive over nothing and the
    * accessors are abstract.
    */
  def fromWire(kind: String, wireId: String): Option[DecisionOptionRef] =
    if wireId.trim.isEmpty then None
    else kind match
      case "button" => Some(Button(wireId))
      case "player" => Some(Player(PlayerId(wireId)))
      case "site" => Some(Site(SiteId(wireId)))
      case "denizen" => Some(Denizen(DenizenId(wireId)))
      case "relic" => Some(Relic(RelicId(wireId)))
      case "vision" => Some(Vision(VisionId(wireId)))
      case "edifice" => Some(Edifice(EdificeId(wireId)))
      case "relic-slot" => slotOf(wireId).map((owner, slot) =>
        RelicSlot(owner, slot))
      case "adviser-slot" => slotOf(wireId).map((owner, slot) =>
        AdviserSlot(owner, slot))
      case "banner" => oathdigital.model.Banner.fromKey(wireId).map(Banner(_))
      case "deck" => CardDeck.fromKey(wireId).map(Deck(_))
      case "favor-bank" => Suit.fromKey(wireId).map(FavorBank(_))
      case "site-slot" => indexOf(wireId).map((site, index) =>
        SiteSlot(SiteId(site), index))
      case "site-relic-slot" => indexOf(wireId).map((site, index) =>
        SiteRelicSlot(SiteId(site), index))
      case "adviser-play" => facingOf(wireId).flatMap((slot, faceup) =>
        slotOf(slot).map((owner, index) => AdviserPlay(owner, index, faceup)))
      case "revealed-vision" => Some(RevealedVision(PlayerId(wireId)))
      case "discard-pile" => Region.all.find(_.key == wireId).map(DiscardPile(_))
      case _ => None

  /** The owner and non-negative slot of a slot reference's wire id. */
  private def slotOf(wireId: String): Option[(PlayerId, Int)] =
    indexOf(wireId).map((owner, slot) => PlayerId(owner) -> slot)

  /** The text before the last colon, which may hold colons itself, and the
    * non-negative index after it. */
  private def indexOf(wireId: String): Option[(String, Int)] =
    val at = wireId.lastIndexOf(':')
    Option.when(at > 0)(wireId.take(at)).filter(_.trim.nonEmpty)
      .flatMap(owner => wireId.drop(at + 1).toIntOption
        .filter(_ >= 0).map(owner -> _))

  /** An adviser play's slot part and facing: the facing follows the last
    * colon. */
  private def facingOf(wireId: String): Option[(String, Boolean)] =
    val at = wireId.lastIndexOf(':')
    Option.when(at > 0)(wireId.take(at)).flatMap(slot =>
      wireId.drop(at + 1) match
        case "faceup" => Some(slot -> true)
        case "facedown" => Some(slot -> false)
        case _ => None)

/** One selectable option on a decision: its stable reference, plus whatever
  * display detail the action itself authors.
  *
  * Only [[DecisionOption.Button]] authors any, because only a button has no
  * game object whose name the projector could resolve. Every other case is
  * its reference and nothing more; it exists as a distinct case so that a
  * query cannot pair a button's label with a card, and so the projector can
  * dispatch on the option kind.
  */
sealed trait DecisionOption extends Product with Serializable:
  def ref: DecisionOptionRef

  /** Whether a player may choose the option now. Only
    * [[DecisionOption.Unavailable]] says no, and a wrapper asks what it
    * wraps.
    */
  def available: Boolean = true

  /** Whether the option may not be chosen now but its blockers may be asked
    * to waive it (consent design). */
  def askable: Boolean = false
object DecisionOption:
  final case class Button(ref: DecisionOptionRef.Button, label: String)
      extends DecisionOption
  final case class Player(ref: DecisionOptionRef.Player) extends DecisionOption
  final case class Site(ref: DecisionOptionRef.Site) extends DecisionOption
  final case class Denizen(ref: DecisionOptionRef.Denizen)
      extends DecisionOption
  final case class Relic(ref: DecisionOptionRef.Relic) extends DecisionOption
  final case class Vision(ref: DecisionOptionRef.Vision) extends DecisionOption
  final case class Edifice(ref: DecisionOptionRef.Edifice) extends DecisionOption
  final case class RelicSlot(ref: DecisionOptionRef.RelicSlot)
      extends DecisionOption
  final case class AdviserSlot(ref: DecisionOptionRef.AdviserSlot)
      extends DecisionOption
  final case class Banner(ref: DecisionOptionRef.Banner) extends DecisionOption
  final case class Deck(ref: DecisionOptionRef.Deck) extends DecisionOption
  final case class FavorBank(ref: DecisionOptionRef.FavorBank)
      extends DecisionOption
  final case class SiteSlot(ref: DecisionOptionRef.SiteSlot)
      extends DecisionOption
  final case class SiteRelicSlot(ref: DecisionOptionRef.SiteRelicSlot)
      extends DecisionOption
  final case class AdviserPlay(ref: DecisionOptionRef.AdviserPlay)
      extends DecisionOption
  final case class RevealedVision(ref: DecisionOptionRef.RevealedVision)
      extends DecisionOption
  final case class DiscardPile(ref: DecisionOptionRef.DiscardPile)
      extends DecisionOption

  /** An option that states what choosing it costs, which the projector words.
    * The choice is the wrapped option's: `ref` is its reference, so an answer
    * names the same thing whether or not the option carries a price, and the
    * price never affects legality.
    */
  final case class Priced(option: DecisionOption, price: OptionPrice)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = option.available

  /** An option carrying one short label a client draws as a chip: what kind of
    * thing the option is, as opposed to what choosing it costs. Wrapping for
    * the same reason `Priced` wraps -- `ref` is the wrapped option's, so an
    * answer names the same thing badge or no badge, and the badge never
    * affects legality.
    */
  final case class Badged(option: DecisionOption, badge: String)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = option.available

  /** An option shown with why it may not be chosen now. `ref` is the wrapped
    * option's, and the answer check reads only available options, so an
    * answer naming it is refused as an absent option is.
    */
  final case class Unavailable(option: DecisionOption, reason: OptionReason)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = false
    override def askable: Boolean = reason.isInstanceOf[OptionReason.Askable]

  /** An option shown with a reason to think twice, still choosable: a
    * selected power that has no effect on it says so (Hunting Party on a
    * discard pile). `ref` is the wrapped option's, so the warning never
    * affects legality. */
  final case class Warned(option: DecisionOption, reason: OptionReason)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = option.available

  /** An option whose play discards `card` to make room: the card in that
    * site slot or adviser slot, or the revealed Vision a new one displaces.
    * Wrapping for the reason `Priced` wraps: `ref` is the wrapped option's,
    * so the mark never affects legality. The projector names the card.
    */
  final case class Replacing(option: DecisionOption, card: CardId)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = option.available

  /** An option whose play buries `card` at the site first (Forest Warden):
    * the card in that site slot, or the relic in that site relic slot.
    * Wrapping for the reason `Replacing` wraps: `ref` is the wrapped
    * option's. The projector names the card as the viewer may see it.
    */
  final case class Burying(option: DecisionOption, card: CardId)
      extends DecisionOption:
    def ref: DecisionOptionRef = option.ref
    override def available: Boolean = option.available

  /** The option presenting `ref`, for every kind whose name the projector
    * resolves itself. A button has no such name: its label is authored, so
    * a reference alone cannot present one.
    */
  def forRef(ref: DecisionOptionRef): Option[DecisionOption] = ref match
    case _: DecisionOptionRef.Button => None
    case value: DecisionOptionRef.Player => Some(Player(value))
    case value: DecisionOptionRef.Site => Some(Site(value))
    case value: DecisionOptionRef.Denizen => Some(Denizen(value))
    case value: DecisionOptionRef.Relic => Some(Relic(value))
    case value: DecisionOptionRef.Vision => Some(Vision(value))
    case value: DecisionOptionRef.Edifice => Some(Edifice(value))
    case value: DecisionOptionRef.RelicSlot => Some(RelicSlot(value))
    case value: DecisionOptionRef.AdviserSlot => Some(AdviserSlot(value))
    case value: DecisionOptionRef.Banner => Some(Banner(value))
    case value: DecisionOptionRef.Deck => Some(Deck(value))
    case value: DecisionOptionRef.FavorBank => Some(FavorBank(value))
    case value: DecisionOptionRef.SiteSlot => Some(SiteSlot(value))
    case value: DecisionOptionRef.SiteRelicSlot => Some(SiteRelicSlot(value))
    case value: DecisionOptionRef.AdviserPlay => Some(AdviserPlay(value))
    case value: DecisionOptionRef.RevealedVision => Some(RevealedVision(value))
    case value: DecisionOptionRef.DiscardPile => Some(DiscardPile(value))

/** What choosing an option costs the chooser, as a dry run of the choice found
  * it: favor and secrets paid (placed, or burnt to the shared bank) and
  * warbands sacrificed. Everything is a count of what leaves the chooser.
  * `secretsX` marks a price in secrets that the player chooses when the
  * option is applied (Arcane Armor's X).
  */
final case class OptionPrice(favor: Int = 0, secrets: Int = 0,
    favorBurnt: Int = 0, secretsBurnt: Int = 0, warbands: Int = 0,
    secretsX: Boolean = false):
  require(favor >= 0 && secrets >= 0 && favorBurnt >= 0 && secretsBurnt >= 0 &&
    warbands >= 0, "an option price is never negative")
  def isFree: Boolean = this == OptionPrice()

/** Why an option is shown but may not be chosen. It is player text, either
  * written by the builder that withheld the option, or the note of the
  * power that hid it, which the projector words as the Game Log does.
  */
sealed trait OptionReason extends Product with Serializable
object OptionReason:
  final case class Written(text: String) extends OptionReason
  final case class Noted(power: PowerId, note: LogNote) extends OptionReason
  /** Blocked only by waivable powers whose rulers may be asked (consent
    * design, ruling 8). `power` and `note` are the first such power and its
    * consent-worded reason; `asks` are the granters with their powers.
    * `covers` are the keys a grant is remembered under when they are not
    * the asked option's: an operation restriction's grant covers the
    * operation it refuses (slice 3). */
  final case class Askable(power: PowerId, note: LogNote,
      asks: Vector[ConsentAsk], covers: Vector[ConsentKey] = Vector.empty)
      extends OptionReason

/** One named bucket a [[DecisionQuery.Partition]] spreads its options across.
  *
  * @param key stable identity, and what a [[DecisionPlacement]] names.
  * @param label prompt copy for the section, authored by the action.
  * @param minRequired fewest options the section must receive; `0` means the
  *   section may be left empty. Never negative.
  */
final case class DecisionSection(key: String, label: String, minRequired: Int,
    maxAllowed: Option[Int] = None)

/** One amount-taking slot of a [[DecisionQuery.Distribute]].
  *
  * @param ref what the slot distributes to. The projector presents it
  *   through [[DecisionOption.forRef]], so a button cannot be a slot.
  * @param minimum fewest the slot may take; never negative.
  * @param maximum most the slot may take; never below `minimum`.
  * @param suggested the amount a client's draft opens at. Either every slot
  *   of a query suggests one or none does, and the suggestions together must
  *   be an accepted answer.
  */
final case class DistributeSlot(ref: DecisionOptionRef, minimum: Int,
    maximum: Int, suggested: Option[Int])

/** A running sum a client keeps while a choose-many is answered: each
  * selected or locked option adds its value. `limit` is the most the sum may
  * reach, and a client blocks an option while choosing it would pass the
  * limit. It is advice: the answer check never reads it, so the action that
  * declares a tally still refuses an answer that breaks it.
  *
  * @param label the total's name, "Defending force".
  * @param unit what a value counts, "warbands", for a row and a limit's
  *   reason.
  */
final case class DecisionTally(label: String, unit: String,
    perOption: Map[DecisionOptionRef, Int], limit: Option[Int] = None)

/** The line a choose-many's panel states its selection with: "Target 2
  * sites" is verb "Target", one "site", many "sites".
  */
final case class DecisionSummary(verb: String, one: String, many: String):
  def of(count: Int): String =
    s"$verb $count ${if count == 1 then one else many}"

/** What a choose-many's confirm button says, counted off the selection:
  * "Reveal 2 cards" is verb "Reveal", one "card", many "cards".
  *
  * @param none what the button says with nothing selected, when the
  *   counted form would read badly ("Reveal nothing", not "Reveal 0
  *   cards"). Without it the counted form stands at zero too.
  * @param countsLocked whether the locked options count toward the
  *   number: a Campaign's mandatory target is one of the targets it
  *   attacks, so "Raid 1 target" before any pick.
  */
final case class CountedConfirm(verb: String, one: String, many: String,
    none: Option[String] = None, countsLocked: Boolean = false)

/** Where a decision is answered. `Board` puts every option with a place on
  * the table there; `Panel` keeps the whole decision in the Action panel.
  */
enum DecisionPresentation(val key: String):
  case Board extends DecisionPresentation("board")
  case Panel extends DecisionPresentation("panel")

/** The question a parked `Decide` asks.
  *
  * Sealed, unlike [[DecisionAnswer]]: an action declares its decision by
  * populating one of these shapes, never by inventing a new shape, which is
  * what lets one generic validator and one generic projector serve every
  * action. A new shape is a change to this file and to both of them.
  */
sealed trait DecisionQuery extends Product with Serializable:
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
object DecisionQuery:
  /** Pick exactly one of `options`.
    *
    * `confirmVerb` is display only, like `heading`: where the pick is made
    * on the board and then confirmed, the button reads the verb and the
    * picked option, "Travel to Deep Woods". Without one it reads "Confirm
    * Deep Woods".
    */
  final case class ChooseOne(options: Vector[DecisionOption],
      heading: Option[String] = None,
      confirmVerb: Option[String] = None) extends DecisionQuery

  /** Pick between `min` and `max` distinct available options, inclusive.
    * Well-formed only when `0 <= min <= max <= n`, `max >= 1`, and not the
    * forced case `min == max == n`, where `n` counts the options that may be
    * chosen. `min == 0` makes the pick optional: the empty selection is an
    * answer. An exact count is `min == max`.
    *
    * `locked` options are decided already, such as a Campaign's origin: a
    * client shows them chosen, and no answer names them. `tallies`,
    * `summary` and `confirm` are display only, like `heading`; a query with
    * no `confirm` gets the client's generic "Confirm".
    */
  final case class ChooseMany(min: Int, max: Int,
      options: Vector[DecisionOption], heading: Option[String] = None,
      locked: Vector[DecisionOption] = Vector.empty,
      tallies: Vector[DecisionTally] = Vector.empty,
      summary: Option[DecisionSummary] = None,
      confirm: Option[CountedConfirm] = None) extends DecisionQuery

  /** Pick an integer from `min` to `max` inclusive. Both labels are required,
    * as on [[Distribute]]: the panel has a confirm step, and a range with no
    * heading says nothing about what is being chosen. A single-value range is
    * still a decision the player confirms.
    *
    * `suggested` is where the panel opens, for a question whose usual answer
    * is not its minimum -- Campaign's force, where an attacker nearly always
    * commits everything. It is display only: the answer is still whatever the
    * player confirms, and it is clamped to the range, so a suggestion can
    * never widen what is legal.
    */
  final case class ChooseAmount(min: Int, max: Int, heading: Option[String],
      confirmLabel: String, suggested: Option[Int] = None)
      extends DecisionQuery

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

  /** Spread an amount across the slots, each within its own bounds, so that
    * the amounts sum to between `minTotal` and `maxTotal` inclusive. An exact
    * distribution sets both to one value ([[Distribute.exactly]]).
    *
    * Both labels are required. `heading` keeps the `Option` type that
    * [[DecisionQuery.heading]] declares, and `DecisionQueries.wellFormed`
    * rejects `None` or blank. A distribution has a confirm step, so it
    * always names its confirm control.
    */
  final case class Distribute(slots: Vector[DistributeSlot], minTotal: Int,
      maxTotal: Int, heading: Option[String], confirmLabel: String)
      extends DecisionQuery
  object Distribute:
    /** A distribution whose amounts must sum to exactly `total`. */
    def exactly(slots: Vector[DistributeSlot], total: Int,
        heading: Option[String], confirmLabel: String): Distribute =
      Distribute(slots, total, total, heading, confirmLabel)

  /** A negotiation deal, as a snapshot the action rebuilds from live state and
    * the recorded answers on every command: who is in it, everyone's current
    * terms, who has accepted, what each author may still offer, and who may
    * accept now. Legality depends on WHO answers (each author has their own
    * bounds), which is why `DecisionQueries.accepts` receives the answerer.
    * Any participant may answer it: the deal is a `Decide` with co-owners.
    */
  final case class Negotiate(participants: Vector[PlayerId],
      terms: Map[PlayerId, NegotiationTerms], accepted: Set[PlayerId],
      bounds: Map[PlayerId, NegotiationBounds], acceptors: Set[PlayerId],
      heading: Option[String] = None) extends DecisionQuery

  /** Look at `cards`, in order, then press Done (catalog batch 2, N5). There
    * is no choice: the one answer is the choose-one answer of the `Done`
    * button, labelled `doneLabel`. The action records a `Peek` of each card
    * before it, so the owner may identify them. The heading is required: a
    * list of cards with no heading says nothing about where they lie.
    */
  final case class Inspect(cards: Vector[CardId], heading: Option[String],
      doneLabel: String = "Done") extends DecisionQuery
  object Inspect:
    /** The one answer an `Inspect` takes. */
    val Done: DecisionOptionRef.Button = DecisionOptionRef.Button("done")

/** One option assigned to one section in a [[DecisionAnswer.PartitionAnswer]].
  */
final case class DecisionPlacement(option: DecisionOptionRef,
    sectionKey: String)

/** One slot's amount in a [[DecisionAnswer.DistributeAnswer]]. */
final case class DistributeAmount(ref: DecisionOptionRef, amount: Int)

/** What a player submitted for a walker `Decide` leaf, stored in
  * `PendingTree.answered`.
  *
  * The answer must be MODEL-safe: answered decisions are persisted on
  * `CurrentGameState.walkerPending` between commands, so the family and every
  * concrete case live in the model, never importing gameplay.
  *
  * SEALED, and this reversed an earlier decision, so the reason matters.
  * The family used to be open so a power could declare its own answer case.
  * That freedom is now unreachable: [[DecisionQuery]] is sealed, and
  * `DecisionQueries.accepts` is total over it and rejects anything that is
  * not one of the cases below. A fourth case could therefore be
  * constructed but never recorded — while every encoder over the family had
  * to carry a runtime throw for a case the walker cannot produce. Sealing
  * turns that structural fact into a compile-time one: the journal codec's
  * match is exhaustive with no fallthrough, and a genuinely new answer shape
  * is a change to this file, to the query it answers, and to the validator,
  * which is where such a change belongs.
  */
sealed trait DecisionAnswer extends Product with Serializable

object DecisionAnswer:
  /** Answer to a [[DecisionQuery.ChooseOne]]: the one option reference the
    * player selected.
    */
  final case class ChooseOneAnswer(selected: DecisionOptionRef)
      extends DecisionAnswer

  /** Answer to a [[DecisionQuery.ChooseMany]]: the distinct option
    * references the player selected, exactly the query's `count` of them.
    */
  final case class ChooseManyAnswer(selected: Vector[DecisionOptionRef])
      extends DecisionAnswer

  /** Answer to a [[DecisionQuery.ChooseAmount]]: the amount picked. */
  final case class ChooseAmountAnswer(amount: Int) extends DecisionAnswer

  /** Answer to a [[DecisionQuery.Partition]]: every declared option
    * reference, each placed in exactly one declared section.
    */
  final case class PartitionAnswer(placements: Vector[DecisionPlacement])
      extends DecisionAnswer

  /** Answer to a [[DecisionQuery.Distribute]]: every declared slot exactly
    * once, each with its amount.
    */
  final case class DistributeAnswer(amounts: Vector[DistributeAmount])
      extends DecisionAnswer

  /** Answer to a [[DecisionQuery.Negotiate]]: this author's complete terms,
    * replacing their earlier ones and clearing every acceptance.
    */
  final case class ProposeTerms(terms: NegotiationTerms) extends DecisionAnswer

  /** Answer to a [[DecisionQuery.Negotiate]]: accepts the current deal. */
  case object AcceptDeal extends DecisionAnswer

  /** Answer to a [[DecisionQuery.Negotiate]]: declines, which ends it. */
  case object DeclineDeal extends DecisionAnswer
