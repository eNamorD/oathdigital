package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.gameplay.PlacementFixture
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{AdviserLimit, NoteText, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, PlacementPicks,
  PowerRules, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

class InsomniaSuite extends munit.FunSuite:
  private val rules = PowerRules.withWalkerAndPhasePowers
  /** The automatic production powers, as a command with no modifier walks. */
  private val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
    Vector.empty)
  private val insomnia = CatalogNames.denizen("Insomnia")
  private val tongue = CatalogNames.denizen("Silver Tongue")
  private val use = ActionRef.UsePower(Insomnia.id)
  private val source = DecisionOptionRef.Denizen(insomnia)
  // Hearth denizens whose powers these tests do not use.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val newsFromAfar = CatalogNames.denizen("News from Afar")

  /** p1's Rest, holding Insomnia as an adviser, faceup unless `facedown`. */
  private def resting(facedown: Boolean = false): ReadyGame =
    Table.start.turn(p1, Phase.Rest).adviser(p1, insomnia, facedown = facedown)
      .ready

  private def secrets(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)

  /** The placement options of `card` from p1's hand, under the production
    * powers. */
  private def placementOptions(ready: ReadyGame, card: DenizenId)
      : Vector[DecisionOption] =
    val tree = PlacementFixture.build(ready, p1, card)
    PlacementFixture.placementOptions(ready, tree,
      PlacementFixture.park(ready, tree, powers), powers)

  private def playing(options: Vector[DecisionOption], way: PlayedAs)
      : Vector[DecisionOption] =
    options.filter(option => CardPlayProcedure.playedAs(option.ref)
      .contains(way))

  test("REST: its holder gains 1 secret, once per turn"):
    val ready = resting()
    val used = accepted(
      rules.startWalker(Ready(ready), use, p1, Vector(source)))
    assertEquals(secrets(Outcomes.ready(used.state)), secrets(ready) + 1)
    val ref = PowerUseRef(PowerTiming.Rest, PowerSourceRef.Card(insomnia),
      Insomnia.id)
    assertEquals(rules.startWalker(used.state, use, p1, Vector(source)).left.toOption, Some(OathViolation.PowerAlreadyUsed(ref)))

  test("its line restates the gain, covering the generic one"):
    val used = accepted(
      rules.startWalker(Ready(resting()), use, p1, Vector(source)))
    assertEquals(NoteText.said(Insomnia, used.events),
      Vector(NoteText.Said(NoteKey.Used, s"${p1.value} gained 1 secret.",
        covers = true)))

  test("a facedown Insomnia offers no REST power and sets no limit"):
    val ready = resting(facedown = true)
    assert(!PhasePowerProcedure.usable(catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .exists(_.power.id == Insomnia.id))
    assertEquals(AdviserLimit.of(catalog, ready, p1), AdviserLimit.Default)

  test("its holder may have only two advisers: a third faceup adviser " +
      "replaces one, and the locked Insomnia cannot go"):
    val ready = Table.start.hand(p1, homesteaders).adviser(p1, insomnia)
      .adviser(p1, landWarden, facedown = true).ready
    val faceup = playing(placementOptions(ready, homesteaders), PlayedAs.Faceup)
    assertEquals(faceup.filter(_.available).map(PlacementPicks.replaces),
      Vector(Some(landWarden)))
    assertEquals(faceup.filterNot(_.available).map(PlacementPicks.replaces),
      Vector(Some(insomnia)))
    assertEquals(AdviserLimit.of(catalog, ready, p1), Insomnia.HolderLimit)

  test("playing Insomnia faceup needs room under its own limit"):
    // Two advisers and a third would fit the default limit of three.
    val ready = Table.start.hand(p1, insomnia)
      .adviser(p1, homesteaders, facedown = true)
        .adviser(p1, landWarden, facedown = true).ready
    val options = placementOptions(ready, insomnia)
    assertEquals(playing(options, PlayedAs.Faceup).map(PlacementPicks.replaces)
      .toSet, Set[Option[CardId]](Some(homesteaders), Some(landWarden)))
    assertEquals(playing(options, PlayedAs.Facedown).map(_.ref),
      Vector[DecisionOptionRef](
        DecisionOptionRef.AdviserPlay(p1, 2, faceup = false)))

  test("with Silver Tongue as well the limit stays 2, and the Mob's discard " +
      "stays"):
    val ready = Table.start.hand(p1, homesteaders).pawn(p1, "Ancient City")
      .denizen(newsFromAfar, at = "Ancient City").adviser(p1, tongue)
      .adviser(p1, insomnia).peoplesFavor(Some(p1), 1).ready
    assertEquals(AdviserLimit.of(catalog, ready, p1), 2)
    assert(placementOptions(ready, homesteaders).exists(option =>
      PlacementPicks.replaces(option).contains(newsFromAfar)))
