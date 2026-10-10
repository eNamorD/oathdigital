package oathdigital.application

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.{SearchPlay, TravelPlay}
import oathdigital.gameplay.actions.recover.RecoverProcedure
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.oathkeeper.OathkeeperProcedure
import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.{LeagueTreatyContribution, SilverTongue}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.gameplay.walker.{ParkedDecisionAssertions, WalkerParked}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Dice, Look, MenuPlay, Outcomes,
  PlacementPicks, Situation, SituationDriver, Table, Unwrap}
import oathdigital.testkit.Table.{p1, p2, p3}

/** Real games parked on a walker through the application service.
  *
  * Each park states its board as `Table` steps and builds its own service,
  * begun at that table, so the stream holds only what the park's commands
  * journaled. A park returns the service and repository with the position
  * and the journaled situation to play on from, for a test that goes on to
  * drive or reload it.
  */
object ParkedServiceFixture:
  /** The parked decision, as these fixtures rebuild it: the same catalog and
    * power catalogs `GameApplicationService` builds its rules with
    * (`GameApplicationService.scala:87-90`).
    */
  val parkedAssertions = new ParkedDecisionAssertions(catalog,
    WalkerPowerCatalog.default(catalog), PhasePowerCatalog.default(catalog))

  /** `actor` starts playing a facedown adviser; the walk parks on the pick. */
  def playFacedownAdviser(actor: PlayerId): GameCommand =
    MenuPlay.starting(actor, ActionRef.PlayFacedownAdviser)

  /** `actor` answers the facedown-adviser pick with their adviser `card`. */
  def pickFacedownAdviser(actor: PlayerId, card: WorldCardId): GameCommand =
    GameCommand.ResolveWalker(actor, TreeDecision(
      CardPlayProcedure.facedownAdviserDecisionId,
      DecisionAnswer.ChooseOneAnswer(PlacementPicks.optionNaming(card))))

  /** `actor` starts a Travel; the walk parks on the modifier decision, or
    * on the destination when no modifier can be picked. */
  def startTravel(actor: PlayerId): GameCommand =
    MenuPlay.starting(actor, ActionRef.Travel)

  /** `actor` answers the destination decision with `site`. */
  def pickTravelDestination(actor: PlayerId, site: SiteId): GameCommand =
    GameCommand.ResolveWalker(actor, TreeDecision(
      TravelProcedure.destinationDecisionId, TravelPlay.destination(site)))

  /** `actor` starts a Search; the walk parks on the source. */
  def startSearch(actor: PlayerId): GameCommand =
    MenuPlay.starting(actor, ActionRef.Search)

  /** `actor` answers the source decision with `pile`. */
  def pickSearchSource(actor: PlayerId, pile: SearchSource): GameCommand =
    GameCommand.ResolveWalker(actor, TreeDecision(
      SearchProcedure.sourceDecisionId, SearchPlay.source(pile)))

  /** A Travel played through the service: its start and its pick, the two
    * command batches it is. */
  final case class TravelBatches(started: GameAccepted, picked: GameAccepted)

  /** `actor` Travels through `service` to the site where `to`'s pawn stands
    * in game `gameId` as stored, at journal position `sequence`. */
  def travelToPawnOf(service: GameApplicationService, gameId: String,
      sequence: Long, actor: PlayerId, to: PlayerId)(using munit.Location)
      : TravelBatches =
    val standing = Look(Outcomes.ready(loaded(service.load(gameId)).state))
      .pawn(to)
    travelTo(service, gameId, sequence, actor, standing)

  /** `actor` Travels through `service` to `site`, at journal position
    * `sequence` of game `gameId`. */
  def travelTo(service: GameApplicationService, gameId: String,
      sequence: Long, actor: PlayerId, site: SiteId)(using munit.Location)
      : TravelBatches =
    val started = Unwrap.accepted(service.handle(gameId, sequence,
      startTravel(actor)))
    TravelBatches(started, Unwrap.accepted(service.handle(gameId,
      started.nextSequence, pickTravelDestination(actor, site))))

  /** The first player of the fixture's first game: Setup seats p2 first. */
  val firstPlayer: PlayerId = p2

  /** The game a stream holds; fails with the load error, or saying no game
    * is stored. */
  def loaded[A](result: Either[Any, Option[A]])(using munit.Location): A =
    Unwrap.present(Unwrap.accepted(result), "a stored game")

  /** A second service over the same journal, replaying it from the same
    * start as `service`. */
  def replayingSecond(service: GameApplicationService,
      repository: EventStreamRepository): GameApplicationService =
    new GameApplicationService(catalog, repository, genesis = service.genesis)

  /** Setup driven through `service` as `gameId`, the n-th pawn placed at
    * `placementSites(n)`: the accepted position after Setup, holding every
    * event Setup journaled. The first player is in Wake if Wake has an
    * option, else in Act: a Wake with nothing to decide ends by itself. */
  def setupViaService(service: GameApplicationService, gameId: String,
      placementSites: Vector[SiteId] = sites,
      setupChronicle: Chronicle = chronicle,
      setupOrders: SetupOrders = orders): GameAccepted =
    accepted(woken(Situation.journaled(service, catalog, gameId),
      placementSites, setupChronicle, setupOrders))

  /** [[Situation.endingWake]] from an accepted position of `service`: ends
    * `player`'s Wake unless it has already ended by itself for having
    * nothing to decide. A park the End Wake reaches is answered by default,
    * as `Situation.after` answers it. */
  def endingWake(service: GameApplicationService, gameId: String,
      from: GameAccepted, player: PlayerId)(using munit.Location)
      : GameAccepted =
    accepted(Situation(from.state, Vector.empty, from.nextSequence,
      Situation.journaled(service, catalog, gameId), Vector.empty)
      .endingWake(player))

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

  /** A Chronicle and the deal order built from it. */
  final case class ChronicleWithDeckTop(chronicle: Chronicle,
      orders: SetupOrders)

  /** `cards`, in order, become the top of the world deck. A card already
    * dealt by setup swaps places with the card it displaces, and the world
    * deck order is rebuilt with `ChronicleFirstGamePlan`'s own interleaving
    * of visions -- the same production splice, not a duplicate of it.
    */
  def withWorldDeckTop(baseChronicle: Chronicle, baseOrders: SetupOrders,
      cards: Vector[DenizenId]): ChronicleWithDeckTop =
    val dealt = dealtBeforeDeckTop(baseOrders.participants.size)
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
    ChronicleWithDeckTop(newChronicle,
      ChronicleFirstGamePlan.dealOrder(newChronicle, config))

  /** A park: the service and journal it was driven through, the position it
    * stopped at, and the journaled situation parked there, to play on from.
    * `accepted.events` are the parking command's only; `situation.events`
    * hold every event since the table. */
  final case class ParkedGame(service: GameApplicationService,
      repository: InMemoryEventStreamRepository, accepted: GameAccepted,
      situation: Situation)

  /** League Treaty's park: `resting` began Rest, `ruler` is asked. */
  final case class LeagueTreatyPark(game: ParkedGame, resting: PlayerId,
      ruler: PlayerId)

  /** A Recover choice park: `actor` is asked; `seats` are every seated
    * player. */
  final case class RecoverChoicePark(game: ParkedGame, actor: PlayerId,
      seats: Vector[PlayerId])

  /** The Oathkeeper tie park: `mover` travelled, `holder` is asked, and
    * `secondLeader` is the leader other than the mover. */
  final case class OathkeeperTiePark(game: ParkedGame, mover: PlayerId,
      holder: PlayerId, secondLeader: PlayerId)

  /** Silver Tongue's park: `actor` is asked, offered `offeredSuit`'s bank. */
  final case class SilverTonguePark(game: ParkedGame, actor: PlayerId,
      offeredSuit: Suit)

  /** A fresh service begun at `table`, and the journaled situation there. */
  private def journaled(table: Table, gameId: String,
      dice: CampaignDicePort = CampaignDicePort.random)(
      using munit.Location)
      : (GameApplicationService, InMemoryEventStreamRepository, Situation) =
    val (service, repository) = table.service(campaignDice = dice)
    (service, repository,
      table.situation(Situation.journaled(service, catalog, repository, gameId)))

  /** League Treaty at Ancient City, ruled by p2's warband and holding 2
    * favor of its own suit, taken from that suit's bank. p1's pawn stands at
    * Ancient City, p2's at Broken Peaks and p3's at Buried Giant, so p2 is
    * alone on its site. p2's board keeps
    * the 3 warbands it started with, 1 at the site and 2 at home. p1 begins
    * Rest, which finishes Rest and parks on p2's destination decision.
    * Broken Peaks' secrets give p2's coming Wake an option, so that Wake
    * waits once p2 has answered. */
  def leagueTreatyPark(gameId: String)(using munit.Location): LeagueTreatyPark =
    val treatyCard = CatalogNames.denizen("League Treaty")
    val base = Table.start
    val suit = catalog.suitOf(treatyCard).get
    val table = base
      .denizen("League Treaty", "Ancient City")
      .tokens("League Treaty", favor = 2)
      .bankFavor(suit, base.ready.banks.favor(suit) - 2)
      .warbandsAt("Ancient City", p2, 1)
      .boardWarbands(p2, 2)
      .pawn(p1, "Ancient City")
      .pawn(p2, "Broken Peaks")
      .pawn(p3, "Buried Giant")
      .siteTokens("Broken Peaks", secrets = 2)
    val (service, repository, act) = journaled(table, gameId)
    val parked = act.parkedAfter(MenuPlay.start(p1, PhaseTransitionRef.BeginRest))
    parkedAssertions.assertParked(parked.state, PhaseTransitionRef.FinishRest,
      LeagueTreatyContribution.destinationDecisionId(table.ready, p1,
        CatalogNames.site("Ancient City"), treatyCard), p2)
    LeagueTreatyPark(
      ParkedGame(service, repository, accepted(parked, act), parked),
      p1, p2)

  /** p1 at Broken Peaks, where Recover has difficulty 4, holding two
    * facedown relics. p2 stands at Deep Woods (any site but Broken Peaks
    * would do), since a player at the site sees the relics lying there and
    * only p1 should. */
  lazy val recoverTable: Table = Table.start
    .pawn(p1, "Broken Peaks")
    .pawn(p2, "Deep Woods")
    .relicAt("Sticky Fire", "Broken Peaks")
    .relicAt("Cursed Cauldron", "Broken Peaks")

  /** A Recover parked in Act on its continue-or-stop choice on
    * [[recoverTable]]. The service rolls `dice`, and the default
    * `Dice.blanks` fail the roll, so it parks on the choice instead of
    * landing wherever a random port would send it. */
  def recoverChoicePark(gameId: String, dice: CampaignDicePort = Dice.blanks)(
      using munit.Location): RecoverChoicePark =
    val (service, repository, act) = journaled(recoverTable, gameId, dice)
    val parked = act.parkedAfter(
      MenuPlay.start(p1, ActionRef.Recover))
    parkedAssertions.assertParked(parked.state, ActionRef.Recover,
      RecoverProcedure.choiceDecisionId, p1)
    RecoverChoicePark(
      ParkedGame(service, repository, accepted(parked, act), parked),
      p1, orders.participants.map(_.playerId))

  /** The off-turn Oathkeeper tie: p3 holds the title, and p1 and p2 each
    * rule a site with one warband (the other 2 stay on their boards). p1's
    * pawn stands at Ancient City, p2's at Broken Peaks, p3's at Buried Giant.
    * The holder must pick between the two tied leaders after p1's Travel to
    * p3's pawn site. Bandits fill the empty sites first, so the refill that
    * follows a first command does not journal an event of its own. */
  def oathkeeperTiePark(gameId: String)(using munit.Location)
      : OathkeeperTiePark =
    val table = Table.start
      .pawn(p1, "Ancient City")
      .pawn(p2, "Broken Peaks")
      .pawn(p3, "Buried Giant")
      .oathkeeper(Some(p3))
      .warbandsAt("Ancient City", p1, 1)
      .warbandsAt("Broken Peaks", p2, 1)
      .boardWarbands(p1, 2)
      .boardWarbands(p2, 2)
      .banditsRefilled
    val (service, repository, act) = journaled(table, gameId)
    val parked = act.parkedAfter(startTravel(p1))
      .parkedAfter(pickTravelDestination(p1, CatalogNames.site("Buried Giant")))
    assert(parked.events.last match {
      case WalkerParked(TriggeredProcedureRef.Oathkeeper, _, _, _, _) => true
      case _ => false
    }, s"expected a triggered Oathkeeper park, got ${parked.events.last}")
    parkedAssertions.assertParked(parked.state, TriggeredProcedureRef.Oathkeeper,
      OathkeeperProcedure.recipientDecisionId, p3)
    OathkeeperTiePark(
      ParkedGame(service, repository, accepted(parked, act), parked),
      mover = p1, holder = p3, secondLeader = p2)

  /** Silver Tongue as p1's faceup adviser, with Wrestlers (Order) and
    * Bandit Chief (Discord) faceup at Ancient City, where p1's pawn stands
    * (p2's is at Broken Peaks, p3's at Buried Giant). p2 holds a facedown
    * adviser, so the use-power log has another player's hidden card to show
    * (or hide) from each viewer. Bandits fill the empty sites first, so no
    * refill is journaled after the first command. Every favor bank starts with at
    * least 3 favor, so Begin Rest stops at the Rest action, and using Silver
    * Tongue parks on its bank choice. The returned suit is the first site
    * card's, which that choice offers. */
  def silverTonguePark(gameId: String,
      dice: CampaignDicePort = CampaignDicePort.random)(
      using munit.Location): SilverTonguePark =
    val table = Table.start
      .pawn(p1, "Ancient City")
      .pawn(p2, "Broken Peaks")
      .pawn(p3, "Buried Giant")
      .adviser(p1, "Silver Tongue")
      .denizen("Wrestlers", "Ancient City")
      .denizen("Bandit Chief", "Ancient City")
      .adviser(p2, "Birdsong", facedown = true)
      .banditsRefilled
    val (service, repository, act) = journaled(table, gameId, dice)
    val resting = act.after(MenuPlay.start(p1, PhaseTransitionRef.BeginRest))
    parkedAssertions.assertMenuParked(resting.state, PhaseMenu.Menu.Rest, p1)
    val parked = resting.parkedAfter(MenuPlay.picking(p1,
      DecisionOptionRef.Denizen(CatalogNames.denizen("Silver Tongue"))))
    parkedAssertions.assertParked(parked.state, ActionRef.UsePower(SilverTongue.id),
      SilverTongue.choiceDecisionId(resting.ready, p1), p1)
    // Wrestlers, the first site card, is an Order card.
    SilverTonguePark(
      ParkedGame(service, repository, accepted(parked, resting), parked),
      p1, Suit.Order)

  /** The Oathkeeper tie holder answers by choosing `leader`, one of the two
    * tied leaders, as the recipient. */
  def holderPicksLeader(park: OathkeeperTiePark, leader: PlayerId)
      : GameCommand =
    GameCommand.ResolveWalker(park.holder, TreeDecision(
      OathkeeperProcedure.recipientDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.Player(leader))))
