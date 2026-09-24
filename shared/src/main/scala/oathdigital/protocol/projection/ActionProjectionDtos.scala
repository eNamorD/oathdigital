package oathdigital.protocol.projection

/** What a board-target action can select. Only sites remain: Travel and
  * the setup pawn placement are the whole of it.
  */
sealed trait BoardTargetRefProjection extends Product with Serializable {
  def stableKey: String = this match {
    case BoardTargetRefProjection.Site(id) => s"site:$id"
  }
}
object BoardTargetRefProjection {
  final case class Site(siteId: String) extends BoardTargetRefProjection
}
final case class BoardTargetCandidateProjection(
    target: BoardTargetRefProjection,
    label: String,
    details: Vector[String] = Vector.empty
)
final case class BoardTargetActionProjection(
    actionKind: String,
    prompt: String,
    minimum: Int,
    maximum: Int,
    autoActivate: Boolean,
    candidates: Vector[BoardTargetCandidateProjection],
    decisionId: Option[String] = None,
    explicitConfirm: Boolean = false
) {
  require(minimum >= 0, "selection minimum must be non-negative")
  require(maximum >= minimum, "selection maximum must include minimum")
  require(maximum <= 1, "a board-target action selects at most one target")
  require(maximum <= candidates.size,
    "selection maximum cannot exceed authorized candidates")
}
final case class CardResolutionProjection(
    kind: String,
    orientation: Option[String] = None,
    replacementRequired: Boolean = false,
    replacementTargets: Vector[CardDetailsProjection] = Vector.empty) {
  def replacement: Option[CardDetailsProjection] = replacementTargets.headOption
}
final case class PendingCardDecisionProjection(
    decisionId: String,
    kind: String,
    actorPlayerId: String,
    prompt: String,
    instructions: Vector[String],
    cards: Vector[CardDetailsProjection],
    keepMinimum: Int,
    keepMaximum: Int,
    orderingRequired: Boolean,
    resolutionsByCard: Map[String, Vector[CardResolutionProjection]]
)
/** A phase power the viewer can use now: its id, the card it is used from,
  * and the card's printed power name and rules text.
  */
final case class PhasePowerProjection(powerId: String,
    source: DecisionOptionProjection, name: String, rulesText: String)
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
  * `form` is the query shape: `"choose-one"` (pick exactly one option),
  * `"partition"` (spread every option across the declared sections),
  * `"distribute"` (assign amounts across the declared slots),
  * `"choose-many"` (pick `minimum` to `maximum` of the options), or
  * `"choose-amount"` (pick an integer from `minimum` to `maximum`, with no
  * options). A choose-one
  * query carries no `sections` at all. `slots` and `total` are a distribute
  * form's whole content. That form's `options` is empty, because every
  * option it offers sits on a slot.
  *
  * There is deliberately NO prebuilt wire answer on an option. The client
  * already holds everything an answer needs: a `ChooseOneWire(kind, id)`, a
  * `PartitionWire` of placements, or a `DistributeWire` of amounts is built
  * from the same kind-and-id pair each option or slot carries. Embedding an
  * answer would duplicate the identity and couple these DTOs to the command
  * protocol for nothing.
  *
  * `heading` and `confirmLabel` (Task 5b) are the panel's own prompt copy,
  * passed through from the query the action declared -- the frame around the
  * options, where an option's `label` is the copy on the option itself. Both
  * are optional, and a client that is handed neither falls back to generic
  * copy of its own; nothing on the server supplies a default. A choose-one
  * query never carries a `confirmLabel`, because it submits on the click and
  * has no confirm step to name.
  *
  * This is two optional strings, not the start of a form language: no
  * layout, no conditionals, no per-option copy beyond the label an option
  * already carries. A third piece of panel copy is a reason to ask what the
  * panel is really missing.
  */
final case class DecisionQueryProjection(
    form: String,
    options: Vector[DecisionOptionProjection],
    sections: Vector[DecisionSectionProjection] = Vector.empty,
    heading: Option[String] = None,
    confirmLabel: Option[String] = None,
    slots: Vector[DecisionSlotProjection] = Vector.empty,
    minTotal: Option[Int] = None,
    maxTotal: Option[Int] = None,
    minimum: Option[Int] = None,
    maximum: Option[Int] = None,
    suggested: Option[Int] = None,
    deal: Option[NegotiationDealProjection] = None)

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
  */
final case class DecisionOptionProjection(kind: String, id: String,
    label: String, card: Option[CardDetailsProjection] = None,
    details: Vector[String] = Vector.empty)

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
/** Wire projection of a parked generic-walker decision (Task 6:
  * `CurrentGameState.walkerPending`/`walkerProcedure`) -- the walker path's
  * counterpart to [[PendingCardDecisionProjection]] above, which the walker
  * deliberately never populates.
  *
  * Owner-private the same way that one is: the projector only ever
  * returns this for the parked actor, so `GameProjection.walkerDecision`
  * is `None` for every other viewer -- not a redacted copy of this type.
  *
  * `kind` is the client-facing verb, not the tree's structural node type:
  * `"roll"` means answer with `RollWalker` (no faces ride the command --
  * `pool`/`count` are informational only), `"decide"` means answer with
  * `ResolveWalker`. `decisionId` is always the parked node's stable
  * identity (a synthetic id for a Roll park, since only `Decide` nodes
  * carry one natively), so the three Recover parks -- roll, the
  * continue/stop choice, and the relic pick -- are each distinguishable
  * by `decisionId` alone.
  *
  * `query` is populated for a `"decide"` park and empty for a `"roll"`
  * park: it is the parked `Decide`'s own transformed
  * [[DecisionQueryProjection]], which is the single source of both what the
  * client may offer and what the engine will accept. It is absent entirely
  * when any declared option's identity cannot be presented -- a
  * half-described option a client would render as a blank control and then
  * submit is worse than no prompt, so the whole projection is omitted.
  *
  * `rollOutcome` (I5) carries the parked pool's accumulated roll feedback --
  * the dice faces rolled so far, the derived score, and the site's
  * Recover difficulty -- so the panel can show the player what they rolled
  * and how close they are, matching the legacy (deleted) `RecoverProjection`
  * this replaced. Owner-private exactly like the rest of this projection:
  * the projector only ever returns the whole `WalkerDecisionProjection` for
  * the parked actor, so no other viewer sees a roll outcome either.
  */
final case class WalkerDecisionProjection(
    action: String,
    decisionId: String,
    kind: String,
    pool: Option[String] = None,
    count: Option[Int] = None,
    query: Option[DecisionQueryProjection] = None,
    rollOutcome: Option[WalkerRollOutcomeProjection] = None,
    /** The cards this decision is ABOUT, as opposed to the cards its options
      * name: the card being placed by a `cardplay.place.*` question, which is
      * neither an option nor in the temporary hand. Plural so a decision about
      * several cards needs no second field. Projected under the same
      * disclosure rules as every other card, so a viewer who may not identify
      * one receives it hidden.
      */
    subjectCards: Vector[CardDetailsProjection] = Vector.empty
)
/** `faces` are display-ready die-face labels (e.g. `"one-shield"`), in roll
  * order across every roll of the parked pool so far; `score` is the
  * derived total (a `Doubler` on a later roll multiplies earlier shields,
  * so this is not simply a per-face sum); `difficulty` is the acting
  * player's current site's Recover difficulty, the target `score` must
  * reach.
  */
final case class WalkerRollOutcomeProjection(
    faces: Vector[String],
    score: Int,
    difficulty: Int
)
/** Public: who a parked walker position waits on, and the question's heading
  * when it has one (`None` for a roll). Every viewer except the awaited
  * player receives this; the awaited player receives `WalkerDecisionProjection`.
  */
final case class WalkerWaitingProjection(playerId: String,
    heading: Option[String] = None,
    coOwnerPlayerIds: Vector[String] = Vector.empty,
    deal: Option[NegotiationDealProjection] = None)
final case class BannerProjection(key: String, face: String,
    holderPlayerId: Option[String], resources: Int) {
  def banner: String = key
}
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
    banners: Vector[BannerProjection] = Vector.empty
)
final case class MinorAdviserProjection(card: CardDetailsProjection,
    placements: Vector[CardResolutionProjection])
final case class MinorActionsProjection(advisers: Vector[MinorAdviserProjection],
    canPeekSiteRelics: Boolean, facedownRelics: Vector[CardDetailsProjection],
    siteId: Option[String], maxBoardToSite: Int, maxSiteToBoard: Int)
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

/** The public record of the last Campaign fought (see `CampaignResult`).
  * `defenderPlayerId` is `None` for bandits. Dice use the wire spellings; a
  * Raid's targets are stable keys.
  */
final case class CampaignResultProjection(
    attackerPlayerId: String,
    kind: String,
    defenderPlayerId: Option[String],
    targetSiteIds: Vector[String],
    raidTargets: Vector[String],
    force: Int,
    attackDice: Vector[String],
    attackScore: Int,
    skullLosses: Int,
    sacrificed: Int,
    defenseDice: Vector[String],
    defenseScore: Int,
    attackerWins: Boolean)
