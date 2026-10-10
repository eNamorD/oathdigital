package oathdigital.gameplay.actions

import oathdigital.model._

/** Leave to discard one card from the actor's site before a play there. Any
  * card of the site's card list may go: an edifice is a denizen for
  * placement, and an intact edifice is refused only as a locked card.
  * `source` is the power or rule that gives the leave, and `note` the line
  * card play writes when a discard it allowed is made.
  */
final case class DiscardPermission(source: PowerId, note: Note)

/** An adviser as card play counts it: the card and the face it shows. */
final case class AdviserFace(card: CardId, orientation: Orientation)

object AdviserFace:
  def of(adviser: AdviserState): AdviserFace = adviser match
    case DenizenState(id, orientation, _) => AdviserFace(id, orientation)
    case VisionState(id, orientation) => AdviserFace(id, orientation)

/** A note card play shows on an adviser play: `reason`, when `applies`
  * holds of the advisers after the play and the played face (Family Wagon's
  * "Family Wagon will bury it"). The play stays on offer. */
final case class AdviserPlayNote(reason: OptionReason,
    applies: (Vector[AdviserFace], AdviserFace) => Boolean)

/** A limit on where a card may be played: the placements it `allows`,
  * and the `reason` every other play is refused (Land Warden's "one card
  * must go to a site"). */
final case class PlacementLimit(reason: OptionReason,
    allows: SearchPlacement => Boolean)

/** The rules a card play is planned under. A power changes them through the
  * `SearchPlayAdviser` window, and card play reads nothing else, so the play
  * never names a power.
  *
  * The rulebook checks a play against the state after it (catalog batch 5
  * rulings, "Limits are checked after the play"). Card play therefore asks
  * `fits` of the advisers as they would be after each possible play.
  *  - `adviserLimit` is the base limit on all of the player's advisers,
  *    whatever they hold.
  *  - `lifts` are the cards that remove the base limit while they are a
  *    faceup adviser (Family Wagon). Cards' own limits still apply.
  *  - `cardLimits` are the limits a card sets while it is a faceup adviser
  *    (Silver Tongue). A facedown card has no power, and a replaced card is
  *    gone, so neither sets its limit.
  *  - `uncounted` are the adviser faces that do not count toward the limit
  *    (a faceup Pied Piper; a facedown Vision under Lost Tapestry).
  *  - `siteDiscards` are the permissions to discard a card from the site a
  *    card is played to, one card each. They stack. The Homeland rule's is
  *    not among them: card play adds it at the Homeland of the played card's
  *    suit (`homeland`).
  *  - `adviserNotes` are the notes an adviser play may carry. The first
  *    that applies to the advisers after the play wins.
  *  - `placementLimits` are limits on where the card may go. A play one
  *    refuses is shown unavailable with its reason. When the limits
  *    leave no play, the discard pile stays open: a card that cannot be
  *    played is discarded.
  *
  * Every change lowers or lifts a limit, exempts a face, or adds a
  * permission, a note or a placement limit, so contributions compose in any
  * order. Only the notes keep their order, and no two powers note the same
  * play.
  */
final case class CardPlayRules(
    adviserLimit: Int = CardPlayRules.DefaultAdviserLimit,
    cardLimits: Map[CardId, Int] = Map.empty,
    uncounted: Set[AdviserFace] = Set.empty,
    siteDiscards: Vector[DiscardPermission] = Vector.empty,
    lifts: Set[CardId] = Set.empty,
    adviserNotes: Vector[AdviserPlayNote] = Vector.empty,
    placementLimits: Vector[PlacementLimit] = Vector.empty):

  /** The limit over `advisers`, the advisers after a play: `adviserLimit`,
    * or none while a card that lifts it is faceup, lowered by each faceup
    * card that sets a limit. */
  def limitOver(advisers: Vector[AdviserFace]): Int =
    val faceUp = advisers.collect {
      case AdviserFace(card, Orientation.FaceUp) => card }
    val base = if faceUp.exists(lifts) then CardPlayRules.Unbounded
      else adviserLimit
    faceUp.flatMap(cardLimits.get).foldLeft(base)(math.min)

  /** How many of `advisers` count toward the limit. */
  def counted(advisers: Vector[AdviserFace]): Int =
    advisers.count(face => !uncounted(face))

  /** Whether `advisers`, the advisers after a play, keep within the limit. */
  def fits(advisers: Vector[AdviserFace]): Boolean =
    counted(advisers) <= limitOver(advisers)

  /** Lowers the base limit to at most `limit`. */
  def limitAdvisers(limit: Int): CardPlayRules =
    copy(adviserLimit = math.min(adviserLimit, limit))

  /** `card` sets a limit of `limit` while it is a faceup adviser. */
  def limitWhileFaceUp(card: CardId, limit: Int): CardPlayRules =
    copy(cardLimits = cardLimits.updated(card,
      cardLimits.get(card).fold(limit)(math.min(_, limit))))

  /** `face` does not count toward the limit. */
  def ignoreLimit(face: AdviserFace): CardPlayRules =
    copy(uncounted = uncounted + face)

  /** `card` removes the base limit while it is a faceup adviser. */
  def liftWhileFaceUp(card: CardId): CardPlayRules =
    copy(lifts = lifts + card)

  /** Adds a note an adviser play may carry. */
  def noteAdviserPlay(note: AdviserPlayNote): CardPlayRules =
    copy(adviserNotes = adviserNotes :+ note)

  /** The reason of the first note that applies to the play of `played`
    * that leaves `advisers`. */
  def noteOn(advisers: Vector[AdviserFace], played: AdviserFace)
      : Option[OptionReason] =
    adviserNotes.find(_.applies(advisers, played)).map(_.reason)

  /** Adds one site discard. */
  def permitSiteDiscard(permission: DiscardPermission): CardPlayRules =
    copy(siteDiscards = siteDiscards :+ permission)

  /** Adds a limit on where the card may be played. */
  def limitPlacements(limit: PlacementLimit): CardPlayRules =
    copy(placementLimits = placementLimits :+ limit)

  /** The reason of the first limit that refuses `placement`. */
  def refusal(placement: SearchPlacement): Option[OptionReason] =
    placementLimits.find(!_.allows(placement)).map(_.reason)

object CardPlayRules:
  val DefaultAdviserLimit: Int = 3
  /** No limit: the base limit while a card that lifts it is faceup. */
  val Unbounded: Int = Int.MaxValue
  val default: CardPlayRules = CardPlayRules()

  /** Why a Replace of an adviser that does not count is unavailable:
    * replacing it frees no room. */
  val notCounted: String = "Does not count toward the adviser limit"

  /** "{Red} may discard a card at their site first.": the line of the power
    * or rule that let a play to a site discard a card there. */
  val discardFirst: NoteKey = NoteKey("discard-first", Vector(NotePart.Arg(0),
    NotePart.Text(" may discard a card at their site first.")))

  /** The Homeland rule's permission (CR p. 31) for `actor`'s play to `site`,
    * the Homeland of the played card's suit: one discard of any card there,
    * whatever that card's suit. */
  def homeland(site: SiteId, actor: PlayerId): DiscardPermission =
    DiscardPermission(RuleNotes.homelandDiscard,
      Note(RuleNotes.homelandDiscard, _ => Some(discardFirst(
        PowerSourceRef.Site(site), NoteArg.Player(actor)))))
