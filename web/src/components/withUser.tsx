import _ from 'lodash';
import React from 'react';
import { connect } from 'react-redux';
import { bindActionCreators } from 'redux';
import { retrieveUser } from '../actions/user';
import { AppState } from '../reducers';
import { User } from '../types';

export interface WithUserStateProps {
  isCheckingAuth: boolean;
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

    loadUser = _.debounce((props: WithUserProps) => {
      if (
        !props.isCheckingAuth &&
        !this.props.userSelf &&
        !this.props.retrievingUser
      ) {
        this.props.retrieveUser(false);
      }
    }, 100);

    render() {
      return <Component {...this.props as P & WithUserProps} />;
    }
  }

  const mapStateToProps = (appState: AppState) => {
    return {
      isCheckingAuth: appState.auth.checkingAuth,
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
  )(WithUser as any);
};

export default withUser;
