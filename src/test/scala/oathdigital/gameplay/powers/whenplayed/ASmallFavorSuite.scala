package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.PowerFixture
import oathdigital.model.ReadyGame
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PhasePowerHarness,
  Table}

class ASmallFavorSuite extends munit.FunSuite:
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = registered[ASmallFavor.type]
  private val card = power.cardId
  private val kind = Look(quietTable).forceKind(actor)

  /** p1 holds A Small Favor, and p1's warband bank holds `left`. */
  private def holding(left: Int): ReadyGame =
    Table.from(quietTable).adviser(actor, card).warbandsInBank(actor, left)
      .ready

  // 6 in the bank, more than the 4 the card gains.
  private val staged = holding(6)

  test("playing it gains four warbands"):
    val done = Outcomes.finished(play(staged, power, card))
    assertEquals(Look(done.treeless).player(actor).board.warbands,
      Look(staged).player(actor).board.warbands + 4)
    assertJournalRebuilds(staged, done.events, done.treeless)

  test("the gain is capped by the warband bank"):
    val short = holding(2)
    val done = Outcomes.finished(play(short, power, card))
    assertEquals(Look(done.treeless).player(actor).board.warbands,
      Look(short).player(actor).board.warbands + 2)
    assertEquals(warbandBank(done.treeless, kind), 0)

  test("an empty bank gains nothing and records nothing"):
    val empty = holding(0)
    val done = Outcomes.finished(play(empty, power, card))
    assertEquals(Look(done.treeless).player(actor).board.warbands,
      Look(empty).player(actor).board.warbands)
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)

  test("another card being played does nothing"):
    val other = CatalogNames.denizen("Wrestlers")
    val ready = asAdviser(staged, other)
    val done = Outcomes.finished(play(ready, power, other))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
