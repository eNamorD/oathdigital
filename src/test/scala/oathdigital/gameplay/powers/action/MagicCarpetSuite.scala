package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Refusals,
  Table}
import oathdigital.testkit.ActPowerHarness.usableIds
import oathdigital.testkit.Outcomes.ready
import oathdigital.testkit.Table.{p2, p3}
import oathdigital.testkit.Unwrap.accepted

class MagicCarpetSuite extends munit.FunSuite:
  import PowerFixture._
  import MovementFixture._
  import PhasePowerHarness.{awaits, backToActing, ops, replayed}

  private val carpet = CatalogNames.relic("Magic Carpet")
  /** p1's Act at Ancient City holding Magic Carpet; p2 stands at Broken
    * Peaks and p3 at Buried Giant. `homelands` adds the ruined edifices at
    * Deep Woods and Golden Valley. */
  private def staged = Table.from(homelands(quietTable)).turn(actor, Phase.Act)
    .pawn(actor, "Ancient City").pawn(p2, "Broken Peaks")
    .pawn(p3, "Buried Giant").relic(actor, carpet).unchecked
  private def site(name: String) =
    DecisionOptionRef.Site(CatalogNames.site(name))

  /** The walk the Carpet started, parked at its site question, and the walk
    * after the site was answered. */
  private case class Placed(started: OathTransition, answered: OathTransition)

  /** Starts the Carpet and answers the site question. */
  private def placedAt(start: ReadyGame, at: String): Placed =
    val started = accepted(use(start, MagicCarpet.id, carpet))
    assert(awaits(started, MagicCarpet.siteDecisionId))
    Placed(started, accepted(choose(started.state, MagicCarpet.siteDecisionId,
      site(at))))

  test("it moves the pawn to the chosen site, then can be discarded"):
    val Placed(first, placed) = placedAt(staged, "Deep Woods")
    assert(awaits(placed, MagicCarpet.fateDecisionId))
    assertEquals(Look(ready(placed.state)).pawn(actor),
      CatalogNames.site("Deep Woods"))

    val done = accepted(choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard))
    val after = ready(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, carpet), None)
    assertEquals(after.game.current.setAsideRelics, Vector(carpet))
    assertEquals(replayed(staged, first.events ++ placed.events ++ done.events),
      Right(done.state))

  test("it can be given faceup to a player at a different site"):
    val Placed(_, placed) = placedAt(staged, "Deep Woods")
    val done = accepted(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p2)))
    val after = ready(done.state)
    assert(backToActing(done))
    assertEquals(relicOf(after, carpet), None)
    assertEquals(relicOf(after, carpet, p2).map(_.orientation),
      Some(Orientation.FaceUp))
    assertEquals(after.game.current.setAsideRelics, Vector.empty)

  test("a player at the new site is not eligible to receive it"):
    val Placed(_, placed) = placedAt(staged, "Broken Peaks")
    assertEquals(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p2)).left.toOption,
      Some(Refusals.notOffered(MagicCarpet.fateDecisionId)))
    val handed = accepted(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p3)))
    assert(backToActing(handed))

  test("choosing the current site skips the move"):
    val Placed(_, placed) = placedAt(staged, "Ancient City")
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
    val Placed(_, placed) = placedAt(crowded, "Deep Woods")
    val after = ready(placed.state)
    assert(backToActing(placed))
    assertEquals(after.game.current.setAsideRelics, Vector(carpet))
    assertEquals(relicOf(after, carpet), None)

  test("a secret on the Carpet returns to its holder facedown when it is discarded"):
    val start = Table.from(staged).tokens(carpet, secrets = 1).unchecked
    val Placed(_, placed) = placedAt(start, "Deep Woods")
    val done = accepted(choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard))
    assertEquals(Look(ready(done.state)).player(actor).board.faceDownSecrets,
      Look(start).player(actor).board.faceDownSecrets + 1)

  test("it costs nothing and needs no secret"):
    assert(usableIds(Table.from(staged).secrets(actor, 0).unchecked)
      .contains(MagicCarpet.id))

  test("it writes where the pawn went, then that it was discarded"):
    val Placed(first, placed) = placedAt(staged, "Deep Woods")
    val done = accepted(choose(placed.state, MagicCarpet.fateDecisionId,
      MagicCarpet.discard))
    assertEquals(NoteText.said(MagicCarpet,
      first.events ++ placed.events ++ done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${actor.value} placed at ${CatalogNames.site("Deep Woods").value}.",
        covers = false),
      NoteText.Said("discarded", "Discarded.", covers = false)))

  test("a Carpet given away is written as given"):
    val Placed(_, placed) = placedAt(staged, "Deep Woods")
    val done = accepted(choose(placed.state, MagicCarpet.fateDecisionId,
      DecisionOptionRef.Player(p2)))
    assertEquals(NoteText.said(MagicCarpet, done.events), Vector(
      NoteText.Said("given", s"Given to ${p2.value}.", covers = false)))
