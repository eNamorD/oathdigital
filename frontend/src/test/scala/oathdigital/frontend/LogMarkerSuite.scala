package oathdigital.frontend

class LogMarkerSuite extends munit.FunSuite:
  private final class FakeStore(initial: Map[String, String] = Map.empty) extends LogStore:
    var values = initial
    def read(key: String): Option[String] = values.get(key)
    def write(key: String, value: String): Unit = values += key -> value

  /** Stands in for `setTimeout`: runs nothing until `fire`. */
  private final class FakeTimer:
    var pending = Vector.empty[Option[() => Unit]]
    def schedule(run: () => Unit): () => Unit =
      pending :+= Some(run)
      val index = pending.size - 1
      () => pending = pending.updated(index, None)
    def live: Int = pending.count(_.nonEmpty)
    def fire(): Unit =
      val due = pending.flatten
      pending = Vector.empty
      due.foreach(_())

  test("the key names the game and the seat"):
    assertEquals(LogMarker.key("g1", "red-exile"), "oath.log.seen.g1.red-exile")

  test("opening a seat reads its stored sequence"):
    val store = FakeStore(Map("oath.log.seen.g1.red" -> "42"))
    assertEquals(new LogMarker(Some(store)).open("g1", "red"), Some(42L))
    assertEquals(new LogMarker(Some(store)).open("g1", "blue"), None)

  test("a stored value that is not a sequence reads as no marker"):
    val store = FakeStore(Map("oath.log.seen.g1.red" -> "soon"))
    assertEquals(new LogMarker(Some(store)).open("g1", "red"), None)

  test("a second at the end writes the last sequence; leaving first writes nothing"):
    val store = FakeStore()
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    marker.open("g1", "red")
    marker.observe(atEnd = true, last = Some(7))
    marker.observe(atEnd = true, last = Some(9))
    assertEquals(timer.live, 1)
    marker.observe(atEnd = false, last = Some(9))
    assertEquals(timer.live, 0)
    timer.fire()
    assertEquals(store.values, Map.empty[String, String])
    marker.observe(atEnd = true, last = Some(9))
    timer.fire()
    assertEquals(store.values, Map("oath.log.seen.g1.red" -> "9"))

  test("the write takes the newest sequence seen while waiting"):
    val store = FakeStore()
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    marker.open("g1", "red")
    marker.observe(atEnd = true, last = Some(7))
    marker.observe(atEnd = true, last = Some(12))
    timer.fire()
    assertEquals(store.values, Map("oath.log.seen.g1.red" -> "12"))

  test("flush writes a pending mark at once, and nothing when none is pending"):
    val store = FakeStore()
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    marker.open("g1", "red")
    marker.flush()
    assertEquals(store.values, Map.empty[String, String])
    marker.observe(atEnd = true, last = Some(5))
    marker.flush()
    assertEquals(store.values, Map("oath.log.seen.g1.red" -> "5"))
    assertEquals(timer.live, 0)

  test("opening another seat drops the first seat's pending mark"):
    val store = FakeStore()
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    marker.open("g1", "red")
    marker.observe(atEnd = true, last = Some(5))
    marker.open("g1", "blue")
    timer.fire()
    assertEquals(store.values, Map.empty[String, String])

  test("an observer, with no seat, reads nothing and schedules nothing"):
    val store = FakeStore(Map("oath.log.seen.g1." -> "3"))
    val timer = FakeTimer()
    val marker = new LogMarker(Some(store), timer.schedule)
    assertEquals(marker.open("g1", ""), None)
    marker.observe(atEnd = true, last = Some(5))
    assertEquals(timer.live, 0)

  test("with no store there is no marker and no timer"):
    val timer = FakeTimer()
    val marker = new LogMarker(None, timer.schedule)
    assertEquals(marker.open("g1", "red"), None)
    marker.observe(atEnd = true, last = Some(5))
    assertEquals(timer.live, 0)

  test("a browser that refuses storage gives no store"):
    val browser = new TestBrowser()
    try assertEquals(LogStore.browser, None)
    finally browser.close()
