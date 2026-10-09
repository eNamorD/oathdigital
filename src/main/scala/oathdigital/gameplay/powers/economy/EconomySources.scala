package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.actions.economy.MusterSource
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

  /** Every card at the sites in play in the region of `actor`'s pawn, the
    * pawn's own site included, in map then site order. */
  def inPawnRegion(ready: ReadyGame, actor: PlayerId)
      : Vector[SiteDenizenState] =
    val map = ready.game.current.map
    val region = PowerAccess.pawnSite(ready, actor).flatMap(map.regionOf)
    map.inPlay.filter(site => region.exists(map.regionOf(site).contains))
      .flatMap(site => map.sites.get(site).toVector.flatMap(_.denizens))

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
