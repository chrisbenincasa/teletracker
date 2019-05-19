import _ from 'lodash';
import React from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { retrieveUser } from '../actions/user';
import { AppState } from '../reducers';
import { User } from '../types';
import { LinearProgress } from '@material-ui/core';

export interface WithUserStateProps {
  isCheckingAuth: boolean;
  isLoggedIn: boolean;
  retrievingUser: boolean;
  userSelf?: User;
}

export interface WithUserDispatchProps {
  retrieveUser: (force: boolean) => void;
}

export type WithUserProps = WithUserStateProps & WithUserDispatchProps;

const withUser = <P extends object>(
  Component: React.ComponentType<P & WithUserProps>,
) => {
  class WithUser extends React.Component<P & WithUserProps> {
    componentWillMount() {
      this.loadUser(this.props);
    }

    componentDidUpdate(oldProps: P & WithUserProps) {
      if (oldProps.isCheckingAuth && !this.props.isCheckingAuth) {
        // Only load the user if we've successfully authenticated the persisted token
        if (this.props.isLoggedIn) {
          this.loadUser(this.props);
        } else {
          // Redirect to login
        }
      }
    }

    loadUser = _.debounce((props: WithUserProps) => {
      if (!props.isCheckingAuth && !props.userSelf && !props.retrievingUser) {
        props.retrieveUser(false);
      }
    }, 100);

    render() {
      // Loading until we've both confirmed we're logged in (auth check) and retrieved
      // the current user from the server
      let isLoading = !this.props.userSelf || !this.props.isLoggedIn;

      return isLoading ? (
        <div style={{ flexGrow: 1 }}>
          <LinearProgress />
        </div>
      ) : (
        <Component {...this.props as P & WithUserProps} />
      );
    }
  }

  const mapStateToProps = (appState: AppState) => {
    return {
      isCheckingAuth: appState.auth.checkingAuth,
      isLoggedIn: appState.auth.isLoggedIn,
      retrievingUser: appState.userSelf.retrievingSelf,
      userSelf: appState.userSelf.self,
    };
  };

  const mapDispatchToProps = dispatch =>
    bindActionCreators(
      {
        retrieveUser,
      },
      dispatch,
    );

  return connect(
    mapStateToProps,
    mapDispatchToProps,
  // @ts-ignore
  )(WithUser);
};

export default withUser;
