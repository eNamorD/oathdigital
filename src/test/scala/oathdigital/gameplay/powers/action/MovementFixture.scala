package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerStepRecorded, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Table

/** Staging and driving shared by the Whistle, Brass Horse and Magic Carpet
  * suites, on the quiet table: the actor p1 at Ancient City, p2 at Broken
  * Peaks and p3 at Buried Giant. `homelands` adds the ruined edifices the
  * first game stands at its two Homelands.
  */
object MovementFixture:
  import PowerFixture._
  import TargetsFixture.rules

  /** The parked decision: the same `ParkedDecisionAssertions`, built on the
    * same catalog and phase power catalog, that `TargetsFixture` already
    * has.
    */
  val parked = TargetsFixture.parked


  val ancientCity: SiteId = SiteId("site:ancient-city")
  val brokenPeaks: SiteId = SiteId("site:broken-peaks")
  val buriedGiant: SiteId = SiteId("site:buried-giant")
  val deepWoods: SiteId = SiteId("site:deep-woods")
  val desolateShore: SiteId = SiteId("site:desolate-shore")
  val dunes: SiteId = SiteId("site:dunes")

  /** A ruined Beast edifice (Hiding Place) at Deep Woods and a ruined Hearth
    * one (Squalid District) at Golden Valley, as the first game stands them
    * at its Homelands; no other card lies at any site. */
  def homelands(ready: ReadyGame): ReadyGame = Table.from(ready)
    .edifice("Hiding Place", EdificeSide.Ruined, at = deepWoods)
    .edifice("Squalid District", EdificeSide.Ruined, at = "Golden Valley")
    .unchecked

  def pawnOf(ready: ReadyGame, id: PlayerId = actor): SiteId =
    player(ready, id).pawnSite.get

  def withSecrets(ready: ReadyGame, faceUp: Int): ReadyGame =
    withBoard(ready)(_.copy(faceUpSecrets = faceUp))

  def relicOf(ready: ReadyGame, id: RelicId,
      holder: PlayerId = actor): Option[RelicState] =
    player(ready, holder).relics.find(_.id == id)

  def withRelicTokens(ready: ReadyGame, id: RelicId, tokens: Tokens)
      : ReadyGame = updateActor(ready)(p => p.copy(relics = p.relics.map(r =>
    if r.id == id then r.copy(tokens = tokens) else r)))

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
  def freshDenizen(ready: ReadyGame, suit: Suit, skip: Int = 0): DenizenId =
    val present = CardIndex.from(ready.game).toOption.get.ids
    catalog.denizens.filter(_.suit == suit).map(d => DenizenId(d.id.value))
      .filterNot(present.contains)(skip)

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
      PhasePowerCatalog.default(catalog), WalkerPowers.empty).exists(_.power.id == power)

  def parkedAt(transition: OathTransition, decisionId: String): Boolean =
    parked.parkedDecision(transition.state).exists(facts =>
      facts.decision == decisionId && facts.awaiting == actor)

  def backToActing(transition: OathTransition): Boolean =
    val turn = readyOf(transition.state).game.current.turn
    parked.parkedDecision(transition.state).isEmpty &&
      turn.phase == Phase.Act && turn.activePlayer == actor

  def ops(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten
