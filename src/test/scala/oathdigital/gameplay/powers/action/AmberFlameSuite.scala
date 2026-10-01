package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, TargetsFixture}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Table}
import oathdigital.testkit.Table.{p1, p2, p3}

class AmberFlameSuite extends munit.FunSuite:
  import TargetsFixture._

  private val flame = CatalogNames.relic("Amber Flame")
  private val source = DecisionOptionRef.Relic(flame)

  /** p1's Act holding Amber Flame faceup with `secrets` faceup secrets. p2
    * stands at p1's site holding the People's Favor with `favor`; p3 stands
    * at its own site holding the Darkest Secret with 2, so it is never
    * offered. */
  private def staged(favor: Int = 2, secrets: Int = 1): Table =
    Table.start.turn(p1, Phase.Act).relic(p1, flame).secrets(p1, secrets)
      .pawn(p2, at = Table.homeOf(p1)).peoplesFavor(Some(p2), favor)
      .darkestSecret(Some(p3), 2)

  private def banner(ready: ReadyGame, which: Banner): Int = which match
    case Banner.PeoplesFavor => ready.game.current.banners.peoplesFavor.favor
    case Banner.DarkestSecret =>
      ready.game.current.banners.darkestSecret.secrets
  private def offer(which: Banner): (String, String) = "banner" -> which.key

  private def burn(ready: ReadyGame, which: Banner) =
    val t = use(ready, AmberFlame, source).toOption.get
    (t, answer(t, p1, AmberFlame.decisionId,
      pick(DecisionOptionRef.Banner(which))).toOption.get)

  test("it places a secret and offers the banners held at the site"):
    val t = use(staged().ready, AmberFlame, source).toOption.get
    assert(awaits(t, AmberFlame.decisionId),
      parked.parkedDecision(t.state).toString)
    assertEquals(Look(after(t)).tokensOn(flame), Tokens(0, 1))
    assertEquals(offered(t, p1), Some(Vector(offer(Banner.PeoplesFavor))))

  test("the People's Favor loses one favor, and the line says whose"):
    val ready = staged().ready
    val (t, done) = burn(ready, Banner.PeoplesFavor)
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(banner(after(done), Banner.PeoplesFavor), 1)
    assertEquals(replayed(ready, t.events ++ done.events), Right(done.state))
    assert(PaidActionHarness.wireRoundTrips(t.events ++ done.events))
    assertEquals(NoteText.said(AmberFlame, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} burned 1 favor from " +
        s"${p2.value}'s People's Favor.", covers = false)))

  test("the player's own Darkest Secret may be chosen, and loses a secret"):
    val ready = staged().darkestSecret(Some(p1), 2).ready
    val t = use(ready, AmberFlame, source).toOption.get
    assertEquals(offered(t, p1), Some(Vector(offer(Banner.PeoplesFavor),
      offer(Banner.DarkestSecret))))
    val done = answer(t, p1, AmberFlame.decisionId,
      pick(DecisionOptionRef.Banner(Banner.DarkestSecret))).toOption.get
    assertEquals(banner(after(done), Banner.DarkestSecret), 1)
    assertEquals(NoteText.said(AmberFlame, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${p1.value} burned 1 secret from " +
        s"${p1.value}'s Darkest Secret.", covers = false)))

  test("an empty banner burns nothing, and the line says so"):
    val (_, done) = burn(staged(favor = 0).ready, Banner.PeoplesFavor)
    assertEquals(banner(after(done), Banner.PeoplesFavor), 0)
    assertEquals(NoteText.said(AmberFlame, done.events), Vector(
      NoteText.Said("used.empty",
        s"${p2.value}'s People's Favor held nothing to burn.",
        covers = false)))

  test("with no banner held at the site, the cost stays paid"):
    val ready = staged().peoplesFavor(None, 2).ready
    val done = use(ready, AmberFlame, source).toOption.get
    parked.assertResumed(done.state, Phase.Act, p1)
    assertEquals(Look(after(done)).tokensOn(flame), Tokens(0, 1))
    assertEquals(banner(after(done), Banner.PeoplesFavor), 2)
    assertEquals(NoteText.said(AmberFlame, done.events), Vector(
      NoteText.Said("used.none", "No player at the site held a banner.",
        covers = false)))

  test("it is unusable without a secret"):
    val broke = staged(secrets = 0).ready
    assert(!usableNow(broke).exists(_.power.id == AmberFlame.id))
    assert(use(broke, AmberFlame, source).isLeft)
