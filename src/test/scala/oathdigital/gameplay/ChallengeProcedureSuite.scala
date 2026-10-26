package oathdigital.gameplay

import oathdigital.gameplay.actions.BannerRules
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.actions.challenge.ChallengeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower,
  ModifierPicks, Transform}
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, ProcedureWalker,
  WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption
import oathdigital.testkit.{CatalogNames, EmptyWindowModifier, Journal, Look,
  MenuPlay, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, rejected}

/** Challenge through the rules, as a client drives it: start, answer the
  * banner, answer the amount, and (Wandering Flame) the tied sites.
  */
class ChallengeProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val parkedOn = new ParkedDecisionAssertions(catalog)
  private val peoplesFavorOption = DecisionOptionRef.Banner(Banner.PeoplesFavor)
  private val darkestSecretOption = DecisionOptionRef.Banner(
    Banner.DarkestSecret)

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

  private def banners(state: OathState): BannersState =
    Outcomes.ready(state).game.current.banners

  /** Starts, answers the banner, and returns the state parked at the amount. */
  private def atAmount(board: ReadyGame, banner: DecisionOptionRef): OathState =
    val started = start(board).getOrElse(fail("a legal Challenge must start"))
    chooseBanner(started.state, banner)
      .getOrElse(fail("the banner must be accepted")).state

  test("starting spends the Supply and parks on the banner decision"):
    val board = challenger.peoplesFavor(None, favor = 2).ready
    val started = start(board).getOrElse(fail("a legal Challenge must start"))
    parkedOn.assertParked(started.state, ActionRef.Challenge,
      "challenge.banner", p1)
    assertEquals(Look(started.state).supply(p1), 7 - 1)
    assertEquals(banners(started.state), board.game.current.banners)

  test("only banners the actor could take are offered"):
    // No faceup secrets, so Darkest Secret is out of reach.
    val board = challenger.secrets(p1, faceUp = 0, faceDown = 4)
      .peoplesFavor(None, favor = 2).ready
    val started = start(board).getOrElse(fail("People's Favor is legal"))
    assertEquals(rejected(chooseBanner(started.state, darkestSecretOption)),
      OathViolation.InvalidEventOrder(
        "decision challenge.banner does not offer the selected option"))
    parkedOn.assertParked(
      accepted(chooseBanner(started.state, peoplesFavorOption)).state,
      ActionRef.Challenge, "challenge.amount", p1)

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
    parkedOn.assertParked(
      accepted(start(base.pawn(p2, at = Table.quietPawnSite(p1)).ready)).state,
      ActionRef.Challenge, "challenge.banner", p1)

  test("the amount must exceed the banner and fit the actor's resources"):
    val parked = atAmount(challenger.peoplesFavor(None, favor = 2).ready,
      peoplesFavorOption)
    // The banner holds 2 favor, so the amount runs from 3 to the actor's 6.
    Vector(2, 7).foreach(amount =>
      assertEquals(rejected(chooseAmount(parked, amount)),
        OathViolation.InvalidEventOrder(
          s"decision challenge.amount amount $amount is outside 3..6")))
    Vector(3, 6).foreach(amount =>
      assertEquals(banners(accepted(chooseAmount(parked, amount)).state)
        .peoplesFavor.favor, amount))

  test("People's Favor: an unclaimed banner is taken and its favor returns to the least banks"):
    val board = challenger.peoplesFavor(None, favor = 2).ready
    val done = chooseAmount(atAmount(board, peoplesFavorOption), 3)
      .getOrElse(fail("the amount must be accepted"))
    val after = Outcomes.ready(done.state)
    val current = after.game.current
    assertEquals(current.banners.peoplesFavor.holder, Some(p1))
    assertEquals(current.banners.peoplesFavor.favor, 3)
    assertEquals(Look(after).favor(p1), 6 - 3)
    assertEquals(Look(after).supply(p1), 7 - 1)
    val expected = BannerRules.addFavor(board.banks.favor,
      BannerRules.raidFavorReturn(board.banks.favor, 2))
    assertEquals(after.banks.favor, expected)
    assertEquals(MenuPlay.pendingBeyondMenu(current), None)

  test("People's Favor: an enemy-held banner moves to the challenger"):
    val board = challenger.peoplesFavor(Some(p2), favor = 2)
      .pawn(p2, at = Table.quietPawnSite(p1)).ready
    val done = chooseAmount(atAmount(board, peoplesFavorOption), 3)
      .getOrElse(fail("the amount must be accepted"))
    val after = Outcomes.ready(done.state).game.current
    assertEquals(after.banners.peoplesFavor.holder, Some(p1))
    assertEquals(after.banners.peoplesFavor.favor, 3)

  test("Wandering Flame: a unique least site takes secrets one at a time, with no site decision"):
    val board = siteSecrets(challenger.darkestSecret(None, secrets = 3),
      "Ancient City" -> 0, "Broken Peaks" -> 1, "Buried Giant" -> 5).ready
    val done = chooseAmount(atAmount(board, darkestSecretOption), 4)
      .getOrElse(fail("the amount must be accepted"))
    val after = Look(done.state)
    // 3 secrets go one at a time to the least site: 0 -> 1, then the two
    // least sites at 1 take one each.
    assertEquals((after.siteTokens("Ancient City").secrets,
      after.siteTokens("Broken Peaks").secrets,
      after.siteTokens("Buried Giant").secrets), (2, 2, 5))
    assertEquals(banners(done.state).darkestSecret.holder,
      Some(p1))
    assertEquals(banners(done.state).darkestSecret.secrets, 4)
    assertEquals(after.faceUpSecrets(p1), 6 - 4)

  test("Wandering Flame: fewer secrets than tied sites parks one choose-many, then finishes"):
    val (a, b, c) = ("Ancient City", "Broken Peaks", "Buried Giant")
    val board = siteSecrets(challenger.darkestSecret(None, secrets = 2),
      a -> 0, b -> 0, c -> 0).ready
    val parked = chooseAmount(atAmount(board, darkestSecretOption), 3)
      .getOrElse(fail("the amount must be accepted"))
    parkedOn.assertParked(parked.state, ActionRef.Challenge,
      "challenge.ribbon-site", p1)
    val siteA = CatalogNames.site(a)
    val siteC = CatalogNames.site(c)
    def refused(sites: Vector[SiteId], reason: String) =
      assertEquals(rejected(chooseSites(parked.state, sites)),
        OathViolation.InvalidEventOrder(s"decision challenge.ribbon-site $reason"))
    refused(Vector(siteA), "selects 1 options instead of 2")
    refused(Vector(siteA, siteA), "selects an option more than once")
    // Dunes holds 10, not tied.
    refused(Vector(siteA, CatalogNames.site("Dunes")),
      "does not offer a selected option")
    val done = chooseSites(parked.state, Vector(siteA, siteC))
      .getOrElse(fail("two tied sites must be accepted"))
    val after = Look(done.state)
    assertEquals((after.siteTokens(a).secrets, after.siteTokens(b).secrets,
      after.siteTokens(c).secrets), (1, 0, 1))
    assertEquals(banners(done.state).darkestSecret.holder,
      Some(p1))
    assertEquals(MenuPlay.pendingBeyondMenu(Outcomes.ready(done.state)), None)

  test("Wandering Flame: an enemy holder gets the retained half back"):
    val board = siteSecrets(challenger.darkestSecret(Some(p2), secrets = 5)
      .pawn(p2, at = Table.quietPawnSite(p1)).secrets(p2, faceUp = 1)).ready
    val parked = chooseAmount(atAmount(board, darkestSecretOption), 6)
      .getOrElse(fail("the amount must be accepted"))
    // 5 secrets: 2 are placed (5 / 2), 3 return to the holder. All sites tie
    // at 10, and 2 secrets are fewer than the tied sites, so the actor picks.
    parkedOn.assertParked(parked.state, ActionRef.Challenge,
      "challenge.ribbon-site", p1)
    val done = chooseSites(parked.state,
      Vector("Ancient City", "Broken Peaks").map(CatalogNames.site(_)))
      .getOrElse(fail("two tied sites must be accepted"))
    assertEquals(Look(done.state).faceUpSecrets(p2), 1 + 3)
    assertEquals(banners(done.state).darkestSecret.holder,
      Some(p1))
    assertEquals(banners(done.state).darkestSecret.secrets, 6)

  /** Stub of ContributingPower: adds Darkest Secret to the banner decision and
    * widens the amount by two, which is exactly what a power hooking those
    * windows would do.
    */
  private final case class WidenChallenge(id: PowerId) extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.Banner("test")
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
      PowerWindow.ChallengeBannerSelection -> Vector(Transform((_, operations) =>
        operations.map {
          case decide: Decide => decide.query match {
            case one: DecisionQuery.ChooseOne =>
              decide.copy(query = one.copy(
                options = one.options :+
                  DecisionOption.Banner(darkestSecretOption))): Operation
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
    val atBanner = Outcomes.parked(
      ProcedureWalker.advance(board, tree, None, powers)).tree
    val banner = ProcedureWalker.parkedDecide(board, tree, atBanner, powers)
      .getOrElse(fail("the walk must park on the banner decision"))
    assertEquals(banner.query match {
      case one: DecisionQuery.ChooseOne => one.options.map(_.ref)
      case _ => Vector.empty
    }, Vector[DecisionOptionRef](peoplesFavorOption, darkestSecretOption))
    val bannerAnswer = Answered("challenge.banner",
      DecisionAnswer.ChooseOneAnswer(peoplesFavorOption), p1)
    val atAmount = Outcomes.parked(
      ProcedureWalker.resolve(board, tree, atBanner, bannerAnswer, powers)).tree
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
    // Stub of OperationRestriction: a banner Take is impossible.
    val noBannerTakenStub = new OperationRestriction:
      override def reason(ready: ReadyGame,
          operation: CoreOperation): Option[OperationReason] = operation match
        case Take(Piece.Banner(_), _, _, _, _, _, _, _) => Some(OperationReason(
          "test.no-banner", "no banner may be taken",
          OperationReasonKind.Impossible))
        case _ => None
    val restricting = new ContributingPower:
      def id: PowerId = PowerId("test.no-banner")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
      override def operationRestrictions: Vector[OperationRestriction] =
        Vector(noBannerTakenStub)
    val powers = WalkerPowers(Vector(restricting))
    assertEquals(rejected(ProcedureWalker.advance(board, tree, None, powers)),
      OathViolation.CoreOperationRejected("test.no-banner",
        "no banner may be taken"))

  test("a completed Challenge replays exactly from its journal"):
    // The first player, p2, places the first pawn: at Fair Isle, which starts
    // with 3 favor. Taking it gives p2 the favor to outbid People's Favor.
    val wealthSite = CatalogNames.site("Fair Isle")
    assertEquals(catalog.site(wealthSite).map(_.startingResources.favor),
      Some(3))
    val orderedSites = wealthSite +: sites.filterNot(_ == wealthSite).take(7)
    val SetupResult(setupState, setupEvents) = setupThroughWake(orderedSites)
    val activeId = firstPlayer
    assertEquals(Outcomes.ready(setupState).game.current.turn.activePlayer,
      activeId)
    // The setup events stop at Setup's end, before the driver parks the
    // menu, so the take starts directly; the Wake it leaves open is asked.
    val wealth = accepted(rules.startWalker(setupState, ActionRef.TakeWealth,
      activeId, Vector(DecisionOptionRef.Button("favor"))))
    val act = accepted(MenuPlay.picked(rules, wealth.state, activeId,
      PhaseMenu.Menu.Wake.endingOption))
    val prior = BannerRules.resources(Outcomes.ready(act.state).game.current,
      Banner.PeoplesFavor)
    val started = MenuPlay.started(rules, act.state, activeId, ActionRef.Challenge)
      .getOrElse(fail("Challenge must start"))
    val banner = rules.resolveWalker(started.state, activeId, "challenge.banner",
      DecisionAnswer.ChooseOneAnswer(peoplesFavorOption))
        .getOrElse(fail("banner"))
    val done = rules.resolveWalker(banner.state, activeId, "challenge.amount",
      DecisionAnswer.ChooseAmountAnswer(prior + 1)).getOrElse(fail("amount"))
    val events = setupEvents ++ wealth.events ++ act.events ++ started.events ++
      banner.events ++ done.events
    val replayed = Journal.replayed(rules, events)
    assertEquals(replayed, done.state)

  test("a modifier for the Challenge is asked about first, beside Start " +
      "Challenge"):
    val powers = EmptyWindowModifier.powersFor(MajorActionType.Challenge)
    val board = challenger.peoplesFavor(None, favor = 2).adviser(p1, "Tents")
      .ready
    val started = accepted(new OathRules(catalog, walkerPowerCatalog = powers)
      .startWalker(Ready(board), ActionRef.Challenge, p1))
    assertEquals(
      EmptyWindowModifier.parkedWithFirstOption(started.state, powers),
      ("challenge.modifiers", Some(DecisionOption.Button(ModifierPicks.start,
        "Start Challenge with no modifiers"))))
    // Nothing is spent until the Challenge starts.
    assertEquals(Look(started.state).supply(p1), 7)
