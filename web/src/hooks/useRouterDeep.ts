import { useRouter } from 'next/router';
import { useEffect, useRef } from 'react';
import dequal from 'dequal';

export function useRouterDeep() {
  let router = useRouter();
  const ref = useRef(router);
  useEffect(() => {
    if (!dequal(ref.current, router)) {
      ref.current = router;
    }
  }, [router]);

  return ref.current;
}
