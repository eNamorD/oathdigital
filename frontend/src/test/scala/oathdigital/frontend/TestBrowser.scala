package oathdigital.frontend

import org.scalajs.dom
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/** Minimal DOM boundary for exercising our renderer and event handlers in Node. */
private[frontend] final class TestBrowser(search: String = "") {
  private val browser = js.eval("""(() => {
    const saved = { document: globalThis.document, window: globalThis.window };
    function node(tag) {
      const n = { tagName: tag, childNodes: [], attributes: {}, className: '',
        value: '', disabled: false, style: {}, ownText: '',
        appendChild(c) { this.childNodes.push(c); return c; },
        removeChild(c) { this.childNodes.splice(this.childNodes.indexOf(c), 1); return c; },
        setAttribute(k,v) { this.attributes[k] = String(v); if (k === 'class') this.className = String(v); },
        getAttribute(k) { return this.attributes[k] || null; },
        addEventListener() {}, focus() {}, select() {}, blur() {} };
      n.classList = { add(...xs) { n.className += ' ' + xs.join(' '); } };
      Object.defineProperty(n, 'lastChild', { get() { return this.childNodes.at(-1) || null; } });
      Object.defineProperty(n, 'textContent', {
        get() { return this.ownText + this.childNodes.map(c => c.textContent).join(''); },
        set(v) { this.ownText = v; this.childNodes = []; } });
      return n;
    }
    const urls = [], timers = [], copied = [], mount = node('main');
    globalThis.document = { hidden: false, createElement: node,
      createElementNS: (_, tag) => node(tag),
      createTextNode(t) { const n = node('#text'); n.textContent = t; return n; },
      addEventListener() {} };
    globalThis.window = { location: { search: '', pathname: '/' },
      navigator: { clipboard: { writeText(t) { copied.push(t); return Promise.resolve(); } } },
      history: { replaceState(_, title, url) { urls.push(url); } },
      setTimeout(f) { timers.push(f); return timers.length; },
      clearTimeout(id) { timers[id - 1] = null; } };
    for (const key of ['localStorage', 'sessionStorage'])
      Object.defineProperty(globalThis.window, key, { get() { throw Error('Unexpected credential storage'); } });
    Object.defineProperty(globalThis.document, 'cookie', { get() { throw Error('Unexpected cookie access'); } });
    return { mount, urls, copied,
      all() { const result = []; function walk(n) { result.push(n); n.childNodes.forEach(walk); }
        walk(mount); return result; },
      tick() { const f = timers.find(f => f); const i = timers.indexOf(f);
        if (f) { timers[i] = null; f(); } },
      close() { globalThis.document = saved.document; globalThis.window = saved.window; }
    };
  })()""").asInstanceOf[js.Dynamic]
  js.Dynamic.global.window.location.search = search
  val mount: dom.Element = browser.mount.asInstanceOf[dom.Element]
  def text: String = mount.textContent
  def nodes: Vector[dom.Element] = browser.all().asInstanceOf[js.Array[dom.Element]].toVector
  def byClass(name: String): Vector[dom.Element] =
    nodes.filter(_.asInstanceOf[js.Dynamic].className.asInstanceOf[String]
      .split(" ").contains(name))
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
