package oathdigital.frontend

import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Real jsdom mount with observable browser boundaries.
  *
  * jsdom's `window` and `document` cannot be replaced, so the seams the trusted
  * UI touches (history, timers, clipboard, credential storage) are overridden in
  * place and restored by `close()`.
  */
private[frontend] final class TestBrowser(search: String = "") {
  private val browser: js.Dynamic = new js.Function("search", """return (() => {
    const urls = [], timers = [], copied = [];
    const saved = { href: window.location.href, setTimeout: window.setTimeout,
      clearTimeout: window.clearTimeout };
    const mount = document.createElement('main');
    document.body.appendChild(mount);
    window.history.replaceState(null, '', search === '' ? window.location.pathname : search);
    window.history.replaceState = function (_, title, url) { urls.push(url); };
    window.setTimeout = function (f) { timers.push(f); return timers.length; };
    window.clearTimeout = function (id) { timers[id - 1] = null; };
    const define = (target, key, descriptor) =>
      Object.defineProperty(target, key, Object.assign({ configurable: true }, descriptor));
    define(window.navigator, 'clipboard', { value: {
      writeText(t) { copied.push(t); return Promise.resolve(); } } });
    for (const key of ['localStorage', 'sessionStorage'])
      define(window, key, { get() { throw Error('Unexpected credential storage'); } });
    define(document, 'cookie', { get() { throw Error('Unexpected cookie access'); } });
    return { mount, urls, copied,
      all() { return [mount, ...mount.querySelectorAll('*')]; },
      tick() { const f = timers.find(f => f); const i = timers.indexOf(f);
        if (f) { timers[i] = null; f(); } },
      close() {
        delete window.history.replaceState;
        window.setTimeout = saved.setTimeout;
        window.clearTimeout = saved.clearTimeout;
        delete window.navigator.clipboard;
        delete window.localStorage; delete window.sessionStorage;
        delete document.cookie;
        window.history.replaceState(null, '', saved.href);
        mount.remove();
      }
    };
  })()""").asInstanceOf[js.Function1[String, js.Dynamic]](search)
  val mount: dom.Element = browser.mount.asInstanceOf[dom.Element]
  def text: String = mount.textContent
  def nodes: Vector[dom.Element] = browser.all().asInstanceOf[js.Array[dom.Element]].toVector
  def byClass(name: String): Vector[dom.Element] =
    nodes.filter(node => Option(node.getAttribute("class")).exists(_.split(" ").contains(name)))
  def input(label: String): dom.html.Input =
    nodes.find(_.getAttribute("aria-label") == label).get.asInstanceOf[dom.html.Input]
  def click(name: String): Unit =
    byClass(name).head.asInstanceOf[js.Dynamic].onclick(
      js.Dynamic.literal(preventDefault = (() => ()): js.Function0[Unit]))
  def tick(): Unit = { browser.tick(); () }
  def urls: Vector[String] = browser.urls.asInstanceOf[js.Array[String]].toVector
  def copied: Vector[String] = browser.copied.asInstanceOf[js.Array[String]].toVector
  def close(): Unit = { browser.close(); () }
  def settle: Future[Unit] = (1 to 12).foldLeft(Future.successful(())) {
    (previous, _) => previous.flatMap(_ => Future.successful(()))
  }
}
