package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.{OathRules, PlacementFixture}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{AdviserLimit, NoteText, PhasePowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.{accepted, as}

class InsomniaSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
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

  /** The card-play tree, and its walk parked after the placement answer. */
  private case class Placement(tree: Operation, parked: WalkerOutcome.Parked)

  /** Plays `card` from p1's hand and answers its placement with `button`. */
  private def placed(ready: ReadyGame, card: DenizenId, button: String)
      : Placement =
    val tree = PlacementFixture.build(ready, p1, card)
    val parked = PlacementFixture.park(ready, tree, powers)
    Placement(tree, as[WalkerOutcome.Parked](
      PlacementFixture.answer(ready, tree, parked, powers,
      PlacementFixture.decisionId(card, "place"),
      DecisionOptionRef.Button(button), p1)))

  test("REST: its holder gains 1 secret, once per turn"):
    val ready = resting()
    val used = accepted(
      rules.startWalker(Ready(ready), use, p1, Vector.empty, Vector(source)))
    assertEquals(secrets(Outcomes.ready(used.state)), secrets(ready) + 1)
    val ref = PowerUseRef(PowerTiming.Rest, PowerSourceRef.Card(insomnia),
      Insomnia.id)
    assertEquals(rules.startWalker(used.state, use, p1, Vector.empty,
      Vector(source)).left.toOption, Some(OathViolation.PowerAlreadyUsed(ref)))

  test("its line restates the gain, covering the generic one"):
    val used = accepted(
      rules.startWalker(Ready(resting()), use, p1, Vector.empty,
      Vector(source)))
    assertEquals(NoteText.said(Insomnia, used.events),
      Vector(NoteText.Said(NoteKey.Used, s"${p1.value} gained 1 secret.",
        covers = true)))

  test("a facedown Insomnia offers no REST power and sets no limit"):
    val ready = resting(facedown = true)
    assert(!PhasePowerProcedure.usable(catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .exists(_.power.id == Insomnia.id))
    assertEquals(AdviserLimit.of(catalog, ready, p1), AdviserLimit.Default)

  test("its holder may have only two advisers: a third faceup adviser needs " +
      "a discard, and the locked Insomnia cannot go"):
    val ready = Table.start.hand(p1, homesteaders).adviser(p1, insomnia)
      .adviser(p1, landWarden, facedown = true).ready
    val Placement(tree, faceup) = placed(ready, homesteaders, "adviser-faceup")
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(landWarden)))
    assertEquals(AdviserLimit.of(catalog, ready, p1), Insomnia.HolderLimit)

  test("playing Insomnia faceup needs room under its own limit"):
    // Two advisers and a third would fit the default limit of three.
    val ready = Table.start.hand(p1, insomnia)
      .adviser(p1, homesteaders, facedown = true)
        .adviser(p1, landWarden, facedown = true).ready
    val Placement(tree, faceup) = placed(ready, insomnia, "adviser-faceup")
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(homesteaders),
        DecisionOptionRef.Denizen(landWarden)))

  test("with Silver Tongue as well the limit stays 2, and both limits keep " +
      "the Mob's discard"):
    val ready = Table.start.hand(p1, homesteaders).pawn(p1, "Ancient City")
      .denizen(newsFromAfar, at = "Ancient City").adviser(p1, tongue)
      .adviser(p1, insomnia).peoplesFavor(Some(p1), 1).ready
    assertEquals(AdviserLimit.of(catalog, ready, p1), 2)
    val Placement(tree, site) = placed(ready, homesteaders, "site")
    assertEquals(PlacementFixture.options(ready, tree, site.tree, powers).head,
      CardPlayProcedure.noReplacement.ref)
