package oathdigital.gameplay.powers.economy

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.economy.MusterSource
import oathdigital.gameplay.powerresolver.{OperationRewrite, PowerCtx}
import oathdigital.gameplay.powers.PowerAnswers
import oathdigital.model._

/** What the Muster and Trade source powers share (catalog batch 5 design,
  * "Slice 1: Muster and Trade sources"). Each rewrites the source decision,
  * which its window folds as `Vector(decide)`.
  */
private[economy] object EconomySources:

  /** `operations` with `cards` added to each source decision, after the
    * options it offers. A card the decision offers already is not added
    * again, and one that holds favor or secrets arrives unavailable, as it
    * would at the pawn site.
    */
  def widened(operations: Vector[Operation],
      cards: Vector[SiteDenizenState]): Vector[Operation] =
    withOptions(operations) { options =>
      val offered = options.map(_.ref).toSet
      options ++ cards.map(MusterSource.offered)
        .filterNot(option => offered(option.ref))
    }

  /** Every card with a suit at the sites in play in the region of `actor`'s
    * pawn, the pawn's own site included, in map then site order. A card
    * without a suit is no source, as at the pawn site. */
  def inPawnRegion(catalog: ExecutableCatalog, ready: ReadyGame,
      actor: PlayerId): Vector[SiteDenizenState] =
    val map = ready.game.current.map
    val region = PowerAccess.pawnSite(ready, actor).flatMap(map.regionOf)
    map.inPlay.filter(site => region.exists(map.regionOf(site).contains))
      .flatMap(site => map.sites.get(site).toVector.flatMap(_.denizens))
      .filter(card => catalog.suitOf(card.id).nonEmpty)

  /** The priority an exemption folds at: after the powers that add cards,
    * at 0, so that a card one of them adds can be exempted too. */
  val ExemptionPriority: Int = 1

  /** `operations` with every source the token-free rule withheld made
    * available, when `admits` accepts its card. */
  def exempted(operations: Vector[Operation],
      admits: DecisionOptionRef => Boolean): Vector[Operation] =
    withOptions(operations)(_.map {
      case DecisionOption.Unavailable(option, MusterSource.Occupied)
          if admits(option.ref) => option
      case other => other
    })

  /** `operations` with the actor's payment onto the answered source marked
    * `intoOccupied`, which `PayCostRules` would refuse onto a card that holds
    * tokens. The source decision is the gate: an occupied card is answerable
    * only when an exemption made it available, so the mark changes nothing
    * for a token-free card. Every other payment keeps the check.
    */
  def intoOccupied(ctx: PowerCtx, operations: Vector[Operation],
      decisionId: String): Vector[Operation] =
    PowerAnswers.last(ctx.answered, decisionId).flatMap(idOf) match
      case None => operations
      case Some(card) => OperationRewrite.ops(ctx, operations)((_, _) => {
        case pay: PayCost if pay.player == ctx.activePlayer &&
            pay.placedAt == Location.OnCard(card) =>
          Vector(pay.copy(intoOccupied = true))
      })

  private def idOf(ref: DecisionOptionRef): Option[CardId] = ref match
    case DecisionOptionRef.Denizen(id) => Some(id)
    case DecisionOptionRef.Edifice(id) => Some(id)
    case _ => None

  /** `operations` with `change` applied to the options of each choose-one
    * decision. */
  private def withOptions(operations: Vector[Operation])(
      change: Vector[DecisionOption] => Vector[DecisionOption])
      : Vector[Operation] = operations.map {
    case decide: Decide => decide.query match {
      case one: DecisionQuery.ChooseOne =>
        decide.copy(query = one.copy(options = change(one.options)))
      case _ => decide
    }
    case other => other
  }
