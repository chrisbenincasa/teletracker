import { useEffect, useRef, useState } from 'react';
import createIntersectionObserver from '../utils/createIntersectionObserver';
import isIntersectionObserverSupported from '../utils/isIntersectionObserverSupported';

interface IUseIntersectionObserver {
  hasHistoryStateChanged?: boolean;
  lazyLoadOptions: IntersectionObserverInit;
  targetRef: React.RefObject<HTMLElement>;
  useLazyLoad: boolean;
}

export default function useIntersectionObserver({
  hasHistoryStateChanged = false,
  lazyLoadOptions,
  targetRef,
  useLazyLoad,
}: IUseIntersectionObserver) {
  const [isIntersecting, setIntersecting] = useState(false);
  const intersectionObserverRef = useRef<IntersectionObserver | null>();

  const handleObserverDisconnect = () => {
    if (intersectionObserverRef.current) {
      intersectionObserverRef.current.disconnect();
    }
  };

  const handleIntersection = () => {
    setIntersecting(true);
    handleObserverDisconnect();
  };

  const isIOSupported = isIntersectionObserverSupported();

  /*
    on 'popstate' the intersection observer causes a memory leak because the observer is still connected
    this effect cleans up the observer on 'popstate'
  */
  useEffect(() => {
    if (hasHistoryStateChanged) {
      handleObserverDisconnect();
    }
  }, [hasHistoryStateChanged]);

  useEffect(() => {
    if (!useLazyLoad || !isIOSupported) {
      setIntersecting(true);
    } else if (useLazyLoad && isIOSupported && targetRef.current) {
      intersectionObserverRef.current = createIntersectionObserver({
        callback: handleIntersection,
        options: lazyLoadOptions,
        targetEl: targetRef.current,
      });
    }

    return () => {
      handleObserverDisconnect();
    };
  }, [
    isIOSupported,
    lazyLoadOptions.root,
    lazyLoadOptions.rootMargin,
    lazyLoadOptions.threshold,
    useLazyLoad,
  ]);

  return isIntersecting;
}
