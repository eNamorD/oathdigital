package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.{PlayerFacts, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class ASmallFavorSuite extends munit.FunSuite {
  import PowerFixture._
  import WhenPlayedHarness._

  private val power = ASmallFavor.forCatalog(catalog).get
  private val card = power.cardId
  private val kind = PlayerFacts.forceKind(base, actor).toOption.get
  private def staged = leaveInBank(asAdviser(base, card), kind, 6)

  test("A Small Favor is in the default walker catalog") {
    assert(WalkerPowerCatalog.default(catalog).powers.contains(power))
  }

  test("playing it gains four warbands") {
    val done = finished(play(staged, power, card))
    assertEquals(player(done.treeless).board.warbands,
      player(staged).board.warbands + 4)
    assertEquals(replayed(staged, done.events), done.treeless)
  }

  test("the gain is capped by the warband bank") {
    val short = leaveInBank(asAdviser(base, card), kind, 2)
    val done = finished(play(short, power, card))
    assertEquals(player(done.treeless).board.warbands,
      player(short).board.warbands + 2)
    assertEquals(warbandBank(done.treeless, kind), 0)
  }

  test("an empty bank gains nothing and records nothing") {
    val empty = leaveInBank(asAdviser(base, card), kind, 0)
    val done = finished(play(empty, power, card))
    assertEquals(player(done.treeless).board.warbands,
      player(empty).board.warbands)
    assertEquals(recorded(done.events), Vector.empty)
  }

  test("another card being played does nothing") {
    val other = DenizenId("1")
    val ready = asAdviser(staged, other)
    val done = finished(play(ready, power, other))
    assertEquals(recorded(done.events), Vector.empty)
  }
}
