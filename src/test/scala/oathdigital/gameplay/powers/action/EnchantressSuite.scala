package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.NoteText
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, PhasePowerHarness, Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.{p1, p2, p3}
import oathdigital.testkit.Unwrap.accepted

class EnchantressSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val enchantress = CatalogNames.denizen("Enchantress")
  private val source = DecisionOptionRef.Denizen(enchantress)
  // Hearth denizens whose powers these tests do not use.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val landWarden = CatalogNames.denizen("Land Warden")
  private val newsFromAfar = CatalogNames.denizen("News from Afar")
  private val insomnia = CatalogNames.denizen("Insomnia")

  /** p1's Act holding Enchantress with `secrets` faceup secrets. p2 holds
    * Homesteaders faceup with 1 favor and 2 secrets on it. */
  /** The walk Enchantress started and the walk after `card` was chosen. */
  private def staged(secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, enchantress)
      .secrets(p1, secrets).adviser(p2, homesteaders)
      .tokens(homesteaders, favor = 1, secrets = 2)

  private def choose(ready: ReadyGame, card: DenizenId): UsedAndAnswered =
    useAndAnswer(ready, Enchantress, source,
      Enchantress.decisionId, DecisionOptionRef.Denizen(card))

  test("it places a secret and offers other players' faceup advisers, not " +
      "its own, facedown or locked ones"):
    val ready = staged().adviser(p1, landWarden)
      .adviser(p2, newsFromAfar, facedown = true).adviser(p3, insomnia).ready
    val t = accepted(use(ready, Enchantress, source))
    assert(awaits(t, Enchantress.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(enchantress), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> homesteaders.value)))

  test("the two cards swap faceup, each with its favor and secrets"):
    val ready = staged().ready
    val UsedAndAnswered(t, done) = choose(ready, homesteaders)
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).player(p1).advisers, Vector[AdviserState](
      DenizenState(homesteaders, Orientation.FaceUp, Tokens(1, 2))))
    assertEquals(Look(end).player(p2).advisers, Vector[AdviserState](
      DenizenState(enchantress, Orientation.FaceUp, Tokens(0, 1))))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assertWireRoundTrips(t.events ++ done.events)

  test("its line names the card taken and its former owner"):
    val done = choose(staged().ready, homesteaders).answered
    assertEquals(NoteText.said(Enchantress, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} swapped it for ${p2.value}'s ${homesteaders.value}.",
        covers = false)))

  test("offered only a locked adviser, it asks nothing and the line says so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, enchantress)
      .secrets(p1, 1).adviser(p2, insomnia).ready
    val done = accepted(use(ready, Enchantress, source))
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](insomnia))
    assertEquals(Look(after(done)).tokensOn(enchantress), Tokens(0, 1))
    assertEquals(NoteText.said(Enchantress, done.events), Vector(
      NoteText.Said("used.none", "No faceup adviser could be swapped.",
        covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).ready
    assert(!usableNow(broke).exists(_.power.id == Enchantress.id))
    assertEquals(use(broke, Enchantress, source).left.toOption,
      Some(OathViolation.InsufficientSecrets(1, 0)))
