import React, { Component } from 'react';
import { connect } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router';
import { bindActionCreators, Dispatch } from 'redux';
import { logout } from '../../actions/auth';
import { push } from 'connected-react-router';
import { Button } from '@material-ui/core';

interface AuthButtonProps {
  logout: () => void;
}

class AuthButton extends Component<RouteComponentProps<any> & AuthButtonProps> {
  render() {
    return (
      <Button
        color="inherit"
        onClick={e => {
          this.props.logout();
          // TODO: Use state or react-router directly to do Redirect?
          push('/login');
        }}
      >
        Sign out
      </Button>
    );
  }
}

const mapDispatchToProps: (
  dispatch: Dispatch,
) => AuthButtonProps = dispatch => {
  return bindActionCreators(
    {
      logout,
    },
    dispatch,
  );
};

export default withRouter(
  connect(
    null,
    mapDispatchToProps,
  )(AuthButton),
);
