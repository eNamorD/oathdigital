package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.powers.{PowerFixture, SearchFixture, TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class GossipSuite extends munit.FunSuite {
  import PowerFixture._
  import SearchFixture._

  private val gossip = DenizenId("99")
  private val plain = denizensOf(Suit.Arcane).take(3)
  private val holder = TargetsFixture.others(base).head

  /** `holder` holds a faceup Gossip, and the actor Searches. */
  private def held(top: Vector[WorldCardId],
      orientation: Orientation = Orientation.FaceUp): ReadyGame =
    TargetsFixture.giveAdviser(SearchFixture.staged(top), holder, gossip,
      orientation)

  private def favor(ready: ReadyGame, id: PlayerId): Int =
    player(ready, id).board.favor
  private def discordBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Discord, 0)

  test("Gossip is a registered persistent rule, so it is automatic") {
    val power = Gossip.forCatalog(catalog).get
    assertEquals(power.cardId, gossip)
    assertEquals(power.resolution, PowerResolution.Automatic)
    assert(WalkerPowerCatalogHas.gossip)
  }

  test("another player's facedown play gains the holder 1 favor from the " +
      "Discord bank") {
    val ready = held(plain)
    val done = play(ready, Vector.empty, plain.head, "adviser-facedown")
    val after = SearchFixture.after(done)
    assertEquals(favor(after, holder), favor(ready, holder) + 1)
    assertEquals(discordBank(after), discordBank(ready) - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), after)
  }

  test("a faceup play and a discard gain nothing") {
    val ready = held(plain)
    Vector("site", "adviser-faceup", "discard").foreach { button =>
      val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
        button))
      assertEquals(favor(after, holder), favor(ready, holder), button)
    }
  }

  test("the holder's own facedown play gains nothing") {
    val ready = asAdviser(SearchFixture.staged(plain), gossip)
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, actor), favor(ready, actor))
    assertEquals(discordBank(after), discordBank(ready))
  }

  test("a facedown Gossip is not active") {
    val ready = held(plain, Orientation.FaceDown)
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, holder), favor(ready, holder))
  }

  test("an empty Discord bank gives nothing") {
    val ready = held(plain).copy(banks = base.banks.copy(favor =
      base.banks.favor.updated(Suit.Discord, 0)))
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, holder), favor(ready, holder))
  }

  private object WalkerPowerCatalogHas {
    def gossip: Boolean = oathdigital.gameplay.powers.WalkerPowerCatalog
      .default(catalog).powers.exists(_.id == Gossip.id)
  }
}
