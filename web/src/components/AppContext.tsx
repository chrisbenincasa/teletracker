import React, { createContext, ReactNode } from 'react';
import useCustomCompareMemo from '../hooks/useMemoCompare';
import dequal from 'dequal';
import { hookDeepEqual } from '../hooks/util';

export interface AppContextState {
  // specific pages informing the app that they are now visible.
  // some pages have special requirements for app layout that need to listen
  // to this callback.
  readonly inViewportChange: (inViewport: boolean) => void;
}

export const AppContext = createContext<AppContextState>({
  inViewportChange: () => {},
});

export interface WithAppContextProps {
  readonly inViewportCallback?: (inViewport: boolean) => void;
  readonly children: ReactNode;
}

const NOP: (unused: boolean) => void = () => {};

function WithAppContext(props: WithAppContextProps) {
  const cb = props.inViewportCallback || NOP;
  const contextState = useCustomCompareMemo<AppContextState>(
    () => {
      return {
        inViewportChange: cb,
      };
    },
    [cb],
    hookDeepEqual,
  );

  return (
    <AppContext.Provider value={contextState}>
      {props.children}
    </AppContext.Provider>
  );
}

export default React.memo(WithAppContext, dequal);
