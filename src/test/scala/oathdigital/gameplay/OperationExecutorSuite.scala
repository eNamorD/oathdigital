package oathdigital.gameplay

import oathdigital.gameplay.operations._
import oathdigital.model._
import oathdigital.model.TestGameFixtures._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Unwrap.{accepted, as, rejected}

class OperationExecutorSuite extends munit.FunSuite:
  private val redForce = ForceKind.Exile(lineageId)
  private val extraDenizen = DenizenId("D5")
  private val heldRelic = RelicState(
    RelicId("R3"), Orientation.FaceUp, Tokens.empty)

  /** Red and blue (see [[TestGameFixtures.redAndBlue]]), blue also holding
    * the faceup relic R3. Red stands at S1 with 1 favor, 1 faceup secret and
    * 3 warbands on the board; S1 holds D2 with 1 favor on it, S2 the
    * facedown relic R1, and every site 1 Bandit. Every suit bank holds 5
    * favor. */
  private val ready = redAndBlue(Vector(heldRelic))

  private val executor = new OperationExecutor

  test("Visions Drawn advances once and rejects integer overflow"):
    val once = accepted(executor.execute(ready, AdvanceVisionsDrawn))
    assertEquals(once.game.current.tracks.visionsDrawn,
      ready.game.current.tracks.visionsDrawn + 1)
    val maximum = Table.from(ready).visionsDrawn(Int.MaxValue).unchecked
    assertEquals(rejectionCode(maximum, AdvanceVisionsDrawn),
      "visions-drawn-overflow")

  /** Rejection code for a single operation through the authoritative pipeline
    * with a permissive allowlist (shape checks only).
    */
  private def rejectionCode(state: ReadyGame, operation: CoreOperation)
      : String =
    rejected(OperationPipeline.run(state, Vector(operation),
      OperationPolicy.Permissive, Vector.empty)(Right(_))) match
      case oathdigital.model.OathViolation.CoreOperationRejected(code, _) =>
        code
      case other => fail(s"expected a CoreOperationRejected, got $other")

  test("policy receives the semantic root before primitive execution"):
    var seen = Vector.empty[CoreOperation]
    /** Spy of OperationPolicy: records each operation it is asked about in
      * `seen`, refuses all. */
    val rejecting = new OperationPolicy:
      override def validate(state: ReadyGame, operation: CoreOperation): Either[OperationError, Unit] =
        seen :+= operation
        Left(OperationError.RestrictedOperation("blocked by test policy"))
    val reveal = Reveal(adviser.id, Location.PlayArea(playerId))

    assertEquals(OperationPipeline.run(ready, Vector(reveal), rejecting,
      Vector.empty)(Right(_)),
      Left(OathViolation.CoreOperationRejected("restricted-operation",
        "blocked by test policy")))
    assertEquals(seen, Vector(reveal))

  test("ordered batches apply staged state in order"):
    val gain = Gain.Favor(playerId, Suit.Order, 2)
    val place = Move(
      Piece.Favor(3),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head))
    )
    val result = accepted(executor.executeAll(ready, Vector(gain, place)))
    val actor = Look(result).player(playerId)

    // 1 favor, 2 gained, 3 placed.
    assertEquals(actor.board.favor, 0)
    assertEquals(result.game.current.map.sites(sites.head).tokens.favor, 3)

  test("later failure returns no partial execution result"):
    val valid = Gain.Favor(playerId, Suit.Order, 1)
    val invalid = Move(
      Piece.Favor(99),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head))
    )

    // The first operation is staged (1 + 1 favor) before the second fails.
    assertEquals(executor.executeAll(ready, Vector(valid, invalid)),
      Left(OperationError.InsufficientPieces(Piece.Favor(99),
        Location.PlayArea(playerId), 2)))
    assertEquals(executor.executeAll(ready, Vector.empty),
      Left(OperationError.EmptyOperationBatch))

  test("Draw removes a top prefix and preserves top-first hand order"):
    val source = Table.from(ready)
      .worldDeckTop(worldDenizen, extraDenizen).unchecked
    val draw = Draw(playerId, Vector(worldDenizen, extraDenizen),
      Location.Deck(CardDeck.World), Location.Hand(playerId))
    val result = accepted(executor.execute(source, draw))

    assertEquals(result.game.current.commonCards.worldDeck, Vector.empty)
    assertEquals(result.game.current.temporaryHands(playerId),
      Vector(worldDenizen, extraDenizen))

  test("a drained temporary hand keeps its always-keyed empty vector"):
    val source = Table.from(ready).hand(playerId, worldDenizen).unchecked
    val discard = Move(
      Piece.Card(worldDenizen),
      PositionedLocation(Location.Hand(playerId)),
      PositionedLocation(Location.RegionalDiscard(Region.Cradle),
        StackPosition.Top))
    val result = accepted(executor.execute(source, discard))

    // The hand key survives with an empty vector: empty means "no cards", and
    // nothing removes a temporary-hand key.
    assertEquals(result.game.current.temporaryHands.get(playerId),
      Some(Vector.empty))
    assertEquals(result.game.current.commonCards.discard(Region.Cradle),
      Vector(worldDenizen))

  test("deck and regional-discard stack conventions preserve insertion order"):
    val source = Table.from(ready)
      .worldDeckTop(worldDenizen, extraDenizen).unchecked
    def discard(id: DenizenId) = Move(
      Piece.Card(id),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.RegionalDiscard(Region.Cradle),
        StackPosition.Top)
    )
    val result = accepted(executor.executeAll(source,
      Vector(discard(worldDenizen), discard(extraDenizen))))

    assertEquals(result.game.current.commonCards.discard(Region.Cradle),
      Vector(worldDenizen, extraDenizen))

    val reordered = accepted(executor.execute(source, Move(
      Piece.Card(worldDenizen),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Bottom)
    )))
    assertEquals(reordered.game.current.commonCards.worldDeck,
      Vector(extraDenizen, worldDenizen))

  test("Play materializes IDs while unsupported Edifice materialization fails"):
    val played = accepted(executor.execute(ready, Play(
      worldDenizen,
      PositionedLocation(Location.Deck(CardDeck.World), StackPosition.Top),
      Location.PlayArea(playerId),
      Orientation.FaceDown
    )))
    val actor = Look(played).player(playerId)

    assert(actor.advisers.contains(
      DenizenState(worldDenizen, Orientation.FaceDown, Tokens.empty)))

    val edifice = EdificeId("E2")
    // No Table step stacks plain-id cards in a deck, so the deck is set here.
    val withEdifice = ready.updateCurrent(_.copy(commonCards = ready.game.current.commonCards.copy(
        edificeDeck = Vector(edifice))))
    val move = Move(Piece.Card(edifice),
      PositionedLocation(Location.Deck(CardDeck.Edifice), StackPosition.Top),
      PositionedLocation(Location.Site(sites.head)),
      Some(Orientation.FaceUp))
    assert(rejected(executor.execute(withEdifice, move))
      .isInstanceOf[OperationError.UnsupportedOrientation])

  test("a site card carrying tokens cannot move to a hand"):
    // D2 at S1 carries 1 favor, which a hand cannot hold.
    val tokenLoss = Move(
      Piece.Card(siteDenizen.id),
      PositionedLocation(Location.Site(sites.head)),
      PositionedLocation(Location.Hand(playerId))
    )
    assert(rejected(executor.execute(ready, tokenLoss))
      .isInstanceOf[OperationError.ConflictingDeltas])

  test("Discard drains card resources from the pre-operation snapshot"):
    val discard = Discard.Denizen(
      siteDenizen.id,
      PositionedLocation(Location.Site(sites.head)),
      Region.Provinces,
      Suit.Order,
      favor = 1,
      secrets = 0,
      actingPlayer = playerId
    )
    val result = accepted(executor.execute(ready, discard))

    assertEquals(result.game.current.commonCards.discard(Region.Provinces),
      Vector(siteDenizen.id))
    // The Order bank's 5, and D2's 1 favor.
    assertEquals(result.banks.favor(Suit.Order), 6)
    assert(!result.game.current.map.sites(sites.head).denizens
      .exists(_.id == siteDenizen.id))

  test("relic discard preserves composite secret orientation semantics"):
    val source = Table.from(ready).tokens(siteRelic.id, secrets = 1).unchecked
    val discard = Discard.Relic(
      siteRelic.id,
      PositionedLocation(Location.Site(sites(1))),
      secrets = 1,
      actingPlayer = playerId
    )
    val result = accepted(executor.execute(source, discard))
    val actor = Look(result).player(playerId)

    assertEquals(result.game.current.setAsideRelics, Vector(siteRelic.id))
    // Red's own faceup secret, and the relic's secret taken facedown.
    assertEquals(actor.board.faceUpSecrets -> actor.board.faceDownSecrets,
      1 -> 1)

  test("Bury can remove state from Atlas but Atlas insertion is ambiguous"):
    val other = EdificeId("E2")
    // No Table step stacks plain-id cards in a deck, so the deck is set here.
    val source = ready.updateCurrent(_.copy(commonCards = ready.game.current.commonCards.copy(
        edificeDeck = Vector(other))))
    val bury = Bury(
      BuryableCard.Edifice(storedEdifice.id),
      PositionedLocation(Location.Atlas)
    )
    val buried = accepted(executor.execute(source, bury))
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

  test("Atlas cards support state mutation and site-scoped knowledge"):
    val storedSite = SiteId("S9")
    val atlasDenizen = DenizenState(
      DenizenId("D-atlas"), Orientation.FaceDown, Tokens(1, 0))
    val atlasRelic = RelicState(
      RelicId("R-atlas"), Orientation.FaceDown, Tokens.empty)
    val source = ready.updateCampaign(_.copy(atlas = AtlasState(Vector(
        AtlasEntry.StoredSite(storedSite, Vector(atlasDenizen),
          Vector(atlasRelic)),
        AtlasEntry.EmpireDivider
      ))))

    val changed = accepted(executor.executeAll(source, Vector(
      Burn.favor(1, PositionedLocation(Location.OnCard(atlasDenizen.id))),
      Flip(atlasDenizen.id, Location.Atlas, Orientation.FaceUp)
    )))
    val stored =
      as[AtlasEntry.StoredSite](changed.game.campaign.atlas.entries.head)
    assertEquals(stored.denizens, Vector(atlasDenizen.copy(
      orientation = Orientation.FaceUp, tokens = Tokens.empty)))

    val peeked = accepted(executor.execute(source,
      Peek(playerId, atlasRelic.id, Location.Atlas)))
    assert(peeked.knowledge.siteRelics(playerId)(storedSite)
      .contains(atlasRelic.id))
    assert(!peeked.knowledge.heldRelics.getOrElse(playerId, Vector.empty)
      .contains(atlasRelic.id))

  test("Swap and Replace commit without invalid intermediate state"):
    val swap = Swap(
      siteRelic.id,
      PositionedLocation(Location.Site(sites(1))),
      heldRelic.id,
      PositionedLocation(Location.PlayArea(blueId))
    )
    val swapped = accepted(executor.execute(ready, swap))
    assert(swapped.game.current.map.sites(sites(1)).relics
      .exists(_.id == heldRelic.id))
    assert(Look(swapped).player(blueId).relics
      .exists(_.id == siteRelic.id))

    val replace = Replace(
      Piece.Warbands(ForceKind.Bandit, 1),
      Piece.Warbands(redForce, 1),
      PositionedLocation(Location.Site(sites.head))
    )
    // S1 holds 1 Bandit, and red has warbands to spare.
    val replaced = accepted(executor.execute(ready, replace))
    assertEquals(replaced.game.current.map.sites(sites.head).forces,
      SiteForces.Occupied(redForce, 1))

  test("secret moves preserve orientation and reject ambiguous mixed sources"):
    val mixed =
      Table.from(ready).secrets(playerId, faceUp = 1, faceDown = 1).unchecked
    val ambiguous = Move(Piece.Secrets(1),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.PlayArea(blueId)))
    assert(rejected(executor.execute(mixed, ambiguous))
      .isInstanceOf[OperationError.AmbiguousSecretOrientation])

    val faceupOnly = Move(Piece.Secrets(1),
      PositionedLocation(Location.PlayArea(playerId)),
      PositionedLocation(Location.Site(sites.head)))
    val result = accepted(executor.execute(mixed, faceupOnly))
    val actor = Look(result).player(playerId)
    assertEquals(actor.board.faceUpSecrets -> actor.board.faceDownSecrets,
      0 -> 1)
    assertEquals(result.game.current.map.sites(sites.head).tokens.secrets, 1)

  test("secret burn consumes only faceup secrets and never facedown"):
    def holder(faceUp: Int, faceDown: Int) =
      Table.from(ready).secrets(playerId, faceUp, faceDown).unchecked
    val mixed = accepted(executor.execute(holder(1, 1), Burn.secrets(1,
      PositionedLocation(Location.PlayArea(playerId)))))
    val actor = Look(mixed).player(playerId)
    assertEquals(actor.board.faceUpSecrets -> actor.board.faceDownSecrets,
      0 -> 1)
    // A player with only facedown secrets cannot burn them: the burn fails as
    // if the player held no secrets at all.
    assert(executor.execute(holder(0, 2), Burn.secrets(2,
      PositionedLocation(Location.PlayArea(playerId)))).isLeft)
    assert(executor.execute(holder(0, 0), Burn.secrets(1,
      PositionedLocation(Location.PlayArea(playerId)))).isLeft)

  test("FlipSecrets and Peek update orientation and knowledge only"):
    val flipped = accepted(executor.execute(ready, FlipSecrets(
      playerId, 1, SecretSide.FaceUp, SecretSide.FaceDown
    )))
    val actor = Look(flipped).player(playerId)
    // Red's 1 faceup secret, turned facedown.
    assertEquals(actor.board.faceUpSecrets -> actor.board.faceDownSecrets,
      0 -> 1)

    val peeked = accepted(executor.execute(ready, Peek(
      playerId, siteRelic.id, Location.Site(sites(1))
    )))
    assert(OperationStateAdapter.knows(peeked, playerId, siteRelic.id))
    assertEquals(peeked.game, ready.game)

  test("Exchange, Sacrifice, Burn, and Reveal execute their primitive plans"):
    val exchange = Exchange(
      Give(Piece.Favor(1), playerId,
        Location.PlayArea(playerId), Location.PlayArea(blueId)),
      Give(Piece.Secrets(1), blueId,
        Location.PlayArea(blueId), Location.PlayArea(playerId))
    )
    val exchanged = accepted(executor.execute(ready, exchange))
    // Red's 1 faceup secret and blue's; blue's 2 favor and red's 1.
    assertEquals(Look(exchanged).player(playerId).board.faceUpSecrets, 2)
    assertEquals(Look(exchanged).player(blueId).board.favor, 3)

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
    val sacrificed = accepted(executor.execute(ready, sacrifice))
    // 3 on red's board, less the 1 sacrificed.
    assertEquals(Look(sacrificed).player(playerId).board.warbands, 2)

    val burned = accepted(executor.execute(ready, Burn.favor(1,
      PositionedLocation(Location.PlayArea(playerId)))))
    // Red's only favor, burned.
    assertEquals(Look(burned).player(playerId).board.favor, 0)

    val facedown = DenizenState(DenizenId("D6"), Orientation.FaceDown,
      Tokens.empty)
    val revealable = Table.from(ready)
      .adviser(playerId, facedown.id, facedown = true).unchecked
    val revealed = accepted(executor.execute(revealable,
      Reveal(facedown.id, Location.PlayArea(playerId))))
    assert(Look(revealed).player(playerId).advisers
      .collectFirst { case value: DenizenState if value.id == facedown.id => value }
      .exists(_.orientation == Orientation.FaceUp))

  test("pawn banner and warband moves update their concrete storage"):
    val movable = Table.from(ready).bandits(sites(2), 0).unchecked
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
    // Red's pawn leaves S1, and 2 of red's 3 warbands go to S3.
    val result = accepted(executor.executeAll(movable, moves))

    assertEquals(Look(result).player(playerId).pawnSite,
      Some(sites(2)))
    assertEquals(result.game.current.banners.peoplesFavor.holder, Some(blueId))
    assertEquals(result.game.current.map.sites(sites(2)).forces,
      SiteForces.Occupied(redForce, 2))

  test("an unheld banner is claimed from the shared bank by a banner move"):
    val unheld = Table.from(ready).peoplesFavor(None, favor = 2).unchecked
    val claim = Move(Piece.Banner(Banner.PeoplesFavor),
      PositionedLocation(Location.SharedBank),
      PositionedLocation(Location.PlayArea(blueId)))
    val result = accepted(executor.executeAll(unheld, Vector(claim)))
    assertEquals(result.game.current.banners.peoplesFavor.holder, Some(blueId))
    // Claiming moves custody only; resources already on the unheld banner stay
    // tracked on the banner.
    assertEquals(result.game.current.banners.peoplesFavor.favor, 2)

  test("transaction rejects direct-update failure and invariant corruption"):
    val operation = Gain.Favor(playerId, Suit.Order, 1)
    val failed = OperationPipeline.run(ready, Vector(operation),
      OperationPolicy.Permissive, Vector.empty)(
      _ => Left(OathViolation.InvalidEventOrder("direct update failed")))
    assertEquals(failed,
      Left(OathViolation.InvalidEventOrder("direct update failed")))

    val corrupt = OperationPipeline.run(ready, Vector(operation),
      OperationPolicy.Permissive, Vector.empty):
      evolved => Right(evolved.updateCurrent(_.copy(commonCards =
          evolved.game.current.commonCards.copy(worldDeck =
            evolved.game.current.commonCards.worldDeck :+ worldDenizen))))
    assert(rejected(corrupt)
      .isInstanceOf[OathViolation.CoreOperationRejected])

  test("constructor failures become typed operation rejections"):
    val saturated = Table.from(ready).favor(playerId, Int.MaxValue).unchecked
    val execution = executor.execute(saturated,
      Gain.Favor(playerId, Suit.Order, 1))
    assertEquals(execution.left.toOption.map(_.code),
      Some("invalid-description"))

    val transaction = OperationPipeline.run(ready,
      Vector(Gain.Favor(playerId, Suit.Order, 1)),
      OperationPolicy.Permissive, Vector.empty
    ) { evolved =>
      val invalidPlayers = evolved.game.current.players.map:
        case value if value.player == playerId => value.copy(
          board = value.board.copy(favor = -1))
        case value => value
      Right(evolved.updateCurrent(_.copy(players = invalidPlayers)))
    }
    assert(transaction.left.toOption.exists {
      case OathViolation.CoreOperationRejected("invalid-description", _) => true
      case _ => false
    })

  test("invalid initial inventory and missing bounded supply are rejected"):
    val duplicate = ready.updateCurrent(_.copy(commonCards = ready.game.current.commonCards.copy(
        worldDeck = ready.game.current.commonCards.worldDeck :+ adviser.id)))
    val duplicateResult = OperationPipeline.run(duplicate,
      Vector(Gain.Secrets(playerId, 1)), OperationPolicy.Permissive,
      Vector.empty)(Right(_))
    assert(rejected(duplicateResult)
      .isInstanceOf[OathViolation.CoreOperationRejected])
    assertEquals(
      as[OathViolation.CoreOperationRejected](rejected(duplicateResult)).code,
      "invalid-card-index")

    val missingSupply = ready.copy(banks = ready.banks.copy(
      warbandSupply = ready.banks.warbandSupply - redForce))
    val missingResult = OperationPipeline.run(missingSupply,
      Vector(Gain.Secrets(playerId, 1)), OperationPolicy.Permissive,
      Vector.empty)(Right(_))
    assertEquals(
      as[OathViolation.CoreOperationRejected](rejected(missingResult)).code,
      "unknown-warband-supply")
