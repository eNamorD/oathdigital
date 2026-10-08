package oathdigital.application

import oathdigital.gameplay.powers.{PhasePowerCatalog, ReviewedPowerCatalog,
  WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.testkit.TestCards

/** `implemented` on a projected [[oathdigital.protocol.projection.CardDetailsProjection]]
  * must agree across the game's three separate power catalogs: a
  * `ReviewedPowerCatalog` handler carries the flag explicitly, while a
  * `WalkerPowerCatalog`/`PhasePowerCatalog` power carries no flag at all --
  * for those, being wired in for this catalog at all is what implemented
  * means. Each case below picks a real production card by what those
  * catalogs say of its powers, so the suite stays honest to the production
  * catalog and moves on its own as powers ship: a case no card fits any
  * more is skipped, not failed. (No denizen is built by a reviewed handler
  * alone any more, so that path has no card-level case.)
  */
class GamePresentationProjectorImplementedSuite extends munit.FunSuite:
  private val projector = new GamePresentationProjector(catalog)

  private val walkerOrPhase: Set[PowerId] =
    WalkerPowerCatalog.default(catalog).powers.map(_.id).toSet ++
      PhasePowerCatalog.default(catalog).powers.map(_.id).toSet
  private val reviewed = ReviewedPowerCatalog.powers.map(power =>
    power.id -> power.handlers).toMap

  /** Only the walker or phase catalog runs the power. */
  private def builtInWalkerOrPhase(id: PowerId): Boolean =
    walkerOrPhase(id) && !reviewed.contains(id)

  /** No catalog runs the power: it is unreviewed, or reviewed with a handler
    * not built, and neither walker nor phase catalog covers it. */
  private def unbuilt(id: PowerId): Boolean =
    !walkerOrPhase(id) && !reviewed.get(id).exists(_.forall(_.implemented))

  /** A denizen whose printed powers all satisfy `built`. */
  private def denizenWhosePowers(built: PowerId => Boolean): DenizenId =
    val card = catalog.denizens.find(denizen => denizen.powers.nonEmpty &&
      denizen.powers.forall(power => built(power.id)))
    assume(card.nonEmpty, "no denizen fits this case any more")
    DenizenId(card.get.id.value)

  test("a denizen with a power no catalog builds reads as not implemented"):
    val unbuiltCard = catalog.denizens.find(_.powers.exists(power =>
      unbuilt(power.id)))
    assume(unbuiltCard.nonEmpty, "every denizen power ships")
    val card = DenizenId(unbuiltCard.get.id.value)
    assert(!projector.cardDetails(card, None, hidden = false).implemented)

  test("a denizen whose power runs only through the walker or phase catalog reads as implemented"):
    val card = denizenWhosePowers(builtInWalkerOrPhase)
    assert(projector.cardDetails(card, None, hidden = false).implemented)

  test("a denizen with no declared power reads as not implemented"):
    // Every production denizen prints at least one power, so this shape --
    // a card with none at all -- is exercised with a card grafted onto the
    // real catalog rather than one already in it.
    val blank = TestCards.denizen(DenizenId("test-blank"), "Blank",
      catalog.denizens.head.suit, Vector.empty)
    val blankProjector = new GamePresentationProjector(
      catalog.copy(denizens = catalog.denizens :+ blank))
    assert(!blankProjector.cardDetails(DenizenId("test-blank"), None,
      hidden = false).implemented)

  test("an edifice face with a power no catalog builds reads as not implemented"):
    val unbuiltFace = catalog.edifices.find(_.ruined.powers.exists(power =>
      unbuilt(power.id)))
    assume(unbuiltFace.nonEmpty, "every Ruined edifice power ships")
    val ruined = EdificeState(EdificeId(unbuiltFace.get.id.value),
      EdificeSide.Ruined, Tokens.empty)
    assert(!projector.edificeCardDetails(ruined).implemented)
