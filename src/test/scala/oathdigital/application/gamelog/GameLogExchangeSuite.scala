package oathdigital.application.gamelog

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
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

  test("an agreed deal's favor transfer names giver and recipient"):
    val script = negotiationAgreed
    val partner = script.players.find(player => player != script.actor &&
      lines(script).contains(s"Negotiated with ${name(player)}")).get
    assert(lines(script).contains(
      s"${name(script.actor)} gave 1 favor to ${name(partner)}"),
      lines(script))

  test("a disclosed adviser is named to its owner and recipient, by slot to others"):
    val script = negotiationDisclosed
    val shown = (viewer: Option[PlayerId]) =>
      texts(format(script, viewer)).find(_.contains(" showed ")).get
    val partner = script.players.find(player => player != script.actor &&
      shown(None).startsWith(name(player))).get
    val third = script.players.find(player => player != script.actor &&
      player != partner).get
    assert(!shown(Some(script.actor)).endsWith("(slot 1)"),
      shown(Some(script.actor)))
    assert(!shown(Some(partner)).endsWith("(slot 1)"), shown(Some(partner)))
    assertEquals(shown(Some(third)),
      s"${name(partner)} showed ${name(script.actor)} facedown adviser (slot 1)")

  test("a used power is named by its source card, once"):
    val all = texts(formatWithoutNotes(usePower, None).filter(_.depth == 1))
    assertEquals(all.count(_.startsWith("Used ")), 1, all)
    // A faceup adviser, so every viewer reads its name.
    assert(all.contains("Used Silver Tongue"), all)

  test("a banner's power is named by its banner, not its id"):
    val script = board
    val ready = script.history.steps.last.after match
      case OathState.Ready(ready) => ready
      case other => fail(s"expected a ready game, got $other")
    val words = new LogWords(catalog, presentation)
    assertEquals(words.power(ready, script.actor,
      PowerId("banner.darkest-secret.wandering-flame.move"), None)
      .map(_.text).mkString, "Darkest Secret")
