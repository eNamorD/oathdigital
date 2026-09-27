package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers}
import oathdigital.model._

/** Barbed Net (relic R36), ACTION: burn 3 secrets, then take a relic from
  * the player's site. The player may keep it facedown; the existing minor
  * action that reveals an owned relic covers turning it up later.
  *
  * Every relic at the site is recorded as a `Peek`, so the player may
  * identify it, and then the player chooses one, as Recover asks. It moves
  * to the player's board facedown, as a recovered relic does. With no relic
  * at the site the cost stays paid and nothing else happens.
  *
  * The relic question separates the peek step from the take step, and a
  * note covers only the step before it. So the peek has its own covering
  * line, `used.peeked`, as Scryer's does, and the take has the `used` line.
  */
case object BarbedNet extends PaidAction("relic.barbed-net",
    Cost(secretBurnt = 3)):
  val decisionId: String = "power.barbed-net.relic"
  /** "{Red} peeked at the relics at {site}: {relics}." */
  val peeked: NoteKey = NoteKey("used.peeked", Vector(NotePart.Arg(0),
    NotePart.Text(" peeked at the relics at "), NotePart.Arg(1),
    NotePart.Text(": "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} took {relic} facedown from {site}." */
  val took: NoteKey = NoteKey(NoteKey.Used, Vector(NotePart.Arg(0),
    NotePart.Text(" took "), NotePart.Arg(1),
    NotePart.Text(" facedown from "), NotePart.Arg(2), NotePart.Text(".")))
  /** "{site} held no relic." */
  val bare: NoteKey = NoteKey("used.none", Vector(NotePart.Arg(0),
    NotePart.Text(" held no relic.")))
  override def noteKeys: Vector[NoteKey] = Vector(peeked, took, bare)

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector[Operation](
    BuildOps((live, _) => Right(here(live, player).toVector.flatMap(
      (site, relics) => relics.map(relic =>
        Peek(player, relic, Location.Site(site)))))),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      (site, relics) <- here(states.now, player)
      if relics.nonEmpty
    yield peeked(card, NoteArg.Player(player), NoteArg.Site(site),
      NoteArg.Cards(relics)), covers = true),
    Note(id, states => for
      card <- PowerSourceRef.of(source)
      (site, relics) <- here(states.now, player)
      if relics.isEmpty
    yield bare(card, NoteArg.Site(site))),
    Branch((live, _) => ask(live, player)),
    BuildOps((live, pending) => take(live, player, pending)),
    Note(id, tookNote(_, player, source)))))

  /** The player's pawn site and the relics there, in the site's order. */
  private def here(ready: ReadyGame, actor: PlayerId)
      : Option[(SiteId, Vector[RelicId])] = for
    site <- PowerAccess.pawnSite(ready, actor)
    state <- ready.game.current.map.sites.get(site)
  yield (site, state.relics.map(_.id))

  private def ask(ready: ReadyGame, actor: PlayerId): Vector[Operation] =
    here(ready, actor).filter(_._2.nonEmpty).toVector.map((_, relics) =>
      Decide(decisionId, actor, DecisionQuery.ChooseOne(
        relics.map(relic => DecisionOption.Relic(DecisionOptionRef.Relic(relic))),
        heading = Some("Barbed Net: take a relic from your site"))))

  private def take(ready: ReadyGame, actor: PlayerId, pending: PendingTree)
      : Either[OathViolation, Vector[CoreOperation]] = here(ready, actor) match
    case Some((site, relics)) if relics.nonEmpty =>
      for
        ref <- PowerAnswers.one(pending, decisionId)
          .toRight(PowerAnswers.missing(decisionId))
        relic <- relics.find(DecisionOptionRef.Relic(_) == ref)
          .toRight(OathViolation.InvalidEventOrder(
            s"${ref.wireId} is not a relic at the actor's site"))
      yield Vector[CoreOperation](Move(Piece.Card(relic),
        PositionedLocation(Location.Site(site)),
        PositionedLocation(Location.PlayArea(actor)),
        resultingOrientation = Some(Orientation.FaceDown)))
    case _ => Right(Vector.empty)

  /** The relic the take step gave the player. */
  private def tookNote(states: NoteStates, actor: PlayerId,
      source: DecisionOptionRef): Option[PowerNote] = for
    card <- PowerSourceRef.of(source)
    step <- states.previous
    relic <- NoteSupport.relicsGained(step, actor).headOption
    site <- PowerAccess.pawnSite(states.now, actor)
  yield took(card, NoteArg.Player(actor), NoteArg.Card(relic),
    NoteArg.Site(site))
