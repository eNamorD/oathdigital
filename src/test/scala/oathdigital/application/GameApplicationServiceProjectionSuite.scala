package oathdigital.application

import oathdigital.protocol.projection.{BoardTargetRefProjection,
  SiteForcesProjection}
import oathdigital.model._
import oathdigital.gameplay.actions.travel.TravelProcedure
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.model.DecisionAnswer.{ChooseManyAnswer, ChooseOneAnswer,
  PartitionAnswer}
import oathdigital.server.GameHttpWire
import oathdigital.gameplay.setup.SetupProcedure
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2}

/** The application service's projections: who sees which decision, site,
  * hand and preview.
  */
class GameApplicationServiceProjectionSuite extends munit.FunSuite:
  test("Wake projection is actor-private and Act boundary is informational"):
    // p2 wakes at Broken Peaks, where no other pawn stands. Its secrets are a
    // Wake option, so Wake waits for the player. Its relic, a bandit and
    // p2's facedown adviser (any facedown adviser will do) are what Act then
    // offers: Recover, a Campaign, a peek and the minor action.
    val table = Table.start.turn(p2, Phase.Wake)
      .siteTokens("Broken Peaks", secrets = 2)
      .relicAt("Sticky Fire", "Broken Peaks")
      .bandits("Broken Peaks", 1)
      .adviser(p2, "Birdsong", facedown = true)
    val (service, _) = table.service()
    val active = p2
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(table.state, 0L)

    val own = projector.project("game-projection-wake", loaded, active)
    val public = projector.projectPublic("game-projection-wake", loaded)
    assertEquals(own.phase, "wake")
    assert(own.legalControls.contains("endWake"))
    assertEquals(public.legalControls, Vector.empty)
    assert(own.activePlayerResources.nonEmpty)
    assert(own.currentSiteResources.nonEmpty)

    val ended = service.handle(
      "game-projection-wake",
      0L,
      GameCommand.EndWake(active)
    ).toOption.get
    val act = projector.project(
      "game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence),
      active
    )
    assertEquals(act.phase, "act-action-selection")
    assert(act.actionSelectionOpen)
    // Broken Peaks also holds a relic: Recover and a peek are offered.
    assertEquals(act.legalControls, Vector("beginRest", "beginRecover",
      "beginCampaign", "facedownAdviserMinorAction", "peekSiteRelics"))
    assert(act.boardTargetActions.exists(_.actionKind == "travel"))
    val travel = act.boardTargetActions.find(_.actionKind == "travel").get
    assertEquals(travel.minimum -> travel.maximum, 1 -> 1)
    assert(!travel.autoActivate)
    assert(travel.candidates.forall(_.target.isInstanceOf[
      BoardTargetRefProjection.Site]))
    assert(travel.candidates.forall(_.details.exists(_.endsWith("Supply"))))
    // Each destination is priced by dry-running the Travel tree.
    val Ready(atAct) = ended.state: @unchecked
    assertEquals(
      travel.candidates.map(candidate => candidate.target -> candidate.details),
      TravelProcedure.candidates(catalog, atAct, active,
        WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty))
        .map((site, cost) =>
          BoardTargetRefProjection.Site(site.value) -> Vector(s"$cost Supply")))
    assertEquals(projector.projectPublic("game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence)).boardTargetActions,
      Vector.empty)

  test("site projection exposes ordered public properties without relic identity"):
    // A projection needs only a ready game, so the base is the quiet table
    // and every fact the test asserts is patched in below. `Table` has no
    // step for Imperial warbands or for a site's loose favor and secrets.
    val ready = Table.start.ready
    val siteId = ready.game.current.map.cradle.head
    val emptySiteId = ready.game.current.map.cradle(1)
    val imperialSiteId = ready.game.current.map.provinces.head
    val banditSiteId = ready.game.current.map.provinces(1)
    val otherPlayerSiteId = ready.game.current.map.provinces(2)
    val definition = catalog.sites.find(_.id == siteId).get
    val denizenDefinitions = catalog.denizens.take(2)
    val relicDefinitions = catalog.relics.take(2)
    val activePlayer = ready.game.current.players.find(
      _.player == ready.game.current.turn.activePlayer).get
    val otherPlayer = ready.game.current.players.find(_ != activePlayer).get
    val populated = ready.game.current.map.sites(siteId).copy(
      forces = SiteForces.Occupied(ForceKind.Exile(activePlayer.lineage), 2),
      denizens = denizenDefinitions.reverse.map(definition =>
        DenizenState(
          DenizenId(definition.id.value),
          Orientation.FaceUp,
          Tokens.empty
        )),
      relics = relicDefinitions.map(definition =>
        RelicState(
          RelicId(definition.id.value),
          Orientation.FaceDown,
          Tokens.empty
        )),
      tokens = Tokens(2, 1)
    )
    val current = ready.game.current.copy(
      commonCards = ready.game.current.commonCards.copy(
        regionalDiscards = Map(
          Region.Cradle -> Vector(DenizenId(denizenDefinitions.head.id.value)),
          Region.Provinces -> Vector(VisionId("vision:conquest")),
          Region.Hinterland -> Vector.empty)),
      map = ready.game.current.map.copy(
        sites = ready.game.current.map.sites
          .updated(siteId, populated)
          .updated(
            emptySiteId,
            ready.game.current.map.sites(emptySiteId).copy(
              forces = SiteForces.Empty,
              denizens = Vector.empty,
              relics = Vector.empty,
              tokens = Tokens.empty
            )
          )
          .updated(imperialSiteId, ready.game.current.map.sites(imperialSiteId)
            .copy(forces = SiteForces.Occupied(ForceKind.Imperial, 1)))
          .updated(banditSiteId, ready.game.current.map.sites(banditSiteId)
            .copy(forces = SiteForces.Occupied(ForceKind.Bandit, 3)))
          .updated(otherPlayerSiteId, ready.game.current.map.sites(otherPlayerSiteId)
            .copy(forces = SiteForces.Occupied(
              ForceKind.Exile(otherPlayer.lineage), 4)))
      )
    )
    val loaded = LoadedGame(
      Ready(ready.copy(game = ready.game.copy(current = current))),
      0L
    )
    val projector = new GameProjector(catalog)
    val own = projector.project("game-site-details", loaded,
      current.turn.activePlayer)
    val public = projector.projectPublic("game-site-details", loaded)
    val site = own.world.flatMap(_.sites).find(_.siteId == siteId.value).get
    val empty = own.world.flatMap(_.sites)
      .find(_.siteId == emptySiteId.value).get

    assertEquals(site.looseFavor, 2)
    assertEquals(site.looseSecrets, 1)
    assertEquals(site.denizenCapacity, definition.capacity)
    assertEquals(site.relicCapacity, definition.relicSlots)
    assertEquals(
      site.denizens.map(card => card.cardId -> card.label),
      denizenDefinitions.reverse.map(definition =>
        definition.id.value -> definition.name)
    )
    assertEquals(site.relics.facedownCount, 2)
    assertEquals(empty.denizens, Vector.empty)
    assertEquals(empty.relics.facedownCount, 0)
    assertEquals(site.forces, Some(SiteForcesProjection.Exile(2,
      activePlayer.player.value, ready.playerColors(activePlayer.player),
      s"${ready.playerColors(activePlayer.player).key.capitalize} Warbands")))
    assertEquals(empty.forces, None)
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == imperialSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection.Imperial(1,
      "Imperial Warbands")))
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == banditSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection.Bandit(3,
      "Bandit Warbands")))
    val otherColor = ready.playerColors(otherPlayer.player)
    assertEquals(own.world.flatMap(_.sites).find(_.siteId == otherPlayerSiteId.value)
      .flatMap(_.forces), Some(SiteForcesProjection.Exile(4,
      otherPlayer.player.value, otherColor,
      s"${otherColor.key.capitalize} Warbands")))
    val wire = ujson.read(GameHttpWire.encodeProjection(own))
    val wireSites = wire("world").arr.flatMap(_("sites").arr)
    val wirePlayerForces = wireSites.find(_("siteId").str == siteId.value)
      .get("forces")
    assertEquals(wirePlayerForces.obj.keySet,
      Set("forceKind", "count", "rulerKind", "rulerPlayerId", "label",
        "colorToken"))
    assertEquals(wirePlayerForces("rulerPlayerId").str, activePlayer.player.value)
    assertEquals(wireSites.find(_("siteId").str == emptySiteId.value)
      .get("forces"), ujson.Null)
    assertEquals(public.world, own.world)
    assertEquals(own.world.map(region => region.regionId ->
      region.discardTopCardKind).toMap,
      Map("cradle" -> Some("denizen"), "provinces" -> Some("vision"),
        "hinterland" -> None))
    assertEquals(own.worldDeckTopCardKind,
      current.commonCards.worldDeck.headOption.map(_.kind))
    def worldTop(deck: Vector[WorldCardId]) = projector.project(
      "game-world-top", LoadedGame(Ready(ready.copy(game = ready.game.copy(
        current = current.copy(commonCards = current.commonCards.copy(
          worldDeck = deck))))), 0L), current.turn.activePlayer)
      .worldDeckTopCardKind
    assertEquals(worldTop(Vector(VisionId("vision:conquest"))), Some("vision"))
    assertEquals(worldTop(Vector.empty), None)
    own.world.flatMap(_.sites).foreach { projected =>
      val source = catalog.sites.find(_.id.value == projected.siteId).get
      if source.capacity == 3 then
        assertEquals(projected.recoverDifficulty, None)
        assertEquals(projected.forgeCost.map(cost =>
          Tokens(cost.favor, cost.secrets)), source.forgeRequirements)
      else
        assertEquals(projected.forgeCost, None)
        assertEquals(projected.recoverDifficulty, source.recoverDifficulty)
    }

    val json = oathdigital.server.GameHttpWire.encodeProjection(public)
    assert(json.contains("\"looseFavor\":2"))
    assert(json.contains("\"facedownCount\":2"))
    assert(json.contains("\"discardTopCardKind\":\"denizen\""))
    current.commonCards.worldDeck.headOption.foreach(card =>
      assert(json.contains(s"\"worldDeckTopCardKind\":\"${card.kind}\"")))
    relicDefinitions.foreach(relic => assert(!json.contains(relic.id.value)))

  /** The hand is a preview, not a question: it is drawn while the walker is
    * asking about something else (the starting site), and goes quiet the
    * moment the same cards become the options of a decision, so the panel
    * never draws one card twice.
    */
  test("a temporary hand previews to its holder until it is asked about"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val begun = service.handle("game-hand", 0L,
      GameCommand.Begin(chronicle, orders)).toOption.get
    val projector = new GameProjector(catalog)
    val waiting = LoadedGame(begun.state, begun.nextSequence)
    val holder = PlayerId("p2")
    val hand = projector.project("game-hand", waiting, holder)
      .temporaryHandPreview

    assertEquals(hand.size, 3)
    assert(hand.forall(_.orientation.contains("face-up")), hand.toString)
    assert(hand.forall(!_.hidden), hand.toString)
    // Every seat was dealt a hand, and each one is shown only its own.
    val otherHand = projector.project("game-hand", waiting, PlayerId("p1"))
      .temporaryHandPreview
    assertEquals(otherHand.size, 3)
    assertEquals(otherHand.map(_.cardId).intersect(hand.map(_.cardId)),
      Vector.empty)
    assertEquals(projector.projectPublic("game-hand", waiting)
      .temporaryHandPreview, Vector.empty)

    val placed = service.handle("game-hand", begun.nextSequence,
      GameCommand.ResolveWalker(holder, TreeDecision(
        SetupProcedure.pawnDecisionId(holder),
        ChooseOneAnswer(DecisionOptionRef.Site(sites.head))))).toOption.get
    val asked = projector.project("game-hand",
      LoadedGame(placed.state, placed.nextSequence), holder)

    assert(asked.walkerDecision.exists(
      _.query.exists(_.offeredOptions.nonEmpty)))
    assertEquals(asked.temporaryHandPreview, Vector.empty)

  /** A placement asks about a card with buttons, never with the card itself.
    * A Search's kept card is both in the temporary hand and the subject of
    * the parked placement, and the panel is handed it once.
    */
  test("a Search's kept card is previewed once while it is being placed"):
    // Three denizens on top of the world deck, so the Search offers a choice.
    val (service, _) = Table.start
      .worldDeckTop("Threatening Roar", "Fae Merchant", "Second Chance")
      .service()
    val gameId = "game-search-preview"
    val actor = p1
    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      .toOption.get
    val Ready(afterDraw) = started.state: @unchecked
    val drawn = afterDraw.game.current.temporaryHands(actor)
    val kept = drawn.head
    val chosen = if drawn.size == 1 then started else
      def ref(card: WorldCardId): DecisionOptionRef = card match
        case id: DenizenId => DecisionOptionRef.Denizen(id)
        case id: VisionId => DecisionOptionRef.Vision(id)
      val assignments = Vector(DecisionPlacement(ref(kept), "keep")) ++
        drawn.tail.map(card => DecisionPlacement(ref(card), "discard"))
      service.handle(gameId, started.nextSequence,
        GameCommand.ResolveWalker(actor, TreeDecision("search.cards",
          DecisionAnswer.PartitionAnswer(assignments)))).toOption.get
    val placing = new GameProjector(catalog).project(gameId,
      LoadedGame(chosen.state, chosen.nextSequence), actor)

    assertEquals(placing.walkerDecision.map(_.decisionId),
      Some(s"cardplay.place.${kept.kind}.${kept.value}"))
    assertEquals(placing.temporaryHandPreview.map(_.cardId), Vector(kept.value))

  /** A facedown adviser is played from the board, so the hand holds nothing;
    * the card being placed is previewed all the same, from where it lies.
    */
  test("a facedown adviser being played is previewed from the board"):
    val table = Table.start.adviser(p1, "Wizard's Conclave", facedown = true)
    val (service, _) = table.service()
    val gameId = "game-facedown-preview"
    val actor = p1
    val adviser = table.ready.game.current.players.find(_.player == actor).get
      .advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) => id
      }.get
    val started = service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty,
          Vector(DecisionOptionRef.Denizen(adviser))))).toOption.get
    val Ready(parked) = started.state: @unchecked
    assertEquals(parked.game.current.temporaryHands.getOrElse(actor,
      Vector.empty), Vector.empty)
    val placing = new GameProjector(catalog).project(gameId,
      LoadedGame(started.state, started.nextSequence), actor)

    assertEquals(placing.walkerDecision.map(_.decisionId),
      Some(s"cardplay.place.${adviser.kind}.${adviser.value}"))
    assertEquals(placing.temporaryHandPreview.map(c => (c.cardId, c.hidden)),
      Vector((adviser.value, false)))

  test("player projection redacts other adviser hands and hidden orders"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val begun = service.handle("game-private", 0L,
      GameCommand.Begin(chronicle, orders)).toOption.get
    val placed = service.handle(
      "game-private",
      begun.nextSequence,
      GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
        SetupProcedure.pawnDecisionId(PlayerId("p2")),
        ChooseOneAnswer(DecisionOptionRef.Site(sites.head))))
    ).toOption.get
    val projector = new GameProjector(catalog)
    val loaded = LoadedGame(placed.state, placed.nextSequence)
    val own = projector.project("game-private", loaded, PlayerId("p2"))
    val other = projector.project("game-private", loaded, PlayerId("p1"))
    val privateIds = own.walkerDecision.toVector.flatMap(_.query.toVector
      .flatMap(_.offeredOptions)).map(_.id)
    val otherJson = oathdigital.server.GameHttpWire
      .encodeProjection(other)

    assertEquals(privateIds.size, 3)
    assertEquals(own.temporaryHandPreview, Vector.empty)
    assertEquals(other.walkerDecision, None)
    val publicShape = ujson.read(otherJson).obj
    assert(!publicShape.contains("privateAdviserChoices"))
    assert(!publicShape.contains("pendingSearch"))
    assert(publicShape.contains("walkerWaiting"))
    val exposedValues = jsonStrings(ujson.read(otherJson))
    // A card id is a bare number, so it is matched against the JSON's
    // string values, not its text: a board's warband count may spell it.
    privateIds.foreach(id => assert(!exposedValues.contains(id), id))
    assertEquals(
      exposedValues.intersect(chronicle.relicDeck.map(_.value).toSet),
      Set.empty[String]
    )
    assertEquals(
      exposedValues.intersect(orders.worldDeckOrder.map(_.value).toSet),
      Set.empty[String]
    )
    val chosen = service.handle("game-private", placed.nextSequence,
      GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
        SetupProcedure.adviserDecisionId(PlayerId("p2")),
        PartitionAnswer(
          DecisionPlacement(DecisionOptionRef.Denizen(DenizenId(privateIds.head)),
            SetupProcedure.adviserKeepKey) +:
          privateIds.tail.map(id => DecisionPlacement(
            DecisionOptionRef.Denizen(DenizenId(id)),
            SetupProcedure.adviserDiscardKey))))))
      .toOption.get
    // Reveal Cards follows while p2 holds a facedown adviser it may reveal:
    // everyone else is told who is deciding, and nothing names the card.
    val kept = privateIds.head
    val revealing =
      if catalog.denizen(DenizenId(kept)).exists(_.isInstanceOf[oathdigital.catalog.SiteOnly]) then chosen
      else
        val waiting = projector.projectPublic("game-private",
          LoadedGame(chosen.state, chosen.nextSequence))
        assertEquals(waiting.walkerWaiting.map(_.playerId), Some("p2"))
        assert(!GameHttpWire.encodeProjection(waiting).contains(kept))
        service.handle("game-private", chosen.nextSequence,
          GameCommand.ResolveWalker(PlayerId("p2"), TreeDecision(
            SetupProcedure.revealDecisionId(PlayerId("p2")),
            ChooseManyAnswer(Vector.empty)))).toOption.get
    val continuedPublic = projector.projectPublic("game-private",
      LoadedGame(revealing.state, revealing.nextSequence))
    val continued = projector.project("game-private",
      LoadedGame(revealing.state, revealing.nextSequence),
      PlayerId(continuedPublic.walkerWaiting.get.playerId))
    assertEquals(continued.phase, "setup-walker-decision")
    assertEquals(continued.world.map(_.discardCount).sum, 8)
    // The next park is the following player's own pawn placement (all 8
    // in-play sites), not another adviser choice.
    assertEquals(continued.walkerDecision.get.query.get.offeredOptions.size,
      8)

  private def jsonStrings(value: ujson.Value): Set[String] =
    value match
      case ujson.Str(text) => Set(text)
      case obj: ujson.Obj =>
        obj.value.valuesIterator.flatMap(jsonStrings).toSet
      case array: ujson.Arr =>
        array.value.iterator.flatMap(jsonStrings).toSet
      case _ => Set.empty
