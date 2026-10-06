package oathdigital.gameplay.powers.action

import oathdigital.gameplay.PlayerSecretSummary
import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class AncientBindingSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val binding = CatalogNames.denizen("Ancient Binding")
  private val source = DecisionOptionRef.Denizen(binding)
  private val home = CatalogNames.site("Ancient City")
  /** Cards that each carry a secret on the staged table: p1's adviser and
    * relic, a denizen at p1's site, and p2's adviser. */
  private val elders = CatalogNames.denizen("Elders")
  private val whistle = CatalogNames.relic("Whistle")
  private val errandBoy = CatalogNames.denizen("Errand Boy")
  private val landWarden = CatalogNames.denizen("Land Warden")

  /** p1's Act at Ancient City holding Ancient Binding, with 2 faceup and 2
    * facedown secrets. Elders, Whistle, Errand Boy at Ancient City and p2's
    * Land Warden carry 1 secret each. p2 holds 1 faceup and 2 facedown
    * secrets, and the Darkest Secret with 2 on it. p3 holds 2 facedown. */
  private def staged: ReadyGame = Table.start.turn(p1, Phase.Act)
    .pawn(p1, at = home).adviser(p1, binding)
    .secrets(p1, faceUp = 2, faceDown = 2)
    .adviser(p1, elders).tokens(elders, secrets = 1)
    .relic(p1, whistle).tokens(whistle, secrets = 1)
    .denizen(errandBoy, at = home).tokens(errandBoy, secrets = 1)
    .adviser(p2, landWarden).tokens(landWarden, secrets = 1)
    .secrets(p2, faceUp = 1, faceDown = 2).darkestSecret(Some(p2), 2)
    .secrets(p3, faceUp = 0, faceDown = 2).ready

  /** The secrets `player`'s board lists on the player UI. */
  private def listed(ready: ReadyGame, player: PlayerId): Int =
    PlayerSecretSummary.derive(ready, player).fold(fail(_), _.totalSecrets)

  test("the actor burns their board and every secret on a card in play but " +
      "the one on Ancient Binding"):
    val done = accepted(use(staged, AncientBinding, source))
    assert(backToActing(done))
    val end = Look(after(done))
    assertEquals((end.faceUpSecrets(p1), end.faceDownSecrets(p1)), (0, 0))
    assertEquals(Vector[CardId](binding, elders, whistle, errandBoy,
      landWarden).map(card => end.tokensOn(card).secrets),
      Vector(1, 0, 0, 0, 0))

  test("every other player keeps one secret, faceup if they have one, and " +
      "the Darkest Secret keeps its own"):
    val end = after(accepted(use(staged, AncientBinding, source)))
    val look = Look(end)
    assertEquals((look.faceUpSecrets(p2), look.faceDownSecrets(p2)), (1, 0))
    assertEquals((look.faceUpSecrets(p3), look.faceDownSecrets(p3)), (0, 1))
    assertEquals(end.game.current.banners.darkestSecret.secrets, 2)

  test("afterwards each player's board lists one secret, the actor's being " +
      "the one on Ancient Binding"):
    val before = staged
    assertEquals(Vector(p1, p2, p3).map(listed(before, _)), Vector(8, 3, 2))
    val end = after(accepted(use(before, AncientBinding, source)))
    assertEquals(Vector(p1, p2, p3).map(listed(end, _)), Vector(1, 1, 1))

  test("its line counts every secret burnt"):
    val ready = staged
    val done = accepted(use(ready, AncientBinding, source))
    // p1's 2 left after the cost, the 4 on cards, p2's 2 and p3's 1.
    assertEquals(NoteText.said(AncientBinding, done.events), Vector(
      NoteText.Said(NoteKey.Used, "Burned 9 secrets.", covers = false)))
    assertEquals(replayed(ready, done.events), Right(done.state))
    assertWireRoundTrips(done.events)

  test("with nothing to burn the line says so"):
    // The cost takes both of p1's secrets; p2 and p3 hold one each.
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, binding)
      .secrets(p1, faceUp = 2).ready
    val done = accepted(use(ready, AncientBinding, source))
    assert(backToActing(done))
    assertEquals(NoteText.said(AncientBinding, done.events), Vector(
      NoteText.Said("used.none", "No secret was burned.", covers = false)))
