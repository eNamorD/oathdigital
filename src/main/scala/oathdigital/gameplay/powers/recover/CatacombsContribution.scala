package oathdigital.gameplay.powers.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Catacombs (Task 5): a Transform at `RecoverActionEligibility` (ruling C)
  * places a relic facedown here for 1 secret; ruling K keeps permission and
  * effect in one contribution. The card may sit at the pawn's site, at a site
  * the actor rules, or be an adviser. The relic goes to the card's own site,
  * or to the pawn's site for an adviser. */
final case class CatacombsContribution private (cardId: DenizenId,
    catalog: ExecutableCatalog) extends ContributingPower {
  def id: PowerId = CatacombsContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def resolution: PowerResolution = PowerResolution.PlayerSelected
  // Stable across the action: card presence, never the relic/secrets spent.
  override def applicable(ctx: PowerCtx): Boolean =
    PowerAccess.locate(ctx.state, ctx.activePlayer, cardId).isDefined
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.RecoverActionEligibility -> Vector(
      Transform((ctx, ops) => place(ctx.activePlayer) +: ops)))
  // Mirrors the legacy capacity guard: no generic execution path enforces
  // `relicSlots` for a card Move (PowerOperations.PlaceRelicAtSite:93).
  private def place(actor: PlayerId): Operation = BuildOps((ready, _) => for {
    siteId <- PowerAccess.siteOf(ready, actor, cardId)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- Either.cond(catalog.sites.find(_.id == siteId).exists(d =>
      ready.game.current.map.sites.get(siteId).fold(0)(_.relics.size) < d.relicSlots),
      (), OathViolation.RecoverUnavailable("site has no empty relic slot"))
    relic <- ready.game.current.commonCards.relicDeck.headOption.toRight(OathViolation.RecoverUnavailable("relic deck is empty"))
  } yield Vector[CoreOperation](
    Move(Piece.Card(relic),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      PositionedLocation(Location.Site(siteId)),
      resultingOrientation = Some(Orientation.FaceDown)),
    Costs.onCard(actor, cardId, Cost(secret = 1), catalog)))
}

object CatacombsContribution {
  val id: PowerId = PowerId("denizen.catacombs")
  // None if the catalog has no such card (e.g. a test stub).
  def forCatalog(catalog: ExecutableCatalog): Option[CatacombsContribution] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(d => new CatacombsContribution(DenizenId(d.id.value), catalog))
}
