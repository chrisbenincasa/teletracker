import { Dispatch, Action } from 'redux';
import { AppState } from '../reducers';
import { TeletrackerApi } from '../utils/api-client';
import { STARTUP } from '../constants';
import { ThunkAction } from 'redux-thunk';

interface StartupAction {
  type: typeof STARTUP;
}

const startupAction: () => StartupAction = () => ({
  type: STARTUP,
});

const startup: () => ThunkAction<
  void,
  AppState,
  null,
  Action<StartupAction>
> = () => {
  return (dispatch: Dispatch, stateFn: () => AppState) => {
    dispatch(startupAction());
    let state = stateFn();

    if (state.auth.token) {
      TeletrackerApi.instance.setToken(state.auth.token);
    }
  };
};

export default startup;
