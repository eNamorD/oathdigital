package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Great Herd (card 30, site-only), WHEN PLAYED: you may swap Great Herd
  * with a Nomad card at any site.
  *
  * The candidates are the faceup Nomad denizens and ruined Nomad edifices at
  * every other site in play, whoever rules it, read live and in map order.
  * With none, nothing is asked and the line says so. Otherwise the actor
  * picks one or keeps Great Herd. The swap is a required batch, so the
  * walker's search hides a card whose swap a restriction refuses. Each card
  * keeps its favor and secrets, and the card moved in does not run its own
  * WHEN PLAYED. The effect is one `Branch`, so the node count this power
  * adds to the card-played window never depends on live state.
  */
final case class GreatHerd private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends WhenPlayedPower:
  import GreatHerd._
  def id: PowerId = GreatHerd.id

  override def noteKeys: Vector[NoteKey] = Vector(swapped, none)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      val found = candidates(live)
      if found.isEmpty then Vector(Note(id, _ =>
        Some(none(PowerSourceRef.Card(cardId)))))
      else Vector(
        Decide(decisionId, actor, DecisionQuery.ChooseOne(
          found.map(_.option) :+ DecisionOption.Button(keep, "Keep Great Herd"),
          heading = Some("Great Herd: swap it with a Nomad card at another " +
            "site?"))),
        BuildOps((ready, pending) => swap(ready, pending), required = true),
        Note(id, swappedNote(_, actor)))))

  /** The faceup Nomad denizens and ruined Nomad edifices at every other
    * site in play, in map order. */
  private def candidates(ready: ReadyGame): Vector[Candidate] =
    val map = ready.game.current.map
    SiteRulers.siteOf(ready, cardId).toVector.flatMap(here => map.inPlay
      .filter(_ != here).flatMap(site => map.sites.get(site).toVector
        .flatMap(_.denizens.collect {
          case DenizenState(card, Orientation.FaceUp, _) if nomad(card) =>
            Candidate(site, card, DecisionOption.Denizen(
              DecisionOptionRef.Denizen(card)))
          case EdificeState(card, EdificeSide.Ruined, _) if nomad(card) =>
            Candidate(site, card, DecisionOption.Edifice(
              DecisionOptionRef.Edifice(card)))
        })))

  private def nomad(card: CardId): Boolean =
    catalog.suitOf(card).contains(Suit.Nomad)

  private def swap(ready: ReadyGame, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] =
    PowerAnswers.one(pending, decisionId) match
      case Some(`keep`) => Right(Vector.empty)
      case Some(ref) => swapWith(ready, ref).toRight(OathViolation
        .InvalidEventOrder(s"${ref.wireId} is not a Nomad card Great Herd " +
          "can swap with"))
      case None => Left(PowerAnswers.missing(decisionId))

  private def swapWith(ready: ReadyGame, ref: DecisionOptionRef)
      : Option[Vector[CoreOperation]] = for
    here <- SiteRulers.siteOf(ready, cardId)
    chosen <- candidates(ready).find(_.option.ref == ref)
  yield Vector(Swap(cardId, PositionedLocation(Location.Site(here)),
    chosen.card, PositionedLocation(Location.Site(chosen.site))))

  /** Written only when the chosen card now stands where Great Herd stood. */
  private def swappedNote(states: NoteStates, actor: PlayerId)
      : Option[PowerNote] = for
    ref <- NoteSupport.answer(states, decisionId)
    (before, after) <- states.previous
    here <- SiteRulers.siteOf(before, cardId)
    chosen <- candidates(before).find(_.option.ref == ref)
    if after.game.current.map.sites.get(here)
      .exists(_.denizens.exists(_.id == chosen.card))
  yield swapped(PowerSourceRef.Card(cardId), NoteArg.Player(actor),
    NoteArg.Card(chosen.card), NoteArg.Site(chosen.site))

object GreatHerd:
  val id: PowerId = PowerId("denizen.great-herd")
  val decisionId: String = "cardplay.great-herd.swap"
  val keep: DecisionOptionRef.Button = DecisionOptionRef.Button("keep")
  /** "{Red} swapped it with {card} at {site}." */
  val swapped: NoteKey = NoteKey("swapped", Vector(NotePart.Arg(0),
    NotePart.Text(" swapped it with "), NotePart.Arg(1), NotePart.Text(" at "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "No Nomad card could be swapped." */
  val none: NoteKey = NoteKey("none", Vector(
    NotePart.Text("No Nomad card could be swapped.")))

  private final case class Candidate(site: SiteId, card: CardId,
      option: DecisionOption)

  def forCatalog(catalog: ExecutableCatalog): Option[GreatHerd] =
    WhenPlayedPower.cardOf(catalog, id).map(new GreatHerd(_, catalog))
