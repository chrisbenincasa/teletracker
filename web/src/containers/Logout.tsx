import React from 'react';
import { AppState } from '../reducers';
import { bindActionCreators } from 'redux';
import { logout } from '../actions/auth';
import { connect } from 'react-redux';
import { LinearProgress } from '@material-ui/core';
import { Redirect } from 'react-router';
import _ from 'lodash';
import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants/';

interface Props {
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

    ReactGA.initialize(GA_TRACKING_ID);
    ReactGA.pageview(window.location.pathname + window.location.search);
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
    return this.state.loggingOut ? (
      <LinearProgress />
    ) : (
      <Redirect to={'/login'} />
    );
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

export default connect(
  mapStateToProps,
  mapDispatchToProps,
)(Logout);
