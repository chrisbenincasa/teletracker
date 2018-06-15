// import AppNavigation from '../Navigation/AppNavigation'
import State from './State';
import { NavigationAction } from 'react-navigation';

export const reducer = (state: State, action: NavigationAction) => {
  // const newState: State = AppNavigation.router.getStateForAction(action, state)
  return state || {};
}
