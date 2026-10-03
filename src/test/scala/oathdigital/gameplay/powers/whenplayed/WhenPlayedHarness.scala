package oathdigital.gameplay.powers.whenplayed

import scala.reflect.ClassTag

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy,
  OperationRestrictions}
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.{PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.PowerFixture.{actor, quietTable}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.PhasePowerHarness
import oathdigital.testkit.Unwrap.{accepted, present}

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
      hook: CardPlayedFaceup, tree: PendingTree)(using munit.Location)
      : Decide =
    present(ProcedureWalker.parkedDecide(ready, hook, tree, powers(power)),
      "the parked question")

  /** Resumes `tree` with the actor's `answer` to `decisionId`. */
  def resume(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, tree: PendingTree, decisionId: String,
      answer: DecisionAnswer): Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(ready, hook, tree,
      Answered(decisionId, answer, actor), powers(power))

  /** The state `events`' recorded operations reach from `from`. The hook is
    * walked bare, outside any action, so its steps replay as operations
    * rather than through the rules' journal evolve. */
  def replayed(from: ReadyGame, events: Vector[OathEvent]): ReadyGame =
    accepted(OperationPipeline.run(from, PhasePowerHarness.ops(events),
      OperationPolicy.Permissive, Vector.empty)(Right(_))).state

  /** The power of type `P` that the default walker catalog registers, so a
    * suite fails when its power is not wired in. */
  def registered[P <: ContributingPower](using tag: ClassTag[P]): P =
    WalkerPowerCatalog.default(catalog).powers.collectFirst {
      case tag(power) => power }.get

  /** p1's site on the quiet table. */
  val homeSite: SiteId = PowerFixture.actorsSite(quietTable)
  /** Another site in p1's region. */
  val nearSite: SiteId = inRegion.find(_ != homeSite).get
  /** A site in another region. */
  val awaySite: SiteId =
    quietTable.game.current.map.inPlay.find(!inRegion.contains(_)).get

  private def inRegion: Vector[SiteId] =
    val map = quietTable.game.current.map
    map.inPlay.filter(map.regionOf(_) == map.regionOf(homeSite))

  /** `cards` by site, flattened in map order. */
  def inMapOrder(cards: (SiteId, Vector[CardId])*): Vector[CardId] =
    val bySite = cards.toMap
    quietTable.game.current.map.inPlay.flatMap(bySite.getOrElse(_,
      Vector.empty))

  /** The edifices of `suit` in the quiet table's edifice deck. */
  def edificesOf(suit: Suit): Vector[EdificeId] =
    quietTable.game.current.commonCards.edificeDeck
      .filter(catalog.suitOf(_).contains(suit))
