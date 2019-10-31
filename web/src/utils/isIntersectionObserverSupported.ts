/*
  this is the standard and recommended way to check if Intersection Observer is supported
  https://github.com/w3c/IntersectionObserver/issues/296
*/

export default function isIntersectionObserverSupported(): boolean {
  const windowRef = window as any;

  return (
    'IntersectionObserver' in windowRef &&
    'IntersectionObserverEntry' in windowRef &&
    'intersectionRatio' in windowRef.IntersectionObserverEntry.prototype
  );
}
