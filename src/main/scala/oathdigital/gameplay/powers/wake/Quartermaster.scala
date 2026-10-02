package oathdigital.gameplay.powers.wake

import oathdigital.catalog.{Denizen, PrintedPower, SiteOnly}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.PhasePower
import oathdigital.gameplay.powers.NoteSupport
import oathdigital.model._

object QuartermasterCard extends Denizen(DenizenId("258"), "Quartermaster", Suit.Order) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.quartermaster"),
    persistent = false, cost = Cost.free,
    text = "**WAKE:** If you rule this card, gain 1 Supply.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Quartermaster (card 258, site-only), WAKE: "If you rule this card, gain 1
  * Supply."
  *
  * An optional Wake power, once per turn, which the engine enforces, as Marble
  * Fountains is. A card at a site the player rules is in reach wherever their
  * pawn is, so the ruler of Quartermaster's site may use it from anywhere. A
  * player whose pawn stands there without ruling it may not. Its line reads
  * the Supply the track allowed, as Wayside Inn's does: a full track gains
  * nothing and writes nothing.
  */
case object Quartermaster extends PhasePower:
  val id: PowerId = QuartermasterCard.power.id
  def timing: PowerTiming = PowerTiming.Wake
  val Supply: Int = 1
  val gained: NoteKey = NoteSupport.gainedKey(NoteKey.Used)
  override def noteKeys: Vector[NoteKey] = Vector(gained)

  def usable(ready: ReadyGame, player: PlayerId,
      source: DecisionOptionRef): Boolean = source match
    case DecisionOptionRef.Denizen(card) =>
      PowerAccess.ruledSites(ready, player).exists(site =>
        ready.game.current.map.sites.get(site)
          .exists(_.denizens.exists(_.id == card)))
    case _ => false

  def build(ready: ReadyGame, player: PlayerId, source: DecisionOptionRef)
      : Either[OathViolation, Operation] = Right(Sequence(Vector(
    GainSupply(player, Supply),
    Note(id, NoteSupport.gainNote(gained, source, player, NoteUnit.Supply,
      NoteSupport.supply)))))
