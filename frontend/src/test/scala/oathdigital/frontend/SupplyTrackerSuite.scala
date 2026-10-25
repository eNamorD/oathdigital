package oathdigital.frontend

import oathdigital.frontend.Dom.{all, firstMatch}
import oathdigital.model.PlayerColor

/** The Supply track as the physical board prints it: the most Supply on
  * the left, each band on the space Rest sets the marker to, and the
  * marker on the space with the player's Supply to its right. Runs under
  * jsdom.
  */
class SupplyTrackerSuite extends munit.FunSuite:
  private val bands = Vector(SupplyBand(9, None, 6), SupplyBand(4, Some(8), 5),
    SupplyBand(0, Some(3), 4))

  /** The track of a red board holding `supply` Supply with `banked`
    * warbands in its bank, on a track whose ceiling is 7. */
  private def redTrack(supply: Int, banked: Int) =
    SupplyTracker.render(SupplyTracker.Track(bands, supply, banked, 7,
      PlayerColor.Red))

  test("eight spaces, the most Supply first, each band printed high to low"):
    val spaces = all(redTrack(supply = 5, banked = 5), ".supply-space")
    assertEquals(spaces.size, 8)
    assertEquals(spaces.map(_.textContent), Vector(
      "★", "9+", "8to4", "3to0", "", "", "", ""))
    assertEquals(all(spaces(1), ".token-warband").size, 1)

  test("each space names its Supply, its band, the current band and the marker"):
    assertEquals(all(redTrack(supply = 6, banked = 5), ".supply-space")
      .take(4).map(_.getAttribute("aria-label")), Vector(
        "Supply 7",
        "Supply 6, 9 or more warbands in bank, marker",
        "Supply 5, 4 to 8 warbands in bank, current band",
        "Supply 4, 0 to 3 warbands in bank"))

  test("the marker leaves as many spaces to its right as the Supply held"):
    val spaces = all(redTrack(supply = 2, banked = 5), ".supply-space")
    val at = spaces.indexWhere(space => firstMatch(space, ".supply-marker").nonEmpty)
    assertEquals(spaces.size - 1 - at, 2)
    assertEquals(all(redTrack(supply = 2, banked = 5), ".supply-marker").size, 1)

  test("the marker wears the board's colour"):
    val marker = firstMatch(redTrack(supply = 5, banked = 5), ".supply-marker")
      .getOrElse(fail("no marker"))
    assert(marker.classList.contains("supply-marker-red"))

  test("a full track puts the marker on the star; an empty one on the last space"):
    val full = all(redTrack(supply = 7, banked = 5), ".supply-space")
    assert(firstMatch(full.head, ".supply-marker").nonEmpty)
    val empty = all(redTrack(supply = 0, banked = 5), ".supply-space")
    assert(firstMatch(empty.last, ".supply-marker").nonEmpty)

  test("an empty bank makes the lowest band current"):
    val current = all(redTrack(supply = 5, banked = 0), ".supply-space-current")
    assertEquals(current.map(_.textContent), Vector("3to0"))

  test("what a space draws is hidden, as its name already says it"):
    val drawn = all(redTrack(supply = 7, banked = 5), ".supply-space > *")
    assert(drawn.nonEmpty)
    assert(drawn.forall(_.getAttribute("aria-hidden") == "true"))
