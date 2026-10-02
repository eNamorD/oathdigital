package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.PowerAccess
import oathdigital.model._

object DarkEnforcerCard extends Denizen(DenizenId("227"), "Dark Enforcer", Suit.Discord):
  val power = PrintedPower(PowerId("denizen.dark-enforcer"),
    persistent = false, cost = Cost(favorBurnt = 1),
    text = "**ACTION:** Discard all [suit-order] and [suit-hearth] " +
      "cards from your site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Dark Enforcer (card 227), ACTION: burn 1 favor, then discard all Order
  * and Hearth cards from your site.
  *
  * Every faceup Order and Hearth denizen and ruined edifice at the player's
  * site gets the standard discard ([[SiteCards]]), as far as the discard
  * rules permit: each discard is optional, so one a restriction refuses is
  * skipped and the rest go. Nothing is asked.
  *
  * Its line names the cards that left the site, as Dazzle's does, and
  * covers the generic discard lines. With none, the line names the site.
  */
final case class DarkEnforcer private (catalog: ExecutableCatalog)
    extends PaidAction(DarkEnforcerCard.power):
  import DarkEnforcer._

  private val cards = new SiteCards(catalog, Set(Suit.Order, Suit.Hearth))
  override def noteKeys: Vector[NoteKey] = Vector(discarded, kept)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => discards(live, player)),
    Note(this.id, discardedNote(_, player, source), covers = true))))

  private def discards(ready: ReadyGame, player: PlayerId)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAccess.pawnSite(ready, player).toVector.flatMap(here =>
      cards.at(ready, here).map((card, suit) =>
        cards.discard(ready, here, card, suit, player, required = false)))
      .foldLeft[Either[OathViolation, Vector[CoreOperation]]](
        Right(Vector.empty))((done, next) =>
        done.flatMap(ops => next.map(ops :+ _)))

  /** The cards at the site before the step that are gone after it. None
    * gone names the site. */
  private def discardedNote(states: NoteStates, player: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    site <- PowerAccess.pawnSite(states.now, player)
  yield
    val gone: Vector[CardId] = states.previous.toVector.flatMap(
      (before, after) => cards.at(before, site).map(_._1.id).filterNot(id =>
        after.game.current.map.sites.get(site)
          .exists(_.denizens.exists(_.id == id))))
    if gone.nonEmpty then discarded(card, NoteArg.Cards(gone))
    else kept(card, NoteArg.Site(site))

object DarkEnforcer:
  val id: PowerId = DarkEnforcerCard.power.id
  /** "Discarded {cards}." */
  val discarded: NoteKey = NoteKey(NoteKey.Used, Vector(
    NotePart.Text("Discarded "), NotePart.Arg(0), NotePart.Text(".")))
  /** "{site} held no Order or Hearth card to discard." */
  val kept: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no Order or Hearth card to discard.")))

  def forCatalog(catalog: ExecutableCatalog): DarkEnforcer =
    new DarkEnforcer(catalog)
