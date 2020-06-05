import React from 'react';
import { AppState } from '../reducers';
import { bindActionCreators } from 'redux';
import { logout } from '../actions/auth';
import { connect } from 'react-redux';
import { LinearProgress } from '@material-ui/core';
import _ from 'lodash';
import { withRouter } from 'next/router';
import { WithRouterProps } from 'next/dist/client/with-router';

interface Props extends WithRouterProps {
  isLoggingOut: boolean;
  logout: () => void;
}

interface State {
  loggingOut: boolean;
}

class Logout extends React.Component<Props, State> {
  constructor(props: Readonly<Props>) {
    super(props);
    this.state = {
      loggingOut: true,
    };
  }

  componentDidMount(): void {
    this.props.logout();
  }

  componentDidUpdate(prevProps: Readonly<Props>): void {
    if (
      (_.isUndefined(prevProps.isLoggingOut) || prevProps.isLoggingOut) &&
      this.props.isLoggingOut === false
    ) {
      this.setState({
        loggingOut: false,
      });
    }
  }

  render() {
    if (this.state.loggingOut) {
      return <LinearProgress />;
    } else {
      this.props.router.replace('/');
    }
  }
}

const mapStateToProps = (appState: AppState) => {
  return {
    isLoggingOut: appState.auth.isLoggingOut,
  };
};

const mapDispatchToProps = dispatch =>
  bindActionCreators(
    {
      logout,
    },
    dispatch,
  );

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(Logout));
