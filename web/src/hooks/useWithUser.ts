import { UserSelf } from '../reducers/user';
import { useDispatchAction } from './useDispatchAction';
import { getUserSelf } from '../actions/user';
import { useEffect } from 'react';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from './useStateSelector';
import _ from 'lodash';

export interface WithUserState {
  isCheckingAuth: boolean;
  isLoggedIn: boolean;
  retrievingUser: boolean;
  userSelf?: UserSelf;
}

export function useWithUser(): WithUserState {
  const [isCheckingAuth, wasCheckingAuth] = useStateSelectorWithPrevious(
    state => state.auth.checkingAuth,
  );
  const retrievingUser = useStateSelector(
    state => state.userSelf.retrievingSelf,
  );
  const userSelf = useStateSelector(state => state.userSelf.self);
  const isLoggedIn = useStateSelector(state => state.auth.isLoggedIn);

  const getUser = useDispatchAction(getUserSelf);

  const loadUser = _.debounce((checkingAuth, user, retrieving) => {
    if (!checkingAuth && !user && !retrieving) {
      getUser(false);
    }
  }, 100);

  useEffect(() => {
    loadUser(isCheckingAuth, userSelf, retrievingUser);
  }, []);

  useEffect(() => {
    if (wasCheckingAuth && !isCheckingAuth) {
      if (isLoggedIn) {
        loadUser(isCheckingAuth, userSelf, retrievingUser);
      }
    }
  }, [isCheckingAuth, wasCheckingAuth, userSelf, retrievingUser]);

  return {
    isCheckingAuth,
    isLoggedIn,
    userSelf,
    retrievingUser,
  };
}
