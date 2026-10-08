package oathdigital.gameplay.powers.travel

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower}
import oathdigital.gameplay.operations.SupplyStrip
import oathdigital.gameplay.powerresolver.{Contribution, PowerCtx, Transform}
import oathdigital.gameplay.powers.SelectedModifier
import oathdigital.model._

object SpecialEnvoyCard extends Denizen(DenizenId("158"), "Special Envoy", Suit.Nomad):
  val power = PrintedPower(PowerId("denizen.special-envoy"),
    persistent = false, cost = Cost.free,
    text = "Spend no Supply. After traveling, end your Act Phase.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Special Envoy (card 158), a selected Travel modifier with no cost: Travel
  * spends no Supply, and after traveling the Act Phase ends.
  *
  * It folds after every other Travel effect, above Royal Stables' priority,
  * so it removes whatever Supply payment Royal Stables left, and its
  * `EnterPhase(Rest)` comes after Dragonskin Drum's gain and Tyrant's kill.
  * Its line, "The Act Phase ended.", always follows. The waiver writes no
  * line: the start line's cost span shows it.
  */
final case class SpecialEnvoy private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  val cardId: DenizenId = SpecialEnvoyCard.id
  def id: PowerId = SpecialEnvoy.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Travel)
  override def priority: Int = SpecialEnvoy.Priority
  override def noteKeys: Vector[NoteKey] = Vector(SpecialEnvoy.ended)

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TravelCost -> Vector(Transform((ctx, operations) =>
      SupplyStrip(ctx.activePlayer, operations) ++ Vector(
        EnterPhase(Phase.Rest),
        Note(id, _ => Some(SpecialEnvoy.ended(
          PowerSourceRef.Card(cardId))))))))

  override def appliesAt(ctx: PowerCtx): Boolean =
    TravelRoute.pawnMove(ctx.operation).nonEmpty

object SpecialEnvoy:
  val id: PowerId = SpecialEnvoyCard.power.id
  /** Folds after every Travel power, Royal Stables included. */
  val Priority: Int = RoyalStables.Priority + 1
  /** "The Act Phase ended." */
  val ended: NoteKey = NoteKey("ended", Vector(
    NotePart.Text("The Act Phase ended.")))

  def forCatalog(catalog: ExecutableCatalog): SpecialEnvoy =
    new SpecialEnvoy(catalog)
