import React, { useEffect, useRef } from 'react';

// const useDidMountEffect = (func, deps) => {
//   const didMount = useRef(false);
//
//   useEffect(() => {
//     let unmount;
//     if (didMount.current) {
//       console.log('execing');
//       unmount = func();
//     } else {
//       console.log('execing curr');
//       didMount.current = true;
//     }
//
//     return () => {
//       didMount.current = false;
//       unmount && unmount();
//     };
//   }, deps);
// };

const useDidMountEffect = (func, deps) => {
  let firstRun = useRef(true);
  useEffect(() => {
    if (firstRun) {
      console.log('first run');
      firstRun.current = false;
    } else {
      console.log('next run');
      func();
    }

    return () => {
      console.log('unmounting');
      firstRun.current = true;
    };
  }, deps);
};

export default useDidMountEffect;
