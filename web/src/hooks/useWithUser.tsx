import React, { useMemo } from 'react';
import { UserSelf } from '../reducers/user';
import { useDispatchAction } from './useDispatchAction';
import { getUserSelf } from '../actions/user';
import { createContext, useContext, useEffect } from 'react';
import useStateSelector, {
  useStateSelectorWithPrevious,
} from './useStateSelector';
import { useDebouncedCallback } from 'use-debounce';
import _ from 'lodash';
import useCustomCompareMemo from './useMemoCompare';

export interface WithUserState {
  isCheckingAuth: boolean;
  isLoggedIn: boolean;
  retrievingUser: boolean;
  userSelf?: UserSelf;
}

export const UserContext = createContext<WithUserState>({
  isCheckingAuth: false,
  isLoggedIn: false,
  retrievingUser: false,
});

export const WithUser = ({ children }) => {
  const userState = useWithUser();
  const memoedUserState = useCustomCompareMemo(
    () => ({ ...userState }),
    [userState],
    _.isEqual,
  );
  return (
    <UserContext.Provider value={memoedUserState}>
      {children}
    </UserContext.Provider>
  );
};

function useWithUser(): WithUserState {
  const [isCheckingAuth, wasCheckingAuth] = useStateSelectorWithPrevious(
    state => state.auth.checkingAuth,
  );
  const retrievingUser = useStateSelector(
    state => state.userSelf.retrievingSelf,
  );
  const userSelf = useStateSelector(state => state.userSelf.self, _.isEqual);
  const isLoggedIn = useStateSelector(state => state.auth.isLoggedIn);

  const getUser = useDispatchAction(getUserSelf);

  const [loadUser] = useDebouncedCallback(
    (
      checkingAuth: boolean,
      user: UserSelf | undefined,
      retrieving: boolean,
    ) => {
      console.log('attempting to load user');
      if (!checkingAuth && !user && !retrieving) {
        getUser(false);
      }
    },
    100,
  );

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

export function useWithUserContext(): WithUserState {
  return useContext(UserContext);
}
