package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.phases.RestCleanupPlan
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class DissentSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[Dissent]
  private val dissent = power.cardId

  private def placed(player: PlayerId, n: Int): NoteText.Said =
    NoteText.Said("placed", s"${player.value} placed $n favor on it.",
      covers = false)

  test("each player but the People's Favor's holder places one favor per " +
      "suit they rule, or all they hold"):
    // p1 rules Dissent (Discord) and Homesteaders (Hearth) and holds 3
    // favor. p2 rules Land Warden (Hearth) and, through its warband there,
    // Disciples (Arcane) at another Cradle site, and holds 1. p3 holds the
    // People's Favor, and Defame (Discord), with 2 favor.
    val ready = Table.start.adviser(p1, dissent).adviser(p1, "Homesteaders")
      .favor(p1, 3)
      .adviser(p2, "Land Warden").warbandsAt(nearSite, p2, 1)
      .denizen("Disciples", at = nearSite).favor(p2, 1)
      .adviser(p3, "Defame").favor(p3, 2).peoplesFavor(Some(p3), 1).ready
    val done = Outcomes.finished(play(ready, power, dissent))
    val end = Look(done.treeless)
    assertEquals(Vector(p1, p2, p3).map(end.favor), Vector(1, 0, 2))
    assertEquals(end.tokensOn(dissent), Tokens(3, 0))
    // The Rest cleanup returns it all to the Discord bank.
    assertEquals(RestCleanupPlan.derive(catalog, done.treeless, p1)
      .map(_.returnedFavor), Right(Map(Suit.Discord -> 3)))
    assertEquals(said(power, done.events), Vector(placed(p1, 2), placed(p2, 1)))
    assertJournalRebuilds(ready, done.events, done.treeless)

  test("with nobody able to place, the line says so"):
    // p1 rules Dissent but holds no favor; p2 and p3 rule nothing.
    val ready = Table.start.adviser(p1, dissent).favor(p1, 0).ready
    val done = Outcomes.finished(play(ready, power, dissent))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
    assertEquals(said(power, done.events), Vector(NoteText.Said("none",
      "Nobody placed favor.", covers = false)))
