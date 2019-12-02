import React, { ReactElement } from 'react';
import _ from 'lodash';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { getUserSelf } from '../actions/user';
import { AppState } from '../reducers';
import { LinearProgress } from '@material-ui/core';
import { UserSelf } from '../reducers/user';

export interface WithUserStateProps {
  isCheckingAuth: boolean;
  isLoggedIn: boolean;
  retrievingUser: boolean;
  userSelf?: UserSelf;
}

export interface WithUserDispatchProps {
  getUserSelf: (force: boolean) => void;
}

export type WithUserProps = WithUserStateProps & WithUserDispatchProps;

const withUser = <P extends object>(
  Component: React.ComponentType<P & WithUserProps>,
  loadingComponent?: (props: P & WithUserProps) => ReactElement | null,
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
        props.getUserSelf(false);
      }
    }, 100);

    render() {
      return <Component {...(this.props as P & WithUserProps)} />;
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
        getUserSelf,
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
