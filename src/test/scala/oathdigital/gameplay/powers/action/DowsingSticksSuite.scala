package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PowerFixture}
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look,
  PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.{assertWireRoundTrips, ready}
import oathdigital.testkit.Unwrap.accepted

class DowsingSticksSuite extends munit.FunSuite:
  import ActPowerHarness._
  import PowerFixture._

  private val parked = PhasePowerHarness.parked

  private val sticks = CatalogNames.relic("Dowsing Sticks")
  private val source = DecisionOptionRef.Relic(sticks)
  private val whistle = CatalogNames.relic("Whistle")

  /** p1's Act holding Dowsing Sticks with `secrets` faceup secrets, and
    * Whistle on top of the relic deck. */
  private def stagedTable(secrets: Int = 3): Table =
    Table.start.turn(actor, Phase.Act).relic(actor, sticks)
      .secrets(actor, secrets).relicDeckTop(whistle)
  private def staged(secrets: Int = 3): ReadyGame = stagedTable(secrets).ready

  test("it places 1 secret, burns 2 and takes the top relic facedown"):
    val ready0 = staged()
    val rules0 = rules()
    val done = accepted(use(rules0, ready0, DowsingSticks.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).player(actor).relics.find(_.id == whistle)
      .map(_.orientation),
      Some(Orientation.FaceDown))
    assert(!end.game.current.commonCards.relicDeck.contains(whistle))
    assertEquals(Look(end).tokensOn(sticks), Tokens(0, 1))
    assertEquals(Look(end).player(actor).board.faceUpSecrets, 0)
    parked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assertWireRoundTrips(done.events)

  test("an empty relic deck pays the cost and draws nothing"):
    val emptied = stagedTable().relicDeckEmptied.ready
    val done = accepted(use(rules(), emptied, DowsingSticks.id, source))
    val end = ready(done.state)
    assertEquals(Look(end).player(actor).relics.map(_.id), Vector(sticks))
    assertEquals(Look(end).tokensOn(sticks), Tokens(0, 1))
    assertEquals(Look(end).player(actor).board.faceUpSecrets, 0)

  test("it is unusable with fewer than 3 faceup secrets"):
    assert(!usableIds(staged(secrets = 2)).contains(DowsingSticks.id))
    assertEquals(
      use(rules(), staged(secrets = 2), DowsingSticks.id, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(3, 2)))

  test("it writes the relic it drew"):
    val done = accepted(use(rules(), staged(), DowsingSticks.id, source))
    assertEquals(NoteText.said(DowsingSticks, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${actor.value} drew ${whistle.value} facedown.",
        covers = false)))

  test("an empty relic deck is written as such"):
    val emptied = stagedTable().relicDeckEmptied.ready
    val done = accepted(use(rules(), emptied, DowsingSticks.id, source))
    assertEquals(NoteText.said(DowsingSticks, done.events), Vector(
      NoteText.Said("used.empty", "The relic deck was empty.", covers = false)))
