package oathdigital.application

import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.{LeagueTreatyContribution, SilverTongue}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerParked}
import oathdigital.model._
import oathdigital.testkit.{Situation, SituationDriver, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

/** Real games parked on a walker through the application service.
  *
  * Each park states its board as `Table` steps and builds its own service,
  * begun at that table, so the stream holds only what the park's commands
  * journaled. A park returns the service and repository with the position,
  * for a test that goes on to drive or reload it.
  */
object ParkedServiceFixture:
  val treatyCard: DenizenId = DenizenId("237")
  val silverTongueCard: DenizenId = DenizenId("92")

  /** The parked decision, as these fixtures rebuild it: the same catalog and
    * power catalogs `GameApplicationService` builds its rules with
    * (`GameApplicationService.scala:87-90`).
    */
  private val parkedAssertions = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  /** Setup driven through `service` as `gameId`, the n-th pawn placed at
    * `placementSites(n)`: the accepted position after Setup, holding every
    * event Setup journaled. The first player is in Wake if Wake has an
    * option, else in Act: a Wake with nothing to decide ends by itself. */
  def setUp(service: GameApplicationService, gameId: String,
      placementSites: Vector[SiteId] = sites,
      setupChronicle: Chronicle = chronicle,
      setupOrders: SetupOrders = orders): GameAccepted =
    accepted(woken(Situation.journaled(service, catalog, gameId),
      placementSites, setupChronicle, setupOrders))

  /** Ends `player`'s Wake through `service` from `from`, unless that Wake
    * has already ended by itself for having nothing to decide. */
  def endingWake(service: GameApplicationService, gameId: String,
      from: GameAccepted, player: PlayerId): GameAccepted =
    from.state match
      case OathState.Ready(ready) if ready.game.current.turn.phase == Phase.Wake =>
        service.handle(gameId, from.nextSequence, GameCommand.EndWake(player))
          .fold(error => throw new AssertionError(s"End Wake rejected: $error"),
            identity)
      case _ => from

  private def woken(driver: SituationDriver, placementSites: Vector[SiteId],
      setupChronicle: Chronicle, setupOrders: SetupOrders): Situation =
    Situation.wake(driver.withAnswers(Situation.pawnsAt(placementSites)),
      setupChronicle, setupOrders)

  /** The situation as the service accepted its last command: `events` are
    * that command's alone, as `GameApplicationService.handle` reports them. */
  private def accepted(situation: Situation, before: Situation): GameAccepted =
    GameAccepted(situation.state, situation.events.drop(before.events.size),
      situation.nextSequence)

  private def accepted(situation: Situation): GameAccepted =
    GameAccepted(situation.state, situation.events, situation.nextSequence)

  /** `cards`, in order, become the top of the world deck. A card already
    * dealt by setup swaps places with the card it displaces, and the world
    * deck order is rebuilt with `ChronicleFirstGamePlan`'s own interleaving
    * of visions -- the same production splice, not a duplicate of it.
    */
  def withWorldDeckTop(baseChronicle: Chronicle, baseOrders: SetupOrders,
      cards: Vector[DenizenId]): (Chronicle, SetupOrders) =
    val dealt = 6 + baseOrders.participants.size * 3
    val targets = (dealt until dealt + cards.size).toSet
    val worldDeck = cards.zipWithIndex.foldLeft(baseChronicle.worldDeck):
      case (current, (card, offset)) =>
        val target = dealt + offset
        val existing = current.indexOf(card)
        val at = if existing >= 0 then existing else
          val suit = catalog.denizens.find(_.id.value == card.value).get.suit
          current.indices.find(index => !targets(index) &&
            !cards.contains(current(index)) && catalog.denizens.exists(denizen =>
              denizen.id.value == current(index).value &&
                denizen.suit == suit)).get
        current.updated(target, card).updated(at, current(target))
    val newChronicle = baseChronicle.copy(worldDeck = worldDeck)
    val config = FirstGameBootstrapConfig(baseOrders.participants,
      baseOrders.firstPlayer)
    (newChronicle, ChronicleFirstGamePlan.dealOrder(newChronicle, config))

  def topOfWorldDeck(card: DenizenId, to: Location,
      orientation: Orientation = Orientation.FaceUp): Move =
    Move(Piece.Card(card), PositionedLocation(Location.Deck(CardDeck.World),
      StackPosition.Top), PositionedLocation(to), Some(orientation))

  /** A park: the service and journal it was driven through, and the
    * position it stopped at. */
  final case class ParkedGame(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, accepted: GameAccepted)

  /** A fresh service begun at `table`, and the journaled situation there. */
  private def journaled(table: Table, gameId: String,
      dice: CampaignDicePort = CampaignDicePort.random)(
      using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository, Situation) =
    val (service, repository) = table.service(campaignDice = dice)
    (service, repository,
      table.situation(Situation.journaled(service, catalog, repository, gameId)))

  /** League Treaty at Ancient City, ruled by p2's warband and holding 2
    * favor of its own suit. p1 begins Rest, which finishes Rest and parks on
    * p2's destination decision. Broken Peaks' secrets give p2's coming Wake
    * an option, so that Wake waits once p2 has answered. */
  def leagueTreatyPark(gameId: String)(using munit.Location)
      : (ParkedGame, PlayerId, PlayerId) =
    val base = Table.start
    val suit = catalog.suitOf(treatyCard).get
    val table = base
      .denizen("League Treaty", "Ancient City")
      .tokens("League Treaty", favor = 2)
      .bankFavor(suit, base.ready.banks.favor(suit) - 2)
      .warbandsAt("Ancient City", p2, 1)
      .warbands(p2, 2)
      .siteTokens("Broken Peaks", secrets = 2)
    val (service, repository, act) = journaled(table, gameId)
    val parked = act.parkedAfter(GameCommand.BeginRest(p1))
    parkedAssertions.assertParked(parked.state, PhaseTransitionRef.FinishRest,
      LeagueTreatyContribution.destinationDecisionId(table.ready, p1,
        Table.homeOf(p1), treatyCard), p2)
    (ParkedGame(service, repository, accepted(parked, act)), p1, p2)

  /** Defense dice that always come up blank. A Recover started with these
    * fails its roll, so it parks on the continue-or-stop choice instead of
    * landing wherever a random port would send it.
    */
  val failingDice: CampaignDicePort = new CampaignDicePort:
    def rollAttack(count: Int): Vector[AttackDieFace] =
      Vector.fill(count)(AttackDieFace.HollowSword)
    def rollDefense(count: Int): Vector[DefenseDieFace] =
      Vector.fill(count)(DefenseDieFace.Blank)

  /** The Chronicle with the first catalog site Recover can target in the
    * first atlas slot, so the first player's pawn starts there, and the
    * sites in play under it in atlas order. */
  lazy val recoverChronicle: Chronicle =
    val recoverSite = catalog.sites.find(site =>
      site.recoverDifficulty.exists(d => d > 0 && d <= 4) &&
        site.relicSlots > 0 &&
        !site.handlers.exists(_.contains(".homeland-"))).get.id
    chronicle.copy(atlasBox =
      chronicle.atlasBox.find(_.site == recoverSite).get +:
        chronicle.atlasBox.filterNot(_.site == recoverSite))
  lazy val recoverSites: Vector[SiteId] =
    recoverChronicle.atlasBox.take(8).map(_.site)

  /** p1 at Broken Peaks, where Recover has difficulty 4, holding two
    * facedown relics. */
  lazy val recoverTable: Table = Table.start
    .pawn(p1, "Broken Peaks")
    .relicAt("Sticky Fire", "Broken Peaks")
    .relicAt("Cursed Cauldron", "Broken Peaks")

  /** A Recover parked in Act on its continue-or-stop choice on
    * [[recoverTable]]. The service rolls `dice`, and the default
    * `failingDice` fail the roll, so it parks on the choice instead of
    * landing wherever a random port would send it. */
  def recoverChoicePark(gameId: String, dice: CampaignDicePort = failingDice)(
      using munit.Location): (ParkedGame, PlayerId, Vector[PlayerId]) =
    val (service, repository, act) = journaled(recoverTable, gameId, dice)
    val parked = act.parkedAfter(
      GameCommand.StartWalker(ActionRef.Recover, StartPayload(p1)))
    parkedAssertions.assertParked(parked.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, p1)
    (ParkedGame(service, repository, accepted(parked, act)), p1,
      orders.participants.map(_.playerId))

  /** The off-turn Oathkeeper tie: p3 holds the title, and p1 and p2 each
    * rule a site with one warband. The holder must pick between the two tied
    * leaders after p1's Travel to Buried Giant. The returned players are the
    * mover, the holder and the second leader. */
  def oathkeeperTiePark(gameId: String)(using munit.Location)
      : (ParkedGame, PlayerId, PlayerId, PlayerId) =
    val table = Table.start
      .oathkeeper(Some(p3))
      .warbandsAt("Ancient City", p1, 1)
      .warbandsAt("Broken Peaks", p2, 1)
      .warbands(p1, 2)
      .warbands(p2, 2)
    val (service, repository, act) = journaled(table, gameId)
    val parked = act.parkedAfter(
      GameCommand.StartWalker(ActionRef.Travel, StartPayload(p1,
        Vector.empty, Vector(DecisionOptionRef.Site(Table.homeOf(p3))))))
    assert(parked.events.last match {
      case WalkerParked(TriggeredProcedureRef.Oathkeeper, _, _, _, _) => true
      case _ => false
    }, s"expected a triggered Oathkeeper park, got ${parked.events.last}")
    parkedAssertions.assertParked(parked.state, TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.recipientDecisionId, p3)
    (ParkedGame(service, repository, accepted(parked, act)), p1, p3, p2)

  /** Silver Tongue as p1's faceup adviser, with Wrestlers (Order) and
    * Bandit Chief (Discord) faceup at p1's pawn site. p2 holds a facedown
    * adviser, which the log scripts read. Every favor bank starts with at
    * least 3 favor, so Begin Rest stops at the Rest action, and using Silver
    * Tongue parks on its bank choice. The returned suit is the first site
    * card's, which that choice offers. */
  def silverTonguePark(gameId: String,
      dice: CampaignDicePort = CampaignDicePort.random)(
      using munit.Location): (ParkedGame, PlayerId, Suit) =
    val table = Table.start
      .adviser(p1, "Silver Tongue")
      .denizen("Wrestlers", "Ancient City")
      .denizen("Bandit Chief", "Ancient City")
      .adviser(p2, "Birdsong", facedown = true)
    val (service, repository, act) = journaled(table, gameId, dice)
    val resting = act.after(GameCommand.BeginRest(p1))
    parkedAssertions.assertResumed(resting.state, Phase.Rest, p1)
    val parked = resting.parkedAfter(GameCommand.UsePower(p1,
      SilverTongue.id, DecisionOptionRef.Denizen(silverTongueCard)))
    parkedAssertions.assertParked(parked.state, ActionRef.UsePower(SilverTongue.id),
      SilverTongue.choiceDecisionId(resting.ready, p1), p1)
    (ParkedGame(service, repository, accepted(parked, resting)), p1, Suit.Order)
