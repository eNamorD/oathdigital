package oathdigital.gameplay

import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.model.OathViolation.NoPlayableOption
import oathdigital.testkit.{Look, Table}
import oathdigital.testkit.Table.p1

class PlaceBannerResourceProcedureSuite extends munit.FunSuite:
  private val rules = new OathRules(catalog)
  private val ds = DecisionOptionRef.Banner(Banner.DarkestSecret)

  /** p1, in Act, holds Darkest Secret with one secret on it, and has 6
    * faceup and 4 facedown secrets and the start's 7 Supply. */
  private def holding: Table = Table.start
    .darkestSecret(Some(p1), secrets = 1)
    .secrets(p1, faceUp = 6, faceDown = 4)

  private def start(board: ReadyGame) = rules.startWalker(Ready(board),
    ActionRef.PlaceBannerResource, p1)

  test("placing resources moves faceup secrets onto the held banner for no Supply"):
    val started = start(holding.ready)
      .getOrElse(fail("a held banner must allow placing"))
    val banner = rules.resolveWalker(started.state, p1,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(ds))
      .getOrElse(fail("the banner must be accepted"))
    val done = rules.resolveWalker(banner.state, p1,
      "place-banner-resource.amount", DecisionAnswer.ChooseAmountAnswer(2))
      .getOrElse(fail("the amount must be accepted"))
    val Ready(after) = done.state: @unchecked
    assertEquals(Look(after).faceUpSecrets(p1), 6 - 2)
    assertEquals(Look(after).faceDownSecrets(p1), 4)
    assertEquals(Look(after).supply(p1), 7)
    assertEquals(after.game.current.banners.darkestSecret.secrets, 1 + 2)
    assertEquals(after.game.current.walkerPending, None)

  test("the amount ranges from one to the actor's relevant resources"):
    val started = start(holding.ready).getOrElse(fail("must start"))
    val banner = rules.resolveWalker(started.state, p1,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(ds))
      .getOrElse(fail("banner"))
    Vector(0, 6 + 1).foreach(amount =>
      assert(rules.resolveWalker(banner.state, p1,
        "place-banner-resource.amount",
        DecisionAnswer.ChooseAmountAnswer(amount)).isLeft, s"amount $amount"))
    assert(rules.resolveWalker(banner.state, p1,
      "place-banner-resource.amount",
      DecisionAnswer.ChooseAmountAnswer(6)).isRight)

  test("the amount question names the banner and the resource it takes"):
    // A banner is named as it is printed, and each takes one resource, so
    // the question says which rather than "resources on darkest-secret".
    assertEquals(oathdigital.gameplay.actions.challenge
      .PlaceBannerResourceProcedure.amountHeading(Banner.DarkestSecret),
      "Place secrets on Darkest Secret")
    assertEquals(oathdigital.gameplay.actions.challenge
      .PlaceBannerResourceProcedure.amountHeading(Banner.PeoplesFavor),
      "Place favor on People's Favor")

  test("only a banner the actor holds is offered"):
    val started = start(holding.ready).getOrElse(fail("must start"))
    assert(rules.resolveWalker(started.state, p1,
      "place-banner-resource.banner", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Banner(Banner.PeoplesFavor))).isLeft)

  test("a start with no held banner or no resources is rejected"):
    val unheld = Table.start.secrets(p1, faceUp = 6).ready
    assertEquals(start(unheld),
      Left(NoPlayableOption("place-banner-resource")))
    val broke = holding.secrets(p1, faceUp = 0, faceDown = 4).ready
    assertEquals(start(broke), Left(NoPlayableOption("place-banner-resource")))
