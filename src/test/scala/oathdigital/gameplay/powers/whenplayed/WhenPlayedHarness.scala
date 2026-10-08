package oathdigital.gameplay.powers.whenplayed

import scala.reflect.ClassTag

import oathdigital.gameplay.operations.{OperationPipeline, OperationPolicy,
  OperationRestrictions}
import oathdigital.gameplay.powerresolver.ContributingPower
import oathdigital.gameplay.powers.{NoteText, PowerFixture,
  WalkerPowerCatalog}
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

  /** `play`, with `others` registered beside `power`: a test that needs
    * another power's rule, such as Lost Tongue's, names it here. */
  def playBeside(ready: ReadyGame, power: ContributingPower, card: DenizenId,
      others: ContributingPower*): Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook(card), None,
      WalkerPowers(power +: others.toVector,
        restrictionSet = OperationRestrictions.forCatalog(catalog)))

  /** Walks `hook`, for a card played to a site or as an adviser. */
  def fire(ready: ReadyGame, power: ContributingPower, hook: CardPlayedFaceup)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook, None, powers(power))

  /** Walks `hook` with every production walker power registered, as a real
    * card play would: for a test that pins what another power does not do. */
  def fireWithEveryPower(ready: ReadyGame, hook: CardPlayedFaceup)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.advance(ready, hook, None,
      WalkerPowerCatalog.default(catalog))

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
    resumeBy(ready, power, hook, tree, decisionId, answer, actor)

  /** Resumes `tree` with `by`'s `answer` to `decisionId`: a question another
    * player owns, such as each player's burn in Revelation. */
  def resumeBy(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, tree: PendingTree, decisionId: String,
      answer: DecisionAnswer, by: PlayerId)
      : Either[OathViolation, WalkerOutcome] =
    ProcedureWalker.resolve(ready, hook, tree,
      Answered(decisionId, answer, by), powers(power))

  /** A walk answered to its end: each question in the order it was asked,
    * the events of every command, and the state the walk finished in. */
  final case class Walked(asked: Vector[Decide], events: Vector[OathEvent],
      end: ReadyGame)

  /** Fires `hook` from `ready`, then answers each question the walk parks at
    * with the next of `answers`, as the question's owner, from the state the
    * walk has reached. Fails when a question finds no answer left, or when
    * answers are left over. */
  def walkAnswering(ready: ReadyGame, power: ContributingPower,
      hook: CardPlayedFaceup, answers: DecisionAnswer*)(
      using munit.Location): Walked =
    def go(outcome: WalkerOutcome, events: Vector[OathEvent],
        asked: Vector[Decide], left: List[DecisionAnswer]): Walked =
      outcome match
        case WalkerOutcome.Finished(end, more) =>
          if left.nonEmpty then munit.Assertions.fail(
            s"${left.size} answers were left over")
          Walked(asked, events ++ more, end)
        case WalkerOutcome.Parked(tree, more) =>
          val reached = events ++ more
          val at = reachedFrom(ready, reached)
          val parked = question(at, power, hook, tree)
          val next = left.headOption.getOrElse(munit.Assertions.fail(
            s"no answer is left for ${parked.decisionId}"))
          go(accepted(resumeBy(at, power, hook, tree, parked.decisionId,
            next, parked.owner)), reached, asked :+ parked, left.drop(1))
    go(accepted(fire(ready, power, hook)), Vector.empty, Vector.empty,
      answers.toList)

  /** The state `events` reach from `ready`: `ready` itself while no step
    * has run, since `replayed` refuses an empty batch. */
  private def reachedFrom(ready: ReadyGame, events: Vector[OathEvent])
      : ReadyGame =
    if PhasePowerHarness.ops(events).isEmpty then ready
    else replayed(ready, events)

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

  /** The notes `power` journaled in `events`, in order, as plain sentences. */
  def said(power: ContributingPower, events: Vector[OathEvent])
      : Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  /** The answer that picks `ref` from a ChooseOne question. */
  def chooseOne(ref: DecisionOptionRef): DecisionAnswer =
    DecisionAnswer.ChooseOneAnswer(ref)

  /** The options of a ChooseOne question; fails with the query otherwise. */
  def chooseOneOptions(asked: Decide)(using munit.Location)
      : Vector[DecisionOption] = asked.query match
    case query: DecisionQuery.ChooseOne => query.options
    case other => munit.Assertions.fail(s"expected a ChooseOne, got $other")

  /** The largest amount each ChooseAmount question of `walked` allowed, in
    * the order they were asked. */
  def amountMaximaAsked(walked: Walked): Vector[Int] =
    walked.asked.map(_.query).collect {
      case query: DecisionQuery.ChooseAmount => query.max }
