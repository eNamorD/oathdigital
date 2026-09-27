package oathdigital.gameplay.actions

import oathdigital.model.{Note, NoteKey, NotePart, Orientation}

/** The rules a card play is planned under. A power changes them through the
  * `SearchPlayAdviser` window, and card play reads nothing else, so the play
  * never names a power.
  *
  *  - `faceupAdviserLimit` and `facedownAdviserLimit` are how many advisers
  *    the player may hold in each orientation. A play that would exceed the
  *    limit must discard an adviser.
  *  - `siteDiscardFirst` lets a play to a site first discard one card of the
  *    site's card list, at any capacity. It is optional with room and required
  *    without, and it lifts the rule that a full site accepts only a card that
  *    matches its homeland edifice.
  *  - `siteDiscardNote` is the line of the power that permits the discard.
  *    Card play writes it after the discard answer at a site. Without the
  *    permission, the discard is asked only at a full Homeland of the card's
  *    suit, and card play writes the Homeland rule's line there instead.
  *
  * Every change narrows or adds a permission independently of the others, so
  * two powers that change the rules compose in any order.
  */
final case class PlacementRules(
    faceupAdviserLimit: Int = PlacementRules.DefaultAdviserLimit,
    facedownAdviserLimit: Int = PlacementRules.DefaultAdviserLimit,
    siteDiscardFirst: Boolean = false,
    siteDiscardNote: Option[Note] = None):

  def adviserLimit(orientation: Orientation): Int = orientation match
    case Orientation.FaceUp => faceupAdviserLimit
    case Orientation.FaceDown => facedownAdviserLimit

  /** Lowers both limits to at most `limit`. */
  def limitAdvisers(limit: Int): PlacementRules = copy(
    faceupAdviserLimit = math.min(faceupAdviserLimit, limit),
    facedownAdviserLimit = math.min(facedownAdviserLimit, limit))

  /** Lowers the faceup limit to at most `limit`. */
  def limitFaceupAdvisers(limit: Int): PlacementRules =
    copy(faceupAdviserLimit = math.min(faceupAdviserLimit, limit))

  /** Permits a discard before a play to a site. */
  def withSiteDiscardFirst: PlacementRules = copy(siteDiscardFirst = true)

  /** Permits a discard before a play to a site, and names the line the
    * permitting power writes where the discard is asked. */
  def withSiteDiscardFirstBy(note: Note): PlacementRules =
    copy(siteDiscardFirst = true, siteDiscardNote = Some(note))

object PlacementRules:
  val DefaultAdviserLimit: Int = 3
  val default: PlacementRules = PlacementRules()

  /** "{Red} may discard a card at their site first.": the line of the power
    * or rule that lets a play to a site discard first. */
  val discardFirst: NoteKey = NoteKey("discard-first", Vector(NotePart.Arg(0),
    NotePart.Text(" may discard a card at their site first.")))
