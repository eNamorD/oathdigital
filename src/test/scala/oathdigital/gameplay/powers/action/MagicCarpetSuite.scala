package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{PhasePowerCatalog, PowerFixture, TargetsFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class MagicCarpetSuite extends munit.FunSuite {
  import PowerFixture._
  import MovementFixture._
  import TargetsFixture.{replayed, withPawn}

  private val carpet = RelicId("R39")
  private def staged = inPhase(withRelic(base, carpet), Phase.Act)
  private def site(id: SiteId) = DecisionOptionRef.Site(id)

  /** Starts the Carpet and answers the site question. */
  private def placedAt(start: ReadyGame, at: SiteId) = {
    val parked = use(start, MagicCarpet.id, carpet).toOption.get
    assert(parkedAt(parked, MagicCarpet.siteDecisionId))
    (parked, choose(parked.state, MagicCarpet.siteDecisionId, site(at)).toOption.get)
  }

  test("Magic Carpet is a registered phase power") {
    assert(PhasePowerCatalog.default(catalog).find(MagicCarpet.id).isDefined)
  }

  test("it moves the pawn to the chosen site, then can be discarded") {
    val (first, placed) = placedAt(staged, deepWoods)
    assert(parkedAt(placed, MagicCarpet.fateDecisionId))
    assertEquals(pawnOf(readyOf(placed.state)), deepWoods)

    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, carpet), None)
    assertEquals(after.game.current.setAsideRelics, Vector(carpet))
    assertEquals(replayed(staged, first.events ++ placed.events ++ done.events),
      Right(done.state))
  }

  test("it can be given faceup to a player at a different site") {
    val (_, placed) = placedAt(staged, deepWoods)
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p3)).toOption.get
    val after = readyOf(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, carpet), None)
    assertEquals(relicOf(after, carpet, p3).map(_.orientation),
      Some(Orientation.FaceUp))
    assertEquals(after.game.current.setAsideRelics, Vector.empty)
  }

  test("a player at the new site is not eligible to receive it") {
    val (_, placed) = placedAt(staged, brokenPeaks)
    assert(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p3)).isLeft)
    assert(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p1)).isRight)
  }

  test("choosing the current site skips the move") {
    val (_, placed) = placedAt(staged, ancientCity)
    assert(parkedAt(placed, MagicCarpet.fateDecisionId))
    assert(!ops(placed.events).exists {
      case Move(Piece.Pawn(_), _, _, _) => true
      case _ => false
    })
    assertEquals(pawnOf(readyOf(placed.state)), ancientCity)
  }

  test("with nobody eligible the Carpet is discarded without a second question") {
    val crowded = withPawn(withPawn(staged, p1, deepWoods), p3, deepWoods)
    val (_, placed) = placedAt(crowded, deepWoods)
    val after = readyOf(placed.state)
    assert(backToActing(placed))
    assertEquals(after.game.current.setAsideRelics, Vector(carpet))
    assertEquals(relicOf(after, carpet), None)
  }

  test("a secret on the Carpet returns to its holder facedown when it is discarded") {
    val start = withRelicTokens(staged, carpet, Tokens(0, 1))
    val (_, placed) = placedAt(start, deepWoods)
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard).toOption.get
    assertEquals(player(readyOf(done.state)).board.faceDownSecrets,
      player(start).board.faceDownSecrets + 1)
  }

  test("it costs nothing and needs no secret") {
    assert(usable(withSecrets(staged, 0), MagicCarpet.id))
  }

  test("a facedown Carpet cannot be used") {
    val facedown = inPhase(withRelic(base, carpet, Orientation.FaceDown), Phase.Act)
    assert(!usable(facedown, MagicCarpet.id))
    assert(use(facedown, MagicCarpet.id, carpet).isLeft)
  }
}
