package oathdigital.application.gamelog

import oathdigital.model._
import LogScripts._

class GameLogActionLineSuite extends munit.FunSuite:
  private def lines(script: Script, viewer: Option[PlayerId]): Vector[String] =
    texts(format(script, viewer).filter(_.depth == 1))

  private def other(script: Script): PlayerId =
    script.players.find(_ != script.actor).get

  private val backs = Set("a Denizen", "a Vision", "a Relic")

  test("Search: the searcher reads the cards; everyone else reads their backs"):
    val script = search
    val mine = lines(script, Some(script.actor))
    val theirs = lines(script, Some(other(script)))
    assert(mine.exists(_.startsWith("Started Search")), mine)
    val drew = mine.find(_.startsWith("Drew ")).get
    assert(drew.contains(" from the World Deck and kept "), drew)
    val seen = theirs.find(_.startsWith("Drew ")).get
    assert(backs.exists(back => seen.contains(back.stripPrefix("a "))), seen)
    val entries = format(script, Some(other(script)))
    val drawn = entries.find(entry => text(entry).startsWith("Drew ")).get
    assert(!drawn.spans.exists(_.isInstanceOf[LogSpan.Card]), drawn.spans)

  test("Play Facedown Adviser: a start line, then where the card went"):
    val script = facedownAdviser
    val mine = lines(script, Some(script.actor))
    assert(mine.contains("Playing Facedown Adviser"), mine)
    assert(mine.exists(line => line.startsWith("Played ") ||
      line.startsWith("Discarded ")), mine)

  test("Muster: the start line waits for the cost, then names the card"):
    val script = muster
    val mine = lines(script, None)
    val start = mine.indexWhere(_.startsWith("Started Muster"))
    val mustered = mine.indexWhere(_.startsWith("Mustered "))
    assert(start >= 0 && mustered > start, mine)
    assert(mine(start).endsWith("−1 Supply"), mine(start))
    assert("^Mustered \\d+ warbands? with .+$".r.matches(mine(mustered)),
      mine(mustered))

  test("Trade: the start line waits for the cost, then says what it bought"):
    val script = trade
    val mine = lines(script, None)
    assert(mine.exists(_.startsWith("Started Trade")), mine)
    assert(mine.exists(line => "^Traded with .+ for (no|\\d+) secrets?$".r
      .matches(line)), mine)

  test("Take Wealth: one line naming the site, and no start line"):
    val script = takeWealth
    val mine = lines(script, None)
    assert(mine.exists(line => line.startsWith("Took 1 favor from ")), mine)
    assert(!mine.exists(_.startsWith("Started")), mine)

  test("a failed Recover: start line, one continued spend, then the failure"):
    val script = recoverFailed
    val mine = lines(script, None)
    assert(mine.exists(line => line.startsWith("Started Recover") &&
      line.endsWith("−1 Supply")), mine)
    assertEquals(mine.count(_ == "Continued Recover −1 Supply"), 1, mine)
    assert(mine.last.startsWith("Failed to recover at "), mine)

  test("a successful Recover names the relic to the recoverer only"):
    val script = recoverSucceeded
    val mine = format(script, Some(script.actor))
      .find(entry => text(entry).startsWith("Recovered ")).get
    assert(mine.spans.exists(_.isInstanceOf[LogSpan.Card]), mine.spans)
    val theirs = format(script, Some(other(script)))
      .find(entry => text(entry).startsWith("Recovered ")).get
    assert(!theirs.spans.exists(_.isInstanceOf[LogSpan.Card]) ||
      theirs.spans == mine.spans, theirs.spans)

  test("Forge: the payment, then the relic, named to the forger"):
    val script = forge
    val mine = lines(script, Some(script.actor))
    val start = mine.lastIndexWhere(_.startsWith("Started Forge"))
    assert(start >= 0, mine)
    assert(mine.drop(start).exists(_.startsWith("Placed ")), mine)
    val forged = format(script, Some(script.actor))
      .filter(entry => text(entry).startsWith("Forged ")).last
    assert(forged.spans.exists(_.isInstanceOf[LogSpan.Card]), forged.spans)
    assert(lines(script, Some(other(script))).contains("Forged a Relic"))

  test("Campaign: a start line naming kind and defender, and the winner"):
    val all = lines(forge, None)
    val start = all.indexWhere(_.startsWith("Started Campaign: "))
    val wins = all.indexWhere(line => line.endsWith(" wins!") ||
      line == "The bandits win!")
    assert(start >= 0 && wins > start, all)
    assert("^Started Campaign: (Raid|Conquest) against .+$".r
      .findPrefixOf(all(start)).nonEmpty, all(start))

  test("Challenge takes the banner from the bank; Place Banner Resource names the amount"):
    val all = lines(banners, None)
    assert(all.exists(_.startsWith("Started Challenge")), all)
    assert(all.exists(line => "^Took .+ from the bank with \\d+ (favor|secrets?)$"
      .r.matches(line)), all)
    assert(all.exists(line => "^Placed \\d+ (favor|secrets?) on .+$".r
      .matches(line)), all)

  test("Play Facedown Adviser: a card placed as an adviser or at the site"):
    val adviser = facedownAdviser("placed-adviser", Some("adviser-faceup"))
    assert(lines(adviser, None).exists(line => line.startsWith("Played ") &&
      line.endsWith(" as an adviser")), lines(adviser, None))
    val site = facedownAdviser("placed-site", Some("site"))
    assert(lines(site, None).exists(line => "^Played .+ to .+$".r.matches(line)),
      lines(site, None))
