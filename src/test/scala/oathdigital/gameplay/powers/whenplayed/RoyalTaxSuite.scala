package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class RoyalTaxSuite extends munit.FunSuite:
  import WhenPlayedHarness._

  private val power = registered[RoyalTax.type]
  private val tax = power.cardId

  /** Royal Tax as p1's adviser. p1 rules its own site and another site in
    * its region. p2 stands at the other site with 3 favor, and p3 at p1's
    * site with `held`. */
  private def staged(held: Int): ReadyGame = Table.start.adviser(p1, tax)
    .warbandsAt(homeSite, p1, 1).warbandsAt(nearSite, p1, 1)
    .pawn(p2, nearSite).favor(p2, 3).pawn(p3, homeSite).favor(p3, held).ready

  private def said(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def took(n: Int, from: PlayerId): NoteText.Said = NoteText.Said(
    "took", s"${p1.value} took $n favor from ${from.value}.", covers = false)

  test("each player at a site p1 rules in p1's region gives 2 favor, or " +
      "what they hold"):
    val ready = staged(held = 1)
    val done = finished(play(ready, power, tax))
    val after = Look(done.treeless)
    assertEquals(after.favor(p1), 1 + 2 + 1)
    assertEquals(after.favor(p2), 1)
    assertEquals(after.favor(p3), 0)
    assertEquals(replayed(ready, done.events), done.treeless)
    assertEquals(said(done.events), Vector(took(2, p2), took(1, p3)))

  test("a player with no favor is named, and nothing is taken from them"):
    val done = finished(play(staged(held = 0), power, tax))
    assertEquals(Look(done.treeless).favor(p1), 1 + 2)
    assertEquals(said(done.events), Vector(took(2, p2), NoteText.Said("broke",
      s"${p3.value} had no favor to take.", covers = false)))

  test("players at unruled sites or outside the region are not taxed"):
    // p2 stands in p1's region at a site nobody rules; p3 stands at a site
    // p1 rules in another region.
    val ready = Table.start.adviser(p1, tax).warbandsAt(awaySite, p1, 1)
      .pawn(p2, nearSite).pawn(p3, awaySite).ready
    val done = finished(play(ready, power, tax))
    assertEquals(recorded(done.events), Vector.empty)
    assertEquals(said(done.events), Vector(NoteText.Said("none",
      "No player could be taxed.", covers = false)))
