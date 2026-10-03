package oathdigital.frontend

/** Fake of `LogStore`: a working in-memory map in place of `localStorage`,
  * shared by the suites that need a log's place without a browser. `values`
  * is the whole store, from `initial`, which a test reads to see what was
  * written. `LogStoreContract` holds it to what the real store promises.
  */
private[frontend] final class FakeLogStore(
    initial: Map[String, String] = Map.empty) extends LogStore:
  var values: Map[String, String] = initial
  def read(key: String): Option[String] = values.get(key)
  def write(key: String, value: String): Unit = values += key -> value
