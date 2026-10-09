package oathdigital.gameplay.powers.search

import oathdigital.gameplay.SearchPlay
import oathdigital.gameplay.powers.SearchFixture
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

/** Mushrooms (card 183): for 1 secret, the Search spends no Supply and
  * draws one card from the bottom of a discard pile; the world deck is
  * unavailable (catalog batch 6, slice 3). */
class MushroomsSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val mushrooms = CatalogNames.denizen("Mushrooms")
  private val modifiers = Vector(Mushrooms.id)
  /** The Cradle pile, first named at the bottom. */
  private val cradle = Vector("Threatening Roar", "Fae Merchant",
    "Second Chance").map(CatalogNames.denizen(_))
  private val provinces = Vector("Wrestlers", "Mercenaries", "Homesteaders")
    .map(CatalogNames.denizen(_))

  /** p1 at Ancient City (Cradle), which holds Mushrooms, with 5 Supply,
    * the start's 1 faceup secret, and three cards in the Cradle and
    * Provinces discard piles. */
  private def besideMushrooms: Table = Table.start
    .pawn(p1, "Ancient City").denizen(mushrooms, at = "Ancient City")
    .supply(p1, 5).discarded(Region.Cradle, cradle*)
    .discarded(Region.Provinces, provinces*)

  private def searched(table: Table, pile: Region,
      picked: Vector[PowerId]): ReadyGame =
    SearchFixture.after(accepted(SearchPlay.searched(rules,
      Ready(table.ready), p1, SearchSource.RegionalDiscard(pile), picked)))

  test("picked, the world deck is unavailable and the pile costs no " +
      "Supply"):
    val shown = ShownOptions.sourcesAfterPicking(besideMushrooms.ready,
      modifiers)
    assertEquals(ShownOptions.optionFor(shown,
      SearchSource.ref(SearchSource.WorldDeck)).map(option =>
      (option.state, option.reason)),
      Some(("unavailable", Some(Mushrooms.worldDeck))))
    assertEquals(ShownOptions.optionFor(shown, SearchSource.ref(
      SearchSource.RegionalDiscard(Region.Cradle))).map(option =>
      (option.state, option.badge)), Some(("available", Some("0 Supply"))))

  test("a Search of p1's pile pays a secret onto it and draws the bottom " +
      "card"):
    val after = searched(besideMushrooms, Region.Cradle, modifiers)
    assertEquals(after.game.current.temporaryHands(p1), Vector(cradle(0)))
    assertEquals(Look(after).supply(p1), 5)
    assertEquals(Look(after).tokensOn(mushrooms), Tokens(0, 1))
    assertEquals(Look(after).player(p1).board.faceUpSecrets, 0)

  test("a ruler's toll is still paid: only the Supply is waived"):
    val home = CatalogNames.site("Ancient City")
    val peaks = CatalogNames.site("Broken Peaks")
    val table = besideMushrooms.denizen("Forced Labor", at = "Broken Peaks")
      .warbandsAt(peaks, p2, 1).warbandsAt(home, p2, 1)
    val before = Look(table.ready)
    val after = Look(searched(table, Region.Cradle, modifiers))
    assertEquals(after.favor(p1), before.favor(p1) - 1)
    assertEquals(after.favor(p2), before.favor(p2) + 1)
    assertEquals(after.supply(p1), 5)

  test("with Augury it draws two cards from the bottom"):
    val table = besideMushrooms.denizen("Augury", at = "Ancient City")
    val after = searched(table, Region.Cradle,
      Vector(Mushrooms.id, Augury.id))
    assertEquals(after.game.current.temporaryHands(p1), cradle.take(2))

  test("with Observatory it draws the bottom card of another region's " +
      "pile"):
    val table = besideMushrooms.denizen("Observatory", at = "Ancient City")
    val after = searched(table, Region.Provinces,
      Vector(Mushrooms.id, Observatory.id))
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(provinces(0)))
