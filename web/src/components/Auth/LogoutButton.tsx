import React, { Component } from 'react';
import { connect } from 'react-redux';
import { bindActionCreators, Dispatch } from 'redux';
import { logout } from '../../actions/auth';
import { Button } from '@material-ui/core';

interface AuthButtonProps {
  logout: () => void;
}

class AuthButton extends Component<AuthButtonProps> {
  render() {
    return (
      <Button
        color="inherit"
        onClick={e => {
          this.props.logout();
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

export default connect(null, mapDispatchToProps)(AuthButton);
