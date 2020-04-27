import React from 'react';
import { mount } from 'enzyme';
import Toolbar from '../../../src/components/Toolbar/Toolbar';
import configureMockStore from 'redux-mock-store';
import * as nextRouter from 'next/router';
import { Provider } from 'react-redux';
import reducers, { AppState } from '../../../src/reducers';
import { expect } from 'chai';

const mockStore = configureMockStore<Partial<AppState>>();

// @ts-ignore
nextRouter.useRouter = jest.fn() as jest.Mock<nextRouter.NextRouter>;
// @ts-ignore
nextRouter.useRouter.mockImplementation(() => ({ route: '/' }));

jest.mock('next/router');

describe('<Toolbar />', () => {
  test('Hide login button while booting', () => {
    const initialState = reducers()(undefined, { type: 'initial-state' });
    const store = mockStore(initialState);

   expect(initialState.startup.isBooting).to.be.true;

    const wrapper = mount(
      <Provider store={store}>
        <Toolbar
          drawerOpen={false}
          showToolbarSearch={false}
          onDrawerChange={() => {}}
        />
      </Provider>,
    );

    expect(
      wrapper
        .find(Toolbar)
        .find('.loginButton'),
    ).to.have.lengthOf(0);
  });
});
