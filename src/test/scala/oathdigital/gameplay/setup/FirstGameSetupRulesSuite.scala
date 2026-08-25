package oathdigital.gameplay.setup

import java.nio.file.Paths

import oathdigital.catalog._
import oathdigital.engine.{EventReplayEngine, RecordedEvent}
import oathdigital.model._
import oathdigital.gameplay.setup.OathContinue._
import oathdigital.gameplay.setup.FirstGameSetupCommand._
import oathdigital.gameplay.setup.OathEvent._
import oathdigital.gameplay.setup.OathState._
import oathdigital.gameplay.setup.OathViolation._

object FirstGameSetupFixture {
  val catalogRef =
    CatalogRef("oath-new-foundations", "2026.08.03-pre3")
  val catalog: ExecutableCatalog =
    CatalogLoader
      .load(
        Paths.get("docs/catalog/new-foundations-component-catalog.json"),
        CatalogLoadRequest(expectedCatalog = Some(catalogRef))
      )
      .toOption
      .get
  val participants = Vector(
    FirstGameParticipant(
      PlayerId("p1"),
      LineageId("l1"),
      PlayerColor("red")
    ),
    FirstGameParticipant(
      PlayerId("p2"),
      LineageId("l2"),
      PlayerColor("blue")
    ),
    FirstGameParticipant(
      PlayerId("p3"),
      LineageId("l3"),
      PlayerColor("yellow")
    )
  )
  val sites = catalog.sites.take(8).map(_.id)
  val denizens = oathdigital.catalog.Suit.values.toVector.sorted.flatMap {
    suit =>
      catalog.denizens.filter(_.suit.value == suit).take(10)
        .map(d => DenizenId(d.id.value))
  }
  private val remaining = denizens.drop(6 + participants.size * 3)
  val worldDeck: Vector[WorldCardId] =
    remaining.take(10) ++ FirstGameRulesData.visions.take(2) ++
      remaining.slice(10, 25) ++ FirstGameRulesData.visions.drop(2) ++
      remaining.drop(25)
  val relics = catalog.relics
    .filter(_.role == RelicRole.Ordinary)
    .map(r => RelicId(r.id.value))
  val homelandEdifices = sites.flatMap { siteId =>
    catalog.sites.find(_.id == siteId).get.handlers.collectFirst {
      case handler if handler.contains(".homeland-") =>
        val suit = handler.substring(handler.indexOf(".homeland-") + 10)
        val edifice = catalog.edifices.find(_.suit.value == suit).get
        siteId -> EdificeId(edifice.id.value)
    }
  }
  val plan = FirstGameSetupPlan(
    catalogRef,
    participants,
    PlayerId("p2"),
    sites,
    denizens,
    worldDeck,
    relics,
    homelandEdifices
  )

  def execute(
      rules: FirstGameSetupRules,
      setupPlan: FirstGameSetupPlan = plan
  ): (OathState, Vector[OathEvent]) = {
    val started = rules.handle(NoGame, Begin(setupPlan)).toOption.get
    val order = Vector(PlayerId("p2"), PlayerId("p3"), PlayerId("p1"))
    order.zipWithIndex.foldLeft(started.state -> started.events) {
      case ((state, events), (playerId, index)) =>
        val placed = rules
          .handle(state, PlacePawn(playerId, setupPlan.orderedSites(index)))
          .toOption
          .get
        val participantIndex =
          setupPlan.participants.indexWhere(_.playerId == playerId)
        val adviser =
          setupPlan.denizenOrder(6 + participantIndex * 3)
        val chosen = rules
          .handle(placed.state, ChooseAdviser(playerId, adviser))
          .toOption
          .get
        chosen.state -> (events ++ placed.events ++ chosen.events)
    }
  }
}

class FirstGameSetupRulesSuite extends munit.FunSuite {
  import FirstGameSetupFixture._

  private val rules = new FirstGameSetupRules(catalog)

  test("valid setup ends with the selected first Exile ready for Wake") {
    val (state, _) = execute(rules)
    val ready = state.asInstanceOf[Ready].value

    assertEquals(ready.game.current.turn.activePlayer, PlayerId("p2"))
    assertEquals(ready.game.current.turn.phase, Phase.Wake)
    assertEquals(
      ready.game.current.map.cradle -> ready.game.current.map.provinces ->
        ready.game.current.map.hinterland,
      sites.take(2) -> sites.slice(2, 5) -> sites.slice(5, 8)
    )
    assertEquals(DomainValidation.validate(ready.game), Vector.empty)
    assertEquals(
      ready.support.foundationProfile,
      FirstGameFoundationProfile.FixedUnaltered
    )
    assertEquals(
      ready.game.campaign.foundations.keySet,
      FoundationNumber.all.toSet
    )
    assert(
      ready.game.campaign.foundations.values.forall(
        _ == FoundationState(FoundationFace.Normal, Set.empty)
      )
    )
    val selectedEdificeSuits = homelandEdifices.map { case (_, id) =>
      catalog.edifices.find(_.id.value == id.value).get.suit.value
    }
    oathdigital.model.Suit.all.foreach { suit =>
      assertEquals(
        ready.support.favorBanks(suit),
        3 + selectedEdificeSuits.count(_ == suit.key)
      )
    }
  }

  test("all players are unique Exiles with fixed starting board values") {
    val ready = execute(rules)._1.asInstanceOf[Ready].value

    assert(ready.game.campaign.lineages.values.forall(_.role == Role.Exile))
    assert(ready.game.campaign.lineages.values.forall(_.legacies.isEmpty))
    assertEquals(ready.playerColors.values.toSet.size, participants.size)
    ready.game.current.players.foreach { player =>
      assertEquals(
        player.board,
        PlayerBoardState(1, 1, 0, 3, SupplyTrack.full)
      )
      assertEquals(player.advisers.size, 1)
      assert(player.relics.isEmpty)
    }
    assertEquals(ready.game.current.title.holder, None)
    assertEquals(ready.game.current.commonCards.legacyDeck, Vector.empty)
  }

  test("sites contain resources, capacity bandits, relics, and ruined Homelands") {
    val ready = execute(rules)._1.asInstanceOf[Ready].value

    sites.foreach { siteId =>
      val definition = catalog.sites.find(_.id == siteId).get
      val state = ready.game.current.map.sites(siteId)
      assertEquals(state.tokens, definition.startingResources)
      assertEquals(state.relics.size, definition.relicSlots)
      assert(state.relics.forall(_.orientation == Orientation.FaceDown))
      if (definition.capacity == 0) assertEquals(state.forces, SiteForces.Empty)
      else
        assertEquals(
          state.forces,
          SiteForces.Occupied(ForceKind.Bandit, definition.capacity)
        )
      assertEquals(
        state.denizens.collect { case e: EdificeState => e.side },
        homelandEdifices.find(_._1 == siteId).toVector
          .map(_ => EdificeSide.Ruined)
      )
    }
  }

  test("denizens and ordinary relics are conserved uniquely") {
    val ready = execute(rules)._1.asInstanceOf[Ready].value
    val advisers = ready.game.current.players.flatMap(_.advisers).collect {
      case DenizenState(id, _, _) => id
    }
    val discards = Region.all.flatMap(
      ready.game.current.commonCards.regionalDiscards
    ).collect { case id: DenizenId => id }
    val deckDenizens = ready.game.current.commonCards.worldDeck.collect {
      case id: DenizenId => id
    }
    val allDenizens = advisers ++ discards ++ deckDenizens
    val siteRelics = ready.game.current.map.sites.values.toVector
      .flatMap(_.relics.map(_.id))
    val allRelics = siteRelics ++ ready.game.current.commonCards.relicDeck

    assertEquals(allDenizens.size, 60)
    assertEquals(allDenizens.toSet, denizens.toSet)
    assertEquals(allDenizens.distinct.size, allDenizens.size)
    assertEquals(allRelics.toSet, relics.toSet)
    assertEquals(allRelics.distinct.size, relics.size)
    assert(!allRelics.contains(RelicId("grand-scepter")))
  }

  test("placement order and adviser ownership are enforced") {
    val started = rules.handle(NoGame, Begin(plan)).toOption.get
    assertEquals(
      rules.handle(
        started.state,
        PlacePawn(PlayerId("p1"), sites.head)
      ),
      Left(WrongPlayer(PlayerId("p2"), PlayerId("p1")))
    )
    val placed = rules
      .handle(started.state, PlacePawn(PlayerId("p2"), sites.head))
      .toOption
      .get
    assertEquals(placed.continue, AwaitingAdviser(PlayerId("p2")))
    assertEquals(
      rules.handle(
        placed.state,
        ChooseAdviser(PlayerId("p2"), denizens.head)
      ),
      Left(AdviserNotInHand(PlayerId("p2"), denizens.head))
    )
  }

  test("malformed and incomplete plans have typed failures") {
    assertEquals(
      rules.handle(
        NoGame,
        Begin(plan.copy(participants = participants.updated(
          1,
          participants(1).copy(color = PlayerColor("red"))
        )))
      ),
      Left(DuplicateColor(PlayerColor("red")))
    )
    assert(
      rules.handle(
        NoGame,
        Begin(plan.copy(worldDeckOrder = worldDeck.tail))
      ).left.toOption.get.isInstanceOf[InvalidWorldDeck]
    )
    assert(
      rules.handle(
        NoGame,
        Begin(plan.copy(relicOrder = relics.tail))
      ).left.toOption.get.isInstanceOf[InvalidRelicOrder]
    )
    assert(
      rules.handle(
        NoGame,
        Begin(plan.copy(homelandEdifices = Vector.empty))
      ).left.toOption.get.isInstanceOf[InvalidHomelandEdifice]
    )
  }

  test("event replay exactly equals command evolution in stable order") {
    val (commandState, events) = execute(rules)
    val records = events.zipWithIndex.map { case (event, index) =>
      RecordedEvent(index.toLong, event)
    }
    val replay = new EventReplayEngine(rules).replay(records)

    assertEquals(replay, Right(commandState))
    assertEquals(events.head, FirstGameStarted(plan))
    assertEquals(events.last, FirstGameCompleted)
    assertEquals(events.size, 8)
  }
}
