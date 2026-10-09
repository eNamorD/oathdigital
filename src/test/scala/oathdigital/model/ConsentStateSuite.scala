package oathdigital.model

import oathdigital.testkit.Unwrap.accepted

/** The consent ledger's transitions (consent design, "State" and rulings
  * 9, 12, 14, 15 and 17). */
class ConsentStateSuite extends munit.FunSuite:
  private val red = PlayerId("red")
  private val blue = PlayerId("blue")
  private val green = PlayerId("green")
  private val pass = PowerId("site.narrow-pass.pass")
  private val police = PowerId("denizen.secret-police")
  private val woods = DecisionOptionRef.Site(SiteId("site:deep-woods"))
  private val turn = ConsentTurn(1, blue)

  private def request(asks: ConsentAsk*): ConsentRequest =
    ConsentRequest(blue, "travel.destination", woods, asks.toVector)
  private def key(power: PowerId): ConsentKey =
    ConsentKey(power, blue, "travel.destination", woods)
  private def waitingOn(asks: ConsentAsk*): ConsentState =
    accepted(ConsentState().requested(request(asks*)))
  private val redOnPass = ConsentAsk(red, Vector(pass))
  private val greenOnPolice = ConsentAsk(green, Vector(police))

  test("a grant from the only ask is remembered for the turn and ends the request"):
    val granted = accepted(waitingOn(redOnPass).answered(red, granted = true,
      turn))
    assertEquals(granted.pending, None)
    assertEquals(granted.remembered(key(pass), turn),
      Some(ConsentAnswer.Granted))

  test("a refusal settles the request at once and refuses every key"):
    val refused = accepted(waitingOn(redOnPass, greenOnPolice)
      .answered(green, granted = false, turn))
    assertEquals(refused.pending, None)
    assertEquals(refused.remembered(key(pass), turn),
      Some(ConsentAnswer.Refused))
    assertEquals(refused.remembered(key(police), turn),
      Some(ConsentAnswer.Refused))

  test("a grant with another ask still open keeps the request waiting"):
    val partial = accepted(waitingOn(redOnPass, greenOnPolice)
      .answered(red, granted = true, turn))
    assertEquals(partial.pending.map(_.open.map(_.granter)),
      Some(Vector(green)))
    assertEquals(partial.remembered(key(pass), turn), None)

  test("answers expire with their turn, and the next turn's replace them"):
    val granted = accepted(waitingOn(redOnPass).answered(red, granted = true,
      turn))
    val later = ConsentTurn(1, red)
    assertEquals(granted.remembered(key(pass), later), None)
    val next = accepted(accepted(granted.requested(request(greenOnPolice)))
      .answered(green, granted = false, later))
    assertEquals(next.answers.keySet, Set(key(police)))

  test("only an asked granter answers, and only the requester withdraws"):
    val open = waitingOn(redOnPass)
    assert(open.answered(green, granted = true, turn).isLeft)
    assert(open.withdrawn(red).isLeft)
    assertEquals(accepted(open.withdrawn(blue)), ConsentState())

  test("a second request while one waits is refused, as is one with no ask"):
    assert(waitingOn(redOnPass).requested(request(greenOnPolice)).isLeft)
    assert(ConsentState().requested(request()).isLeft)

  test("a preference is set, replaced, removed by case by case, and " +
      "cleared per granter and power"):
    val always = ConsentState().preferenceSet(red, pass, blue,
      ConsentSetting.Always)
    assertEquals(always.preference(red, pass, blue),
      Some(ConsentPreference.Always))
    val never = always.preferenceSet(red, pass, blue, ConsentSetting.Never)
    assertEquals(never.preference(red, pass, blue),
      Some(ConsentPreference.Never))
    assertEquals(never.preferenceSet(red, pass, blue,
      ConsentSetting.CaseByCase), ConsentState())
    val three = never.preferenceSet(red, pass, green, ConsentSetting.Always)
      .preferenceSet(red, police, blue, ConsentSetting.Always)
    assertEquals(three.cleared(red, pass).preferences.keySet,
      Set((red, police, blue)))

  test("what an undo carries keeps answers and preferences, not a request"):
    val open = waitingOn(redOnPass).preferenceSet(red, pass, green,
      ConsentSetting.Always)
    assertEquals(open.carried, open.copy(pending = None))

  test("a choice's grant and the preference it sets"):
    assertEquals(ConsentChoice.values.toVector.map(choice =>
      (choice.key, choice.granted, choice.setting)), Vector(
      ("never", false, Some(ConsentSetting.Never)),
      ("no", false, None),
      ("yes", true, None),
      ("always", true, Some(ConsentSetting.Always))))
    assertEquals(ConsentSetting.fromKey("case-by-case"),
      Some(ConsentSetting.CaseByCase))
    assertEquals(ConsentChoice.fromKey("maybe"), None)
