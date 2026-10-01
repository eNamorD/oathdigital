package oathdigital.gameplay.powers.whenplayed

import scala.reflect.ClassTag

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy, OperationRestrictions}
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.PowerFixture.{actor, base}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers, WalkerStepRecorded}
import oathdigital.model._

/** Drives a When Played power through its `CardPlayed` hook, the way the
  * Dazzle and Conspiracy suites do. The actor is `PowerFixture.actor`, p1.
  */
object WhenPlayedHarness:
  def hook(card: DenizenId): CardPlayedFaceup =
    CardPlayedFaceup(card, RuleSourceRef.Adviser(actor, card))

  /** The hook of `card` played to `site`. */
  def hookAt(card: DenizenId, site: SiteId): CardPlayedFaceup =
    CardPlayedFaceup(card, RuleSourceRef.SiteCard(site, card))

  def powers(power: ContributingPower): WalkerPowers =
    WalkerPowers(Vector(power),
      restrictionSet = OperationRestrictions.forCatalog(catalog))

  def play(ready: ReadyGame, power: ContributingPower, card: DenizenId)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook(card), None, powers(power))

  /** Walks `hook`, for a card played to a site or as an adviser. */
  def fire(ready: ReadyGame, power: ContributingPower, hook: CardPlayedFaceup)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook, None, powers(power))

  /** The question parked at `tree`, as the walker offers it. */
  def question(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, tree: PendingTree): Decide =
    ProcedureWalker.parkedDecide(ready, hook, tree, powers(power)).get

  /** Resumes `tree` with the actor's `answer` to `decisionId`. */
  def resume(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, tree: PendingTree, decisionId: String,
      answer: DecisionAnswer): Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(ready, hook, tree,
      Answered(decisionId, answer, actor), powers(power))

  def finished(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Finished =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Finished]

  def parked(outcome: Either[OathViolation, WalkerOutcome])
      : WalkerOutcome.Parked =
    outcome.toOption.get.asInstanceOf[WalkerOutcome.Parked]

  def recorded(events: Vector[OathEvent]): Vector[CoreOperation] =
    events.collect { case step: WalkerStepRecorded => step.ops }.flatten

  /** The state a journal replay of `events` reaches from `from`. */
  def replayed(from: ReadyGame, events: Vector[OathEvent]): ReadyGame =
    OperationPipeline.run(from, recorded(events),
      OperationPolicy.Permissive, Vector.empty)(Right(_)).toOption.get.state

  /** The power of type `P` that the default walker catalog registers, so a
    * suite fails when its power is not wired in. */
  def registered[P <: ContributingPower](using tag: ClassTag[P]): P =
    WalkerPowerCatalog.default(catalog).powers.collectFirst {
      case tag(power) => power }.get

  /** p1's site on the quiet table. */
  val homeSite: SiteId = PowerFixture.home(base)
  /** Another site in p1's region. */
  val nearSite: SiteId = inRegion.find(_ != homeSite).get
  /** A site in another region. */
  val awaySite: SiteId =
    base.game.current.map.inPlay.find(!inRegion.contains(_)).get

  private def inRegion: Vector[SiteId] =
    val map = base.game.current.map
    map.inPlay.filter(map.regionOf(_) == map.regionOf(homeSite))

  /** `cards` by site, flattened in map order. */
  def inMapOrder(cards: (SiteId, Vector[CardId])*): Vector[CardId] =
    val bySite = cards.toMap
    base.game.current.map.inPlay.flatMap(bySite.getOrElse(_, Vector.empty))

  /** The edifices of `suit` in the quiet table's edifice deck. */
  def edificesOf(suit: Suit): Vector[EdificeId] =
    base.game.current.commonCards.edificeDeck
      .filter(catalog.suitOf(_).contains(suit))
