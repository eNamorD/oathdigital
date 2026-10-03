package oathdigital.frontend

import oathdigital.testkit.Unwrap.present
import org.scalajs.dom
import scala.scalajs.js

/** `LogStore.browser`, held to the store contract over a working
  * `localStorage`: jsdom's own storage throws in this harness, so each test
  * installs a plain object with `getItem` and `setItem` as
  * `window.localStorage` and puts back what was there afterwards.
  */
class BrowserLogStoreSuite extends LogStoreContract:
  private val window = dom.window.asInstanceOf[js.Dynamic]
  private val jsObject = js.Dynamic.global.Object
  private var replaced: js.Dynamic = js.undefined.asInstanceOf[js.Dynamic]

  protected def make(): LogStore =
    replaced = jsObject.getOwnPropertyDescriptor(window, "localStorage")
    val items = js.Dictionary.empty[String]
    val getItem: js.Function1[String, String] =
      key => items.get(key).orNull
    val setItem: js.Function2[String, String, Unit] =
      (key, value) => items(key) = value
    val storage = js.Dynamic.literal(getItem = getItem, setItem = setItem)
    jsObject.defineProperty(window, "localStorage",
      js.Dynamic.literal(value = storage, configurable = true))
    // A fixture whose setup throws is never released, so put the storage
    // back here if there is no store to release.
    try present(LogStore.browser, "a store over the installed localStorage")
    catch
      case error: Throwable =>
        putBackLocalStorage()
        throw error

  override protected def release(store: LogStore): Unit =
    putBackLocalStorage()

  private def putBackLocalStorage(): Unit =
    if js.isUndefined(replaced) then js.special.delete(window, "localStorage")
    else jsObject.defineProperty(window, "localStorage", replaced)
    ()
