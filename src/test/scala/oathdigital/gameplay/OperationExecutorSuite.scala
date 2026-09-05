package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.gameplay.setup.{FirstGameFoundationProfile,
  FirstGameSupportState, PlayerColor}
import oathdigital.model._
import oathdigital.model.TestGameFixtures._

class OperationExecutorSuite extends munit.FunSuite {
  private val blueId = PlayerId("player-blue")
  private val blueLineage = LineageId("blue")
  private val redForce = ForceKind.Exile(lineageId)
  private val blueForce = ForceKind.Exile(blueLineage)
  private val extraDenizen = DenizenId("D5")
  private val heldRelic = RelicState(
    RelicId("R3"), Orientation.FaceUp, Tokens.empty)

  private val bluePlayer = PlayerState(
    blueId,
    blueLineage,
    Some(sites(1)),
    PlayerBoardState(2, 1, 0, 2, SupplyTrack.full),
    Vector.empty,
    Vector(heldRelic),
    None
  )

  private val ready = {
    val current = game.current.copy(
      players = game.current.players :+ bluePlayer,
      banners = game.current.banners.copy(
        peoplesFavor = game.current.banners.peoplesFavor.copy(
          holder = Some(playerId)))
    )
    val campaign = game.campaign.copy(lineages = game.campaign.lineages.updated(
      blueLineage,
      LineageState(blueLineage, Some(blueId), Role.Exile,
        Vector.empty, Vector.empty)
    ))
    ReadyGame(
      game.copy(campaign = campaign, current = current),
      Map(playerId -> PlayerColor("red"), blueId -> PlayerColor("blue")),
      FirstGameSupportState(
        FirstGameFoundationProfile.FixedUnaltered,
        playerId
      ),
      MaterialBankState(
        Suit.all.map(_ -> 5).toMap,
        Map(redForce -> 14, blueForce -> 14, ForceKind.Bandit -> 24)
      )
    )
  }

  private val executor = new OperationExecutor

  /** Rejection code for a single operation through the authoritative pipeline
    * with a permissive allowlist (shape checks only).
    */
  private def rejectionCode(state: ReadyGame, operation: CoreOperation): String =
    OperationPipeline.run(state, Vector(operation), OperationPolicy.Permissive)(
      Right(_)).left.toOption.get match {
      case oathdigital.gameplay.OathViolation.CoreOperationRejected(code, _) =>
        code
      case other => fail(s"expected a CoreOperationRejected, got $other")
    }

  test("shadow comparison distinguishes parity rejection and mismatch") {
    val matching = OperationShadowEvolution.compare(ready, Right(ready))
    assert(matching.comparison.matchesAuthoritative)

    val rejected = OperationShadowEvolution.compare(ready,
      Left(OathViolation.InvalidEventOrder("shadow candidate rejected")))
    assert(!rejected.comparison.matchesAuthoritative)
    assert(!rejected.comparison.candidateSucceeded)
    assertEquals(rejected.authoritative, ready)

    val authoritative = executor.execute(
      ready,
      Flip(heldRelic.id, Location.PlayArea(blueId), Orientation.FaceDown)
    ).toOption.get
    val mismatch = OperationShadowEvolution.compare(authoritative, Right(ready))

    assert(!mismatch.comparison.matchesAuthoritative)
    assert(mismatch.comparison.candidateSucceeded)
    assert(!mismatch.comparison.stateMatches)
    assert(!mismatch.comparison.cardIndexMatches)
    assertEquals(mismatch.authoritative, authoritative)
  }

  test("policy receives the semantic root before primitive execution") {
    var seen = Vector.empty[CoreOperation]
    val rejecting = new OperationPolicy {
      override def validate(state: ReadyGame, operation: CoreOperation) = {
        seen :+= operation
        Left(OperationError.RestrictedOperation("blocked by test policy"))
      }
    }
    val reveal = Reveal(adviser.id, Location.PlayArea(playerId))

    assert(OperationPipeline.run(ready, Vector(reveal), rejecting)(Right(_)).isLeft)
    assertEquals(seen, Vector(reveal))
    assertEquals(ready.game.current.players.head.advisers, Vector(adviser))
  }

  test("ordered batches apply staged state in order") {
    val gain = Gain.Favor(playerId, Suit.Order, 2)
    val place = Move(
      Piece.Favor(3),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head))
    )
    val result = executor.executeAll(ready, Vector(gain, place)).toOption.get
    val actor = result.game.current.players.find(_.player == playerId).get

    assertEquals(actor.board.favor, 0)
    assertEquals(result.game.current.map.sites(sites.head).tokens.favor, 3)
  }

  test("later failure returns no partial execution result") {
    val valid = Gain.Favor(playerId, Suit.Order, 1)
    val invalid = Move(
      Piece.Favor(99),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head))
    )

    assert(executor.executeAll(ready, Vector(valid, invalid)).isLeft)
    assertEquals(ready.game.current.players.head.board.favor, 1)
    assertEquals(executor.executeAll(ready, Vector.empty),
      Left(OperationError.EmptyOperationBatch))
  }

  test("Draw removes a top prefix and preserves top-first hand order") {
    val source = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(commonCards = ready.game.current.commonCards.copy(
        worldDeck = Vector(worldDenizen, extraDenizen)))))
    val draw = Draw(playerId, Vector(worldDenizen, extraDenizen),
      Location.Deck(CardDeck.World), Location.Hand(playerId))
    val result = executor.execute(source, draw).toOption.get

    assertEquals(result.game.current.commonCards.worldDeck, Vector.empty)
    assertEquals(result.game.current.temporaryHands(playerId),
      Vector(worldDenizen, extraDenizen))
  }

  test("a drained temporary hand keeps its always-keyed empty vector") {
    val source = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(
        commonCards = ready.game.current.commonCards.copy(
          worldDeck = ready.game.current.commonCards.worldDeck.filterNot(
            _ == worldDenizen)),
        temporaryHands = Map(playerId -> Vector(worldDenizen)))))
    val discard = Move(
      Piece.Card(worldDenizen),
      PositionedLocation(Location.Hand(playerId)),
      PositionedLocation(Location.RegionalDiscard(Region.Cradle),
        StackPosition.Top))
    val result = executor.execute(source, discard).toOption.get

    // The hand key survives with an empty vector: empty means "no cards", and
    // nothing removes a temporary-hand key.
    assertEquals(result.game.current.temporaryHands.get(playerId),
      Some(Vector.empty))
    assertEquals(result.game.current.commonCards.discard(Region.Cradle),
      Vector(worldDenizen))
  }

  test("deck and regional-discard stack conventions preserve insertion order") {
    val source = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(commonCards = ready.game.current.commonCards.copy(
        worldDeck = Vector(worldDenizen, extraDenizen)))))
    def discard(id: DenizenId) = Move(
      Piece.Card(id),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.RegionalDiscard(Region.Cradle),
        StackPosition.Top)
    )
    val result = executor.executeAll(source,
      Vector(discard(worldDenizen), discard(extraDenizen))).toOption.get

    assertEquals(result.game.current.commonCards.discard(Region.Cradle),
      Vector(worldDenizen, extraDenizen))

    val reordered = executor.execute(source, Move(
      Piece.Card(worldDenizen),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Bottom)
    )).toOption.get
    assertEquals(reordered.game.current.commonCards.worldDeck,
      Vector(extraDenizen, worldDenizen))
  }

  test("Play materializes IDs while unsupported Edifice materialization fails") {
    val played = executor.execute(ready, Play(
      worldDenizen,
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      Location.PlayArea(playerId),
      Orientation.FaceDown
    )).toOption.get
    val actor = played.game.current.players.find(_.player == playerId).get

    assert(actor.advisers.contains(
      DenizenState(worldDenizen, Orientation.FaceDown, Tokens.empty)))

    val edifice = EdificeId("E2")
    val withEdifice = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(commonCards = ready.game.current.commonCards.copy(
        edificeDeck = Vector(edifice)))))
    val move = Move(Piece.Card(edifice),
      PositionedLocation(Location.Deck(CardDeck.Edifice), StackPosition.Top),
      PositionedLocation(Location.Site(sites.head)),
      Some(Orientation.FaceUp))
    assert(executor.execute(withEdifice, move).left.toOption.get
      .isInstanceOf[OperationError.UnsupportedOrientation])

    val tokenLoss = Move(
      Piece.Card(siteDenizen.id),
      PositionedLocation(Location.Site(sites.head)),
      PositionedLocation(Location.Hand(playerId))
    )
    assert(executor.execute(ready, tokenLoss).left.toOption.get
      .isInstanceOf[OperationError.ConflictingDeltas])
  }

  test("Discard drains card resources from the pre-operation snapshot") {
    val discard = Discard.Denizen(
      siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)),
      Region.Provinces,
      Suit.Order,
      favor = 1,
      secrets = 0,
      actingPlayer = playerId
    )
    val result = executor.execute(ready, discard).toOption.get

    assertEquals(result.game.current.commonCards.discard(Region.Provinces),
      Vector(siteDenizen.id))
    assertEquals(result.banks.favor(Suit.Order), 6)
    assert(!result.game.current.map.sites(sites.head).denizens
      .exists(_.id == siteDenizen.id))
  }

  test("relic discard preserves composite secret orientation semantics") {
    val relic = siteRelic.copy(tokens = Tokens(0, 1))
    val source = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(sites(1),
          ready.game.current.map.sites(sites(1)).copy(relics = Vector(relic)))))))
    val discard = Discard.Relic(
      relic.id,
      PositionedLocation(Location.Site(sites(1))),
      secrets = 1,
      actingPlayer = playerId
    )
    val result = executor.execute(source, discard).toOption.get
    val actor = result.game.current.players.find(_.player == playerId).get

    assertEquals(result.game.current.setAsideRelics, Vector(relic.id))
    assertEquals(actor.board.faceUpSecrets -> actor.board.faceDownSecrets, 1 -> 1)
  }

  test("Bury can remove state from Atlas but Atlas insertion is ambiguous") {
    val other = EdificeId("E2")
    val source = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(commonCards = ready.game.current.commonCards.copy(
        edificeDeck = Vector(other)))))
    val bury = Bury(
      BuryableCard.Edifice(storedEdifice.id),
      PositionedLocation(Location.Atlas)
    )
    val buried = executor.execute(source, bury).toOption.get
    assertEquals(buried.game.current.commonCards.edificeDeck,
      Vector(other, storedEdifice.id))
    assert(!buried.game.campaign.atlas.entries.collect {
      case stored: AtlasEntry.StoredSite => stored.denizens
    }.flatten.exists(_.id == storedEdifice.id))

    val ambiguous = Move(
      Piece.Card(siteDenizen.id),
      PositionedLocation(Location.Site(sites.head)),
      PositionedLocation(Location.Atlas)
    )
    assertEquals(rejectionCode(ready, ambiguous), "ambiguous-location")
  }

  test("Atlas cards support state mutation and site-scoped knowledge") {
    val storedSite = SiteId("S9")
    val atlasDenizen = DenizenState(
      DenizenId("D-atlas"), Orientation.FaceDown, Tokens(1, 0))
    val atlasRelic = RelicState(
      RelicId("R-atlas"), Orientation.FaceDown, Tokens.empty)
    val source = ready.copy(game = ready.game.copy(campaign =
      ready.game.campaign.copy(atlas = AtlasState(Vector(
        AtlasEntry.StoredSite(storedSite, Vector(atlasDenizen),
          Vector(atlasRelic)),
        AtlasEntry.EmpireDivider
      )))))

    val changed = executor.executeAll(source, Vector(
      Burn.favor(1, PositionedLocation(Location.OnCard(atlasDenizen.id))),
      Flip(atlasDenizen.id, Location.Atlas, Orientation.FaceUp)
    )).toOption.get
    val stored = changed.game.campaign.atlas.entries.head
      .asInstanceOf[AtlasEntry.StoredSite]
    assertEquals(stored.denizens, Vector(atlasDenizen.copy(
      orientation = Orientation.FaceUp, tokens = Tokens.empty)))

    val peeked = executor.execute(source,
      Peek(playerId, atlasRelic.id, Location.Atlas)).toOption.get
    assert(peeked.knowledge.siteRelics(playerId)(storedSite)
      .contains(atlasRelic.id))
    assert(!peeked.knowledge.heldRelics.getOrElse(playerId, Vector.empty)
      .contains(atlasRelic.id))
  }

  test("Swap and Replace commit without invalid intermediate state") {
    val swap = Swap(
      siteRelic.id,
      PositionedLocation(Location.Site(sites(1))),
      heldRelic.id,
      PositionedLocation(Location.PlayArea(blueId))
    )
    val swapped = executor.execute(ready, swap).toOption.get
    assert(swapped.game.current.map.sites(sites(1)).relics
      .exists(_.id == heldRelic.id))
    assert(swapped.game.current.players.find(_.player == blueId).get.relics
      .exists(_.id == siteRelic.id))

    val replace = Replace(
      Piece.Warbands(ForceKind.Bandit, 1),
      Piece.Warbands(redForce, 1),
      PositionedLocation(Location.Site(sites.head))
    )
    val replaced = executor.execute(ready, replace).toOption.get
    assertEquals(replaced.game.current.map.sites(sites.head).forces,
      SiteForces.Occupied(redForce, 1))
  }

  test("secret moves preserve orientation and reject ambiguous mixed sources") {
    val mixed = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map {
        case value if value.player == playerId => value.copy(board =
          value.board.copy(faceUpSecrets = 1, faceDownSecrets = 1))
        case value => value
      })))
    val ambiguous = Move(Piece.Secrets(1),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.PlayArea(blueId)))
    assert(executor.execute(mixed, ambiguous).left.toOption.get
      .isInstanceOf[OperationError.AmbiguousSecretOrientation])

    val faceupOnly = Move(Piece.Secrets(1),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head)))
    val result = executor.execute(mixed, faceupOnly).toOption.get
    val actor = result.game.current.players.find(_.player == playerId).get
    assertEquals(actor.board.faceUpSecrets -> actor.board.faceDownSecrets, 0 -> 1)
    assertEquals(result.game.current.map.sites(sites.head).tokens.secrets, 1)
  }

  test("secret burn consumes only faceup secrets and never facedown") {
    def holder(faceUp: Int, faceDown: Int) = ready.copy(
      game = ready.game.copy(current = ready.game.current.copy(
        players = ready.game.current.players.map {
          case value if value.player == playerId => value.copy(board =
            value.board.copy(faceUpSecrets = faceUp, faceDownSecrets = faceDown))
          case value => value
        })))
    val mixed = executor.execute(holder(1, 1), Burn.secrets(1,
      PositionedLocation(Location.PlayArea(playerId)))).toOption.get
    val actor = mixed.game.current.players.find(_.player == playerId).get
    assertEquals(actor.board.faceUpSecrets -> actor.board.faceDownSecrets, 0 -> 1)
    // A player with only facedown secrets cannot burn them: the burn fails as
    // if the player held no secrets at all.
    assert(executor.execute(holder(0, 2), Burn.secrets(2,
      PositionedLocation(Location.PlayArea(playerId)))).isLeft)
    assert(executor.execute(holder(0, 0), Burn.secrets(1,
      PositionedLocation(Location.PlayArea(playerId)))).isLeft)
  }

  test("FlipSecrets and Peek update orientation and knowledge only") {
    val flipped = executor.execute(ready, FlipSecrets(
      playerId, 1, SecretSide.FaceUp, SecretSide.FaceDown
    )).toOption.get
    val actor = flipped.game.current.players.find(_.player == playerId).get
    assertEquals(actor.board.faceUpSecrets -> actor.board.faceDownSecrets, 0 -> 1)

    val peeked = executor.execute(ready, Peek(
      playerId, siteRelic.id, Location.Site(sites(1))
    )).toOption.get
    assert(OperationStateAdapter.knows(peeked, playerId, siteRelic.id))
    assertEquals(peeked.game, ready.game)
  }

  test("Exchange, Sacrifice, Burn, and Reveal execute their primitive plans") {
    val exchange = Exchange(
      Give(Piece.Favor(1), playerId,
        Location.PlayArea(playerId), Location.PlayArea(blueId)),
      Give(Piece.Secrets(1), blueId,
        Location.PlayArea(blueId), Location.PlayArea(playerId))
    )
    val exchanged = executor.execute(ready, exchange).toOption.get
    assertEquals(exchanged.game.current.players.find(_.player == playerId).get
      .board.faceUpSecrets, 2)
    assertEquals(exchanged.game.current.players.find(_.player == blueId).get
      .board.favor, 3)

    val incomingCannotFundOutgoing = Exchange(
      Give(Piece.Favor(1), playerId,
        Location.PlayArea(playerId), Location.PlayArea(blueId)),
      Give(Piece.Favor(3), blueId,
        Location.PlayArea(blueId), Location.PlayArea(playerId))
    )
    assertEquals(rejectionCode(ready, incomingCannotFundOutgoing),
      "insufficient-pieces")

    val sacrifice = Sacrifice(playerId, Piece.Warbands(redForce, 1),
      PositionedLocation(Location.PlayArea(playerId)))
    val sacrificed = executor.execute(ready, sacrifice).toOption.get
    assertEquals(sacrificed.game.current.players.find(_.player == playerId).get
      .board.warbands, 2)

    val burned = executor.execute(ready, Burn.favor(1,
      PositionedLocation(Location.PlayArea(playerId)))).toOption.get
    assertEquals(burned.game.current.players.find(_.player == playerId).get
      .board.favor, 0)

    val facedown = DenizenState(DenizenId("D6"), Orientation.FaceDown,
      Tokens.empty)
    val revealable = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map {
        case value if value.player == playerId =>
          value.copy(advisers = value.advisers :+ facedown)
        case value => value
      })))
    val revealed = executor.execute(revealable,
      Reveal(facedown.id, Location.PlayArea(playerId))).toOption.get
    assert(revealed.game.current.players.find(_.player == playerId).get.advisers
      .collectFirst { case value: DenizenState if value.id == facedown.id => value }
      .exists(_.orientation == Orientation.FaceUp))
  }

  test("pawn banner and warband moves update their concrete storage") {
    val destination = ready.game.current.map.sites(sites(2))
    val movable = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(map = ready.game.current.map.copy(sites =
        ready.game.current.map.sites.updated(sites(2),
          destination.copy(forces = SiteForces.Empty))))))
    val moves = Vector[CoreOperation](
      Move(Piece.Pawn(playerId),
        PositionedLocation(Location.Site(sites.head)),
        PositionedLocation(Location.Site(sites(2)))),
      Move(Piece.Banner(Banner.PeoplesFavor),
        PositionedLocation(Location.PlayArea(playerId)),
        PositionedLocation(Location.PlayArea(blueId))),
      Move(Piece.Warbands(redForce, 2),
        PositionedLocation(Location.PlayArea(playerId)),
        PositionedLocation(Location.Site(sites(2))))
    )
    val result = executor.executeAll(movable, moves).toOption.get

    assertEquals(result.game.current.players.find(_.player == playerId).get.pawnSite,
      Some(sites(2)))
    assertEquals(result.game.current.banners.peoplesFavor.holder, Some(blueId))
    assertEquals(result.game.current.map.sites(sites(2)).forces,
      SiteForces.Occupied(redForce, 2))
  }

  test("an unheld banner is claimed from the shared bank by a banner move") {
    val unheld = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(banners = ready.game.current.banners.copy(
        peoplesFavor = ready.game.current.banners.peoplesFavor.copy(
          holder = None, favor = 2)))))
    val claim = Move(Piece.Banner(Banner.PeoplesFavor),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(blueId)))
    val result = executor.executeAll(unheld, Vector(claim)).toOption.get
    assertEquals(result.game.current.banners.peoplesFavor.holder, Some(blueId))
    // Claiming moves custody only; resources already on the unheld banner stay
    // tracked on the banner.
    assertEquals(result.game.current.banners.peoplesFavor.favor, 2)
  }

  test("a held banner cannot be claimed from the shared bank") {
    val claim = Move(Piece.Banner(Banner.PeoplesFavor),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(blueId)))
    assert(executor.executeAll(ready, Vector(claim)).isLeft)
  }

  test("transaction rejects direct-update failure and invariant corruption") {
    val operation = Gain.Favor(playerId, Suit.Order, 1)
    val failed = OperationPipeline.run(ready, Vector(operation),
      OperationPolicy.Permissive)(
      _ => Left(OathViolation.InvalidEventOrder("direct update failed")))
    assertEquals(failed,
      Left(OathViolation.InvalidEventOrder("direct update failed")))

    val corrupt = OperationPipeline.run(ready, Vector(operation),
      OperationPolicy.Permissive) {
      evolved => Right(evolved.copy(game = evolved.game.copy(current =
        evolved.game.current.copy(commonCards =
          evolved.game.current.commonCards.copy(worldDeck =
            evolved.game.current.commonCards.worldDeck :+ worldDenizen)))))
    }
    assert(corrupt.left.toOption.get
      .isInstanceOf[OathViolation.CoreOperationRejected])
  }

  test("constructor failures become typed operation rejections") {
    val saturated = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(players = ready.game.current.players.map {
        case value if value.player == playerId => value.copy(
          board = value.board.copy(favor = Int.MaxValue))
        case value => value
      })))
    val execution = executor.execute(saturated,
      Gain.Favor(playerId, Suit.Order, 1))
    assertEquals(execution.left.toOption.map(_.code),
      Some("invalid-description"))

    val transaction = OperationPipeline.run(ready,
      Vector(Gain.Favor(playerId, Suit.Order, 1)),
      OperationPolicy.Permissive
    ) { evolved =>
      val invalidPlayers = evolved.game.current.players.map {
        case value if value.player == playerId => value.copy(
          board = value.board.copy(favor = -1))
        case value => value
      }
      Right(evolved.copy(game = evolved.game.copy(current =
        evolved.game.current.copy(players = invalidPlayers))))
    }
    assert(transaction.left.toOption.exists {
      case OathViolation.CoreOperationRejected("invalid-description", _) => true
      case _ => false
    })
  }

  test("invalid initial inventory and missing bounded supply are rejected") {
    val duplicate = ready.copy(game = ready.game.copy(current =
      ready.game.current.copy(commonCards = ready.game.current.commonCards.copy(
        worldDeck = ready.game.current.commonCards.worldDeck :+ adviser.id))))
    val duplicateResult = OperationPipeline.run(duplicate,
      Vector(Gain.Secrets(playerId, 1)), OperationPolicy.Permissive)(Right(_))
    assert(duplicateResult.left.toOption.get
      .isInstanceOf[OathViolation.CoreOperationRejected])
    assertEquals(duplicateResult.left.toOption.get
      .asInstanceOf[OathViolation.CoreOperationRejected].code,
      "invalid-card-index")

    val missingSupply = ready.copy(banks = ready.banks.copy(
      warbandSupply = ready.banks.warbandSupply - redForce))
    val missingResult = OperationPipeline.run(missingSupply,
      Vector(Gain.Secrets(playerId, 1)), OperationPolicy.Permissive)(Right(_))
    assertEquals(missingResult.left.toOption.get
      .asInstanceOf[OathViolation.CoreOperationRejected].code,
      "unknown-warband-supply")
  }
}
