# Panel UI verification

The approved design is implemented in the `feat/arcs-panel-ui` worktree. Existing Oath renderers and command callbacks supply the content; the table shell owns layout and local navigation.

## Automated checks

- Baseline: 114 frontend tests passed before implementation.
- Final: 120 frontend tests passed, including six map geometry regressions.
- Frontend fast linking and full optimization passed; Closure reported zero errors and warnings.
- ProductionFrontendRoutesSuite passed, verifying the packaged HTML, JavaScript, stylesheet, cache policy, and missing-asset handling.
- Architecture check passed for 189 production Scala files.
- Markdown link and whitespace checks passed.

The initial navigation tests failed before the model existed. A separate unequal-aspect-ratio test then reproduced the reviewer's zoom-centering finding (100px horizontal scroll instead of 50px). The corrected calculation accounts for both old and new centering margins; horizontal and vertical regression cases now pass.

## Browser checks

Used an isolated development server on port 8093 and its own `var/arcs-panel-ui-test` database. No existing user game was modified.

| Viewport | Four panels within bounds | Root scrolling |
| --- | --- | --- |
| 1440 × 900 | Yes | None |
| 1024 × 768 | Yes | None |
| 768 × 1024 | Yes | None |
| 390 × 844 | Yes | None |

Screenshots were inspected for wide and portrait arrangements. Opening the developer overlay left all four panel rectangles unchanged. Escape closed it and restored focus to its toggle. The reserved Game Log contained no raw authoritative events.

Map zoom, fit, and pointer drag worked. Dragging over Ancient City did not select it; subsequent keyboard activation did. Pawn placement and adviser selection/confirmation completed through existing game controls. Map scale and offsets remained at 71% and (54.5, 241) through the resulting projection and history updates and next-player transition.

On the portrait layout, keyboard scrolling moved the Players panel 80px horizontally. That position remained after a pawn-selection update; the new adviser decision opened at action scroll position zero. Local adviser selection retained the existing action scroll position. When an activated control disappeared, focus returned to the appropriate panel heading.

After the zoom-centering correction, measured world-center coordinates stayed approximately (750, 710) while scale increased from 0.4524 to 0.5655; subpixel rounding accounted for less than one world pixel of difference. Browser console inspection showed no errors for the final loaded page.

An unavailable-game URL showed the error in Actions while preserving the four-panel shell. Existing transport tests continue to cover disconnect/reconnect behavior. Native touch-device gestures were not tested on physical hardware; the map uses native overflow panning for touch.

## Review and delivery

A separate read-only review found one actionable issue, the centering calculation described above. The reviewer checked the correction and reported no remaining findings. Styles are appended to the existing shared stylesheet so both development and packaged routes serve the layout without new backend routes.

The branch and worktree are retained for integration. No merge or push is included in this task.
