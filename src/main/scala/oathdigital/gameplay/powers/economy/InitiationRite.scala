package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{AdviserOnly, Denizen, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  PowerCtx, Transform}
import oathdigital.gameplay.powers.CatalogCards
import oathdigital.model._

object InitiationRiteCard extends Denizen(DenizenId("73"), "Initiation Rite", Suit.Arcane) with Locked with AdviserOnly:
  val power = PrintedPower(PowerId("denizen.initiation-rite"),
    persistent = false, cost = Cost.free,
    text = "To muster, you **must** place [secret] instead of [favor].")
  val powers: Vector[PrintedPower] = Vector(power)

/** Initiation Rite (card 73, adviser-only, locked): "To muster, you must
  * place [secret] instead of [favor]."
  *
  * An automatic rule of a faceup adviser, as Vow of Obedience's is: "must" is
  * not a choice, so the rule keeps the default automatic resolution although
  * the catalog marks the power `persistent: false`. At its holder's Muster
  * cost, the `PayCost` that places favor on the card places as many secrets
  * instead, moved from the holder's faceup secrets as a Trade for favor
  * places one. The Supply payment is unchanged, and Trade is not touched.
  * The payment stays required, so a holder with no faceup secret cannot pay,
  * and the Muster has no source to offer.
  *
  * It writes no line: the start line's cost span shows Supply only, for every
  * Muster, and the secret's move still anchors the start line.
  */
final case class InitiationRite private (cardId: DenizenId)
    extends ContributingPower:
  def id: PowerId = InitiationRite.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.MusterCost -> Vector(Transform((ctx, operations) =>
      operations.map {
        case pay: PayCost if pay.player == ctx.activePlayer &&
            pay.cost.favor > 0 =>
          pay.copy(cost = pay.cost.copy(favor = 0,
            secret = pay.cost.secret + pay.cost.favor))
        case other => other
      })))

  /** The acting player holds this card as a faceup adviser. */
  override def applicable(ctx: PowerCtx): Boolean =
    ctx.state.game.current.players.find(_.player == ctx.activePlayer)
      .exists(_.advisers.exists {
        case DenizenState(card, Orientation.FaceUp, _) => card == cardId
        case _ => false
      })

object InitiationRite:
  val id: PowerId = PowerId("denizen.initiation-rite")

  def forCatalog(catalog: ExecutableCatalog): Option[InitiationRite] =
    CatalogCards.denizen(catalog, id).map(new InitiationRite(_))
