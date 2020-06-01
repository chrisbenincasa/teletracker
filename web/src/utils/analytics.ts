import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants/';

export const initGA = () => {
  if (process.env.NODE_ENV !== 'production') {
    console.log('GA: Initialization');
  }
  ReactGA.initialize(GA_TRACKING_ID);
};

export const setUser = (user: string) => {
  if (process.env.NODE_ENV !== 'production') {
    console.log(`GA: Set user ${user}`);
  }
  ReactGA.set({ userId: user });
};

export const logPageView = (pathName: string) => {
  if (process.env.NODE_ENV !== 'production') {
    console.log(`GA: Logging pageview for ${pathName}`);
  }
  ReactGA.set({ page: pathName });
  ReactGA.pageview(pathName);
};

export const logModalView = (modalName: string) => {
  if (process.env.NODE_ENV !== 'production') {
    console.log(`GA: Logging modal view for ${modalName}`);
  }
  ReactGA.modalview(modalName);
};

export const logEvent = (
  category: string = '',
  action: string = '',
  label?: string,
  value?: number,
  nonInteraction?: boolean,
) => {
  if (process.env.NODE_ENV !== 'production') {
    console.log(
      `GA: Logging event for ${category} & ${action} & ${label} & ${value} & ${nonInteraction}`,
    );
  }
  ReactGA.event({ category, action, label, value, nonInteraction });
};

export const logException = (description: string = '', fatal = false) => {
  if (description) {
    if (process.env.NODE_ENV !== 'production') {
      console.log(`GA: Logging exception for ${description}`);
    }
    ReactGA.exception({ description, fatal });
  }
};
