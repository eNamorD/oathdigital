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
import oathdigital.testkit.{Look, Outcomes}
import oathdigital.testkit.Unwrap.accepted
import ParkedServiceFixture.optionRef
import oathdigital.gameplay.actions.VisionRules
import oathdigital.testkit.CatalogNames
import oathdigital.testkit.Unwrap.present
import ParkedServiceFixture.firstPlayer

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
    val stored = LoadedGame(table.state, 0L)

    val own = projector.project("game-projection-wake", stored, active)
    val public = projector.projectPublic("game-projection-wake", stored)
    assertEquals(own.phase, "wake")
    assert(own.legalControls.contains("endWake"))
    assertEquals(public.legalControls, Vector.empty)
    assert(own.activePlayerResources.nonEmpty)
    assert(own.currentSiteResources.nonEmpty)

    val ended = accepted(service.handle(
      "game-projection-wake",
      0L,
      GameCommand.EndWake(active)
    ))
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
    val atAct = Outcomes.ready(ended.state)
    assertEquals(
      travel.candidates.map(candidate => candidate.target -> candidate.details),
      TravelProcedure.candidates(catalog, atAct, active,
        WalkerPowers.selected(WalkerPowerCatalog.default(catalog), Vector.empty))
        .map((site, cost) =>
          BoardTargetRefProjection.Site(site.value) -> Vector(s"$cost Supply")))
    assertEquals(projector.projectPublic("game-projection-wake",
      LoadedGame(ended.state, ended.nextSequence)).boardTargetActions,
      Vector.empty)

  private val ancientCity = CatalogNames.site("Ancient City")
  private val brokenPeaks = CatalogNames.site("Broken Peaks")
  private val buriedGiant = CatalogNames.site("Buried Giant")
  private val deepWoods = CatalogNames.site("Deep Woods")
  private val desolateShore = CatalogNames.site("Desolate Shore")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val siteRelics = Vector(CatalogNames.relic("Sticky Fire"),
    CatalogNames.relic("Cursed Cauldron"))

  /** The quiet table with every site detail a projection shows. p1 rules
    * Ancient City with 2 warbands; it holds Errand Boy then Wrestlers
    * faceup, Sticky Fire and Cursed Cauldron facedown, 2 favor and 1 secret.
    * Broken Peaks holds nothing, as on the quiet table. Buried Giant holds 1
    * Imperial warband, Deep Woods 3 Bandits, and Desolate Shore 4 of p2's
    * warbands. The Cradle discard pile shows Fae Merchant and the Provinces
    * pile Conspiracy; the Hinterland's is empty. No Table step places
    * Imperial warbands, so they are patched in. */
  private val siteDetails: ReadyGame =
    val table = Table.start
      .warbandsAt(ancientCity, p1, 2)
      .denizen(errandBoy, at = ancientCity).denizen(wrestlers, at = ancientCity)
      .relicAt(siteRelics(0), at = ancientCity)
      .relicAt(siteRelics(1), at = ancientCity)
      .siteTokens(ancientCity, favor = 2, secrets = 1)
      .bandits(deepWoods, 3)
      .warbandsAt(desolateShore, p2, 4)
      .discarded(Region.Cradle, "Fae Merchant")
      .discarded(Region.Provinces, VisionRules.Conspiracy)
      .ready
    table.updateCurrent(current => current.copy(map = current.map.copy(sites =
      current.map.sites.updated(buriedGiant, current.map.sites(buriedGiant)
        .copy(forces = SiteForces.Occupied(ForceKind.Imperial, 1))))))

  private val projector = new GameProjector(catalog)
  private val siteDetailsLoaded = LoadedGame(Ready(siteDetails), 0L)
  private val ownDetails =
    projector.project("game-site-details", siteDetailsLoaded, p1)
  private val publicDetails =
    projector.projectPublic("game-site-details", siteDetailsLoaded)

  private def projectedSite(id: SiteId)(using munit.Location) = present(
    ownDetails.world.flatMap(_.sites).find(_.siteId == id.value),
    s"${id.value} in the projection")

  test("a site projects its loose tokens, capacity, cards in order and " +
      "hides relic identity"):
    val site = projectedSite(ancientCity)
    val empty = projectedSite(brokenPeaks)
    val printed = present(catalog.site(ancientCity), "Ancient City")
    assertEquals(site.looseFavor, 2)
    assertEquals(site.looseSecrets, 1)
    assertEquals(site.denizenCapacity, printed.capacity)
    assertEquals(site.relicCapacity, printed.relicSlots)
    assertEquals(site.denizens.map(card => card.cardId -> card.label),
      Vector(errandBoy.value -> "Errand Boy", wrestlers.value -> "Wrestlers"))
    assertEquals(site.relics.facedownCount, 2)
    assertEquals(empty.denizens, Vector.empty)
    assertEquals(empty.relics.facedownCount, 0)
    assertEquals(publicDetails.world, ownDetails.world)
    val json = GameHttpWire.encodeProjection(publicDetails)
    assert(json.contains("\"looseFavor\":2"))
    assert(json.contains("\"facedownCount\":2"))
    siteRelics.foreach(relic => assert(!json.contains(relic.value)))

  test("a site projects Exile, Imperial and Bandit forces"):
    val p1Color = siteDetails.playerColors(p1)
    val p2Color = siteDetails.playerColors(p2)
    assertEquals(projectedSite(ancientCity).forces,
      Some(SiteForcesProjection.Exile(2, p1.value, p1Color,
        s"${p1Color.key.capitalize} Warbands")))
    assertEquals(projectedSite(brokenPeaks).forces, None)
    assertEquals(projectedSite(buriedGiant).forces,
      Some(SiteForcesProjection.Imperial(1, "Imperial Warbands")))
    assertEquals(projectedSite(deepWoods).forces,
      Some(SiteForcesProjection.Bandit(3, "Bandit Warbands")))
    assertEquals(projectedSite(desolateShore).forces,
      Some(SiteForcesProjection.Exile(4, p2.value, p2Color,
        s"${p2Color.key.capitalize} Warbands")))

  test("forces encode with these wire keys"):
    val wire = ujson.read(GameHttpWire.encodeProjection(ownDetails))
    val wireSites = wire("world").arr.flatMap(_("sites").arr)
    val wirePlayerForces = present(
      wireSites.find(_("siteId").str == ancientCity.value),
      "Ancient City on the wire")("forces")
    assertEquals(wirePlayerForces.obj.keySet,
      Set("forceKind", "count", "rulerKind", "rulerPlayerId", "label",
        "colorToken"))
    assertEquals(wirePlayerForces("rulerPlayerId").str, p1.value)
    assertEquals(present(wireSites.find(_("siteId").str == brokenPeaks.value),
      "Broken Peaks on the wire")("forces"), ujson.Null)

  test("discard and world-deck top card kinds project"):
    assertEquals(ownDetails.world.map(region => region.regionId ->
      region.discardTopCardKind).toMap,
      Map("cradle" -> Some("denizen"), "provinces" -> Some("vision"),
        "hinterland" -> None))
    val deck = siteDetails.game.current.commonCards.worldDeck
    assertEquals(ownDetails.worldDeckTopCardKind, deck.headOption.map(_.kind))
    def worldTop(ready: ReadyGame) = projector.project("game-world-top",
      LoadedGame(Ready(ready), 0L), p1).worldDeckTopCardKind
    assertEquals(worldTop(Table.from(siteDetails)
      .worldDeckTop(VisionRules.Conspiracy).unchecked), Some("vision"))
    // No Table step empties the world deck.
    assertEquals(worldTop(siteDetails.updateCurrent(current => current.copy(
      commonCards = current.commonCards.copy(worldDeck = Vector.empty)))),
      None)
    val json = GameHttpWire.encodeProjection(publicDetails)
    assert(json.contains("\"discardTopCardKind\":\"denizen\""))
    deck.headOption.foreach(card =>
      assert(json.contains(s"\"worldDeckTopCardKind\":\"${card.kind}\"")))

  test("every site projects its printed Recover difficulty or Forge cost"):
    assertEquals(ownDetails.world.flatMap(_.sites).map(site => site.siteId ->
      (site.forgeCost.map(cost => Tokens(cost.favor, cost.secrets)),
        site.recoverDifficulty)).toMap,
      Vector[(String, (Option[Tokens], Option[Int]))](
        "Ancient City" -> (Some(Tokens(3, 0)), None),
        "Broken Peaks" -> (None, Some(4)),
        "Buried Giant" -> (None, Some(6)),
        "Deep Woods" -> (Some(Tokens(1, 2)), None),
        "Desolate Shore" -> (None, Some(5)),
        "Dunes" -> (None, Some(4)),
        "Fair Isle" -> (None, Some(3)),
        "Golden Valley" -> (Some(Tokens(3, 0)), None))
        .map((name, printed) => CatalogNames.site(name).value -> printed)
        .toMap)

  /** The hand is a preview, not a question: it is drawn while the walker is
    * asking about something else (the starting site), and goes quiet the
    * moment the same cards become the options of a decision, so the panel
    * never draws one card twice.
    */
  test("a temporary hand previews to its holder until it is asked about"):
    val repository = new InMemoryEventStreamRepository
    val service = new GameApplicationService(catalog, repository)
    val begun = accepted(service.handle("game-hand", 0L,
      GameCommand.Begin(chronicle, orders)))
    val waiting = LoadedGame(begun.state, begun.nextSequence)
    val holder = firstPlayer
    val hand = projector.project("game-hand", waiting, holder)
      .temporaryHandPreview

    // Each seat is dealt 3 advisers.
    assertEquals(hand.size, 3)
    assert(hand.forall(_.orientation.contains("face-up")), hand.toString)
    assert(hand.forall(!_.hidden), hand.toString)
    // Every seat was dealt a hand, and each one is shown only its own.
    val otherHand = projector.project("game-hand", waiting, p1)
      .temporaryHandPreview
    assertEquals(otherHand.size, 3)
    assertEquals(otherHand.map(_.cardId).intersect(hand.map(_.cardId)),
      Vector.empty)
    assertEquals(projector.projectPublic("game-hand", waiting)
      .temporaryHandPreview, Vector.empty)

    val placed = accepted(service.handle("game-hand", begun.nextSequence,
      GameCommand.ResolveWalker(holder, TreeDecision(
        SetupProcedure.pawnDecisionId(holder),
        ChooseOneAnswer(DecisionOptionRef.Site(ancientCity))))))
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
    val started = accepted(service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.Search, StartPayload(actor,
        Vector.empty, Vector(DecisionOptionRef.Button("search:world")))))
      )
    val afterDraw = Outcomes.ready(started.state)
    val drawn = afterDraw.game.current.temporaryHands(actor)
    val (kept, merchant, secondChance) = (CatalogNames.denizen(
      "Threatening Roar"), CatalogNames.denizen("Fae Merchant"),
      CatalogNames.denizen("Second Chance"))
    assertEquals(drawn, Vector[WorldCardId](kept, merchant, secondChance))
    // Keep Threatening Roar and discard the other two.
    val assignments = Vector(DecisionPlacement(optionRef(kept), "keep"),
      DecisionPlacement(optionRef(merchant), "discard"),
      DecisionPlacement(optionRef(secondChance), "discard"))
    val chosen = accepted(service.handle(gameId, started.nextSequence,
      GameCommand.ResolveWalker(actor, TreeDecision("search.cards",
        DecisionAnswer.PartitionAnswer(assignments)))))
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
    val adviser = Look(table.ready).player(actor)
      .advisers.collectFirst {
        case DenizenState(id, Orientation.FaceDown, _) => id
      }.get
    val started = accepted(service.handle(gameId, 0L,
      GameCommand.StartWalker(ActionRef.PlayFacedownAdviser,
        StartPayload(actor, Vector.empty,
          Vector(DecisionOptionRef.Denizen(adviser))))))
    val parked = Outcomes.ready(started.state)
    assertEquals(parked.game.current.temporaryHands.getOrElse(actor,
      Vector.empty), Vector.empty)
    val placing = new GameProjector(catalog).project(gameId,
      LoadedGame(started.state, started.nextSequence), actor)

    assertEquals(placing.walkerDecision.map(_.decisionId),
      Some(s"cardplay.place.${adviser.kind}.${adviser.value}"))
    assertEquals(placing.temporaryHandPreview.map(c => (c.cardId, c.hidden)),
      Vector((adviser.value, false)))

  /** p2's dealt hand in the fixture's first game. */
  private val (wizardsConclave, errandBoyCard, natureWorship) = (
    CatalogNames.denizen("Wizard's Conclave"), CatalogNames.denizen(
      "Errand Boy"), CatalogNames.denizen("Nature Worship"))

  /** The fixture's first game begun, and the first player, p2, placed at
    * Ancient City: p2 is now asked which adviser to keep. */
  private def askedForAdvisers(service: GameApplicationService,
      gameId: String)(using munit.Location): GameAccepted =
    val begun = accepted(service.handle(gameId, 0L,
      GameCommand.Begin(chronicle, orders)))
    accepted(service.handle(gameId, begun.nextSequence,
      GameCommand.ResolveWalker(firstPlayer, TreeDecision(
        SetupProcedure.pawnDecisionId(firstPlayer),
        ChooseOneAnswer(DecisionOptionRef.Site(ancientCity))))))

  test("player projection redacts other adviser hands and hidden orders"):
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository)
    val placed = askedForAdvisers(service, "game-private")
    val stored = LoadedGame(placed.state, placed.nextSequence)
    val own = projector.project("game-private", stored, firstPlayer)
    val other = projector.project("game-private", stored, p1)
    val privateIds = own.walkerDecision.toVector.flatMap(_.query.toVector
      .flatMap(_.offeredOptions)).map(_.id)
    val otherJson = GameHttpWire.encodeProjection(other)

    // Each seat is dealt 3 advisers.
    assertEquals(privateIds.size, 3)
    assertEquals(privateIds, Vector(wizardsConclave, errandBoyCard,
      natureWorship).map(_.value))
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

  test("after the adviser choice the next seat is asked for its pawn"):
    val service = new GameApplicationService(catalog,
      new InMemoryEventStreamRepository)
    val placed = askedForAdvisers(service, "game-next-seat")
    // p2 keeps Wizard's Conclave and discards the other two.
    val chosen = accepted(service.handle("game-next-seat", placed.nextSequence,
      GameCommand.ResolveWalker(firstPlayer, TreeDecision(
        SetupProcedure.adviserDecisionId(firstPlayer),
        PartitionAnswer(Vector(
          DecisionPlacement(DecisionOptionRef.Denizen(wizardsConclave),
            SetupProcedure.adviserKeepKey),
          DecisionPlacement(DecisionOptionRef.Denizen(errandBoyCard),
            SetupProcedure.adviserDiscardKey),
          DecisionPlacement(DecisionOptionRef.Denizen(natureWorship),
            SetupProcedure.adviserDiscardKey)))))))
    // Wizard's Conclave may be revealed, so Reveal Cards follows while p2
    // holds it facedown: everyone else is told who is deciding, and nothing
    // names the card.
    val waiting = projector.projectPublic("game-next-seat",
      LoadedGame(chosen.state, chosen.nextSequence))
    assertEquals(waiting.walkerWaiting.map(_.playerId), Some("p2"))
    assert(!GameHttpWire.encodeProjection(waiting)
      .contains(wizardsConclave.value))
    val revealing = accepted(service.handle("game-next-seat",
      chosen.nextSequence, GameCommand.ResolveWalker(firstPlayer,
        TreeDecision(SetupProcedure.revealDecisionId(firstPlayer),
          ChooseManyAnswer(Vector.empty)))))
    val continuedPublic = projector.projectPublic("game-next-seat",
      LoadedGame(revealing.state, revealing.nextSequence))
    val continued = projector.project("game-next-seat",
      LoadedGame(revealing.state, revealing.nextSequence),
      PlayerId(present(continuedPublic.walkerWaiting, "a waiting seat")
        .playerId))
    assertEquals(continued.phase, "setup-walker-decision")
    // 6 seeded discards plus the 2 p2 discarded.
    assertEquals(continued.world.map(_.discardCount).sum, 8)
    // The next park is the following player's own pawn placement (all 8
    // in-play sites), not another adviser choice.
    assertEquals(present(present(continued.walkerDecision, "a decision")
      .query, "a query").offeredOptions.size, 8)

  private def jsonStrings(value: ujson.Value): Set[String] =
    value match
      case ujson.Str(text) => Set(text)
      case obj: ujson.Obj =>
        obj.value.valuesIterator.flatMap(jsonStrings).toSet
      case array: ujson.Arr =>
        array.value.iterator.flatMap(jsonStrings).toSet
      case _ => Set.empty
