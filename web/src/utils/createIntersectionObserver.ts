/*
  More information regarding Intersection Observer types can be found here:
  https://www.w3.org/TR/intersection-observer/#intersection-observer-interface

  IntersectionObserverInit and IntersectionObserverEntry are interfaces from the Intersection Observer API
*/

export function createIntersectionObserver({
  callback,
  options = {
    root: null,
    rootMargin: '50px',
    threshold: 0,
  },
  targetEl,
}: {
  callback: (entry: IntersectionObserverEntry) => void;
  options?: IntersectionObserverInit;
  targetEl: Element;
}): IntersectionObserver | null {
  if (!(targetEl && options && callback)) return null;

  const intersectionObserver = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        callback(entry);
      }
    });
  }, options);

  intersectionObserver.observe(targetEl);

  return intersectionObserver;
}

export default createIntersectionObserver;
