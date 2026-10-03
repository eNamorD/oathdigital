package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PlayerFacts, PowerFixture,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{Look, Outcomes, PhasePowerHarness}

class ASmallFavorSuite extends munit.FunSuite:
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = ASmallFavor
  private val card = power.cardId
  private val kind = PlayerFacts.forceKind(quietTable, actor).toOption.get
  private def staged = leaveInBank(asAdviser(quietTable, card), kind, 6)

  test("A Small Favor is in the default walker catalog"):
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))

  test("playing it gains four warbands"):
    val done = Outcomes.finished(play(staged, power, card))
    assertEquals(Look(done.treeless).player(actor).board.warbands,
      Look(staged).player(actor).board.warbands + 4)
    assertEquals(replayed(staged, done.events), done.treeless)

  test("the gain is capped by the warband bank"):
    val short = leaveInBank(asAdviser(quietTable, card), kind, 2)
    val done = Outcomes.finished(play(short, power, card))
    assertEquals(Look(done.treeless).player(actor).board.warbands,
      Look(short).player(actor).board.warbands + 2)
    assertEquals(warbandBank(done.treeless, kind), 0)

  test("an empty bank gains nothing and records nothing"):
    val empty = leaveInBank(asAdviser(quietTable, card), kind, 0)
    val done = Outcomes.finished(play(empty, power, card))
    assertEquals(Look(done.treeless).player(actor).board.warbands,
      Look(empty).player(actor).board.warbands)
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)

  test("another card being played does nothing"):
    val other = DenizenId("1")
    val ready = asAdviser(staged, other)
    val done = Outcomes.finished(play(ready, power, other))
    assertEquals(PhasePowerHarness.ops(done.events), Vector.empty)
