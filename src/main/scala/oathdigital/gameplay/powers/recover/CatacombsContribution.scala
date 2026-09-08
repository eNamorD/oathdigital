package oathdigital.gameplay.powers.recover

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.{OathViolation, RuleSourceRef}
import oathdigital.gameplay.operations._
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

/** Catacombs on the walker seam (Task 5): draws a relic and places it
  * facedown here for 1 secret. Ruling C: eligibility comes from this
  * Transform firing at `RecoverActionEligibility` (a `Restriction` of
  * `None` cannot mean "eligible", only "irrelevant"); ruling K: one
  * contribution carries the permission and the effect together. */
final case class CatacombsContribution private (cardId: DenizenId)
    extends ContributingPower {
  def id: PowerId = CatacombsContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override def resolution: PowerResolution = PowerResolution.PlayerSelected
  // Stable across the action: card presence, never the relic/secrets spent.
  override def applicable(ctx: PowerCtx): Boolean = (for {
    site <- ctx.state.game.current.players.find(_.player == ctx.actor)
      .flatMap(_.pawnSite)
    state <- ctx.state.game.current.map.sites.get(site)
  } yield state.denizens.exists {
    case d: DenizenState => d.id == cardId && d.orientation == Orientation.FaceUp
    case _ => false
  }).getOrElse(false)
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.RecoverActionEligibility -> Vector(
      Transform((ctx, ops) => place(ctx.actor) +: ops)))
  private def place(actor: PlayerId): Operation = BuildOps((ready, _) => for {
    site <- ready.game.current.players.find(_.player == actor)
      .flatMap(_.pawnSite).toRight(OathViolation.PawnSiteMissing(actor))
    relic <- ready.game.current.commonCards.relicDeck.headOption.toRight(
      OathViolation.RecoverUnavailable("relic deck is empty"))
  } yield Vector[CoreOperation](
    Move(Piece.Card(relic),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      PositionedLocation(Location.Site(site)),
      resultingOrientation = Some(Orientation.FaceDown)),
    PayCost(actor, Location.OnCard(cardId), Cost(secret = 1))))
}

object CatacombsContribution {
  val id: PowerId = PowerId("denizen.catacombs")
  // None when the catalog has no card carrying this power (e.g. a test stub).
  def forCatalog(catalog: ExecutableCatalog): Option[CatacombsContribution] =
    catalog.denizens.find(_.powers.exists(_.id == id))
      .map(d => new CatacombsContribution(DenizenId(d.id.value)))
}
