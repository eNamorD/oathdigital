package oathdigital.gameplay

import oathdigital.catalog.CardRestrictions
import oathdigital.gameplay.actions.PlacementRules
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** Staging and driving shared by the card-play placement suites. The powers
  * built here are test doubles: they change `PlacementRules` and nothing else.
  */
object PlacementFixture {
  /** A power that changes the rules every play is planned under. */
  def rulePower(name: String)(
      change: PlacementRules => PlacementRules): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId(name)
      def source: RuleSourceRef = RuleSourceRef.GameRule(name)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.SearchPlayAdviser -> Vector(Transform((ctx, ops) =>
          ctx.operation match {
            case tree: CardPlayProcedure.PlacementTree =>
              tree.adjust(ops)(change)
            case _ => ops
          })))
    }

  val discardFirst: ContributingPower =
    rulePower("test.discard-first")(_.withSiteDiscardFirst)
  val limitTwo: ContributingPower =
    rulePower("test.limit-two")(_.limitAdvisers(2))

  /** The unrestricted denizens still in the world deck. */
  def plain(ready: ReadyGame): Vector[DenizenId] =
    ready.game.current.commonCards.worldDeck.collect {
      case id: DenizenId if catalog.denizens.exists(d => d.id.value == id.value &&
        d.restrictions == CardRestrictions.Unrestricted) => id
    }

  def actorOf(ready: ReadyGame): PlayerState =
    ready.game.current.players.find(
      _.player == ready.game.current.turn.activePlayer).get

  def denizen(id: DenizenId, tokens: Tokens = Tokens.empty): DenizenState =
    DenizenState(id, Orientation.FaceUp, tokens)

  /** `card` in the actor's hand and the actor's pawn site holding exactly
    * `site`. Every card that leaves a place goes to the matching deck, so the
    * inventory stays whole.
    */
  def staged(card: DenizenId, site: Vector[SiteDenizenState])
      : (ReadyGame, PlayerId, SiteId) = {
    val base = initialReady
    val current = base.game.current
    val actor = actorOf(base)
    val siteId = actor.pawnSite.get
    val placed = site.collect { case d: DenizenState => d.id: CardId } :+ card
    val edifices = site.collect { case e: EdificeState => e.id }
    val before = current.map.sites(siteId).denizens
    (base.updateCurrent(_.copy(
      temporaryHands = current.temporaryHands.updated(actor.player, Vector(card)),
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot(placed.contains) ++
          before.collect { case d: DenizenState => d.id },
        edificeDeck = current.commonCards.edificeDeck.filterNot(edifices.contains) ++
          before.collect { case e: EdificeState if !edifices.contains(e.id) => e.id }),
      map = current.map.copy(sites = current.map.sites.updated(siteId,
        current.map.sites(siteId).copy(denizens = site))))),
      actor.player, siteId)
  }

  /** The actor rules the pawn site, so a Hall of Ministers does not protect it. */
  def ruledByActor(ready: ReadyGame, site: SiteId): ReadyGame =
    ready.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, c.map.sites(site).copy(forces =
        SiteForces.Occupied(ForceKind.Exile(actorOf(ready).lineage), 1))))))

  def decisionId(card: WorldCardId, kind: String): String =
    s"cardplay.$kind.${card.kind}.${card.value}"

  def build(ready: ReadyGame, actor: PlayerId, card: WorldCardId): Operation =
    CardPlayProcedure.build(catalog, ready, actor, card,
      CardPlayProcedure.Origin.TemporaryHand).toOption.get

  def park(ready: ReadyGame, tree: Operation, powers: WalkerPowers)
      : PendingTree = ProcedureWalker.advance(ready, tree, None, powers)
    .toOption.get.asInstanceOf[WalkerOutcome.Parked].tree

  def answer(ready: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers, id: String, ref: DecisionOptionRef,
      actor: PlayerId): WalkerOutcome = ProcedureWalker.resolve(ready, tree,
    pending, Answered(id, DecisionAnswer.ChooseOneAnswer(ref), actor), powers)
    .toOption.get

  /** The options of the decision the walk is parked on. */
  def options(ready: ReadyGame, tree: Operation, pending: PendingTree,
      powers: WalkerPowers): Vector[DecisionOptionRef] =
    ProcedureWalker.parkedDecide(ready, tree, pending, powers).get.query
      .asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref)
}
