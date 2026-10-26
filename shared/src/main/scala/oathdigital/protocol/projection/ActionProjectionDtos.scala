package oathdigital.protocol.projection

/** The parked `Decide`'s declared question, described rather than derived.
  *
  * This is the whole of Task 4: the projector no longer discovers what to
  * offer, it projects the transformed `DecisionQuery` the walker is actually
  * parked on. So a power that adds or removes an option changes what the
  * client is offered and what the engine accepts in the same edit, and the
  * Recover-specific relic-candidate field and the Forge-specific assignment
  * projection this replaced -- two hand-written candidate derivations that
  * agreed with the engine only by convention -- are gone.
  *
  * Sealed, one case per form, for the same reason the model's `DecisionQuery`
  * is: what each form carries is the form's own business, and a flat record
  * could spell a distribute with no slots or a choose-one with a confirm
  * label. Which form carries sections, which carries slots, and which has no
  * confirm step to name are all read off the case list below rather than
  * from prose here.
  *
  * There is deliberately NO prebuilt wire answer on an option. The client
  * already holds everything an answer needs: a `ChooseOneWire(kind, id)`, a
  * `PartitionWire` of placements, or a `DistributeWire` of amounts is built
  * from the same kind-and-id pair each option or slot carries. Embedding an
  * answer would duplicate the identity and couple these DTOs to the command
  * protocol for nothing.
  *
  * `heading` and `confirmLabel` are the panel's own prompt copy, passed
  * through from the query the action declared -- the frame around the
  * options, where an option's `label` is the copy on the option itself. This
  * is two optional strings, not the start of a form language: no layout, no
  * conditionals, no per-option copy beyond the label an option already
  * carries. A third piece of panel copy is a reason to ask what the panel is
  * really missing.
  */
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
      heading: Option[String] = None, confirmVerb: Option[String] = None)
      extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = options

  /** Pick between `minOptions` and `maxOptions` of the options that are
    * `available`. The bounds count OPTIONS; `ChooseAmount`'s bound a value,
    * which is why they are no longer one `minimum`/`maximum` pair serving
    * both. Options whose `state` is `locked` are decided already and are
    * never part of the answer. `tallies`, `summary` and `confirm` are
    * display only.
    */
  final case class ChooseMany(options: Vector[DecisionOptionProjection],
      minOptions: Int, maxOptions: Int, heading: Option[String] = None,
      tallies: Vector[DecisionTallyProjection] = Vector.empty,
      summary: Option[DecisionSummaryProjection] = None,
      confirm: Option[CountedConfirmProjection] = None)
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

  /** Look at `cards`, in order, then press `done` (catalog batch 2, N5).
    * Each card is its face if the owner may identify it, else its back.
    * `done` is the one option, answered as a choose-one.
    */
  final case class Inspect(cards: Vector[CardDetailsProjection],
      done: DecisionOptionProjection, heading: Option[String] = None)
      extends DecisionQueryProjection:
    def offeredOptions: Vector[DecisionOptionProjection] = Vector(done)

/** One selectable option: its stable reference as `kind` plus `id` -- the
  * exact pair `DecisionOptionRef` spells for a submitted answer and a
  * journalled one -- its display text, and for card-shaped options the same
  * [[CardDetailsProjection]] every other card projection carries, so
  * disclosure rules are inherited rather than restated.
  *
  * A button's `label` is the query's own declarative prompt copy, authored
  * by the action. Every other option's label is a game-object name resolved
  * at projection time from the reference, which is why no naming logic
  * enters gameplay.
  *
  * `details` are the consequences the engine annotated on the option (for
  * example the Supply an answer costs and what it yields), already worded
  * for display; empty for an option nothing was annotated on.
  *
  * `badge` is one short label the option carries -- what kind of thing it is,
  * such as `Attack Plan` -- for a client to draw as a chip. It is separate
  * from `details`, which carries consequences and runs together into one line.
  *
  * `state` is `available`, `locked` (decided already, shown chosen),
  * `unavailable` (shown, with `reason` saying why it may not be chosen now),
  * `warning` (choosable, with `reason` saying why to think twice) or
  * `askable` (shown, choosable only by asking `consent`'s granters, with
  * `reason` saying why).
  *
  * `replaces` is the card choosing the option discards to make room (a
  * Replace slot), as the viewer may see it, or the card or relic it buries
  * first. `buries` marks an option whose play buries `replaces` instead of
  * discarding it, Forest Warden's "Bury and replace" and "Bury and play".
  *
  * `variant` is the word that tells this option from the others drawn on the
  * same board home, such as an adviser play's facing; the client's toggle
  * shows it.
  *
  * `group` is the band the option is read in, for a question whose options
  * fall into kinds the player thinks of separately: `major`, `minor`,
  * `power`, `ending` or `other` on a phase menu, and absent on every
  * question that bands nothing. The engine's `OptionGroup` writes it. How
  * the bands are ordered and what headings they are given is the client's;
  * a band a client does not know reads as ungrouped.
  */
final case class DecisionOptionProjection(kind: String, id: String,
    label: String, card: Option[CardDetailsProjection] = None,
    details: Vector[String] = Vector.empty,
    badge: Option[String] = None,
    state: String = "available",
    reason: Option[String] = None,
    replaces: Option[CardDetailsProjection] = None,
    buries: Boolean = false,
    variant: Option[String] = None,
    group: Option[String] = None,
    consent: Option[ConsentAskProjection] = None):
  /** Whether the viewer may choose it: available, or available with a
    * warning. */
  def choosable: Boolean = state == "available" || state == "warning"

/** One named bucket a partition spreads its options across: the stable
  * `key` a placement names, the section's prompt copy, and the fewest
  * options it must receive. A client's confirmation predicate is computed
  * from these minima, never from local knowledge of the action's cost.
  */
final case class DecisionSectionProjection(key: String, label: String,
    minRequired: Int, maxAllowed: Option[Int] = None)

/** One amount-taking slot of a `distribute` query: its option, presented
  * exactly as a choose-one option is, its bounds, and the amount a draft
  * opens at when the query suggests one.
  */
final case class DecisionSlotProjection(option: DecisionOptionProjection,
    minimum: Int, maximum: Int, suggested: Option[Int])

/** A running sum over a choose-many's options: the total's `label`, the
  * `unit` one value counts in, each option's value by its `kind` and `id`,
  * and the most the total may reach. Advice for the client; the engine
  * still refuses an answer that breaks the action's own rule.
  */
final case class DecisionTallyProjection(label: String, unit: String,
    values: Vector[DecisionTallyValueProjection], limit: Option[Int] = None)
final case class DecisionTallyValueProjection(kind: String, id: String,
    value: Int)

/** "Target 2 sites": a choose-many's selection, stated. */
final case class DecisionSummaryProjection(verb: String, one: String,
    many: String):
  def of(count: Int): String =
    s"$verb $count ${if count == 1 then one else many}"

/** "Reveal 2 cards": what a choose-many's confirm button says for a
  * selection of `count`, with `none` standing in at zero when declared.
  * `countsLocked` says whether the caller adds the locked options to the
  * count it passes.
  */
final case class CountedConfirmProjection(verb: String, one: String,
    many: String, none: Option[String] = None, countsLocked: Boolean = false):
  def of(count: Int): String =
    none.filter(_ => count == 0).getOrElse(
      s"$verb $count ${if count == 1 then one else many}")

/** Wire projection of a parked generic-walker decision (Task 6:
  * `CurrentGameState.walkerPending`/`walkerProcedure`).
  *
  * Owner-private: the projector only ever
  * returns this for the parked actor, so `GameProjection.walkerDecision`
  * is `None` for every other viewer -- not a redacted copy of this type.
  *
  * It is answered with `ResolveWalker`. `decisionId` is the parked
  * `Decide`'s stable identity, so Recover's two parks -- the continue/stop
  * choice and the relic pick -- are distinguishable by `decisionId` alone.
  *
  * `query` is the parked `Decide`'s own transformed
  * [[DecisionQueryProjection]], which is the single source of both what the
  * client may offer and what the engine will accept. It is absent entirely
  * when any declared option's identity cannot be presented -- a
  * half-described option a client would render as a blank control and then
  * submit is worse than no prompt, so the whole projection is omitted.
  *
  * `rollOutcome` (I5) carries the roll the parked decision is about -- the
  * pool, the dice faces rolled so far, the derived score, and the target or
  * consequences where the procedure declares them -- so the panel can show
  * the player what they rolled and what it came to. Owner-private exactly
  * like the rest of this projection: the projector only ever returns the
  * whole `WalkerDecisionProjection` for the parked actor, so no other viewer
  * sees a roll outcome either.
  */
final case class WalkerDecisionProjection(
    action: String,
    decisionId: String,
    query: Option[DecisionQueryProjection] = None,
    rollOutcome: Option[WalkerRollOutcomeProjection] = None,
    /** The answers already recorded at THIS decision id, described as options.
      *
      * A decision inside a `Repeat` -- the battle-plan window is the only one
      * today -- re-asks with the chosen answers removed, so without this the
      * panel reads as resetting rather than accumulating.
      *
      * A reference whose option the projector cannot present from state alone
      * is omitted: a button's label is authored by the query, and a spent
      * answer has no query left to read it from. In the plan window that
      * leaves out only the title's own plan.
      */
    answeredOptions: Vector[DecisionOptionProjection] = Vector.empty,
    /** Where the client answers the decision: `"board"` draws each option
      * with a place on the table there, `"panel"` keeps it in the Action
      * panel. A payload without the field was answered in the panel.
      */
    presentation: String = "panel",
    /** Facts shown beside the decision, each a label and its value, worded
      * by the server: a battle-plan window's forces and dice, and what the
      * picks of a pick-and-pay menu have paid so far.
      */
    status: Vector[WalkerStatusProjection] = Vector.empty
)

/** One fact beside a parked decision: "Attack dice" and "9". */
final case class WalkerStatusProjection(label: String, value: String)

/** The roll a parked decision wants shown beside it.
  *
  * `pool` names which roll it is (`"recover"`, `"campaign.attack"`), so the
  * client can word the total without guessing from the action. `faces` are
  * the faces rolled, in roll order across every roll of that pool so far,
  * each carrying its own word and the die it is printed on, so the client
  * needs no face table of its own and no guess at the die from the pool;
  * `score` is the derived total (a `Doubler` on a later roll multiplies
  * earlier shields, so this is not a per-face sum). `target` is the number
  * the score must reach where there is one -- a Recover site's difficulty --
  * and `None` where there is not, as in a Campaign. `detail` carries
  * already-worded consequences such as `"1 skull loss"`.
  */
final case class WalkerRollOutcomeProjection(
    pool: String,
    faces: Vector[DieFace],
    score: Int,
    target: Option[Int] = None,
    detail: Vector[String] = Vector.empty
)
/** Public: who a parked walker position waits on, and the question's heading
  * when it has one (`None` for a roll). Every viewer except the awaited
  * player receives this; the awaited player receives `WalkerDecisionProjection`.
  * `status` is the facts the awaited player sees beside the question, so
  * the table can follow along.
  */
final case class WalkerWaitingProjection(playerId: String,
    heading: Option[String] = None,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    deal: Option[NegotiationDealProjection] = None,
    status: Vector[WalkerStatusProjection] = Vector.empty)
final case class BannerProjection(key: String, face: String,
    holderPlayerId: Option[String], resources: Int):
  def banner: String = key
/** One row of the Rest table: warbands in the bag from `from` to `to`
  * (`None` is "or more") return `supply`. */
final case class SupplyBandProjection(from: Int, to: Option[Int], supply: Int)
final case class OathkeeperProjection(
    goal: String, holderPlayerId: Option[String], side: String,
    usurperLimited: Boolean, winnerPlayerId: Option[String],
    winnerVictoryKind: Option[String] = None)
final case class PlayerBoardProjection(
    playerId: String,
    warbands: Int,
    favor: Int,
    faceUpSecrets: Int,
    faceDownSecrets: Int,
    committedSecrets: Int,
    totalSecrets: Int,
    supply: Int,
    pawnSiteId: Option[String],
    advisers: Vector[CardDetailsProjection],
    relics: Vector[CardDetailsProjection],
    revealedVision: Option[CardDetailsProjection],
    banners: Vector[BannerProjection] = Vector.empty,
    /** The warbands of this player's kind still in their bag: printed, less
      * those on the board and at sites. Public, as the bag is on the table. */
    bankedWarbands: Int = 0,
    printedWarbands: Int = 0,
    siteWarbands: Int = 0,
    /** The Supply the band for `bankedWarbands` returns at Rest, before
      * unspent Supply is added and the track's ceiling applied. */
    restSupplyBase: Int = 0,
    /** How many advisers this player may hold: 3, or 2 under Silver Tongue
      * or Insomnia. The board draws that many adviser slots. */
    adviserLimit: Int = 3
)
final case class NegotiationTransferProjection(authorPlayerId: String,
    recipientPlayerId: String, favor: Int, relicCount: Int,
    relics: Vector[CardDetailsProjection])
final case class NegotiationDisclosureProjection(authorPlayerId: String,
    recipientPlayerId: String, kind: String,
    card: Option[CardDetailsProjection])
final case class NegotiationSiteRelicProjection(siteId: String,
    card: CardDetailsProjection)
/** What the viewer may put into the deal: present only for a participant. */
final case class NegotiationEditingProjection(editableFavor: Int,
    editableRelics: Vector[CardDetailsProjection],
    editableAdvisers: Vector[CardDetailsProjection],
    editableSiteRelics: Vector[NegotiationSiteRelicProjection],
    canAccept: Boolean)

/** A parked negotiation as one viewer sees it. Everyone sees the participants,
  * who has accepted, favor amounts, relic counts and faceup relic details, and
  * each disclosure's kind and recipient. Only an author sees the identity of
  * their own facedown relics and of information they promise. `editing` is
  * present only for a participant.
  */
final case class NegotiationDealProjection(participantPlayerIds: Vector[String],
    acceptedPlayerIds: Vector[String],
    transfers: Vector[NegotiationTransferProjection],
    disclosures: Vector[NegotiationDisclosureProjection],
    editing: Option[NegotiationEditingProjection] = None)
