package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture,
  TargetsFixture}
import oathdigital.gameplay.powers.action.PaidActionHarness
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._

class GossipSuite extends munit.FunSuite:
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

  test("another player's facedown play gains the holder 1 favor from the " +
      "Discord bank"):
    val ready = held(plain)
    val done = play(ready, Vector.empty, plain.head, "adviser-facedown")
    val after = SearchFixture.after(done)
    assertEquals(favor(after, holder), favor(ready, holder) + 1)
    assertEquals(discordBank(after), discordBank(ready) - 1)
    assertEquals(PaidActionHarness.replayed(rules, ready, done.events), after)

  test("a facedown Vision counts too"):
    val ready = held(Vector(VisionRules.Faith))
    val after = SearchFixture.after(play(ready, Vector.empty,
      VisionRules.Faith, "adviser-facedown"))
    assertEquals(favor(after, holder), favor(ready, holder) + 1)

  test("a faceup play and a discard gain nothing"):
    val ready = held(plain)
    Vector("site", "adviser-faceup", "discard").foreach { button =>
      val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
        button))
      assertEquals(favor(after, holder), favor(ready, holder), button)
    }

  test("the holder's own facedown play gains nothing"):
    val ready = asAdviser(SearchFixture.staged(plain), gossip)
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, actor), favor(ready, actor))
    assertEquals(discordBank(after), discordBank(ready))

  test("a facedown Gossip is not active"):
    val ready = held(plain, Orientation.FaceDown)
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, holder), favor(ready, holder))

  test("an empty Discord bank gives nothing"):
    val ready = held(plain).copy(banks = base.banks.copy(favor =
      base.banks.favor.updated(Suit.Discord, 0)))
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      "adviser-facedown"))
    assertEquals(favor(after, holder), favor(ready, holder))

  // ---- Lines ----

  private val power = Gossip.forCatalog(catalog).get
  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("the favor another player's facedown play gains is Gossip's line"):
    val done = play(held(plain), Vector.empty, plain.head, "adviser-facedown")
    assertEquals(said(done.events), Vector(NoteText.Said("gained",
      s"${holder.value} gained 1 favor from the Discord bank.", covers = true)))

  test("a faceup play writes no Gossip line"):
    assertEquals(said(play(held(plain), Vector.empty, plain.head,
      "adviser-faceup").events), Vector.empty)
