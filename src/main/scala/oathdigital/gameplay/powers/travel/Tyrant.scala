package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.PlayerFacts
import oathdigital.model._

object TyrantCard extends Denizen(DenizenId("111"), "Tyrant", Suit.Order) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.tyrant"),
    persistent = false, cost = Cost.free,
    text = "You **must** kill a warband _(even your own)_ at the site " +
      "you travel to, if able.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Tyrant (card 111, adviser-only, locked), an automatic rule of a faceup
  * adviser: after its holder travels, one warband at the destination is
  * killed, whoever's, the holder's own and bandits included, when one stands
  * there. Only Travel walks `TravelCost`, so Quick Exit and a Raid's
  * relocation move the pawn without it.
  *
  * The kill follows the pawn's move in the cost node and reads the
  * destination when it runs, inside an `AutomaticUse`: a power-use
  * restriction judges it there, after the move (catalog batch 8, slice 10).
  * The catalog marks the power `persistent: false`,
  * but it is a must, so it keeps the default automatic resolution, as Vow of
  * Obedience does.
  *
  * Its line names whose warband died, as Bandit Chief's does: "Killed {n}
  * {Blue} warband at {site}.", or "Killed {n} bandit warband at {site}." No
  * generic line exists for a kill.
  */
case object Tyrant extends ContributingPower:
  val cardId: DenizenId = TyrantCard.id
  val id: PowerId = TyrantCard.power.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  val Warbands: Int = 1
  private val notes = NoteSet(id)
  /** "Killed {n} {Blue} warband at {site}." */
  val killed: NoteKey = notes.key("killed", Vector(NotePart.Text("Killed "),
    NotePart.Arg(0), NotePart.Text(" "), NotePart.Arg(1),
    NotePart.Plural(0, " warband at ", " warbands at "), NotePart.Arg(2),
    NotePart.Text(".")))
  /** "Killed {n} bandit warband at {site}." */
  val killedBandits: NoteKey = notes.key("killed.bandits", Vector(
    NotePart.Text("Killed "), NotePart.Arg(0),
    NotePart.Plural(0, " bandit warband at ", " bandit warbands at "),
    NotePart.Arg(1), NotePart.Text(".")))

  override def noteKeys: Vector[NoteKey] = notes.declared

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      TravelRoute.pawnMove(ctx.operation).fold(operations)(route =>
        operations :+ AutomaticUse(route.player, id, cardId, (_, _) =>
          Vector(kill(route.destination),
            Note(id, killedNote(_, route.destination))))))))

  override def applicable(ctx: PowerCtx): Boolean =
    holds(ctx.state, ctx.activePlayer) &&
      TravelRoute.pawnMove(ctx.operation).exists(_.player == ctx.activePlayer)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })

  private def forcesAt(ready: ReadyGame, site: SiteId): SiteForces =
    ready.game.current.map.sites.get(site).map(_.forces)
      .getOrElse(SiteForces.Empty)

  /** One warband of whoever holds `site`, when anyone does. */
  private def kill(site: SiteId): Operation = BuildOps((ready, _) => Right(
    forcesAt(ready, site) match
      case SiteForces.Occupied(kind, _) => Vector[CoreOperation](Kill(
        Piece.Warbands(kind, Warbands), PositionedLocation(Location.Site(site))))
      case SiteForces.Empty => Vector.empty))

  private def held(ready: ReadyGame, site: SiteId, kind: ForceKind): Int =
    forcesAt(ready, site) match
      case SiteForces.Occupied(`kind`, count) => count
      case _ => 0

  /** The warbands the step before killed at `site`, and whose. */
  private def killedNote(states: NoteStates, site: SiteId)
      : Option[LogNote] = states.previous.flatMap { case (before, after) =>
    forcesAt(before, site) match
      case SiteForces.Occupied(kind, count) =>
        val lost = count - held(after, site, kind)
        if lost > 0 then line(after, kind, lost, site) else None
      case SiteForces.Empty => None
  }

  private def line(ready: ReadyGame, kind: ForceKind, lost: Int, site: SiteId)
      : Option[LogNote] =
    val card = PowerSourceRef.Card(cardId)
    kind match
      case ForceKind.Bandit => Some(killedBandits(card, NoteArg.Number(lost),
        NoteArg.Site(site)))
      case _ => ready.game.current.players.find(seat =>
          PlayerFacts.forceKind(ready, seat.player).toOption.contains(kind))
        .map(owner => killed(card, NoteArg.Number(lost),
          NoteArg.Player(owner.player), NoteArg.Site(site)))
