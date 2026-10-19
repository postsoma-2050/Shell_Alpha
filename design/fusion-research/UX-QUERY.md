## UI Pro Max Search Results
**Domain:** ux | **Query:** animation accessibility z-index loading
**Source:** ux-guidelines.csv | **Found:** 3 results

### Result 1
- **Category:** Animation
- **Issue:** Loading States
- **Platform:** All
- **Description:** Show feedback during async operations
- **Do:** Use skeleton screens or spinners
- **Don't:** Leave UI frozen with no feedback
- **Code Example Good:** animate-pulse skeleton
- **Code Example Bad:** Blank screen while loading
- **Severity:** High

### Result 2
- **Category:** Animation
- **Issue:** Continuous Animation
- **Platform:** All
- **Description:** Infinite animations are distracting
- **Do:** Use for loading indicators only
- **Don't:** Use for decorative elements
- **Code Example Good:** animate-spin on loader
- **Code Example Bad:** animate-bounce on icons
- **Severity:** Medium

### Result 3
- **Category:** Layout
- **Issue:** Stacking Context
- **Platform:** Web
- **Description:** New stacking contexts reset z-index
- **Do:** Understand what creates new stacking context
- **Don't:** Expect z-index to work across contexts
- **Code Example Good:** Parent with z-index isolates children
- **Code Example Bad:** z-index: 9999 not working
- **Severity:** Medium

