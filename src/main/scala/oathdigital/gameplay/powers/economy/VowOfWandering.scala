package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, Locked, PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.model._

object VowOfWanderingCard extends Denizen(DenizenId("247"), "Vow of Wandering", Suit.Nomad) with Locked with AdviserOnly:
  val muster = PrintedPower(PowerId("denizen.vow-of-wandering.muster"),
    persistent = false, cost = Cost.free,
    text = "Gain two more warbands.")
  val power = PrintedPower(PowerId("denizen.vow-of-wandering"),
    persistent = true, cost = Cost.free,
    text = "You **cannot** place warbands on sites.")
  val powers: Vector[PrintedPower] = Vector(muster, power)

/** Vow of Wandering's Muster power (card 247, adviser-only, locked), an
  * automatic rule of a faceup adviser: every Muster of its holder gains two
  * more warbands, best effort from the supply. They join the Muster's own
  * gain, as Vow of Beastkin's one does, and the line after the gain says how
  * many came ([[ExtraWarbands]]): "Gained {n} more warbands." The catalog
  * marks the power `persistent: false`, but it is not a choice, so it keeps
  * the default automatic resolution.
  */
case object VowOfWanderingMuster extends ContributingPower:
  val cardId: DenizenId = VowOfWanderingCard.id
  val id: PowerId = VowOfWanderingCard.muster.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  val Warbands: Int = 2

  override def noteKeys: Vector[NoteKey] = Vector(ExtraWarbands.gained)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterGain -> Vector(Transform((ctx, operations) =>
      ExtraWarbands.added(id, cardId, Warbands, ctx.activePlayer,
        operations))))

  override def applicable(ctx: PowerCtx): Boolean =
    holds(ctx.state, ctx.activePlayer)

  private def holds(ready: ReadyGame, player: PlayerId): Boolean =
    ready.game.current.players.find(_.player == player).exists(_.advisers.exists {
      case DenizenState(card, Orientation.FaceUp, _) => card == cardId
      case _ => false
    })
