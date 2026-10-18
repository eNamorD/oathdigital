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

/** Catacombs (Task 5), a Recover modifier picked in the walk's modifier
  * decision. The pick pays its secret onto the card. Once picked, a
  * Transform at `RecoverActionEligibility` (ruling C) places the relic
  * deck's top relic facedown; ruling K keeps permission and effect in one
  * contribution. The card may sit at the pawn's site, at a site the actor
  * rules, or be an adviser. The relic goes to the card's own site, or to
  * the pawn's site for an adviser.
  *
  * A site with no empty relic slot, or an empty relic deck, shows the pick
  * unavailable, so the secret is never paid for a relic that cannot be
  * placed. */
final case class CatacombsContribution private (catalog: ExecutableCatalog)
    extends ContributingPower:
  val cardId: DenizenId = CatacombsCard.id
  def id: PowerId = CatacombsContribution.id
  def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
  override lazy val resolution: PowerResolution =
    CatalogResolution.of(catalog, id)
  override def selectionCard: Option[CardId] = Some(cardId)
  /** Its use at its selection window: its pick pays one secret onto its
    * card (catalog batch 8, slice 10). */
  override def use(ctx: PowerCtx): Option[PowerUse] =
    for
      _ <- SelectedModifier.selectionAction(ctx.window)
      printed <- catalog.printedPower(id)
    yield PowerUse.of(ctx.actor, printed, cardId, secret = true,
      places = true)
  override def selectionWindows: Set[PowerWindow] =
    Set(PowerWindow.RecoverModifierSelection)
  // Stable across the action: card presence, never the relic/secrets spent.
  // At a modifier-selection window it is offered for a Recover only.
  override def applicable(ctx: PowerCtx): Boolean =
    SelectedModifier.selectionAction(ctx.window).forall(
      _ == MajorActionType.Recover) &&
      PowerAccess.locate(ctx.state, ctx.actor, cardId).isDefined
  override def offerReason(ctx: PowerCtx): Option[OfferReason] =
    placement(ctx.state, ctx.actor).left.toOption.collect {
      case OathViolation.RecoverUnavailable(reason) =>
        OfferReason.Unavailable(reason)
    }
  override def selectionPayments(ready: ReadyGame, actor: PlayerId)
      : Vector[CoreOperation] =
    Vector(Costs.onCard(actor, cardId, Cost(secret = 1), catalog))
  def contributions: Map[PowerWindow, Vector[Contribution]] =
    Map(PowerWindow.RecoverActionEligibility -> Vector(
      Transform((ctx, ops) => place(ctx.actor) +: ops)))

  private def place(actor: PlayerId): Operation = BuildOps((ready, _) =>
    placement(ready, actor).map((siteId, relic) => Vector[CoreOperation](
      Move(Piece.Card(relic),
        PositionedLocation(Location.Deck(CardDeck.Relic), StackPosition.Top),
        PositionedLocation(Location.Site(siteId)),
        resultingOrientation = Some(Orientation.FaceDown)))))

  /** Where the relic goes and which relic, or why it cannot be placed. No
    * generic execution path enforces a site's `relicSlots` for a card Move,
    * so the placement checks the capacity itself. */
  private def placement(ready: ReadyGame, actor: PlayerId)
      : Either[OathViolation, (SiteId, RelicId)] = for
    siteId <- PowerAccess.siteOf(ready, actor, cardId)
      .toRight(OathViolation.PawnSiteMissing(actor))
    _ <- Either.cond(catalog.site(siteId).exists(site =>
      ready.game.current.map.sites.get(siteId).fold(0)(_.relics.size) <
        site.relicSlots),
      (), OathViolation.RecoverUnavailable(CatacombsContribution.noEmptySlot))
    relic <- ready.game.current.commonCards.relicDeck.headOption.toRight(
      OathViolation.RecoverUnavailable(CatacombsContribution.emptyRelicDeck))
  yield (siteId, relic)

object CatacombsContribution:
  val id: PowerId = CatacombsCard.power.id
  /** Why it cannot be picked at a site whose relic slots are full. */
  val noEmptySlot: String = "This site has no empty relic slot"
  /** Why it cannot be picked with no relic left to draw. */
  val emptyRelicDeck: String = "The relic deck is empty"
  def forCatalog(catalog: ExecutableCatalog): CatacombsContribution =
    new CatacombsContribution(catalog)
