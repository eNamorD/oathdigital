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
