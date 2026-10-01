package oathdigital.gameplay.powers.rest

import oathdigital.gameplay.{OathRules, PlacementFixture}
import oathdigital.gameplay.actions.cardplay.CardPlayProcedure
import oathdigital.gameplay.phases.PhasePowerProcedure
import oathdigital.gameplay.powers.{AdviserLimit, NoteText, PhasePowerCatalog,
  SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.powers.banner.BannerFixture
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.p1

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
  private val plain = SearchFixture.denizensOf(Suit.Hearth)

  /** p1's Rest, holding Insomnia as an adviser, faceup unless `facedown`. */
  private def resting(facedown: Boolean = false): ReadyGame =
    Table.start.turn(p1, Phase.Rest).adviser(p1, insomnia, facedown = facedown)
      .ready

  private def secrets(ready: ReadyGame): Int =
    Look(ready).faceUpSecrets(p1) + Look(ready).faceDownSecrets(p1)

  /** Plays `card` from p1's hand and answers its placement with `button`. */
  private def placed(ready: ReadyGame, card: DenizenId, button: String)
      : (Operation, WalkerOutcome.Parked) =
    val tree = PlacementFixture.build(ready, p1, card)
    val parked = PlacementFixture.park(ready, tree, powers)
    (tree, PlacementFixture.answer(ready, tree, parked, powers,
      PlacementFixture.decisionId(card, "place"),
      DecisionOptionRef.Button(button), p1).asInstanceOf[WalkerOutcome.Parked])

  test("REST: its holder gains 1 secret, once per turn"):
    val ready = resting()
    val used = rules.startWalker(Ready(ready), use, p1, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(secrets(used.state.asInstanceOf[Ready].value),
      secrets(ready) + 1)
    val ref = PowerUseRef(PowerTiming.Rest, PowerSourceRef.Card(insomnia),
      Insomnia.id)
    assertEquals(rules.startWalker(used.state, use, p1, Vector.empty,
      Vector(source)).left.toOption, Some(OathViolation.PowerAlreadyUsed(ref)))

  test("its line restates the gain, covering the generic one"):
    val used = rules.startWalker(Ready(resting()), use, p1, Vector.empty,
      Vector(source)).toOption.get
    assertEquals(NoteText.said(Insomnia.forCatalog(catalog).get, used.events),
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
    val ready = Table.start.hand(p1, plain(0)).adviser(p1, insomnia)
      .adviser(p1, plain(1), facedown = true).ready
    val (tree, faceup) = placed(ready, plain(0), "adviser-faceup")
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(plain(1))))
    assertEquals(AdviserLimit.of(catalog, ready, p1), Insomnia.HolderLimit)

  test("playing Insomnia faceup needs room under its own limit"):
    // Two advisers and a third would fit the default limit of three.
    val ready = Table.start.hand(p1, insomnia)
      .adviser(p1, plain(0), facedown = true)
      .adviser(p1, plain(1), facedown = true).ready
    val (tree, faceup) = placed(ready, insomnia, "adviser-faceup")
    assertEquals(PlacementFixture.options(ready, tree, faceup.tree, powers).toSet,
      Set[DecisionOptionRef](DecisionOptionRef.Denizen(plain(0)),
        DecisionOptionRef.Denizen(plain(1))))

  test("with Silver Tongue as well the limit stays 2, and both limits keep " +
      "the Mob's discard"):
    val ready = BannerFixture.holdingFavor(Table.start.hand(p1, plain(0))
      .denizen(plain(2), at = Table.homeOf(p1))
      .adviser(p1, tongue).adviser(p1, insomnia).ready)
    assertEquals(AdviserLimit.of(catalog, ready, p1), 2)
    val (tree, site) = placed(ready, plain(0), "site")
    assertEquals(PlacementFixture.options(ready, tree, site.tree, powers).head,
      CardPlayProcedure.noReplacement.ref)
