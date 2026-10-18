package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.EffectiveSuit
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers, SiteRoom}
import oathdigital.model._

object ResettleCard extends Denizen(DenizenId("159"), "Resettle", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.resettle"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Move a faceup [suit-nomad] card from any " +
      "player's advisers to any site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Resettle (card 159, site-only), ACTION, free: move a faceup Nomad card
  * from any player's advisers to any site.
  *
  * Two questions. The first offers every player's faceup Nomad denizen
  * advisers, the actor's own included, in seat then adviser order, read
  * live, and only while some site in play has room ([[SiteRoom]]). The
  * second offers every site in play with room, in map order, Resettle's own
  * included. The move is one `Move` in a required batch, so the search hides
  * an adviser-only or locked adviser, which every site refuses, and the
  * first question shows Continue when nothing can be chosen, with no
  * candidate too. The card carries its favor and secrets. After Continue
  * one line says nothing moved.
  */
final case class Resettle private (catalog: ExecutableCatalog)
    extends PaidAction(ResettleCard.power):
  import Resettle._

  override def noteKeys: Vector[NoteKey] = Resettle.noteKeys
  override def narratedDecisions: Set[String] =
    Set(adviserDecisionId, siteDecisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => askAdviser(live, player)),
    Branch((live, pending) => askSite(live, player, pending)),
    BuildOps((live, pending) => move(live, pending), required = true),
    Note(this.id, movedNote(_, source)))))

  private final case class Candidate(owner: PlayerId, card: DenizenId)

  /** Every player's faceup Nomad denizen advisers, in seat then adviser
    * order, while some site in play has room. */
  private def candidates(ready: ReadyGame): Vector[Candidate] =
    if SiteRoom.open(catalog, ready).isEmpty then Vector.empty
    else for
      player <- ready.game.current.players
      card <- player.advisers.collect {
        case DenizenState(id, Orientation.FaceUp, _)
            if EffectiveSuit.of(catalog, ready, id).contains(Suit.Nomad) =>
              id }
    yield Candidate(player.player, card)

  private def askAdviser(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    Vector(Decide(adviserDecisionId, actor,
      DecisionQuery.ChooseOne(candidates(ready).map(c =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(c.card))),
        heading = Some("Resettle: choose a faceup Nomad adviser to move")),
      passWhenEmpty = true))

  /** Asked once an adviser is chosen. */
  private def askSite(ready: ReadyGame, actor: PlayerId,
      pending: PendingTree): Vector[Operation] =
    if PowerAnswers.one(pending, adviserDecisionId).isEmpty then Vector.empty
    else Vector(Decide(siteDecisionId, actor, DecisionQuery.ChooseOne(
      SiteRoom.open(catalog, ready).map(site =>
        DecisionOption.Site(DecisionOptionRef.Site(site))),
      heading = Some("Resettle: choose the site to move it to"))))

  /** No adviser answer means no adviser could be moved. */
  private def move(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    (PowerAnswers.one(pending, adviserDecisionId),
        PowerAnswers.one(pending, siteDecisionId)) match
      case (None, _) => Right(Vector.empty)
      case (Some(ref), Some(DecisionOptionRef.Site(site))) => (for
          chosen <- candidates(ready)
            .find(c => DecisionOptionRef.Denizen(c.card) == ref)
          if SiteRoom.open(catalog, ready).contains(site)
        yield Vector[CoreOperation](Move(Piece.Card(chosen.card),
          PositionedLocation(Location.PlayArea(chosen.owner)),
          PositionedLocation(Location.Site(site))))).toRight(
        OathViolation.InvalidEventOrder(
          s"Resettle cannot move ${ref.wireId} to ${site.value}"))
      case (Some(_), Some(other)) => Left(OathViolation.InvalidEventOrder(
        s"${other.wireId} is not a site Resettle can move to"))
      case (Some(_), None) => Left(PowerAnswers.missing(siteDecisionId))

  /** The owner is read before the move; written when the adviser now stands
    * at the chosen site. */
  private def movedNote(states: NoteStates, source: DecisionOptionRef)
      : Option[LogNote] =
    PowerSourceRef.of(source).map(card => (for
      case DecisionOptionRef.Denizen(chosen) <-
        NoteSupport.answer(states, adviserDecisionId)
      case DecisionOptionRef.Site(site) <-
        NoteSupport.answer(states, siteDecisionId)
      (before, after) <- states.previous
      owner <- before.game.current.players
        .find(_.advisers.exists(_.id == chosen))
      if after.game.current.map.sites.get(site)
        .exists(_.denizens.exists(_.id == chosen))
    yield moved(card, NoteArg.Player(owner.player), NoteArg.Card(chosen),
      NoteArg.Site(site))).getOrElse(nothing(card)))

object Resettle:
  val id: PowerId = ResettleCard.power.id
  val adviserDecisionId: String = "power.resettle.adviser"
  val siteDecisionId: String = "power.resettle.site"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Moved {Blue}'s {card} to {site}." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text("'s "), NotePart.Arg(1),
    NotePart.Text(" to "), NotePart.Arg(2), NotePart.Text(".")))
  /** "No Nomad adviser could be moved." */
  val nothing: NoteKey = notes.key("used.none", Vector(
    NotePart.Text("No Nomad adviser could be moved.")))

  def forCatalog(catalog: ExecutableCatalog): Resettle =
    new Resettle(catalog)
