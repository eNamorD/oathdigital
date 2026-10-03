package oathdigital.application

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class PresentationLabelsSuite extends munit.FunSuite:
  private val presentation = new GamePresentationProjector(catalog)

  /** The ruined Hiding Place and Wrestlers lie at Deep Woods, p1 holds
    * Wizard's Conclave facedown, and Threatening Roar tops the world deck. */
  private lazy val board: ReadyGame = Table.start
    .edifice("Hiding Place", EdificeSide.Ruined, "Deep Woods")
    .denizen("Wrestlers", "Deep Woods")
    .adviser(p1, "Wizard's Conclave", facedown = true)
    .worldDeckTop("Threatening Roar").ready

  test("a player's label is the one the Players strip shows"):
    assertEquals(presentation.playerLabel(p1),
      presentation.readyPlayers(board).head.displayName)

  test("a player's label is their ID as typed"):
    Vector("roman", "red-team", "Pink", "a.b:c").foreach(id =>
      assertEquals(presentation.playerLabel(PlayerId(id)), id))

  test("a player option in a decision is labelled with the ID as typed"):
    val projector = new WalkerDecisionProjector(catalog, presentation,
      oathdigital.gameplay.walker.WalkerPowers(Vector.empty))
    assertEquals(projector.optionProjection(board, None, None,
      DecisionOption.Player(DecisionOptionRef.Player(p1))).map(_.label),
      Some(p1.value))

  test("a site denizen is identified to everyone; a facedown adviser to its owner only"):
    val denizen = CatalogNames.denizen("Wrestlers")
    Vector(None, Some(p1)).foreach { viewer =>
      assert(presentation.identifiesAt(board, viewer, denizen)) }
    val adviser = CatalogNames.denizen("Wizard's Conclave")
    assert(presentation.identifiesAt(board, Some(p1), adviser))
    assert(!presentation.identifiesAt(board, Some(p2), adviser))
    assert(!presentation.identifiesAt(board, None, adviser))

  test("a card in the world deck is identified to nobody"):
    val top = CatalogNames.denizen("Threatening Roar")
    Vector(p1, p2, p3).foreach { viewer =>
      assert(!presentation.identifiesAt(board, Some(viewer), top)) }

  test("a card's label is its printed name"):
    assertEquals(presentation.cardLabel(board,
      CatalogNames.denizen("Errand Boy")), "Errand Boy")
