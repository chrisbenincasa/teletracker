import React, { useEffect, useRef, useState } from 'react';
import createIntersectionObserver from '../utils/createIntersectionObserver';
import isIntersectionObserverSupported from '../utils/isIntersectionObserverSupported';

interface IUseIntersectionObserver {
  lazyLoadOptions: IntersectionObserverInit;
  targetRef: React.RefObject<HTMLElement>;
  // If true, detaches the IO after the first time the target is deemed as intersecting
  useLoadOnce: boolean;
}

export default function useIntersectionObserver({
  lazyLoadOptions,
  targetRef,
  useLoadOnce = true,
}: IUseIntersectionObserver) {
  const [isIntersecting, setIntersecting] = useState(false);
  const intersectionObserverRef = useRef<IntersectionObserver | null>();

  const handleObserverDisconnect = () => {
    if (intersectionObserverRef.current) {
      intersectionObserverRef.current.disconnect();
    }
  };

  const handleIntersection = entry => {
    setIntersecting(entry.isIntersecting);
    if (useLoadOnce && entry.isIntersecting) {
      handleObserverDisconnect();
    }
  };

  // Check if the users browser supports Intersection Observer
  const isIOSupported = isIntersectionObserverSupported();

  useEffect(() => {
    if (!isIOSupported) {
      // If component wants to bypass IO or if IO is not supported
      setIntersecting(true);
    } else if (isIOSupported && targetRef.current) {
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
    useLoadOnce,
  ]);

  return isIntersecting;
}
