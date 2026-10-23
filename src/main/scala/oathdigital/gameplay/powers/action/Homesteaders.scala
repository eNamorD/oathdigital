package oathdigital.gameplay.powers.action

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.{NoteSupport, PowerAccess}
import oathdigital.gameplay.powers.{PlayerFacts, PowerAnswers, SiteRoom}
import oathdigital.model._

object HomesteadersCard extends Denizen(DenizenId("127"), "Homesteaders", Suit.Hearth):
  val power = PrintedPower(PowerId("denizen.homesteaders"),
    persistent = false, cost = Cost.free,
    text = "**ACTION:** Move one of your faceup advisers to your site.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Homesteaders (card 127), ACTION, free: move one of the player's faceup
  * advisers to the pawn's site.
  *
  * The candidates are the player's faceup denizen advisers, Homesteaders
  * included, in adviser order, read live, and only while the pawn's site
  * has room ([[SiteRoom]]). The move is one `Move` in a required batch, so
  * the restriction-icon rule hides an adviser-only adviser and the Locked
  * rule a locked one; the question shows Continue when nothing can be
  * chosen, with no candidate too. The card carries its favor and secrets.
  * After Continue one line says nothing moved.
  */
final case class Homesteaders private (catalog: ExecutableCatalog)
    extends PaidAction(HomesteadersCard.power):
  import Homesteaders._

  override def noteKeys: Vector[NoteKey] = Homesteaders.noteKeys
  override def narratedDecisions: Set[String] = Set(decisionId)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => move(live, player, pending), required = true),
    Note(this.id, movedNote(_, player, source)))))

  /** The player's faceup denizen advisers, in adviser order, while the
    * pawn's site has room. */
  private def candidates(ready: ReadyGame, actor: PlayerId)
      : Vector[DenizenId] =
    val room = PowerAccess.pawnSite(ready, actor)
      .exists(SiteRoom.hasRoom(catalog, ready, _))
    if !room then Vector.empty
    else PlayerFacts.player(ready, actor).toOption.toVector
      .flatMap(_.advisers.collect {
        case DenizenState(id, Orientation.FaceUp, _) => id })

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    Vector(Decide(decisionId, actor, DecisionQuery.ChooseOne(
      candidates(ready, actor).map(card =>
        DecisionOption.Denizen(DecisionOptionRef.Denizen(card))),
      heading = Some("Homesteaders: move a faceup adviser to your site")),
      passWhenEmpty = true))

  /** No answer means no adviser could be moved. */
  private def move(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case None => Right(Vector.empty)
      case Some(ref) => (for
        site <- PowerAccess.pawnSite(ready, actor)
        card <- candidates(ready, actor)
          .find(card => DecisionOptionRef.Denizen(card) == ref)
      yield Vector[CoreOperation](Move(Piece.Card(card),
        PositionedLocation(Location.PlayArea(actor)),
        PositionedLocation(Location.Site(site))))).toRight(
        OathViolation.InvalidEventOrder(
          s"${ref.wireId} is not an adviser Homesteaders can move"))

  /** Written when the chosen adviser now stands at the pawn's site. */
  private def movedNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[LogNote] =
    PowerSourceRef.of(source).map(card => (for
      case DecisionOptionRef.Denizen(chosen) <-
        NoteSupport.answer(states, decisionId)
      site <- PowerAccess.pawnSite(states.now, actor)
      if states.now.game.current.map.sites.get(site)
        .exists(_.denizens.exists(_.id == chosen))
    yield moved(card, NoteArg.Card(chosen), NoteArg.Site(site)))
      .getOrElse(nothing(card)))

object Homesteaders:
  val id: PowerId = HomesteadersCard.power.id
  val decisionId: String = "power.homesteaders.adviser"
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Moved {card} to {site}." */
  val moved: NoteKey = notes.key(NoteKey.Used, Vector(NotePart.Text("Moved "),
    NotePart.Arg(0), NotePart.Text(" to "), NotePart.Arg(1),
    NotePart.Text(".")))
  /** "No adviser could be moved." */
  val nothing: NoteKey = notes.key("used.none", Vector(
    NotePart.Text("No adviser could be moved.")))

  def forCatalog(catalog: ExecutableCatalog): Homesteaders =
    new Homesteaders(catalog)
