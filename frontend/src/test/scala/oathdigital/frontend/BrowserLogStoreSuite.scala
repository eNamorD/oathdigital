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
    present(LogStore.browser, "a store over the installed localStorage")

  override protected def release(store: LogStore): Unit =
    if js.isUndefined(replaced) then js.special.delete(window, "localStorage")
    else jsObject.defineProperty(window, "localStorage", replaced)
    ()
