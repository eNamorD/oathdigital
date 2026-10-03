package oathdigital.application.gamelog

import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Table.{p1, p2, p3}
import LogScripts._

/** The negotiation scripts stand p2 with the actor, p1, at Ancient City:
  * p2 is the partner and p3 the bystander. */
class GameLogExchangeSuite extends munit.FunSuite:
  private def lines(script: Script): Vector[String] =
    texts(format(script, None).filter(_.depth == 1))

  private val partner = p2
  private val bystander = p3

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
    assert(negotiated.head.contains(playerLabel(partner)), negotiated.head)

  test("an agreed deal's favor transfer names giver and recipient"):
    val script = negotiationAgreed
    assert(lines(script).contains(s"Negotiated with ${playerLabel(partner)}"),
      lines(script))
    // The script's deal gives the partner one favor.
    assert(lines(script).contains(
      s"${playerLabel(script.actor)} gave 1 favor to ${playerLabel(partner)}"),
      lines(script))

  test("a disclosed adviser is named to its owner and recipient, by slot to others"):
    val script = negotiationDisclosed
    val shown = (viewer: Option[PlayerId]) =>
      val all = texts(format(script, viewer))
      all.find(_.contains(" showed ")).getOrElse(
        fail(s"no disclosure line for $viewer in $all"))
    assert(shown(None).startsWith(playerLabel(partner)), shown(None))
    // Birdsong is p2's only adviser, so it is slot 1.
    assert(!shown(Some(script.actor)).endsWith("(slot 1)"),
      shown(Some(script.actor)))
    assert(!shown(Some(partner)).endsWith("(slot 1)"), shown(Some(partner)))
    val (giver, recipient) = (playerLabel(partner), playerLabel(script.actor))
    assertEquals(shown(Some(bystander)),
      s"$giver showed $recipient facedown adviser (slot 1)")

  test("a used power is named by its source card, once"):
    val all = texts(formatWithoutNotes(silverTongue, None).filter(_.depth == 1))
    assertEquals(all.count(_.startsWith("Used ")), 1, all)
    // A faceup adviser, so every viewer reads its name.
    assert(all.contains("Used Silver Tongue"), all)

  test("a banner's power is named by its banner, not its id"):
    // Any ready state will do: a banner's name is the same on every board.
    val ready = Table.start.ready
    val words = new LogWords(catalog, presentation)
    assertEquals(words.power(ready, p1,
      PowerId("banner.darkest-secret.wandering-flame.move"), None)
      .map(_.text).mkString, "Darkest Secret")
