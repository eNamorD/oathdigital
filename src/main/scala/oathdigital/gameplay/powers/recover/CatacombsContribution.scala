package oathdigital.gameplay.powers.recover

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powers.{CatalogResolution, SelectedModifier}
import oathdigital.gameplay.operations.Costs
import oathdigital.gameplay.powerresolver._
import oathdigital.model._

object CatacombsCard extends Denizen(DenizenId("201"), "Catacombs", Suit.Arcane) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.catacombs"),
    persistent = false, cost = Cost(secret = 1),
    text = "If this site has an empty relic slot, draw a relic from the " +
      "relic deck and place it here facedown.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Catacombs (Task 5): a Transform at `RecoverActionEligibility` (ruling C)
  * places a relic facedown here for 1 secret; ruling K keeps permission and
  * effect in one contribution. The card may sit at the pawn's site, at a site
  * the actor rules, or be an adviser. The relic goes to the card's own site,
  * or to the pawn's site for an adviser. */
final case class CatacombsContribution private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = CatacombsCard.id
  def id: PowerId = CatacombsContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  // Stable across the action: card presence, never the relic/secrets spent.
  // At a modifier-selection window it is offered for a Recover only.
  override def applicable(ctx: PowerCtx): Boolean =
    SelectedModifier.selectionAction(ctx.window).forall(
      _ == MajorActionType.Recover) &&
      PowerAccess.locate(ctx.state, ctx.activePlayer, cardId).isDefined
  override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    Vector(Costs.onCard(actor, cardId, Cost(secret = 1), catalog))
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.RecoverActionEligibility -> Vector(
      Transform((ctx, ops) => place(ctx.activePlayer) +: ops)))
  // No generic execution path enforces a site's `relicSlots` for a card
  // Move, so the placement checks the capacity itself.
  private def place(actor: PlayerId): Operation = BuildOps((ready, _) => for
    siteId <- PowerAccess.siteOf(ready, actor, cardId)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- Either.cond(catalog.site(siteId).exists(d =>
      ready.game.current.map.sites.get(siteId).fold(0)(_.relics.size) < d.relicSlots),
      (), OathViolation.RecoverUnavailable("site has no empty relic slot"))
    relic <- ready.game.current.commonCards.relicDeck.headOption.toRight(OathViolation.RecoverUnavailable("relic deck is empty"))
  yield Vector[CoreOperation](
    Move(Piece.Card(relic),
      PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
      PositionedLocation(Location.Site(siteId)),
      resultingOrientation = Some(Orientation.FaceDown)),
    Costs.onCard(actor, cardId, Cost(secret = 1), catalog)))

object CatacombsContribution:
  val id: PowerId = CatacombsCard.power.id
  def forCatalog(catalog: ExecutableCatalog): CatacombsContribution =
    new CatacombsContribution(catalog)
