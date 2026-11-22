/**
 * Macierze korekcji barw (daltonizacja: symulacja Brettel/Viénot + przesunięcie utraconej informacji do kanałów
 * rozróżnialnych). Wstrzykiwane raz w layout.tsx, używane przez `filter: url(#…)` na #app-content-wrapper.
 */
const FILTERS = {
  protanopia: "1 0 0 0 0  -0.2549 1.2549 0 0 0  0.3031 -0.5451 1.242 0 0  0 0 0 1 0",
  deuteranopia: "1 0 0 0 0  -0.4375 1.4375 0 0 0  0.2625 -0.5625 1.3 0 0  0 0 0 1 0",
  tritanopia: "1 -0.3325 0.3325 0 0  0 0.6675 0.3325 0 0  0 0 1 0 0  0 0 0 1 0",
} as const;

export function ColorBlindFilters() {
  return (
    <svg aria-hidden="true" focusable="false" width="0" height="0" className="absolute h-0 w-0 overflow-hidden">
      <defs>
        {Object.entries(FILTERS).map(([mode, matrix]) => (
          <filter key={mode} id={`a11y-filter-${mode}`} colorInterpolationFilters="sRGB">
            <feColorMatrix type="matrix" values={matrix} />
          </filter>
        ))}
      </defs>
    </svg>
  );
}
