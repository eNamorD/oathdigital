package oathdigital.gameplay.powers.whenplayed

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.PowerCtx
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

/** Key to the City (card 18, site-only), WHEN PLAYED: if the ruler's pawn
  * is not at this site, kill any warbands at this site, then gain a warband
  * and place it here.
  *
  * The site and its ruler are read live, after the card is in place. When
  * the ruler is a player whose pawn stands there, nothing happens and one
  * line says who was there. The actor's pawn is always there, so a site the
  * actor rules is kept. Otherwise three steps run in order: every warband
  * there is killed, the actor gains 1 warband, and 1 warband moves from the
  * actor's board to the site. Each is best effort. With no warband on the
  * board, nothing is placed, and the refill after the command puts bandits
  * on the emptied site.
  */
final case class KeyToTheCity private (cardId: DenizenId)
    extends WhenPlayedPower:
  import KeyToTheCity._
  def id: PowerId = KeyToTheCity.id

  override def noteKeys: Vector[NoteKey] =
    Vector(killed, bandits, placed, unplaced, guarded)

  private def cardSource: PowerSourceRef = PowerSourceRef.Card(cardId)

  def effect(ctx: PowerCtx): Vector[Operation] =
    val actor = ctx.activePlayer
    Vector(Branch((live, _) =>
      SiteRulers.siteOf(live, cardId).toVector.flatMap(steps(live, actor, _))))

  private def steps(ready: ReadyGame, actor: PlayerId, site: SiteId)
      : Vector[Operation] = guard(ready, site) match
    case Some(ruler) => Vector(Note(id, _ => Some(guarded(cardSource,
      NoteArg.Player(ruler), NoteArg.Site(site)))))
    case None => Vector(
      BuildOps((live, _) => Right(kill(live, site))),
      Note(id, killedNote(_, site)),
      BuildOps((live, _) => PlayerFacts.forceKind(live, actor).map(kind =>
        Vector(Gain.Warbands(actor, kind, Placed)))),
      BuildOps((live, _) => PlayerFacts.forceKind(live, actor).map(kind =>
        Vector(Move(Piece.Warbands(kind, Placed),
          PositionedLocation(Location.PlayArea(actor)),
          PositionedLocation(Location.Site(site)))))),
      Note(id, placedNote(_, actor, site), covers = true))

  /** The warbands the kill step took from the site. */
  private def killedNote(states: NoteStates, site: SiteId)
      : Option[PowerNote] = for
    (before, after) <- states.previous
    case SiteForces.Occupied(kind, count) <- forcesAt(before, site)
    lost = count - held(after, site, kind)
    if lost > 0
  yield killedLine(before, kind, lost, site)

  private def killedLine(ready: ReadyGame, kind: ForceKind, lost: Int,
      site: SiteId): PowerNote = owner(ready, kind) match
    case Some(player) => killed(cardSource, NoteArg.Number(lost),
      NoteArg.Player(player), NoteArg.Site(site))
    case None => bandits(cardSource, NoteArg.Number(lost), NoteArg.Site(site))

  /** The actor's warbands the place step put at the site. */
  private def placedNote(states: NoteStates, actor: PlayerId, site: SiteId)
      : Option[PowerNote] =
    PlayerFacts.forceKind(states.now, actor).toOption.map { kind =>
      val moved = states.previous.fold(0)((before, after) =>
        held(after, site, kind) - held(before, site, kind))
      if moved > 0 then placed(cardSource, NoteArg.Player(actor),
        NoteArg.Amount(moved, NoteUnit.Warband), NoteArg.Site(site))
      else unplaced(cardSource, NoteArg.Player(actor))
    }

object KeyToTheCity:
  val id: PowerId = PowerId("denizen.key-to-the-city")
  /** The warbands gained and placed. */
  val Placed: Int = 1
  /** "Killed {n} {Blue} warband at {site}." */
  val killed: NoteKey = NoteKey("killed", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "Killed {n} bandit warband at {site}." */
  val bandits: NoteKey = NoteKey("bandits", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Plural(0, " bandit warband at ",
      " bandit warbands at "), NotePart.Arg(1), NotePart.Text(".")))
  /** "{Red} placed {1 warband} at {site}." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Arg(0),
    NotePart.Text(" placed "), NotePart.Arg(1), NotePart.Text(" at "),
    NotePart.Arg(2), NotePart.Text(".")))
  /** "{Red} had no warband to place." */
  val unplaced: NoteKey = NoteKey("unplaced", Vector(NotePart.Arg(0),
    NotePart.Text(" had no warband to place.")))
  /** "{Blue} was at {site}." */
  val guarded: NoteKey = NoteKey("guarded", Vector(NotePart.Arg(0),
    NotePart.Text(" was at "), NotePart.Arg(1), NotePart.Text(".")))

  def forCatalog(catalog: ExecutableCatalog): Option[KeyToTheCity] =
    WhenPlayedPower.cardOf(catalog, id).map(new KeyToTheCity(_))

  /** The player ruling `site` whose pawn stands there, if any. */
  private def guard(ready: ReadyGame, site: SiteId): Option[PlayerId] =
    SiteRulers.rulerOf(ready, site).collect {
      case SiteRuler.Player(ruler) if ready.game.current.players.exists(
        state => state.player == ruler && state.pawnSite.contains(site)) =>
        ruler
    }

  private def kill(ready: ReadyGame, site: SiteId): Vector[CoreOperation] =
    forcesAt(ready, site).toVector.collect {
      case SiteForces.Occupied(kind, count) if count > 0 => Kill(
        Piece.Warbands(kind, count), PositionedLocation(Location.Site(site)))
    }

  private def forcesAt(ready: ReadyGame, site: SiteId): Option[SiteForces] =
    ready.game.current.map.sites.get(site).map(_.forces)

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    forcesAt(ready, site) match
      case Some(SiteForces.Occupied(`kind`, count)) => count
      case _ => 0

  /** The player whose warbands are of `kind`; none for bandits. */
  private def owner(ready: ReadyGame, kind: ForceKind): Option[PlayerId] =
    ready.game.current.players.find(state =>
      PlayerForceKind.of(ready, state).contains(kind)).map(_.player)
