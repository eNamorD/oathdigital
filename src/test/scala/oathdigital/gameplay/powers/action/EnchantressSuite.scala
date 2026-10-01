package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, SearchFixture, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class EnchantressSuite extends munit.FunSuite:
  import TargetsFixture._

  private val enchantress = CatalogNames.denizen("Enchantress")
  private val source = DecisionOptionRef.Denizen(enchantress)
  private val plain = SearchFixture.denizensOf(Suit.Hearth)
  private val insomnia = CatalogNames.denizen("Insomnia")

  /** p1's Act holding Enchantress with `secrets` faceup secrets. p2 holds
    * plain(0) faceup with 1 favor and 2 secrets on it. */
  private def staged(secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).adviser(p1, enchantress)
      .secrets(p1, secrets).adviser(p2, plain(0))
      .tokens(plain(0), favor = 1, secrets = 2)

  private def choose(ready: ReadyGame, card: DenizenId) =
    val t = use(ready, Enchantress, source).toOption.get
    (t, answer(t, p1, Enchantress.decisionId,
      pick(DecisionOptionRef.Denizen(card))).toOption.get)

  test("it places a secret and offers other players' faceup advisers, not " +
      "its own, facedown or locked ones"):
    val ready = staged().adviser(p1, plain(1))
      .adviser(p2, plain(2), facedown = true).adviser(p3, insomnia).ready
    val t = use(ready, Enchantress, source).toOption.get
    assert(awaits(t, Enchantress.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(enchantress), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector("denizen" -> plain(0).value)))

  test("the two cards swap faceup, each with its favor and secrets"):
    val ready = staged().ready
    val (t, done) = choose(ready, plain(0))
    parked.assertResumed(done.state, Phase.Act, p1)
    val end = after(done)
    assertEquals(Look(end).player(p1).advisers, Vector[AdviserState](
      DenizenState(plain(0), Orientation.FaceUp, Tokens(1, 2))))
    assertEquals(Look(end).player(p2).advisers, Vector[AdviserState](
      DenizenState(enchantress, Orientation.FaceUp, Tokens(0, 1))))
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))

  test("its line names the card taken and its former owner"):
    val (_, done) = choose(staged().ready, plain(0))
    assertEquals(NoteText.said(Enchantress, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        s"${p1.value} swapped it for ${p2.value}'s ${plain(0).value}.",
        covers = false)))

  test("offered only a locked adviser, it asks nothing and the line says so"):
    val ready = Table.start.turn(p1, Phase.Act).adviser(p1, enchantress)
      .secrets(p1, 1).adviser(p2, insomnia).ready
    val done = use(ready, Enchantress, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).advisers(p2), Vector[CardId](insomnia))
    assertEquals(Look(after(done)).tokensOn(enchantress), Tokens(0, 1))
    assertEquals(NoteText.said(Enchantress, done.events), Vector(
      NoteText.Said("used.none", "No faceup adviser could be swapped.",
        covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).ready
    assert(!usableNow(broke).exists(_.power.id == Enchantress.id))
    assert(use(broke, Enchantress, source).isLeft)
