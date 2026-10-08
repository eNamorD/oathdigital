package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2}

class CharlatanSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Charlatan.type]
  private val charlatan = power.cardId

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.darkestSecret.secrets

  private val none = NoteText.Said("none",
    "The Darkest Secret had no secret to burn.", covers = false)

  private def withBanner(holder: Option[PlayerId], secrets: Int): ReadyGame =
    Table.start.adviser(p1, charlatan).darkestSecret(holder, secrets).ready

  test("it burns all but one secret from a held Darkest Secret"):
    val ready = withBanner(Some(p2), 4)
    val done = Outcomes.finished(play(ready, power, charlatan))
    assertEquals(onBanner(done.treeless), 1)
    assertEquals(done.treeless.game.current.banners.darkestSecret.holder,
      Some(p2))
    assertJournalRebuilds(ready, done.events, done.treeless)
    assertEquals(said(power, done.events), Vector(NoteText.Said("burned",
      "Burned 3 secrets from the Darkest Secret.", covers = false)))

  test("a single secret stays, and the line says none was burned"):
    val done = Outcomes.finished(play(withBanner(None, 1), power, charlatan))
    assertEquals(onBanner(done.treeless), 1)
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(none))

  test("an empty Darkest Secret burns nothing"):
    val done = Outcomes.finished(play(withBanner(None, 0), power, charlatan))
    assertEquals(said(power, done.events), Vector(none))
