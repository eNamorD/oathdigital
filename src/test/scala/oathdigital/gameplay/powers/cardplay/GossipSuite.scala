package oathdigital.gameplay.powers.cardplay

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powers.{NoteText, PowerFixture, SearchFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{ActPowerHarness, CatalogNames, Look, Table}

class GossipSuite extends munit.FunSuite:
  import PowerFixture._
  import SearchFixture._

  private val gossip = CatalogNames.denizen("Gossip")
  // Three Arcane denizens whose powers these tests do not use; p1 plays the
  // first, Ward of Silence.
  private val plain = Vector("Ward of Silence", "Arcane Brokers",
    "Arcane Armor").map(CatalogNames.denizen(_))
  private val holder = Table.p2

  /** `holder` holds a faceup Gossip, the Discord bank holds 3 favor, and the
    * actor Searches. */
  private def heldTable(top: Vector[WorldCardId],
      orientation: Orientation = Orientation.FaceUp): Table =
    Table.from(SearchFixture.staged(top, supply = 5)).adviser(holder, gossip,
      facedown = orientation == Orientation.FaceDown)
      .bankFavor(Suit.Discord, 3)
  private def held(top: Vector[WorldCardId],
      orientation: Orientation = Orientation.FaceUp)(
          using munit.Location): ReadyGame =
    heldTable(top, orientation).ready

  private def favor(ready: ReadyGame, id: PlayerId): Int =
    Look(ready).player(id).board.favor
  private def discordBank(ready: ReadyGame): Int =
    ready.banks.favor.getOrElse(Suit.Discord, 0)

  private val power = Gossip.forCatalog(catalog)

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  test("another player's facedown play gains the holder 1 favor from the " +
      "Discord bank"):
    val ready = held(plain)
    val done = play(ready, Vector.empty, plain.head, PlayedAs.Facedown)
    val after = SearchFixture.after(done)
    assertEquals(favor(after, holder), favor(ready, holder) + 1)
    assertEquals(discordBank(after), 3 - 1)
    assertEquals(ActPowerHarness.replayed(rules, ready, done.events), after)

  test("a facedown Vision counts too"):
    val ready = held(Vector(VisionRules.Faith))
    val after = SearchFixture.after(play(ready, Vector.empty,
      VisionRules.Faith, PlayedAs.Facedown))
    assertEquals(favor(after, holder), favor(ready, holder) + 1)

  test("a faceup play and a discard gain nothing"):
    val ready = held(plain)
    Vector(PlayedAs.AtSite, PlayedAs.Faceup, PlayedAs.Discarded).foreach { button =>
      val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
        button))
      assertEquals(favor(after, holder), favor(ready, holder), button)
    }

  test("the holder's own facedown play gains nothing"):
    val ready = asAdviser(SearchFixture.staged(plain, supply = 5), gossip)
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      PlayedAs.Facedown))
    assertEquals(favor(after, actor), favor(ready, actor))
    assertEquals(discordBank(after), discordBank(ready))

  test("a facedown Gossip is not active"):
    val ready = held(plain, Orientation.FaceDown)
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      PlayedAs.Facedown))
    assertEquals(favor(after, holder), favor(ready, holder))

  test("an empty Discord bank gives nothing"):
    val ready = heldTable(plain).bankFavor(Suit.Discord, 0).ready
    val after = SearchFixture.after(play(ready, Vector.empty, plain.head,
      PlayedAs.Facedown))
    assertEquals(favor(after, holder), favor(ready, holder))

  // ---- Lines ----

  test("the favor another player's facedown play gains is Gossip's line"):
    val done = play(held(plain), Vector.empty, plain.head, PlayedAs.Facedown)
    assertEquals(said(done.events), Vector(NoteText.Said("gained",
      s"${holder.value} gained 1 favor from the Discord bank.", covers = true)))

  test("a faceup play writes no Gossip line"):
    assertEquals(said(play(held(plain), Vector.empty, plain.head,
      PlayedAs.Faceup).events), Vector.empty)
