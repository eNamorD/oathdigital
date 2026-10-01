package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2}

class StorytellerSuite extends munit.FunSuite:
  import TargetsFixture._

  private val teller = CatalogNames.denizen("Storyteller")
  private val source = DecisionOptionRef.Denizen(teller)

  private def staged(favor: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, teller).favor(p1, favor)

  private def onBanner(ready: ReadyGame): Int =
    ready.game.current.banners.darkestSecret.secrets

  test("it places a favor, then a secret from the shared bank on a held " +
      "Darkest Secret"):
    val ready = staged().darkestSecret(Some(p2), 1).ready
    val t = use(ready, Storyteller, source).toOption.get
    parked.assertResumed(t.state, Phase.Act, p1)
    val end = after(t)
    assertEquals(Look(end).tokensOn(teller), Tokens(1, 0))
    assertEquals(onBanner(end), 2)
    assertEquals(end.game.current.banners.darkestSecret.holder, Some(p2))
    assertEquals(NoteText.said(Storyteller, t.events), Vector(NoteText.Said(
      NoteKey.Used, s"${p1.value} placed 1 secret on the Darkest Secret.",
      covers = false)))
    assertEquals(replayed(ready, t.events), Right(t.state))
    assert(PaidActionHarness.wireRoundTrips(t.events))

  test("a Darkest Secret nobody holds takes the secret too"):
    val t = use(staged().darkestSecret(None, 0).ready, Storyteller, source)
      .toOption.get
    assertEquals(onBanner(after(t)), 1)

  test("it is unusable without a favor"):
    val broke = staged(favor = 0).ready
    assert(!usableNow(broke).exists(_.power.id == Storyteller.id))
    assert(use(broke, Storyteller, source).isLeft)
