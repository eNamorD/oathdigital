package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.ActPowerHarness.usableIds
import oathdigital.testkit.Outcomes.ready
import oathdigital.testkit.Table.{p2, p3}

class MagicCarpetSuite extends munit.FunSuite:
  import PowerFixture._
  import MovementFixture._
  import PhasePowerHarness.{awaits, backToActing, ops, replayed}

  private val carpet = RelicId("R39")
  private def staged = inPhase(withRelic(homelands(quietTable), carpet),
    Phase.Act)
  private def site(name: String) = DecisionOptionRef.Site(CatalogNames.site(
    name))

  /** Starts the Carpet and answers the site question. */
  private def placedAt(start: ReadyGame, at: String) =
    val parked = use(start, MagicCarpet.id, carpet).toOption.get
    assert(awaits(parked, MagicCarpet.siteDecisionId))
    (parked, choose(parked.state, MagicCarpet.siteDecisionId, site(at)).toOption.get)

  test("it moves the pawn to the chosen site, then can be discarded"):
    val (first, placed) = placedAt(staged, "Deep Woods")
    assert(awaits(placed, MagicCarpet.fateDecisionId))
    assertEquals(Look(ready(placed.state)).pawn(actor),
      CatalogNames.site("Deep Woods"))

    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard).toOption.get
    val after = ready(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, carpet), None)
    assertEquals(after.game.current.setAsideRelics, Vector(carpet))
    assertEquals(replayed(staged, first.events ++ placed.events ++ done.events),
      Right(done.state))

  test("it can be given faceup to a player at a different site"):
    val (_, placed) = placedAt(staged, "Deep Woods")
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p2)).toOption.get
    val after = ready(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, carpet), None)
    assertEquals(relicOf(after, carpet, p2).map(_.orientation),
      Some(Orientation.FaceUp))
    assertEquals(after.game.current.setAsideRelics, Vector.empty)

  test("a player at the new site is not eligible to receive it"):
    val (_, placed) = placedAt(staged, "Broken Peaks")
    assert(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p2)).isLeft)
    assert(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p3)).isRight)

  test("choosing the current site skips the move"):
    val (_, placed) = placedAt(staged, "Ancient City")
    assert(awaits(placed, MagicCarpet.fateDecisionId))
    assert(!ops(placed.events).exists {
      case Move(Piece.Pawn(_), _, _, _) => true
      case _ => false
    })
    assertEquals(Look(ready(placed.state)).pawn(actor),
      CatalogNames.site("Ancient City"))

  test("with nobody eligible the Carpet is discarded without a second question"):
    val crowded = Table.from(staged).pawn(p3, "Deep Woods").pawn(p2,
      "Deep Woods").unchecked
    val (_, placed) = placedAt(crowded, "Deep Woods")
    val after = ready(placed.state)
    assert(backToActing(placed))
    assertEquals(after.game.current.setAsideRelics, Vector(carpet))
    assertEquals(relicOf(after, carpet), None)

  test("a secret on the Carpet returns to its holder facedown when it is discarded"):
    val start = Table.from(staged).tokens(carpet, secrets = 1).unchecked
    val (_, placed) = placedAt(start, "Deep Woods")
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard).toOption.get
    assertEquals(Look(ready(done.state)).player(actor).board.faceDownSecrets,
      Look(start).player(actor).board.faceDownSecrets + 1)

  test("it costs nothing and needs no secret"):
    assert(usableIds(Table.from(staged).secrets(actor, 0).unchecked)
      .contains(MagicCarpet.id))

  test("it writes where the pawn went, then that it was discarded"):
    val (first, placed) = placedAt(staged, "Deep Woods")
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard).toOption.get
    assertEquals(NoteText.said(MagicCarpet,
      first.events ++ placed.events ++ done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${actor.value} placed at ${CatalogNames.site("Deep Woods").value}.",
        covers = false),
      NoteText.Said("discarded", "Discarded.", covers = false)))

  test("a Carpet given away is written as given"):
    val (_, placed) = placedAt(staged, "Deep Woods")
    val done = choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p2)).toOption.get
    assertEquals(NoteText.said(MagicCarpet, done.events), Vector(
      NoteText.Said("given", s"Given to ${p2.value}.", covers = false)))
