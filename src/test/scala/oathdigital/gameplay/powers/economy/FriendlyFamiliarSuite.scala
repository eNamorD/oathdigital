package oathdigital.gameplay.powers.economy

import oathdigital.gameplay.PowerAccess
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, MenuPlay, PhasePowerHarness,
  PowerRules, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class FriendlyFamiliarSuite extends munit.FunSuite:
  private val familiar = CatalogNames.denizen("Friendly Familiar")
  private val friends = CatalogNames.denizen("Small Friends")
  private val merchants = CatalogNames.denizen("Skilled Merchants")
  private val library = CatalogNames.denizen("Map Library")
  /** p1's pawn site. */
  private val home = CatalogNames.site("Ancient City")
  /** Another site in play in the pawn's region. */
  private val homeNeighbour = Table.otherSiteInRegionOf(home)
  /** A site in play in another region. */
  private val away = Table.siteInAnotherRegionThan(home)
  /** Another site in play in `away`'s region. */
  private val awayNeighbour = Table.otherSiteInRegionOf(away)
  /** A plain Beast denizen. */
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  /** A plain Order denizen. */
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  /** A plain Arcane denizen. */
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val modifiers =
    ModifierPicks.decisionId(PowerWindow.TradeModifierSelection)
  private val rules = PowerRules.withWalkerPowers

  /** p1 at Ancient City holding Friendly Familiar, with a second faceup
    * secret: one for the Familiar, one for a Trade for favor. */
  private def holdingFamiliar: Table = Table.start.pawn(p1, at = home)
    .adviser(p1, familiar).secrets(p1, faceUp = 2)

  private def tradeBegun(ready: ReadyGame): OathTransition =
    accepted(MenuPlay.started(rules, Ready(ready), p1, ActionRef.Trade,
      Vector(DecisionOptionRef.Button("favor"))))

  private def answeredWith(from: OathTransition, decisionId: String,
      ref: DecisionOptionRef): OathTransition =
    val next = accepted(rules.resolveWalker(from.state, p1, decisionId,
      ChooseOneAnswer(ref)))
    next.copy(events = from.events ++ next.events)

  /** `from` with `card`'s modifier picked and acting as if at `site`. */
  private def actingAt(from: OathTransition, card: DenizenId, site: SiteId)
      : OathTransition = answeredWith(answeredWith(from, modifiers,
    DecisionOptionRef.Denizen(card)), PowerAccess.asIfDecision,
    DecisionOptionRef.Site(site))

  private def optionStates(at: OathTransition)
      : Option[Vector[(String, String, Option[String])]] =
    PhasePowerHarness.queryOf(at, p1).map(_.offeredOptions.map(option =>
      (option.id, option.state, option.reason)))

  test("picked, it pays a secret onto itself and offers every other site " +
      "with a card to trade with"):
    // Errand Boy stands at the pawn site, which is not offered.
    val ready = holdingFamiliar.denizen(wrestlers, at = away)
      .denizen(errandBoy, at = home).ready
    val asked = answeredWith(tradeBegun(ready), modifiers,
      DecisionOptionRef.Denizen(familiar))
    assertEquals(PhasePowerHarness.offered(asked, p1),
      Some(Vector("site" -> away.value)))
    assertEquals(Look(asked.state).adviserTokens(p1, familiar), Tokens(0, 1))

  test("acting at the chosen site, Skilled Merchants adds that site's " +
      "region, not the pawn's"):
    val ready = holdingFamiliar.adviser(p1, merchants)
      .denizen(wrestlers, at = away).denizen(alchemist, at = awayNeighbour)
      .denizen(errandBoy, at = homeNeighbour).ready
    val acting = actingAt(tradeBegun(ready), familiar, away)
    // Nothing is left to pick, so the Trade starts by itself.
    val sources = answeredWith(acting, modifiers,
      DecisionOptionRef.Denizen(merchants))
    assertEquals(PhasePowerHarness.offered(sources, p1), Some(Vector(
      "denizen" -> wrestlers.value, "denizen" -> alchemist.value)))

  test("Map Library at the chosen site is selectable while the pawn " +
      "stands elsewhere"):
    val ready = holdingFamiliar.denizen(library, at = away).ready
    val begun = tradeBegun(ready)
    assert(!optionStates(begun).exists(_.exists(_._1 == library.value)))
    assert(optionStates(actingAt(begun, familiar, away)).exists(_.contains(
      (library.value, "available", None))))

  test("once Small Friends acts at another site, it is unavailable"):
    val ready = Table.start.pawn(p1, at = home).adviser(p1, familiar)
      .adviser(p1, friends).denizen(errandBoy, at = away).ready
    val acting = actingAt(tradeBegun(ready), friends, away)
    assert(optionStates(acting).exists(_.contains((familiar.value,
      "unavailable", Some("Already acting as if at another site")))))
