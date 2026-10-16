package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

object RovingTerrorCard extends Denizen(DenizenId("46"), "Roving Terror", Suit.Beast) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.roving-terror"),
    persistent = false, cost = Cost(secret = 1),
    text = "**ACTION:** Discard a denizen card at any other site and " +
      "move this card there.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Roving Terror (card 46, site-only), ACTION: place 1 secret on this card,
  * then discard a denizen card at any other site and move this card there.
  *
  * The candidates are the faceup denizens and ruined edifices at every other
  * site in play, in map then site order, read live after the cost is paid
  * ([[SiteCards]], every suit). An intact edifice is locked, so it is never
  * offered. The chosen card gets the standard discard: favor to its suit's
  * bank, secrets to the player facedown. Roving Terror then moves to that
  * site carrying its secret, and takes the site's last slot, since a row
  * has no gaps. The discard and the move are one required batch, so a card
  * the Hall of Ministers protects is shown with its reason, or askable of
  * the Hall's ruler (consent slice 3). With no card that may be chosen the
  * question shows Continue beside any askable card. With no candidate the
  * cost stays paid and one line says so.
  *
  * The line covers the generic discard line.
  */
final case class RovingTerror private (catalog: ExecutableCatalog)
    extends PaidAction(RovingTerrorCard.power):
  import RovingTerror._

  private val cards = new SiteCards(catalog, Suit.all.toSet)
  override def noteKeys: Vector[NoteKey] = RovingTerror.noteKeys
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => replace(live, player, pending),
      required = true),
    Note(this.id, replacedNote(_, source), covers = true))))

  private final case class Candidate(site: SiteId, card: SiteDenizenState,
      suit: Suit)

  /** The discardable cards at every site in play but Roving Terror's own. */
  private def candidates(ready: ReadyGame): Vector[Candidate] =
    SiteRulers.siteOf(ready, RovingTerrorCard.id).toVector.flatMap(here =>
      ready.game.current.map.inPlay.filter(_ != here).flatMap(site =>
        cards.at(ready, site).map((card, suit) =>
          Candidate(site, card, suit))))

  /** The question over the candidates, each judged for the actor's
    * discard. With none that may be chosen it is the Continue screen, where
    * an askable card still asks (catalog batch 8, slice 10, part 2b). */
  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    val found = candidates(ready).flatMap(c =>
      cards.judged(ready, actor, c.site, c.card, c.suit))
    Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(found,
      heading = Some("Roving Terror: discard a card at another site and " +
        "move there")), passWhenEmpty = true))

  /** No answer means no card could be replaced. */
  private def replace(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) =>
        for
          here <- SiteRulers.siteOf(ready, RovingTerrorCard.id).toRight(
            OathViolation.InvalidEventOrder("Roving Terror is not at a site"))
          chosen <- candidates(ready).find(c => cards.refOf(c.card) == ref)
            .toRight(OathViolation.InvalidEventOrder(
              s"${ref.wireId} is not a card Roving Terror can replace"))
          discard <- cards.discard(ready, chosen.site, chosen.card,
            chosen.suit, actor, required = true)
        yield Vector(discard, Move(Piece.Card(RovingTerrorCard.id),
          PositionedLocation(Location.Site(here)),
          PositionedLocation(Location.Site(chosen.site))))

  /** The chosen card, read before the step, once Roving Terror stands at its
    * site. */
  private def replacedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[PowerNote] =
    PowerSourceRef.of(source).map(card => (for
      ref <- NoteSupport.answer(states, decisionId)
      (before, _) <- states.previous
      chosen <- candidates(before).find(c => cards.refOf(c.card) == ref)
      if SiteRulers.siteOf(states.now, RovingTerrorCard.id)
        .contains(chosen.site)
    yield replaced(card, NoteArg.Card(chosen.card.id),
      NoteArg.Site(chosen.site))).getOrElse(nothing(card)))

object RovingTerror:
  val id: PowerId = RovingTerrorCard.power.id
  val decisionId: String = "power.roving-terror.card"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Replaced {card} at {site}." */
  val replaced: NoteKey = notes.key(NoteKey.Used, Vector(
    NotePart.Text("Replaced "), NotePart.Arg(0), NotePart.Text(" at "),
    NotePart.Arg(1), NotePart.Text(".")))
  /** "No card could be replaced." */
  val nothing: NoteKey = notes.key("used.none", Vector(
    NotePart.Text("No card could be replaced.")))

  def forCatalog(catalog: ExecutableCatalog): RovingTerror =
    new RovingTerror(catalog)
