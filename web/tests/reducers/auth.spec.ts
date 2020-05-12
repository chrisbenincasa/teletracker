import reducer, { makeState } from '../../src/reducers/auth';
import { SignupInitiated, UserStateChange } from '../../src/actions/auth';

describe('auth reducer', () => {
  it('should', () => {
    const state = makeState();
    console.log(state.toJSON());
    const newState = reducer(state, UserStateChange(undefined));
    console.log(newState.toJSON());
  });

  it('should initiate signup', () => {
    const newState = reducer(
      makeState(),
      SignupInitiated({
        email: 'fake@test.com',
        username: 'username',
        password: 'password ',
      }),
    );
    console.log(newState.toJSON());
  });
});
