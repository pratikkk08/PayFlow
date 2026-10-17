/** jsdom helpers for Tailwind responsive classes in layout tests. */

export function mockViewport(width: number): void {
  Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
  window.matchMedia = ((query: string) => {
    const minMatch = /min-width:\s*(\d+)px/.exec(query);
    const maxMatch = /max-width:\s*(\d+)px/.exec(query);
    let matches = false;
    if (minMatch) {
      matches = width >= Number(minMatch[1]);
    } else if (maxMatch) {
      matches = width <= Number(maxMatch[1]);
    }
    return {
      matches,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    };
  }) as typeof window.matchMedia;
}
