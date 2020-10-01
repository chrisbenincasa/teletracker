import React, { createContext } from 'react';

export interface AppContextState {
  // specific pages informing the app that they are now visible.
  // some pages have special requirements for app layout that need to listen
  // to this callback.
  readonly inViewportChange: (inViewport: boolean) => void;
}

export const AppContext = createContext<AppContextState>({
  inViewportChange: () => {},
});
