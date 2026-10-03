package oathdigital.frontend

import munit.FunSuite

class MapViewStateSuite extends FunSuite:
  private val bounds = MapBounds(viewWidth = 500, viewHeight = 300,
    contentWidth = 1000, contentHeight = 600)

  test("initial fit includes the whole map without enlarging small content"):
    // Fit = min(500/1000, 300/600) = 0.5.
    assertEquals(MapViewState().resize(bounds).scale, 0.5)
    // Content smaller than the view would fit at min(3, 4), but never
    // above 1.
    val small = MapBounds(viewWidth = 900, viewHeight = 800,
      contentWidth = 300, contentHeight = 200)
    assertEquals(MapViewState().resize(small).scale, 1.0)

  test("zoom preserves the world point at the viewport center"):
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds)
    // Fit 0.5 doubled is 1.0. The viewport centre (250, 150) sits over world
    // (500, 300), so at scale 1 left = 500 - 250 and top = 300 - 150.
    assertEquals(manual.scale, 1.0)
    assertEquals(manual.left, 250.0)
    assertEquals(manual.top, 150.0)
    assert(!manual.fit)

  test("panning and resize keep the map recoverable"):
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds)
    val panned = manual.panTo(9999, -10, bounds)
    // At scale 1 the content is 1000 wide in a 500 view, so left stops at
    // 500; a negative top stops at 0.
    assertEquals(panned.left, 500.0)
    assertEquals(panned.top, 0.0)
    // A 1200 view is wider than the 1000 content, so nothing is left to
    // scroll.
    assertEquals(panned.resize(bounds.copy(viewWidth = 1200)).left, 0.0)

  test("zoom preserves the center when fitted content has horizontal or vertical margins"):
    // Fit = min(500/1000, 300/1000) = 0.3; doubled is 0.6. Across, the
    // content (300) is inset (500 - 300) / 2 = 100, so left = (250 - 100) *
    // 2 - 250 = 50. Down, there is no inset, so top = 150 * 2 - 150 = 150.
    val square = MapBounds(viewWidth = 500, viewHeight = 300,
      contentWidth = 1000, contentHeight = 1000)
    val zoomed = MapViewState().resize(square).zoomBy(2, square)
    assertEquals(zoomed.scale, 0.6)
    assertEquals(zoomed.left, 50.0)
    assertEquals(zoomed.top, 150.0)
    // The same sums with the axes swapped.
    val wide = MapBounds(viewWidth = 300, viewHeight = 500,
      contentWidth = 1000, contentHeight = 1000)
    val portrait = MapViewState().resize(wide).zoomBy(2, wide)
    assertEquals(portrait.left, 150.0)
    assertEquals(portrait.top, 50.0)

  test("refresh retains manual navigation while fit mode follows viewport size"):
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds).panTo(120, 80, bounds)
    assertEquals(manual.resize(bounds), manual)
    // A manual scale of 1 stays: the new fit (0.5) is smaller.
    assertEquals(manual.resize(bounds.copy(viewWidth = 600)).scale, 1.0)
    // A fitted map follows the view: min(250/1000, 300/600) = 0.25.
    assertEquals(MapViewState().resize(bounds.copy(viewWidth = 250)).scale, 0.25)

  test("zoom limits and temporarily zero-size containers produce finite geometry"):
    // The largest scale is 3; the smallest is the fit, 0.5 here.
    val huge = MapViewState().resize(bounds).zoomBy(100, bounds)
    assertEquals(huge.scale, 3.0)
    assertEquals(huge.zoomBy(0.001, bounds).scale, 0.5)
    val hidden = MapViewState().resize(MapBounds(viewWidth = 0,
      viewHeight = 0, contentWidth = 0, contentHeight = 0))
    assert(hidden.scale > 0 && !hidden.scale.isInfinity && !hidden.scale.isNaN)

  test("map cards go compact where their name would fall under 11px on screen"):
    // Measured fit-to-screen scales: 0.36 at 1024x768 and 0.43 at 1440x900.
    // The name at rest is 15.2px in map space, so 0.72 is where it holds
    // 11px; at and above that the ordinary face reads.
    assert(GameTableShell.compactAtScale(0.36))
    assert(GameTableShell.compactAtScale(0.4524))
    assert(GameTableShell.compactAtScale(0.71))
    assert(!GameTableShell.compactAtScale(0.7237))
    assert(!GameTableShell.compactAtScale(0.8))
    assert(!GameTableShell.compactAtScale(1.0))

  test("the name box is the width one line of floor-sized text has on a compact card"):
    // A card on a site holding forces is 116.8px wide in map space, so on
    // screen it is 42px at 0.36 and 52.8px at 0.4524; less the border, that
    // is 3.64em and 4.62em of 11px text.
    assertEqualsDouble(GameTableShell.nameBoxEm(0.36).get, 3.64, 0.01)
    assertEqualsDouble(GameTableShell.nameBoxEm(0.4524).get, 4.62, 0.01)
    assertEqualsDouble(GameTableShell.nameBoxEm(0.2).get, 1.94, 0.01)
    assertEquals(GameTableShell.nameBoxEm(0.8), None)

  test("zoom at an anchor keeps the world point under the anchor fixed"):
    val fitted = MapViewState().resize(bounds)
    // The top-left corner keeps world (0, 0) under it.
    val corner = fitted.zoomAt(2, 0, 0, bounds)
    assertEquals(corner.scale, 1.0)
    assertEquals(corner.left, 0.0)
    assertEquals(corner.top, 0.0)
    // The bottom-right corner shows world (1000, 600) at fit 0.5; at scale 1
    // it keeps it, so left = 1000 - 500 and top = 600 - 300.
    val far = fitted.zoomAt(2, 500, 300, bounds)
    assertEquals(far.left, 500.0)
    assertEquals(far.top, 300.0)

  test("zoom at the viewport centre is the button zoom"):
    val fitted = MapViewState().resize(bounds)
    assertEquals(fitted.zoomAt(2, 250, 150, bounds), fitted.zoomBy(2, bounds))

  // `deltaMode` as the DOM reports it.
  private val Pixels = 0
  private val Lines = 1
  private val Pages = 2
  private val viewHeight = 300

  test("one wheel notch is one button step in either delta mode"):
    // A notch is 100 pixels or 3 lines, and a page is the 300 px view, which
    // is at least a notch. Scrolling up zooms in by one step (1.25); down
    // zooms out by 1 / 1.25 = 0.8.
    assertEqualsDouble(MapViewState.wheelFactor(-100, Pixels, pinch = false,
      viewHeight), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(100, Pixels, pinch = false,
      viewHeight), 0.8, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(-3, Lines, pinch = false,
      viewHeight), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(-1, Pages, pinch = false,
      viewHeight), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(0, Pixels, pinch = false,
      viewHeight), 1.0, 1e-9)

  test("a wheel flick is clamped to one step and a small delta is a fraction of one"):
    assertEqualsDouble(MapViewState.wheelFactor(-100000, Pixels,
      pinch = false, viewHeight), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(100000, Pixels,
      pinch = false, viewHeight), 0.8, 1e-9)
    val half = MapViewState.wheelFactor(-50, Pixels, pinch = false, viewHeight)
    assert(half > 1.0 && half < 1.25, half.toString)

  test("a trackpad pinch delta is small, so it counts five times"):
    // A pinch delta of 20 px counts as 5 * 20 = 100, a full notch.
    assertEqualsDouble(MapViewState.wheelFactor(-20, Pixels, pinch = true,
      viewHeight), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(4, Pixels, pinch = true,
      viewHeight),
      MapViewState.wheelFactor(20, Pixels, pinch = false, viewHeight), 1e-9)
