package oathdigital.frontend

import munit.FunSuite

class MapViewStateSuite extends FunSuite:
  private val bounds = MapBounds(500, 300, 1000, 600)

  test("initial fit includes the whole map without enlarging small content"):
    assertEquals(MapViewState().resize(bounds).scale, 0.5)
    assertEquals(MapViewState().resize(MapBounds(900, 800, 300, 200)).scale, 1.0)

  test("zoom preserves the world point at the viewport center"):
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds)
    assertEquals(manual.scale, 1.0)
    assertEquals(manual.left, 250.0)
    assertEquals(manual.top, 150.0)
    assert(!manual.fit)

  test("panning and resize keep the map recoverable"):
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds)
    val panned = manual.panTo(9999, -10, bounds)
    assertEquals(panned.left, 500.0)
    assertEquals(panned.top, 0.0)
    assertEquals(panned.resize(bounds.copy(viewWidth = 1200)).left, 0.0)

  test("zoom preserves the center when fitted content has horizontal or vertical margins"):
    val square = MapBounds(500, 300, 1000, 1000)
    val zoomed = MapViewState().resize(square).zoomBy(2, square)
    assertEquals(zoomed.scale, 0.6)
    assertEquals(zoomed.left, 50.0)
    assertEquals(zoomed.top, 150.0)
    val wide = MapBounds(300, 500, 1000, 1000)
    val portrait = MapViewState().resize(wide).zoomBy(2, wide)
    assertEquals(portrait.left, 150.0)
    assertEquals(portrait.top, 50.0)

  test("refresh retains manual navigation while fit mode follows viewport size"):
    val manual = MapViewState().resize(bounds).zoomBy(2, bounds).panTo(120, 80, bounds)
    assertEquals(manual.resize(bounds), manual)
    assertEquals(manual.resize(bounds.copy(viewWidth = 600)).scale, 1.0)
    assertEquals(MapViewState().resize(bounds.copy(viewWidth = 250)).scale, 0.25)

  test("zoom limits and temporarily zero-size containers produce finite geometry"):
    val huge = MapViewState().resize(bounds).zoomBy(100, bounds)
    assertEquals(huge.scale, 3.0)
    assertEquals(huge.zoomBy(0.001, bounds).scale, 0.5)
    val hidden = MapViewState().resize(MapBounds(0, 0, 0, 0))
    assert(hidden.scale > 0 && !hidden.scale.isInfinity && !hidden.scale.isNaN)

  test("map cards drop to name only below 0.6 scale and not at or above it"):
    // Measured working scales: fit-to-screen lands at 0.4524 and 0.5655,
    // comfortable reading at 0.71. Below 0.6 the token and suit lines are
    // noise while a name is still legible and is what a player scans for.
    assert(GameTableShell.compactAtScale(0.4524))
    assert(GameTableShell.compactAtScale(0.5655))
    assert(GameTableShell.compactAtScale(0.5999))
    assert(!GameTableShell.compactAtScale(0.6))
    assert(!GameTableShell.compactAtScale(0.71))
    assert(!GameTableShell.compactAtScale(1.0))

  test("zoom at an anchor keeps the world point under the anchor fixed"):
    val fitted = MapViewState().resize(bounds)
    val corner = fitted.zoomAt(2, 0, 0, bounds)
    assertEquals(corner.scale, 1.0)
    assertEquals(corner.left, 0.0)
    assertEquals(corner.top, 0.0)
    val far = fitted.zoomAt(2, 500, 300, bounds)
    assertEquals(far.left, 500.0)
    assertEquals(far.top, 300.0)

  test("zoom at the viewport centre is the button zoom"):
    val fitted = MapViewState().resize(bounds)
    assertEquals(fitted.zoomAt(2, 250, 150, bounds), fitted.zoomBy(2, bounds))

  test("one wheel notch is one button step in either delta mode"):
    assertEqualsDouble(MapViewState.wheelFactor(-100, 0, pinch = false, 300), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(100, 0, pinch = false, 300), 0.8, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(-3, 1, pinch = false, 300), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(-1, 2, pinch = false, 300), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(0, 0, pinch = false, 300), 1.0, 1e-9)

  test("a wheel flick is clamped to one step and a small delta is a fraction of one"):
    assertEqualsDouble(MapViewState.wheelFactor(-100000, 0, pinch = false, 300), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(100000, 0, pinch = false, 300), 0.8, 1e-9)
    val half = MapViewState.wheelFactor(-50, 0, pinch = false, 300)
    assert(half > 1.0 && half < 1.25, half.toString)

  test("a trackpad pinch delta is small, so it counts five times"):
    assertEqualsDouble(MapViewState.wheelFactor(-20, 0, pinch = true, 300), 1.25, 1e-9)
    assertEqualsDouble(MapViewState.wheelFactor(4, 0, pinch = true, 300),
      MapViewState.wheelFactor(20, 0, pinch = false, 300), 1e-9)
