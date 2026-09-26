package oathdigital.application.gamelog

import LogScripts._

class GameLogExchangeSuite extends munit.FunSuite:
  private def lines(script: Script): Vector[String] =
    texts(format(script, None).filter(_.depth == 1))

  test("a declined negotiation names who ended it, and nothing else"):
    val script = negotiationDeclined
    val all = lines(script)
    assertEquals(all.count(_.startsWith("Negotiation ended by ")), 1, all)
    assert(!all.exists(_.startsWith("Negotiated with ")), all)
    assert(!all.exists(_.startsWith("Started")), all)

  test("an agreed negotiation names the other negotiators once"):
    val script = negotiationAgreed
    val all = lines(script)
    val negotiated = all.filter(_.startsWith("Negotiated with "))
    assertEquals(negotiated.size, 1, all)
    val partner = script.players.filterNot(_ == script.actor).map(name)
    assert(partner.exists(negotiated.head.contains), negotiated.head)

  test("a used power is named by its source card, once"):
    val all = lines(usePower)
    assertEquals(all.count(_.startsWith("Used ")), 1, all)
    assert(all.exists(_ == "Used Silver Tongue") ||
      all.exists(_.startsWith("Used a ")), all)
