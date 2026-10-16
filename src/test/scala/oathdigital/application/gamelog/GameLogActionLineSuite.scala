package oathdigital.application.gamelog

import oathdigital.gameplay.actions.cardplay.CardPlayProcedure.PlayedAs
import oathdigital.model._
import LogScripts._

class GameLogActionLineSuite extends munit.FunSuite:
  private def lines(script: Script, viewer: Option[PlayerId]): Vector[String] =
    texts(format(script, viewer).filter(_.depth == 1))

  private val backs = Set("a Denizen", "a Vision", "a Relic")

  test("Search: the searcher reads the cards; everyone else reads their backs"):
    val script = search
    val mine = lines(script, Some(script.actor))
    val theirs = lines(script, Some(script.otherSeat))
    assert(mine.exists(_.startsWith("Search −")), mine)
    val drew = lineStarting(mine, "Drew ")
    assert(drew.contains(" from the World Deck and kept "), drew)
    val seen = lineStarting(theirs, "Drew ")
    assert(backs.exists(back => seen.contains(back.stripPrefix("a "))), seen)
    val entries = format(script, Some(script.otherSeat))
    val drawn = entryStarting(entries, "Drew ")
    assert(!drawn.spans.exists(_.isInstanceOf[LogSpan.Card]), drawn.spans)

  test("Muster: the tag waits for the cost, then names the card"):
    val script = muster
    val mine = lines(script, None)
    // Muster's printed cost: 1 Supply.
    val start = mine.indexOf("Muster −1 Supply")
    val mustered = mine.indexWhere(_.startsWith("Mustered "))
    assert(start >= 0 && mustered > start, mine)
    assert("^Mustered \\d+ warbands? with .+$".r.matches(mine(mustered)),
      mine(mustered))

  test("Muster under Initiation Rite: the tag still waits for the " +
      "cost, which places a secret, and shows only the Supply"):
    val script = initiatedMuster
    val mine = lines(script, None)
    // Muster's printed cost: 1 Supply.
    val start = mine.indexOf("Muster −1 Supply")
    val mustered = mine.indexWhere(_.startsWith("Mustered "))
    assert(start >= 0 && mustered > start, mine)

  test("Muster under Vow of Wandering: one Muster line, then the vow's"):
    val script = vowMuster
    val mine = lines(script, None)
    assertEquals(mine.count(_.startsWith("Mustered ")), 1, mine)
    assert(mine.contains("Vow of Wandering: Gained 2 more warbands."), mine)

  test("Muster on Golem Legions: the line names the adviser as the source"):
    val mine = lines(golemMuster, None)
    // 1, plus 1 for Golem Legions, a faceup Arcane adviser.
    assert(mine.contains("Mustered 2 warbands with Golem Legions"), mine)

  test("Trade: the tag waits for the cost, then says what it bought"):
    val script = trade
    val mine = lines(script, None)
    assert(mine.exists(_.startsWith("Trade −")), mine)
    assert(mine.exists(line => "^Traded with .+ for (no|\\d+) secrets?$".r
      .matches(line)), mine)

  test("Take Wealth: one line naming the site, and no tag"):
    val script = takeWealth
    val mine = lines(script, None)
    // The take-wealth script lays one favor at p2's site.
    assert(mine.exists(line => line.startsWith("Took 1 favor from ")), mine)
    assert(!format(script, None).exists(_.kind == LogKind.Section))

  test("a failed Recover: tag, one continued spend, then the failure"):
    val script = recoverFailed
    val mine = lines(script, None)
    // Recover's printed cost, paid again for the one continue: 1 Supply.
    assert(mine.contains("Recover −1 Supply"), mine)
    assertEquals(mine.count(_ == "Continued −1 Supply"), 1, mine)
    assert(mine.last.startsWith("Failed to recover at "), mine)

  test("a successful Recover names the relic to the recoverer only"):
    val script = recoverSucceeded
    val mine = entryStarting(format(script, Some(script.actor)), "Recovered ")
    assert(mine.spans.exists(_.isInstanceOf[LogSpan.Card]), mine.spans)
    val theirs =
      entryStarting(format(script, Some(script.otherSeat)), "Recovered ")
    // The other seat reads that a relic was recovered, never which.
    assertEquals(theirs.spans.filterNot(_.isInstanceOf[LogSpan.Site]), Vector[LogSpan](
      LogSpan.Text("Recovered "), LogSpan.Text("a Relic"), LogSpan.Text(" at ")))

  test("Forge: the payment, then the relic, named to the forger"):
    val script = forge
    val mine = lines(script, Some(script.actor))
    val start = mine.lastIndexWhere(_.startsWith("Forge −"))
    assert(start >= 0, mine)
    assert(mine.drop(start).exists(_.startsWith("Placed ")), mine)
    val forged = format(script, Some(script.actor))
      .filter(entry => text(entry).startsWith("Forged ")).last
    assert(forged.spans.exists(_.isInstanceOf[LogSpan.Card]), forged.spans)
    assert(lines(script, Some(script.otherSeat)).contains("Forged a Relic"))

  test("Campaign: a tag, a line naming kind and defender, and the winner"):
    val all = lines(raid, None)
    val start = all.indexWhere(_.startsWith("Campaign −"))
    val wins = all.indexWhere(line => line.endsWith(" wins!") ||
      line == "The bandits win!")
    assert(start >= 0 && wins > start, all)
    assert("^(Raid|Conquest) against .+$".r.matches(all(start + 1)),
      all(start + 1))

  test("Challenge takes the banner from the bank; Place Banner Resource names the amount"):
    val all = lines(banners, None)
    assert(all.exists(_.startsWith("Challenge")), all)
    assert(all.exists(line => "^Took .+ from the bank with \\d+ (favor|secrets?)$"
      .r.matches(line)), all)
    assert(all.exists(line => "^Placed \\d+ (favor|secrets?) on .+$".r
      .matches(line)), all)

  test("a Vision played faceup is revealed, not played as an adviser"):
    val all = lines(oracle("revealed-vision", PlayedAs.Faceup), None)
    assert(all.exists(line => line.startsWith("Revealed ") &&
      line.contains("Vision")), all)
    assert(!all.exists(_.endsWith(" as an adviser")), all)
    assertEquals(all.count(_.startsWith("Revealed ")), 1, all)

  test("Play Facedown Adviser: a card placed as an adviser or at the site"):
    val adviser = facedownAdviser("placed-adviser", Some(PlayedAs.Faceup))
    assert(lines(adviser, None).exists(line => line.startsWith("Played ") &&
      line.endsWith(" as an adviser")), lines(adviser, None))
    val site = facedownAdviser("placed-site", Some(PlayedAs.AtSite))
    assert(lines(site, None).exists(line => "^Played .+ to .+$".r.matches(line)),
      lines(site, None))
