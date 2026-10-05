package oathdigital.gameplay.actions

import oathdigital.model._

/** Leave to discard one card from the actor's site before a play there. Any
  * card of the site's card list may go: an edifice is a denizen for
  * placement, and an intact edifice is refused only as a locked card.
  * `source` is the power or rule that gives the leave, and `note` the line
  * card play writes when a discard it allowed is made.
  */
final case class DiscardPermission(source: PowerId, note: Note)

/** The rules a card play is planned under. A power changes them through the
  * `SearchPlayAdviser` window, and card play reads nothing else, so the play
  * never names a power.
  *
  *  - `adviserLimit` is the one limit on all of the player's advisers,
  *    whatever their facing, set by the advisers they hold.
  *    `playedCardLimit` is the limit the played card sets once faceup. The
  *    rulebook checks a play that changes the limit against the new limit,
  *    so a faceup play counts against the lower of the two and a facedown
  *    play, whose card has no power, against `adviserLimit` alone.
  *  - `siteDiscards` are the permissions to discard a card from the site a
  *    card is played to, one card each. They stack. The Homeland rule's is
  *    not among them: card play adds it at the Homeland of the played card's
  *    suit (`homeland`).
  *
  * Every change lowers a limit or adds a permission, so contributions
  * compose in any order. (The limit cards implemented so far only lower.)
  */
final case class CardPlayRules(
    adviserLimit: Int = CardPlayRules.DefaultAdviserLimit,
    playedCardLimit: Int = CardPlayRules.DefaultAdviserLimit,
    siteDiscards: Vector[DiscardPermission] = Vector.empty):

  /** The limit a play counts against: the new one when the played card goes
    * faceup. */
  def limitAfter(faceup: Boolean): Int =
    if faceup then math.min(adviserLimit, playedCardLimit) else adviserLimit

  /** Lowers the player's limit to at most `limit`. */
  def limitAdvisers(limit: Int): CardPlayRules =
    copy(adviserLimit = math.min(adviserLimit, limit))

  /** The played card sets a limit of `limit` once faceup. */
  def limitPlayedCard(limit: Int): CardPlayRules =
    copy(playedCardLimit = math.min(playedCardLimit, limit))

  /** Adds one site discard. */
  def permitSiteDiscard(permission: DiscardPermission): CardPlayRules =
    copy(siteDiscards = siteDiscards :+ permission)

object CardPlayRules:
  val DefaultAdviserLimit: Int = 3
  val default: CardPlayRules = CardPlayRules()

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
