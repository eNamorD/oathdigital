package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerStepRecorded
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Staging and driving shared by the Whistle, Brass Horse and Magic Carpet
  * suites. The first game seats three players: the actor (p2) at
  * ancient-city, p1 at buried-giant and p3 at broken-peaks.
  */
object MovementFixture {
  import PowerFixture._
  import TargetsFixture.rules

  val p1: PlayerId = PlayerId("p1")
  val p3: PlayerId = PlayerId("p3")

  val ancientCity: SiteId = SiteId("site:ancient-city")
  val brokenPeaks: SiteId = SiteId("site:broken-peaks")
  val buriedGiant: SiteId = SiteId("site:buried-giant")
  val deepWoods: SiteId = SiteId("site:deep-woods")
  val desolateShore: SiteId = SiteId("site:desolate-shore")
  val dunes: SiteId = SiteId("site:dunes")

  def pawnOf(ready: ReadyGame, id: PlayerId = actor): SiteId =
    player(ready, id).pawnSite.get

  def withSecrets(ready: ReadyGame, faceUp: Int): ReadyGame =
    withBoard(ready)(_.copy(faceUpSecrets = faceUp))

  def relicOf(ready: ReadyGame, id: RelicId,
      holder: PlayerId = actor): Option[RelicState] =
    player(ready, holder).relics.find(_.id == id)

  def withRelicTokens(ready: ReadyGame, id: RelicId, tokens: Tokens)
      : ReadyGame = updateActor(ready)(p => p.copy(relics = p.relics.map(r =>
    if (r.id == id) r.copy(tokens = tokens) else r)))

  /** Replaces a regional discard pile. The old pile goes back under the world
    * deck and a new card leaves it if it is there, so the inventory stays
    * whole. A card the first game did not deal is simply added.
    */
  def withDiscard(ready: ReadyGame, region: Region,
      pile: Vector[WorldCardId]): ReadyGame = ready.updateCurrent { c =>
    val cards = c.commonCards
    c.copy(commonCards = cards.copy(
      worldDeck = cards.worldDeck.filterNot(pile.contains) ++
        cards.discard(region).filterNot(pile.contains),
      regionalDiscards = cards.regionalDiscards.updated(region, pile)))
  }

  /** A denizen of `suit` that is nowhere in the game yet. */
  def freshDenizen(ready: ReadyGame, suit: Suit, skip: Int = 0): DenizenId = {
    val present = CardIndex.from(ready.game).toOption.get.ids
    catalog.denizens.filter(_.suit == suit).map(d => DenizenId(d.id.value))
      .filterNot(present.contains)(skip)
  }

  def aVision(ready: ReadyGame): VisionId =
    ready.game.current.commonCards.worldDeck.collectFirst {
      case vision: VisionId => vision }.get

  def use(ready: ReadyGame, power: PowerId, relic: RelicId)
      : Either[OathViolation, OathTransition] = rules.startWalker(Ready(ready),
    ActionRef.UsePower(power), actor, Vector.empty,
    Vector(DecisionOptionRef.Relic(relic)))

  def choose(state: OathState, decisionId: String, ref: DecisionOptionRef)
      : Either[OathViolation, OathTransition] = rules.resolveWalker(state,
    actor, decisionId, DecisionAnswer.ChooseOneAnswer(ref))

  def readyOf(state: OathState): ReadyGame = state.asInstanceOf[Ready].value

  def usable(ready: ReadyGame, power: PowerId): Boolean =
    PhasePowerProcedure.usable(catalog, ready, actor,
      PhasePowerCatalog.default(catalog)).exists(_.power.id == power)

  def parkedAt(transition: OathTransition, decisionId: String): Boolean =
    transition.continue ==
      OathContinue.AwaitingPowerDecision(actor, DecisionId(decisionId))

  def backToActing(transition: OathTransition): Boolean =
    transition.continue == OathContinue.ActActionSelection(actor)

  def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten
}
