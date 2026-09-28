package oathdigital.gameplay.powers.action

import oathdigital.gameplay.powers.{NoteText, PhasePowerCatalog, PlayerFacts,
  PowerFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.ParkedDecisionAssertions
import oathdigital.model._

class MurkyFountainSuite extends munit.FunSuite:
  import PaidActionHarness._
  import PowerFixture._

  private val walkerParked = new ParkedDecisionAssertions(catalog,
    phasePowerCatalog = PhasePowerCatalog.default(catalog))

  private val fountain = EdificeId("E15")
  private val source = DecisionOptionRef.Edifice(fountain)
  private val kind = PlayerFacts.forceKind(base, actor).toOption.get

  /** The edifice is at the pawn's site, or at a far site the actor rules, so
    * that in the second case only the pawn condition fails.
    */
  private def staged(side: EdificeSide = EdificeSide.Ruined,
      pawnAtEdifice: Boolean = true, supply: Int = 1): ReadyGame =
    val site = home(base)
    val far = base.game.current.map.inPlay.toVector.sortBy(_.value)
      .find(_ != site).get
    val placed = withEdifice(base, fountain, side,
      if pawnAtEdifice then site else far)
    val ruled = if pawnAtEdifice then placed else placed.updateCurrent(c =>
      c.copy(map = c.map.copy(sites = c.map.sites.updated(far,
        c.map.sites(far).copy(forces = SiteForces.Occupied(kind, 1))))))
    act(withBoard(ruled)(_.copy(supply = SupplyTrack(supply))))

  test("at its site it places a secret and gains Supply equal to the total"):
    val rules0 = rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields))
    val ready0 = staged()
    val done = use(rules0, ready0, MurkyFountain.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(player(end).board.supply, SupplyTrack(4))
    assertEquals(tokensOn(end, fountain), Tokens(0, 1))
    walkerParked.assertResumed(done.state, Phase.Act, actor)
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("a Doubler doubles the shields"):
    val rules0 = rules(defenseDice(DefenseDieFace.TwoShields,
      DefenseDieFace.Doubler))
    val end = ready(use(rules0, staged(), MurkyFountain.id, source)
      .toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(5))

  test("the gain is clamped at the track maximum"):
    val rules0 = rules(defenseDice(DefenseDieFace.TwoShields,
      DefenseDieFace.TwoShields))
    val end = ready(use(rules0, staged(supply = 5), MurkyFountain.id, source)
      .toOption.get.state)
    assertEquals(player(end).board.supply, SupplyTrack(7))

  test("a total of zero ends the Act phase without gaining Supply"):
    val rules0 = rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank))
    val ready0 = staged()
    val done = use(rules0, ready0, MurkyFountain.id, source).toOption.get
    val end = ready(done.state)
    walkerParked.assertResumed(done.state, Phase.Rest, actor)
    assertEquals(player(end).board.supply, SupplyTrack(1))
    assertEquals(tokensOn(end, fountain), Tokens(0, 1))
    assertEquals(replayed(rules0, ready0, done.events), end)
    assert(wireRoundTrips(done.events))

  test("with the pawn elsewhere the cost is paid, nothing rolls and the phase stays"):
    val away = staged(pawnAtEdifice = false)
    assert(usableIds(away).contains(MurkyFountain.id))
    val done = use(rules(), away, MurkyFountain.id, source).toOption.get
    val end = ready(done.state)
    assertEquals(tokensOn(end, fountain), Tokens(0, 1))
    assertEquals(secrets(end), secrets(away) - 1)
    assertEquals(player(end).board.supply, SupplyTrack(1))
    walkerParked.assertResumed(done.state, Phase.Act, actor)

  test("it is unusable without a faceup secret, or with a secret already on the card"):
    val noSecret = withBoard(staged())(_.copy(faceUpSecrets = 0))
    assert(!usableIds(noSecret).contains(MurkyFountain.id))
    assert(use(rules(), noSecret, MurkyFountain.id, source).isLeft)
    val rules0 = rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.OneShield))
    val first = ready(use(rules0, withBoard(staged())(_.copy(faceUpSecrets = 2)),
      MurkyFountain.id, source).toOption.get.state)
    assert(!usableIds(first).contains(MurkyFountain.id))

  test("the intact face is Marble Fountains and offers no Murky Fountain"):
    val intact = staged(side = EdificeSide.Intact)
    assert(!usableIds(intact).contains(MurkyFountain.id))
    assert(use(rules(), intact, MurkyFountain.id, source).isLeft)

  test("it writes its roll and the Supply it gained"):
    val done = use(rules(defenseDice(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields)), staged(), MurkyFountain.id, source).toOption.get
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 3",
        covers = true),
      NoteText.Said("gained", s"${actor.value} gained 3 Supply.", covers = false)))

  test("a total of zero writes that the Act phase ended"):
    val done = use(rules(defenseDice(DefenseDieFace.Blank, DefenseDieFace.Blank)),
      staged(), MurkyFountain.id, source).toOption.get
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said(NoteKey.Used, s"${actor.value} rolled 2 dice, Total: 0",
        covers = true),
      NoteText.Said("ended", s"${actor.value}'s Act phase ended.", covers = false)))

  test("with the pawn elsewhere it writes only that"):
    val done = use(rules(), staged(pawnAtEdifice = false), MurkyFountain.id,
      source).toOption.get
    assertEquals(NoteText.said(MurkyFountain, done.events), Vector(
      NoteText.Said("used.away", s"${actor.value} was not at its site.",
        covers = false)))
