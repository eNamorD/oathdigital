package oathdigital.application

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.banner.{WanderingFlameMove,
  WanderingFlamePlace}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{MenuPlay, Table}
import oathdigital.testkit.Table.p1

/** The banner faces' phase powers reach the player: the Act menu lists each
  * by its own button, available for the holder only, and the menu's source (a
  * banner) binds back to the engine's source.
  */
class BannerFaceProjectionSuite extends munit.FunSuite:
  private val move = WanderingFlameMove.id
  private val place = WanderingFlamePlace.id
  private val banner = DecisionOptionRef.Banner(Banner.DarkestSecret)

  private def labelOf(option: DecisionOption): Option[String] = option match
    case DecisionOption.Button(_, label) => Some(label)
    case DecisionOption.Priced(inner, _) => labelOf(inner)
    case DecisionOption.Badged(inner, _) => labelOf(inner)
    case DecisionOption.Warned(inner, _) => labelOf(inner)
    case DecisionOption.Unavailable(inner, _) => labelOf(inner)
    case _ => None

  /** The power buttons of the Act menu on `ready`, with their labels. */
  private def powerButtons(ready: ReadyGame)
      : Vector[(DecisionOptionRef, String)] =
    MenuPlay.options(Table.from(ready).phaseMenuParked.state, catalog)
      .collect:
        case option if option.ref.isInstanceOf[DecisionOptionRef.Button] &&
            option.ref.asInstanceOf[DecisionOptionRef.Button].key
              .startsWith("use-power:") =>
          option.ref -> labelOf(option).getOrElse("")

  test("the holder is offered both Wandering Flame powers, each by name"):
    // p1 stands at Ancient City and holds the Wandering Flame and a faceup
    // secret; Broken Peaks holds a secret.
    val holding = Table.start.pawn(p1, "Ancient City")
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .siteTokens("Broken Peaks", secrets = 1).secrets(p1, faceUp = 1).ready
    assertEquals(powerButtons(holding), Vector[(DecisionOptionRef, String)](
      PhaseMenu.powerButton(move) -> "Wandering Flame: move",
      PhaseMenu.powerButton(place) ->
        "Wandering Flame: place a secret on your site"))
    assert(MenuPlay.availableInMenu(catalog, holding,
      ActionRef.UsePower(move), Vector(banner)))
    assert(MenuPlay.availableInMenu(catalog, holding,
      ActionRef.UsePower(place), Vector(banner)))

  test("a power with nothing to do is unavailable: Move needs a secret-" +
      "bearing site, and the place power needs a faceup secret"):
    def availability(ready: ReadyGame) = Vector(move, place).map(power =>
      MenuPlay.availableInMenu(catalog, ready, ActionRef.UsePower(power),
        Vector(banner)))
    // p1 stands at Ancient City throughout. The quiet table has no site
    // secrets, so Move has no target.
    val noSite = Table.start.pawn(p1, "Ancient City")
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .secrets(p1, faceUp = 1).ready
    assertEquals(availability(noSite), Vector(false, true))
    val noSecret = Table.start.pawn(p1, "Ancient City")
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .siteTokens("Broken Peaks", secrets = 1)
      .secrets(p1, faceUp = 0, faceDown = 2).ready
    assertEquals(availability(noSecret), Vector(true, false))
    val neither = Table.start.pawn(p1, "Ancient City")
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.WanderingFlame)
      .secrets(p1, faceUp = 0, faceDown = 2).ready
    assertEquals(availability(neither), Vector(false, false))

  test("the Festival face is offered no power"):
    val festival = Table.start
      .darkestSecret(Some(p1), 1, face = DarkestSecretFace.Festival)
      .siteTokens("Broken Peaks", secrets = 1).ready
    assertEquals(powerButtons(festival), Vector.empty)
