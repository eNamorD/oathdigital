package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.setup.FirstGameSetupRules
import oathdigital.gameplay.actions.challenge.ChallengeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerParked,
  WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption

/** Challenge through the rules, as a client drives it: start, answer the
  * banner, answer the amount, and (Wandering Flame) the tied sites.
  */
class ChallengeProcedureSuite extends munit.FunSuite {
  import ChallengeFixture._

  private val setup = new FirstGameSetupRules(catalog)
  private val rules = new OathRules(catalog)
  private val pf = DecisionOptionRef.Banner(Banner.PeoplesFavor)
  private val ds = DecisionOptionRef.Banner(Banner.DarkestSecret)

  private def start(board: ReadyGame) =
    rules.startWalker(Ready(board), ActionRef.Challenge, active(board))

  private def chooseBanner(state: OathState, actor: PlayerId,
      banner: DecisionOptionRef) = rules.resolveWalker(state, actor,
    "challenge.banner", DecisionAnswer.ChooseOneAnswer(banner))

  private def chooseAmount(state: OathState, actor: PlayerId, amount: Int) =
    rules.resolveWalker(state, actor, "challenge.amount",
      DecisionAnswer.ChooseAmountAnswer(amount))

  private def chooseSites(state: OathState, actor: PlayerId,
      sites: Vector[SiteId]) = rules.resolveWalker(state, actor,
    "challenge.ribbon-site", DecisionAnswer.ChooseManyAnswer(
      sites.map(DecisionOptionRef.Site(_))))

  private def ready(state: OathState): ReadyGame = state match {
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")
  }

  private def player(board: ReadyGame, id: PlayerId): PlayerState =
    board.game.current.players.find(_.player == id).get

  /** Starts, answers the banner, and returns the state parked at the amount. */
  private def atAmount(board: ReadyGame, banner: DecisionOptionRef): OathState = {
    val started = start(board).getOrElse(fail("a legal Challenge must start"))
    chooseBanner(started.state, active(board), banner)
      .getOrElse(fail("the banner must be accepted")).state
  }

  test("starting spends the Supply and parks on the banner decision") {
    val (board, actor) = ChallengeFixture.ready(resources = 2)
    val started = start(board).getOrElse(fail("a legal Challenge must start"))
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(player(ready(started.state), actor.player).board.supply,
      SupplyTrack(actor.board.supply.supply - 1))
    assertEquals(ready(started.state).game.current.banners,
      board.game.current.banners)
  }

  test("only banners the actor could take are offered") {
    val (board, actor) = ChallengeFixture.ready(resources = 2, faceup = 0)
    val started = start(board).getOrElse(fail("People's Favor is legal"))
    assert(chooseBanner(started.state, actor.player, ds).isLeft)
    assert(chooseBanner(started.state, actor.player, pf).isRight)
  }

  test("a start with no legal banner is rejected and changes nothing") {
    val (board, _) = ChallengeFixture.ready(resources = 6, favor = 6, faceup = 0)
    assertEquals(start(board), Left(NoPlayableOption("challenge")))
  }

  test("a start with no Supply is rejected") {
    val (board, _) = ChallengeFixture.ready(resources = 2, supply = 0)
    assert(start(board).isLeft)
  }

  test("an enemy-held banner needs co-location") {
    // No faceup secrets, so People's Favor is the only banner in question.
    val (base, _) = ChallengeFixture.ready(resources = 2, faceup = 0)
    val apart = enemyHolds(base, Banner.PeoplesFavor, 2, colocated = false)
    assertEquals(start(apart), Left(NoPlayableOption("challenge")))
    assert(start(enemyHolds(base, Banner.PeoplesFavor, 2)).isRight)
  }

  test("the amount must exceed the banner and fit the actor's resources") {
    val (board, actor) = ChallengeFixture.ready(resources = 2, favor = 6)
    val parked = atAmount(board, pf)
    assert(chooseAmount(parked, actor.player, 2).isLeft)
    assert(chooseAmount(parked, actor.player, 7).isLeft)
    assert(chooseAmount(parked, actor.player, 3).isRight)
    assert(chooseAmount(parked, actor.player, 6).isRight)
  }

  test("People's Favor: an unclaimed banner is taken and its favor returns to the least banks") {
    val (board, actor) = ChallengeFixture.ready(resources = 2)
    val done = chooseAmount(atAmount(board, pf), actor.player, 3)
      .getOrElse(fail("the amount must be accepted"))
    val after = ready(done.state)
    val current = after.game.current
    assertEquals(current.banners.peoplesFavor.holder, Some(actor.player))
    assertEquals(current.banners.peoplesFavor.favor, 3)
    assertEquals(player(after, actor.player).board.favor, actor.board.favor - 3)
    assertEquals(player(after, actor.player).board.supply,
      SupplyTrack(actor.board.supply.supply - 1))
    val expected = BannerRules.addFavor(board.banks.favor,
      BannerRules.raidFavorReturn(board.banks.favor, 2))
    assertEquals(after.banks.favor, expected)
    assertEquals(current.walkerPending, None)
  }

  test("People's Favor: an enemy-held banner moves to the challenger") {
    val (base, actor) = ChallengeFixture.ready(resources = 2)
    val board = enemyHolds(base, Banner.PeoplesFavor, 2)
    val done = chooseAmount(atAmount(board, pf), actor.player, 3)
      .getOrElse(fail("the amount must be accepted"))
    val after = ready(done.state).game.current
    assertEquals(after.banners.peoplesFavor.holder, Some(actor.player))
    assertEquals(after.banners.peoplesFavor.favor, 3)
  }

  test("Wandering Flame: a unique least site takes secrets one at a time, with no site decision") {
    val (base, actor) = ChallengeFixture.ready(resources = 3,
      banner = Banner.DarkestSecret)
    val Vector(a, b, c) = base.game.current.map.inPlay.take(3): @unchecked
    val board = withSiteSecrets(base, Map(a -> 0, b -> 1, c -> 5))
    val done = chooseAmount(atAmount(board, ds), actor.player, 4)
      .getOrElse(fail("the amount must be accepted"))
    val after = ready(done.state)
    val sites = after.game.current.map.sites
    assertEquals((sites(a).tokens.secrets, sites(b).tokens.secrets,
      sites(c).tokens.secrets), (2, 2, 5))
    assertEquals(after.game.current.banners.darkestSecret.holder,
      Some(actor.player))
    assertEquals(after.game.current.banners.darkestSecret.secrets, 4)
    assertEquals(player(after, actor.player).board.faceUpSecrets,
      actor.board.faceUpSecrets - 4)
  }

  test("Wandering Flame: fewer secrets than tied sites parks one choose-many, then finishes") {
    val (base, actor) = ChallengeFixture.ready(resources = 2,
      banner = Banner.DarkestSecret)
    val Vector(a, b, c) = base.game.current.map.inPlay.take(3): @unchecked
    val board = withSiteSecrets(base, Map(a -> 0, b -> 0, c -> 0))
    val parked = chooseAmount(atAmount(board, ds), actor.player, 3)
      .getOrElse(fail("the amount must be accepted"))
    assert(parked.events.last.isInstanceOf[WalkerParked])
    assert(chooseSites(parked.state, actor.player, Vector(a)).isLeft)
    assert(chooseSites(parked.state, actor.player, Vector(a, a)).isLeft)
    val outsider = base.game.current.map.inPlay.find(id =>
      !Set(a, b, c).contains(id)).get
    assert(chooseSites(parked.state, actor.player, Vector(a, outsider)).isLeft)
    val done = chooseSites(parked.state, actor.player, Vector(a, c))
      .getOrElse(fail("two tied sites must be accepted"))
    val after = ready(done.state)
    val sites = after.game.current.map.sites
    assertEquals((sites(a).tokens.secrets, sites(b).tokens.secrets,
      sites(c).tokens.secrets), (1, 0, 1))
    assertEquals(after.game.current.banners.darkestSecret.holder,
      Some(actor.player))
    assertEquals(after.game.current.walkerPending, None)
  }

  test("Wandering Flame: an enemy holder gets the retained half back") {
    val (base, actor) = ChallengeFixture.ready(resources = 5,
      banner = Banner.DarkestSecret)
    val board = withSiteSecrets(enemyHolds(base, Banner.DarkestSecret, 5), Map.empty)
    val rival = enemy(board)
    val sites = board.game.current.map.inPlay
    val parked = chooseAmount(atAmount(board, ds), actor.player, 6)
      .getOrElse(fail("the amount must be accepted"))
    // 5 secrets: 2 are placed (5 / 2), 3 return to the holder. All sites tie
    // at 10, and 2 secrets are fewer than the tied sites, so the actor picks.
    assert(parked.events.last.isInstanceOf[WalkerParked])
    val done = chooseSites(parked.state, actor.player, sites.take(2))
      .getOrElse(fail("two tied sites must be accepted"))
    val after = ready(done.state)
    assertEquals(player(after, rival.player).board.faceUpSecrets,
      rival.board.faceUpSecrets + 3)
    assertEquals(after.game.current.banners.darkestSecret.holder,
      Some(actor.player))
    assertEquals(after.game.current.banners.darkestSecret.secrets, 6)
  }

  /** Adds Darkest Secret to the banner decision and widens the amount by two,
    * which is exactly what a power hooking those windows would do.
    */
  private final case class WidenChallenge(id: PowerId) extends ContributingPower {
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.ChallengeBannerSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case DecisionQuery.ChooseOne(options, heading) => decide.copy(query =
              DecisionQuery.ChooseOne(options :+ DecisionOption.Banner(ds),
                heading)): Operation
            case _ => decide
          }
          case other => other
        })),
      PowerWindow.ChallengeAmountSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case query: DecisionQuery.ChooseAmount =>
              decide.copy(query = query.copy(max = query.max + 2)): Operation
            case _ => decide
          }
          case other => other
        })))
  }

  test("a power's transforms reach the banner and amount selections") {
    val (board, actor) = ChallengeFixture.ready(resources = 2, faceup = 0)
    val tree = ChallengeProcedure.build(catalog, board, actor.player, Vector.empty)
      .getOrElse(fail("the tree must build"))
    val powers = WalkerPowers(Vector(WidenChallenge(PowerId("test.widen-challenge"))))
    val Right(WalkerOutcome.Parked(atBanner, _)) =
      ProcedureWalker.advance(board, tree, None, powers): @unchecked
    val banner = ProcedureWalker.parkedDecide(board, tree, atBanner, powers)
      .getOrElse(fail("the walk must park on the banner decision"))
    assertEquals(banner.query match {
      case DecisionQuery.ChooseOne(options, _) => options.map(_.ref)
      case _ => Vector.empty
    }, Vector[DecisionOptionRef](pf, ds))
    val Right(WalkerOutcome.Parked(atAmount, _)) = ProcedureWalker.resolve(board,
      tree, atBanner, Answered("challenge.banner",
        DecisionAnswer.ChooseOneAnswer(pf), actor.player), powers): @unchecked
    val amount = ProcedureWalker.parkedDecide(board, tree, atAmount, powers)
      .getOrElse(fail("the walk must park on the amount decision"))
    assertEquals(amount.query match {
      case query: DecisionQuery.ChooseAmount => (query.min, query.max)
      case _ => (0, 0)
    }, (3, actor.board.favor + 2))
  }

  test("a completed Challenge replays exactly from its journal") {
    val wealthSite = catalog.sites.find(_.startingResources.favor > 0).get.id
    val orderedSites = wealthSite +: sites.filterNot(_ == wealthSite).take(7)
    val replayPlan = plan.copy(orderedSites = orderedSites)
    val (setupState, setupEvents) = execute(setup, replayPlan)
    val activeId = setupState.asInstanceOf[Ready].value.game.current.turn.activePlayer
    val wealth = rules.startWalker(setupState, ActionRef.TakeWealth, activeId,
      Vector.empty, Vector(DecisionOptionRef.Button("favor"))).toOption.get
    val act = rules.startWalker(wealth.state, PhaseTransitionRef.EndWake, activeId)
      .toOption.get
    val prior = BannerRules.resources(act.state.asInstanceOf[Ready].value.game.current,
      Banner.PeoplesFavor)
    val started = rules.startWalker(act.state, ActionRef.Challenge, activeId)
      .getOrElse(fail("Challenge must start"))
    val banner = chooseBanner(started.state, activeId, pf)
      .getOrElse(fail("banner"))
    val done = chooseAmount(banner.state, activeId, prior + 1)
      .getOrElse(fail("amount"))
    val events = setupEvents ++ wealth.events ++ act.events ++ started.events ++
      banner.events ++ done.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, done.state)
  }
}
