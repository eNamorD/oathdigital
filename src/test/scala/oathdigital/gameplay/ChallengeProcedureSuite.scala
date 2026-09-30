package oathdigital.gameplay

import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.actions.challenge.ChallengeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerParked,
  WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

/** Challenge through the rules, as a client drives it: start, answer the
  * banner, answer the amount, and (Wandering Flame) the tied sites.
  */
class ChallengeProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val pf = DecisionOptionRef.Banner(Banner.PeoplesFavor)
  private val ds = DecisionOptionRef.Banner(Banner.DarkestSecret)

  /** p1, in Act at Ancient City, has 6 favor, 6 faceup and 4 facedown
    * secrets, and the start's 7 Supply. Nobody holds a banner. */
  private def challenger: Table = Table.start
    .favor(p1, 6).secrets(p1, faceUp = 6, faceDown = 4)

  /** Every site holds 10 secrets except those named, so the least sites are
    * the named ones with the fewest. */
  private def siteSecrets(table: Table, named: (String, Int)*): Table =
    val secretsBySite = named.map((name, n) => CatalogNames.site(name) -> n).toMap
    table.ready.game.current.map.inPlay.foldLeft(table) { (t, site) =>
      t.siteTokens(site, secrets = secretsBySite.getOrElse(site, 10))
    }

  private def start(board: ReadyGame) =
    rules.startWalker(Ready(board), ActionRef.Challenge, p1)

  private def chooseBanner(state: OathState, banner: DecisionOptionRef) =
    rules.resolveWalker(state, p1, "challenge.banner",
      DecisionAnswer.ChooseOneAnswer(banner))

  private def chooseAmount(state: OathState, amount: Int) =
    rules.resolveWalker(state, p1, "challenge.amount",
      DecisionAnswer.ChooseAmountAnswer(amount))

  private def chooseSites(state: OathState, sites: Vector[SiteId]) =
    rules.resolveWalker(state, p1, "challenge.ribbon-site",
      DecisionAnswer.ChooseManyAnswer(sites.map(DecisionOptionRef.Site(_))))

  private def ready(state: OathState): ReadyGame = state match
    case Ready(value) => value
    case other => fail(s"expected a ready game, got $other")

  /** Starts, answers the banner, and returns the state parked at the amount. */
  private def atAmount(board: ReadyGame, banner: DecisionOptionRef): OathState =
    val started = start(board).getOrElse(fail("a legal Challenge must start"))
    chooseBanner(started.state, banner)
      .getOrElse(fail("the banner must be accepted")).state

  test("starting spends the Supply and parks on the banner decision"):
    val board = challenger.peoplesFavor(None, favor = 2).ready
    val started = start(board).getOrElse(fail("a legal Challenge must start"))
    assert(started.events.last.isInstanceOf[WalkerParked])
    assertEquals(Look(started.state).supply(p1), 7 - 1)
    assertEquals(ready(started.state).game.current.banners,
      board.game.current.banners)

  test("only banners the actor could take are offered"):
    // No faceup secrets, so Darkest Secret is out of reach.
    val board = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 2).ready
    val started = start(board).getOrElse(fail("People's Favor is legal"))
    assert(chooseBanner(started.state, ds).isLeft)
    assert(chooseBanner(started.state, pf).isRight)

  test("a start with no legal banner is rejected and changes nothing"):
    // 6 favor cannot exceed People's Favor's 6, and there are no faceup secrets.
    val board = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 6).ready
    assertEquals(start(board), Left(NoPlayableOption("challenge")))

  test("a start with no Supply is rejected"):
    assertEquals(start(challenger.supply(p1, 0).peoplesFavor(None, favor = 2)
      .ready).left.toOption, Some(OathViolation.CoreOperationRejected("insufficient-supply",
        "a supply spend of 1 exceeds the 0 available")))

  test("an enemy-held banner needs co-location"):
    // No faceup secrets, so People's Favor is the only banner in question.
    val base = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(Some(p2), favor = 2)
    // p2 stands at Broken Peaks, away from p1.
    assertEquals(start(base.ready), Left(NoPlayableOption("challenge")))
    assert(start(base.pawn(p2, at = Table.homeOf(p1)).ready).isRight)

  test("the amount must exceed the banner and fit the actor's resources"):
    val parked = atAmount(challenger.peoplesFavor(None, favor = 2).ready, pf)
    assert(chooseAmount(parked, 2).isLeft)
    assert(chooseAmount(parked, 7).isLeft)
    assert(chooseAmount(parked, 3).isRight)
    assert(chooseAmount(parked, 6).isRight)

  test("People's Favor: an unclaimed banner is taken and its favor returns to the least banks"):
    val board = challenger.peoplesFavor(None, favor = 2).ready
    val done = chooseAmount(atAmount(board, pf), 3)
      .getOrElse(fail("the amount must be accepted"))
    val after = ready(done.state)
    val current = after.game.current
    assertEquals(current.banners.peoplesFavor.holder, Some(p1))
    assertEquals(current.banners.peoplesFavor.favor, 3)
    assertEquals(Look(after).favor(p1), 6 - 3)
    assertEquals(Look(after).supply(p1), 7 - 1)
    val expected = BannerRules.addFavor(board.banks.favor,
      BannerRules.raidFavorReturn(board.banks.favor, 2))
    assertEquals(after.banks.favor, expected)
    assertEquals(current.walkerPending, None)

  test("People's Favor: an enemy-held banner moves to the challenger"):
    val board = challenger.peoplesFavor(Some(p2), favor = 2)
      .pawn(p2, at = Table.homeOf(p1)).ready
    val done = chooseAmount(atAmount(board, pf), 3)
      .getOrElse(fail("the amount must be accepted"))
    val after = ready(done.state).game.current
    assertEquals(after.banners.peoplesFavor.holder, Some(p1))
    assertEquals(after.banners.peoplesFavor.favor, 3)

  test("Wandering Flame: a unique least site takes secrets one at a time, with no site decision"):
    val board = siteSecrets(challenger.darkestSecret(None, secrets = 3),
      "Ancient City" -> 0, "Broken Peaks" -> 1, "Buried Giant" -> 5).ready
    val done = chooseAmount(atAmount(board, ds), 4)
      .getOrElse(fail("the amount must be accepted"))
    val after = Look(done.state)
    // 3 secrets go one at a time to the least site: 0 -> 1, then the two
    // least sites at 1 take one each.
    assertEquals((after.siteTokens("Ancient City").secrets,
      after.siteTokens("Broken Peaks").secrets,
      after.siteTokens("Buried Giant").secrets), (2, 2, 5))
    assertEquals(ready(done.state).game.current.banners.darkestSecret.holder,
      Some(p1))
    assertEquals(ready(done.state).game.current.banners.darkestSecret.secrets, 4)
    assertEquals(after.faceUpSecrets(p1), 6 - 4)

  test("Wandering Flame: fewer secrets than tied sites parks one choose-many, then finishes"):
    val (a, b, c) = ("Ancient City", "Broken Peaks", "Buried Giant")
    val board = siteSecrets(challenger.darkestSecret(None, secrets = 2),
      a -> 0, b -> 0, c -> 0).ready
    val parked = chooseAmount(atAmount(board, ds), 3)
      .getOrElse(fail("the amount must be accepted"))
    assert(parked.events.last.isInstanceOf[WalkerParked])
    val Vector(siteA, siteC) = Vector(a, c).map(CatalogNames.site(_))
    assert(chooseSites(parked.state, Vector(siteA)).isLeft)
    assert(chooseSites(parked.state, Vector(siteA, siteA)).isLeft)
    assert(chooseSites(parked.state, Vector(siteA,
      CatalogNames.site("Dunes"))).isLeft)          // Dunes holds 10, not tied
    val done = chooseSites(parked.state, Vector(siteA, siteC))
      .getOrElse(fail("two tied sites must be accepted"))
    val after = Look(done.state)
    assertEquals((after.siteTokens(a).secrets, after.siteTokens(b).secrets,
      after.siteTokens(c).secrets), (1, 0, 1))
    assertEquals(ready(done.state).game.current.banners.darkestSecret.holder,
      Some(p1))
    assertEquals(ready(done.state).game.current.walkerPending, None)

  test("Wandering Flame: an enemy holder gets the retained half back"):
    val board = siteSecrets(challenger.darkestSecret(Some(p2), secrets = 5)
      .pawn(p2, at = Table.homeOf(p1)).secrets(p2, faceUp = 1)).ready
    val parked = chooseAmount(atAmount(board, ds), 6)
      .getOrElse(fail("the amount must be accepted"))
    // 5 secrets: 2 are placed (5 / 2), 3 return to the holder. All sites tie
    // at 10, and 2 secrets are fewer than the tied sites, so the actor picks.
    assert(parked.events.last.isInstanceOf[WalkerParked])
    val done = chooseSites(parked.state,
      Vector("Ancient City", "Broken Peaks").map(CatalogNames.site(_)))
      .getOrElse(fail("two tied sites must be accepted"))
    assertEquals(Look(done.state).faceUpSecrets(p2), 1 + 3)
    assertEquals(ready(done.state).game.current.banners.darkestSecret.holder,
      Some(p1))
    assertEquals(ready(done.state).game.current.banners.darkestSecret.secrets, 6)

  /** Adds Darkest Secret to the banner decision and widens the amount by two,
    * which is exactly what a power hooking those windows would do.
    */
  private final case class WidenChallenge(id: PowerId) extends ContributingPower:
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

  test("a power's transforms reach the banner and amount selections"):
    val board = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 2).ready
    val tree = ChallengeProcedure.build(catalog, board, p1, Vector.empty)
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
        DecisionAnswer.ChooseOneAnswer(pf), p1), powers): @unchecked
    val amount = ProcedureWalker.parkedDecide(board, tree, atAmount, powers)
      .getOrElse(fail("the walk must park on the amount decision"))
    assertEquals(amount.query match {
      case query: DecisionQuery.ChooseAmount => (query.min, query.max)
      case _ => (0, 0)
    }, (3, 6 + 2))

  test("a restriction on Take sees Challenge's custody and hides the banner"):
    val board = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 2).ready
    val tree = ChallengeProcedure.build(catalog, board, p1, Vector.empty)
      .getOrElse(fail("the tree must build"))
    val noBannerTaken = new OperationRestriction:
      override def reason(ready: ReadyGame,
          operation: CoreOperation): Option[OperationReason] = operation match
        case Take(Piece.Banner(_), _, _, _, _, _) => Some(OperationReason(
          "test.no-banner", "no banner may be taken",
          OperationReasonKind.Impossible))
        case _ => None
    val powers = WalkerPowers(Vector(
      ProcedureWalkerSuite.TestOperationRestrictionPower(
        PowerId("test.no-banner"), noBannerTaken)))
    assert(ProcedureWalker.advance(board, tree, None, powers).isLeft)

  test("a completed Challenge replays exactly from its journal"):
    val wealthSite = catalog.sites.find(_.startingResources.favor > 0).get.id
    val orderedSites = wealthSite +: sites.filterNot(_ == wealthSite).take(7)
    val (setupState, setupEvents) = execute(orderedSites)
    val activeId = setupState.asInstanceOf[Ready].value.game.current.turn.activePlayer
    val wealth = rules.startWalker(setupState, ActionRef.TakeWealth, activeId,
      Vector.empty, Vector(DecisionOptionRef.Button("favor"))).toOption.get
    val act = rules.startWalker(wealth.state, PhaseTransitionRef.EndWake, activeId)
      .toOption.get
    val prior = BannerRules.resources(act.state.asInstanceOf[Ready].value.game.current,
      Banner.PeoplesFavor)
    val started = rules.startWalker(act.state, ActionRef.Challenge, activeId)
      .getOrElse(fail("Challenge must start"))
    val banner = rules.resolveWalker(started.state, activeId, "challenge.banner",
      DecisionAnswer.ChooseOneAnswer(pf)).getOrElse(fail("banner"))
    val done = rules.resolveWalker(banner.state, activeId, "challenge.amount",
      DecisionAnswer.ChooseAmountAnswer(prior + 1)).getOrElse(fail("amount"))
    val events = setupEvents ++ wealth.events ++ act.events ++ started.events ++
      banner.events ++ done.events
    val replayed = new EventReplayEngine(rules).replay(events.zipWithIndex.map {
      case (event, index) => RecordedEvent(index.toLong, event)
    }).toOption.get
    assertEquals(replayed, done.state)
